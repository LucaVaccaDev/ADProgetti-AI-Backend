package it.adprogetti.zio.hub.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import it.adprogetti.zio.hub.IntegrationTestBase;
import it.adprogetti.zio.hub.audit.AuditLogRepository;
import it.adprogetti.zio.hub.identity.UserAccount;
import it.adprogetti.zio.hub.identity.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
class AuthAndUsersTest extends IntegrationTestBase {

    private static final String PASSWORD = "correct-horse-battery";
    private final JsonMapper json = JsonMapper.builder().build();

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuditLogRepository audit;

    @BeforeEach
    void clean() {
        jdbc.execute("delete from refresh_tokens");
        jdbc.execute("delete from user_roles");
        jdbc.execute("delete from users");
        jdbc.execute("delete from audit_log");
        createUser("admin", "ADMINISTRATOR");
        createUser("mario", "COLLABORATOR");
    }

    private void createUser(String username, String role) {
        users.save(new UserAccount(UUID.randomUUID(), username, username, null, encoder.encode(PASSWORD),
                Set.of(role), Instant.now()));
    }

    private MvcResult login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")).andReturn();
    }

    private String accessToken(String username) throws Exception {
        MvcResult result = login(username, PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void migrationsSeedRolesAndPermissions() {
        assertThat(jdbc.queryForObject("select count(*) from roles", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "select count(*) from role_permissions where role_code='ADMINISTRATOR' and permission_code='TENDERS_ALL'",
                Integer.class)).isZero(); // D-06: administrator does not see tenders by default
    }

    @Test
    void healthIsPublicAndEverythingElseDeniedWithoutToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/anything-unknown")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginReturnsTokenAndSecureCookie() throws Exception {
        MvcResult result = login("admin", PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).contains("refresh_token=").contains("HttpOnly").contains("SameSite=Strict")
                .contains("Path=/api/auth");
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("passwordHash").doesNotContain("refresh_token");
        assertThat(audit.findByActionOrderByIdAsc("LOGIN")).hasSize(1);
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGenericAnswer() throws Exception {
        MvcResult wrong = login("admin", "wrong-password-xx");
        MvcResult unknown = login("nobody", "wrong-password-xx");
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getContentAsString()).isEqualTo(unknown.getResponse().getContentAsString());
        assertThat(wrong.getResponse().getContentAsString()).contains("Credenziali non valide.");
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(login("mario", "wrong-password-xx").getResponse().getStatus()).isEqualTo(401);
        }
        // Even the right password is now refused, with the same generic answer.
        MvcResult locked = login("mario", PASSWORD);
        assertThat(locked.getResponse().getStatus()).isEqualTo(401);
        assertThat(locked.getResponse().getContentAsString()).contains("Credenziali non valide.");
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesTheFamily() throws Exception {
        MvcResult first = login("admin", PASSWORD);
        String token1 = cookieValue(first);

        MvcResult second = mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", token1)))
                .andExpect(status().isOk()).andReturn();
        String token2 = cookieValue(second);
        assertThat(token2).isNotEqualTo(token1);

        // The old token is presented again: refused, and the new one is revoked too.
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", token1)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", token2)))
                .andExpect(status().isUnauthorized());
        assertThat(audit.findByActionOrderByIdAsc("REFRESH_REUSE")).hasSize(2);
    }

    @Test
    void refreshWithoutOrWithGarbageCookieIsRefused() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", "garbage")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        MvcResult login = login("admin", PASSWORD);
        String token = cookieValue(login);
        String access = json.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(access))
                .cookie(new Cookie("refresh_token", token))).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedOrForeignTokenIsRefused() throws Exception {
        String token = accessToken("mario");
        String tampered = token.substring(0, token.length() - 3) + "abc";
        mvc.perform(get("/api/me").header("Authorization", bearer(tampered))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", bearer(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("mario"))
                .andExpect(jsonPath("$.permissions").isArray());
    }

    @Test
    void collaboratorCannotManageUsersButAdministratorCan() throws Exception {
        String mario = accessToken("mario");
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(mario))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users").header("Authorization", bearer(mario))
                .contentType(MediaType.APPLICATION_JSON).content(newUser("x.user", PASSWORD, "COLLABORATOR")))
                .andExpect(status().isForbidden());

        String admin = accessToken("admin");
        mvc.perform(post("/api/admin/users").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(newUser("Anna.Rossi", PASSWORD, "COLLABORATOR")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("anna.rossi"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        assertThat(accessToken("anna.rossi")).isNotBlank();
        assertThat(audit.findByActionOrderByIdAsc("USER_CREATED")).hasSize(1);
    }

    @Test
    void userCreationValidatesInput() throws Exception {
        String admin = accessToken("admin");
        mvc.perform(post("/api/admin/users").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(newUser("short.pw", "short", "COLLABORATOR")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/users").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(newUser("bad.role", PASSWORD, "GOD")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/users").header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content(newUser("mario", PASSWORD, "COLLABORATOR")))
                .andExpect(status().isConflict());
    }

    @Test
    void lastActiveAdministratorCannotBeDisabledOrDemoted() throws Exception {
        String admin = accessToken("admin");
        UUID adminId = users.findByUsername("admin").orElseThrow().getId();
        mvc.perform(patch("/api/admin/users/" + adminId).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/users/" + adminId).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"COLLABORATOR\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void disablingAUserBlocksLoginAndRefresh() throws Exception {
        String admin = accessToken("admin");
        MvcResult marioLogin = login("mario", PASSWORD);
        String marioRefresh = cookieValue(marioLogin);
        UUID marioId = users.findByUsername("mario").orElseThrow().getId();
        mvc.perform(patch("/api/admin/users/" + marioId).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        assertThat(login("mario", PASSWORD).getResponse().getStatus()).isEqualTo(401);
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", marioRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void massAssignmentFieldsAreIgnored() throws Exception {
        String admin = accessToken("admin");
        UUID marioId = users.findByUsername("mario").orElseThrow().getId();
        mvc.perform(patch("/api/admin/users/" + marioId).header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Mario R.\",\"passwordHash\":\"x\",\"id\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Mario R."));
        assertThat(accessToken("mario")).isNotBlank(); // password unchanged
    }

    @Test
    void validationErrorsExposeNoInternals() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Exception").doesNotContain("at it.");
    }

    private static String newUser(String username, String password, String role) {
        return "{\"username\":\"" + username + "\",\"displayName\":\"Test User\",\"password\":\"" + password
                + "\",\"roles\":[\"" + role + "\"]}";
    }

    private static String cookieValue(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        return header.substring("refresh_token=".length(), header.indexOf(';'));
    }
}

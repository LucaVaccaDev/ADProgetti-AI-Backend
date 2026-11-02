package it.adprogetti.zio.hub.bootstrap;

import it.adprogetti.zio.hub.identity.CreateUserRequest;
import it.adprogetti.zio.hub.identity.RoleCodes;
import it.adprogetti.zio.hub.identity.UserRepository;
import it.adprogetti.zio.hub.identity.UserService;
import java.io.Console;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator from the command line (scripts/create-admin.sh).
 * Active only with --hub.bootstrap-admin.enabled=true. The password is typed at the prompt
 * (or taken from HUB_BOOTSTRAP_ADMIN_PASSWORD for automation); it is never logged.
 */
@Component
@ConditionalOnProperty(name = "hub.bootstrap-admin.enabled", havingValue = "true")
class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserService userService;
    private final UserRepository users;
    private final ConfigurableApplicationContext context;
    private final String username;
    private final String displayName;

    BootstrapAdminRunner(UserService userService, UserRepository users, ConfigurableApplicationContext context,
            @Value("${hub.bootstrap-admin.username:admin}") String username,
            @Value("${hub.bootstrap-admin.display-name:Amministratore}") String displayName) {
        this.userService = userService;
        this.users = users;
        this.context = context;
        this.username = username;
        this.displayName = displayName;
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 0;
        try {
            if (users.countWithRole(RoleCodes.ADMINISTRATOR) > 0) {
                log.error("An administrator already exists: nothing created.");
                exitCode = 1;
            } else {
                String password = readPassword();
                userService.create(new CreateUserRequest(username, displayName, null, password,
                        Set.of(RoleCodes.ADMINISTRATOR)), null, "bootstrap");
                log.info("Administrator '{}' created.", username);
            }
        } catch (RuntimeException e) {
            log.error("Administrator not created: {}", e.getMessage());
            exitCode = 1;
        }
        int code = exitCode;
        System.exit(org.springframework.boot.SpringApplication.exit(context, () -> code));
    }

    private static String readPassword() {
        String fromEnv = System.getenv("HUB_BOOTSTRAP_ADMIN_PASSWORD");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("No terminal available: set HUB_BOOTSTRAP_ADMIN_PASSWORD.");
        }
        char[] first = console.readPassword("Password amministratore (min 12 caratteri): ");
        char[] second = console.readPassword("Ripeti la password: ");
        if (!java.util.Arrays.equals(first, second)) {
            throw new IllegalStateException("Le password non coincidono.");
        }
        return new String(first);
    }
}

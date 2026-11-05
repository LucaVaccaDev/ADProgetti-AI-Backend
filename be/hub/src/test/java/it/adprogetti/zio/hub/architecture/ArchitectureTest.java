package it.adprogetti.zio.hub.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "it.adprogetti.zio.hub", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "it.adprogetti.zio.hub.";

    @ArchTest
    static final ArchRule common_depends_on_nothing_else = noClasses().that().resideInAPackage(BASE + "common..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + "identity..", BASE + "auth..",
                    BASE + "audit..", BASE + "security..", BASE + "api..");

    @ArchTest
    static final ArchRule audit_is_independent = noClasses().that().resideInAPackage(BASE + "audit..")
            .should().dependOnClassesThat().resideInAnyPackage(BASE + "identity..", BASE + "auth..",
                    BASE + "security..", BASE + "api..");

    @ArchTest
    static final ArchRule identity_does_not_know_auth_or_web = noClasses().that()
            .resideInAPackage(BASE + "identity..").should().dependOnClassesThat()
            .resideInAnyPackage(BASE + "auth..", BASE + "security..", BASE + "api..");

    @ArchTest
    static final ArchRule security_does_not_know_business_modules = noClasses().that()
            .resideInAPackage(BASE + "security..").should().dependOnClassesThat()
            .resideInAnyPackage(BASE + "identity..", BASE + "auth..", BASE + "api..");

    @ArchTest
    static final ArchRule nothing_depends_on_api = noClasses().that()
            .resideOutsideOfPackage(BASE + "api..").should().dependOnClassesThat()
            .resideInAPackage(BASE + "api..");

    @ArchTest
    static final ArchRule controllers_never_touch_entities_or_repositories = noClasses().that()
            .resideInAPackage(BASE + "api..").should().dependOnClassesThat()
            .areAnnotatedWith(jakarta.persistence.Entity.class)
            .orShould().dependOnClassesThat().areAssignableTo(org.springframework.data.repository.Repository.class);

    @ArchTest
    static final ArchRule controllers_are_in_api = classes().that()
            .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideInAPackage(BASE + "api..");

    @ArchTest
    static final ArchRule channel_is_independent_of_identity_and_web = noClasses().that()
            .resideInAPackage(BASE + "channel..").should().dependOnClassesThat()
            .resideInAnyPackage(BASE + "identity..", BASE + "auth..", BASE + "security..", BASE + "api..");

    /** D-04: the Hub never calls AI providers (or any other external service) on its own. */
    @ArchTest
    static final ArchRule hub_makes_no_outbound_http_calls = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("java.net.http..", "org.springframework.web.client..",
                    "org.springframework.web.reactive.function.client..", "org.apache.hc..");
}

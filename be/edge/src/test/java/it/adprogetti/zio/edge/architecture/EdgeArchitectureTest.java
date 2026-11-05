package it.adprogetti.zio.edge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "it.adprogetti.zio.edge", importOptions = ImportOption.DoNotIncludeTests.class)
class EdgeArchitectureTest {

    private static final String BASE = "it.adprogetti.zio.edge.";

    /** D-04: outbound HTTP is allowed only to local AI services, and only from the ai package. */
    @ArchTest
    static final ArchRule http_clients_only_in_ai = noClasses().that().resideOutsideOfPackage(BASE + "ai..")
            .should().dependOnClassesThat().resideInAnyPackage("java.net.http..",
                    "org.springframework.web.client..", "org.apache.hc..");

    @ArchTest
    static final ArchRule secrets_know_nothing_about_the_rest = noClasses().that()
            .resideInAPackage(BASE + "secrets..").should().dependOnClassesThat()
            .resideInAnyPackage(BASE + "channel..", BASE + "command..", BASE + "ai..", BASE + "health..");

    @ArchTest
    static final ArchRule commands_do_not_know_the_transport = noClasses().that()
            .resideInAPackage(BASE + "command..").should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web.socket..", BASE + "ai..");

    @ArchTest
    static final ArchRule the_hub_is_not_a_dependency = noClasses().should().dependOnClassesThat()
            .resideInAPackage("it.adprogetti.zio.hub..");
}

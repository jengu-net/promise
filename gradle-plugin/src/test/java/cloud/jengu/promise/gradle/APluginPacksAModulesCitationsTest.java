package cloud.jengu.promise.gradle;

import cloud.jengu.promise.Promise;
import cloud.jengu.promise.Proofs;
import cloud.jengu.promise.ToolsetPromises;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A module applying the plugin gets its catalogues, its production citations
 * and its tests' citations into its jar with no build code of its own; one
 * that does not is unchanged; and two products' jars on one classpath answer
 * for both.
 *
 * <p>Real builds, run by Gradle's own test kit against the framework this
 * build made, so what is proven is what a product's build would do.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class APluginPacksAModulesCitationsTest {

    private Path build;
    private BuildResult result;
    /** The second build: the ledger drawn after a run left its results. */
    private BuildResult reported;
    /** What the job's summary page would have been given. */
    private Path stepSummary;

    @BeforeAll
    void aProductsBuild() throws IOException {
        build = Files.createTempDirectory("promise-plugin");
        write("settings.gradle.kts", """
                rootProject.name = "products"
                include("one", "two", "plain")
                dependencyResolutionManagement {
                    repositories {
                        maven { url = uri("%s") }
                        mavenCentral()
                    }
                }
                """.formatted(System.getProperty("promise.repo").replace('\\', '/')));
        for (String product : List.of("one", "two")) {
            // Its tests are citation sites, never run here: a jar that waited
            // on them shows as the test task in the result, not as a failure.
            write(product + "/build.gradle.kts", """
                    plugins {
                        java
                        id("cloud.jengu.promise")
                    }
                    tasks.test { onlyIf { false } }
                    """);
            sources(product);
        }
        // The same sources, and no plugin: the framework named by hand, as
        // before, and nothing else.
        write("plain/build.gradle.kts", """
                plugins { java }
                dependencies {
                    implementation("cloud.jengu.promise:promise:%s")
                }
                """.formatted(System.getProperty("promise.version")));
        sources("plain");

        // The root is no module of its own, and applies the plugin for a
        // ledger of the whole product.
        write("build.gradle.kts", """
                plugins { id("cloud.jengu.promise") }
                promise { title.set("The products") }
                """);

        result = GradleRunner.create()
                .withProjectDir(build.toFile())
                .withPluginClasspath()
                .withArguments("jar", "--stacktrace")
                .forwardOutput()
                .build();

        // A run of one's tests, as the test task leaves it; two's never ran.
        write("one/build/test-results/test/TEST-one.GuardTest.xml", """
                <testsuite name="one.GuardTest" tests="1"><testcase name="holds" classname="one.GuardTest"/></testsuite>
                """);
        stepSummary = Files.createTempFile("step-summary", ".md");
        java.util.Map<String, String> environment = new java.util.HashMap<>(System.getenv());
        environment.put("GITHUB_STEP_SUMMARY", stepSummary.toString());
        reported = GradleRunner.create()
                .withProjectDir(build.toFile())
                .withPluginClasspath()
                .withEnvironment(environment)
                .withArguments("promiseReport", "--stacktrace")
                .forwardOutput()
                .build();
    }

    private void sources(String product) throws IOException {
        write(product + "/src/main/java/" + product + "/Promises.java", """
                package %1$s;
                import cloud.jengu.promise.Catalogue;
                import cloud.jengu.promise.Promise;
                @Catalogue(namespace = "REQ-%2$s")
                public enum Promises implements Promise {
                    KEPT, ENFORCED;
                    @Override public String text() { return name(); }
                }
                """.formatted(product, product.toUpperCase()));
        write(product + "/src/main/java/" + product + "/Proving.java", """
                package %s;
                import cloud.jengu.promise.Cites;
                import java.lang.annotation.*;
                @Cites
                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.METHOD, ElementType.TYPE})
                public @interface Proving { Promises[] value(); }
                """.formatted(product));
        write(product + "/src/main/java/" + product + "/Guard.java", """
                package %s;
                @Proving(Promises.ENFORCED)
                public class Guard {}
                """.formatted(product));
        write(product + "/src/test/java/" + product + "/GuardTest.java", """
                package %s;
                public class GuardTest {
                    @Proving(Promises.KEPT)
                    void holds() {}
                }
                """.formatted(product));
    }

    @Test
    @DisplayName("a module applying the plugin carries its catalogue, its production citations "
            + "under the production name and its tests' citations under the tested name, "
            + "neither overwriting the other")
    @ProvingToolset({ToolsetPromises.APPLYING_THE_PLUGIN_IS_ALL_A_MODULE_DOES, ToolsetPromises.TESTS_CITATIONS_TRAVEL_APART})
    void theJarCarriesBothIndexes() throws IOException {
        try (JarFile jar = new JarFile(jarOf("one").toFile())) {
            assertEquals("one.Promises", read(jar, "META-INF/promise/catalogues"));
            assertEquals("one.Guard=one.Promises:ENFORCED", read(jar, Proofs.INDEX));
            assertEquals("one.GuardTest#holds=one.Promises:KEPT",
                    read(jar, Proofs.TESTED_INDEX));
            assertNull(jar.getEntry("one/GuardTest.class"),
                    "the jar carries its tests' citations, not its tests");
        }
    }

    @Test
    @DisplayName("the jar waits for the tests to compile and not for them to run")
    @ProvingToolset(ToolsetPromises.APPLYING_THE_PLUGIN_IS_ALL_A_MODULE_DOES)
    void theJarDoesNotWaitForTheTests() {
        assertEquals(TaskOutcome.SUCCESS, result.task(":one:compileTestJava").getOutcome());
        assertNull(result.task(":one:test"), "building the jar ran the tests");
    }

    @Test
    @DisplayName("a module that does not apply the plugin is unchanged: no index the plugin "
            + "would have put there")
    @ProvingToolset(ToolsetPromises.APPLYING_THE_PLUGIN_IS_ALL_A_MODULE_DOES)
    void aModuleWithoutThePluginIsUnchanged() throws IOException {
        try (JarFile jar = new JarFile(jarOf("plain").toFile())) {
            assertNull(jar.getEntry(Proofs.TESTED_INDEX));
            assertNull(jar.getEntry(Proofs.INDEX));
            assertNull(jar.getEntry("META-INF/promise/catalogues"));
        }
        assertNull(result.task(":plain:compileTestJava"),
                "a module without the plugin compiled its tests to build its jar");
    }

    @Test
    @DisplayName("on one classpath, two products' jars answer the tested citations of both, and "
            + "production code's citation of a promise proves nothing")
    @ProvingToolset({ToolsetPromises.TESTS_CITATIONS_TRAVEL_APART, ToolsetPromises.ONLY_A_TEST_PROVES})
    void twoProductsJarsAnswerForBoth() throws Exception {
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] {jarOf("one").toUri().toURL(), jarOf("two").toUri().toURL()},
                getClass().getClassLoader())) {
            Proofs proofs = Proofs.load(loader);
            assertEquals(Set.of("one.GuardTest#holds"),
                    proofs.citing(constant(loader, "one.Promises", "KEPT")));
            assertEquals(Set.of("two.GuardTest#holds"),
                    proofs.citing(constant(loader, "two.Promises", "KEPT")));
            Promise enforced = constant(loader, "two.Promises", "ENFORCED");
            assertEquals(Set.of("two.Guard"), proofs.enforcing(enforced));
            assertTrue(!proofs.cited(enforced), "enforcing a promise proved it");
        }
    }

    @Test
    @DisplayName("the framework's own jar carries the citations its tests make, so a product "
            + "composing it reads the toolset's promises proven")
    @ProvingToolset(ToolsetPromises.TESTS_CITATIONS_TRAVEL_APART)
    void theFrameworksJarCarriesItsOwnProof() {
        // This test's classpath holds the framework as a jar, as a product's does.
        ClassLoader loader = getClass().getClassLoader();
        cloud.jengu.promise.Registry.Model model = cloud.jengu.promise.Registry.load(loader)
                .model(Proofs.load(loader));
        assertEquals(cloud.jengu.promise.PromiseStatus.PROVEN,
                model.statusOf(ToolsetPromises.NAME_IS_THE_CODE));
        assertTrue(model.citing(ToolsetPromises.NAME_IS_THE_CODE).stream()
                        .anyMatch(site -> site.startsWith("cloud.jengu.promise.CodeAndGapTest#")),
                model.citing(ToolsetPromises.NAME_IS_THE_CODE).toString());
    }

    @Test
    @DisplayName("a module's ledger is drawn from what it ships and its last run, without "
            + "running its tests")
    @ProvingToolset(ToolsetPromises.THE_PLUGIN_DRAWS_THE_LEDGER)
    void eachModuleDrawsItsLedger() throws IOException {
        String one = Files.readString(build.resolve("one/build/reports/promise/index.html"));
        assertTrue(one.startsWith("<!doctype html>"), one.substring(0, 200));
        assertTrue(one.contains("<title>one</title>"), "the ledger is not named for its module");
        assertTrue(one.contains("\"REQ-ONE-KEPT\":{\"text\":\"KEPT\",\"status\":\"PROVEN\","
                + "\"gap\":false,\"catalogue\":\"REQ-ONE\",\"standing\":\"PASSING\""), one);
        String two = Files.readString(build.resolve("two/build/reports/promise/index.html"));
        assertTrue(two.contains("\"standing\":\"NO_RESULT\""),
                "a module whose tests never ran reads as passing");
        String badge = Files.readString(build.resolve("one/build/reports/promise/badge.svg"));
        assertTrue(badge.contains("aria-label=\"promises: 50% kept · 1/2 passing\""), badge);
        assertNull(reported.task(":one:test"), "drawing the ledger ran the tests");
        assertNull(reported.task(":plain:promiseReport"), "a module without the plugin drew a ledger");
    }

    @Test
    @DisplayName("a root applying the plugin draws one ledger over every module that does, and "
            + "in a GitHub Actions job each summary joins the job's own")
    @ProvingToolset(ToolsetPromises.THE_PLUGIN_DRAWS_THE_LEDGER)
    void theProductDrawsOneLedgerAndTheJobSeesIt() throws IOException {
        String product = Files.readString(build.resolve("build/reports/promise/index.html"));
        assertTrue(product.contains("<title>The products</title>"), product.substring(0, 400));
        assertTrue(product.contains("\"REQ-ONE-KEPT\"") && product.contains("\"REQ-TWO-KEPT\""),
                "the product's ledger is missing a module");
        assertTrue(!product.contains("REQ-PLAIN"), "a module without the plugin joined the ledger");

        String summary = Files.readString(stepSummary);
        assertTrue(summary.contains("## one: 50% kept"), summary);
        assertTrue(summary.contains("## The products: 50% kept"), summary);
        assertTrue(summary.contains("last run: 1 passing"), summary);
    }

    private Path jarOf(String product) throws IOException {
        try (var jars = Files.list(build.resolve(product + "/build/libs"))) {
            return jars.filter(path -> path.toString().endsWith(".jar")).findFirst()
                    .orElseThrow(() -> new AssertionError(product + " built no jar"));
        }
    }

    private static String read(JarFile jar, String name) throws IOException {
        ZipEntry entry = jar.getEntry(name);
        assertNotNull(entry, name + " is not in " + jar.getName());
        return new String(jar.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8)
                .strip();
    }

    private static Promise constant(ClassLoader loader, String enumName, String name)
            throws Exception {
        for (Object value : Class.forName(enumName, true, loader).getEnumConstants()) {
            if (((Enum<?>) value).name().equals(name)) {
                return (Promise) value;
            }
        }
        throw new AssertionError(enumName + "." + name);
    }

    private void write(String path, String content) throws IOException {
        Path file = build.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

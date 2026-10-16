package cloud.jengu.promise;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static cloud.jengu.promise.TwoIndexesTest.compile;
import static cloud.jengu.promise.TwoIndexesTest.constant;
import static cloud.jengu.promise.TwoIndexesTest.loader;
import static cloud.jengu.promise.TwoIndexesTest.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A run's results travel with what a release ships: packed as one document,
 * they answer each promise's standing on a classpath with no build behind it
 * as the build itself would have.
 */
class PackedResultsTest {

    /** A product whose promises each stand on one test class. */
    private static Path product(String pkg, String ns) throws Exception {
        Path jar = Files.createTempDirectory("promise-" + pkg);
        compile(jar, null, List.of(
                source(pkg + ".Promises", """
                        package %1$s;
                        import cloud.jengu.promise.Catalogue;
                        import cloud.jengu.promise.Promise;
                        @Catalogue(namespace = "%2$s")
                        public enum Promises implements Promise {
                            PASSES, FAILS, SKIPS, EMPTY, UNRUN;
                            @Override public String text() { return name(); }
                        }
                        """.formatted(pkg, ns)),
                source(pkg + ".Proving", """
                        package %s;
                        import cloud.jengu.promise.Cites;
                        import java.lang.annotation.*;
                        @Cites @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
                        public @interface Proving { Promises[] value(); }
                        """.formatted(pkg))), List.of());
        compile(jar, "tested", List.of(), List.of(
                tests(pkg, "PassesTest", "PASSES"), tests(pkg, "FailsTest", "FAILS"),
                tests(pkg, "SkipsTest", "SKIPS"), tests(pkg, "EmptyTest", "EMPTY"),
                tests(pkg, "UnrunTest", "UNRUN")));
        return jar;
    }

    private static javax.tools.JavaFileObject tests(String pkg, String cls, String promise) {
        return source(pkg + "." + cls, """
                package %1$s;
                public class %2$s { @Proving(Promises.%3$s) void check() {} }
                """.formatted(pkg, cls, promise));
    }

    /** A run as a build leaves it, with the messages and output a release must not carry. */
    private static Path run(String pkg) throws Exception {
        Path run = Files.createTempDirectory("run");
        Path task = Files.createDirectories(run.resolve("test-results/test"));
        Files.writeString(task.resolve("TEST-" + pkg + ".PassesTest.xml"),
                "<testsuite name=\"" + pkg + ".PassesTest\" tests=\"2\">"
                        + "<testcase name=\"check\" classname=\"" + pkg + ".PassesTest\"/>"
                        + "<testcase name=\"again\" classname=\"" + pkg + ".PassesTest\"/>"
                        + "<system-out>patient 3801231-4567 seen</system-out></testsuite>");
        Files.writeString(task.resolve("TEST-" + pkg + ".FailsTest.xml"),
                "<testsuite name=\"" + pkg + ".FailsTest\" tests=\"1\">"
                        + "<testcase name=\"check\" classname=\"" + pkg + ".FailsTest\">"
                        + "<failure message=\"expected the secret s3cr3t\">trace</failure>"
                        + "</testcase></testsuite>");
        Files.writeString(task.resolve("TEST-" + pkg + ".SkipsTest.xml"),
                "<testsuite name=\"" + pkg + ".SkipsTest\" tests=\"1\">"
                        + "<testcase name=\"check\" classname=\"" + pkg + ".SkipsTest\">"
                        + "<skipped/></testcase></testsuite>");
        Files.writeString(task.resolve("TEST-" + pkg + ".EmptyTest.xml"),
                "<testsuite name=\"" + pkg + ".EmptyTest\" tests=\"0\"></testsuite>");
        // UnrunTest: the run never reported it.
        return run;
    }

    /** The product's jar, with the run's results packed into it as a release would. */
    private static Path packed(Path jar, Path run, String name) throws Exception {
        Path results = jar.resolve(Evidence.RESULTS);
        Files.createDirectories(results.getParent());
        Files.writeString(results, PackedResults.of(List.of(run), name));
        return jar;
    }

    @Test
    @DisplayName("results packed into a jar answer each cited promise's standing on a classpath "
            + "with no build behind it, as the build would have — a failing or skipped class "
            + "included, a class that did not run left without a result")
    @ProvingToolset(ToolsetPromises.A_RUN_IS_PACKED_FAITHFULLY)
    void packedResultsAnswerAsTheRunWould() throws Exception {
        Path jar = product("shop", "REQ-SHOP");
        Path run = run("shop");
        try (URLClassLoader asBuilt = loader(jar)) {
            Evidence built = Evidence.read(asBuilt, List.of(run));
            packed(jar, run, "shop 1.2.0 · run 42");
            try (URLClassLoader deployed = loader(jar)) {
                Evidence fromJar = Evidence.read(deployed, List.of());
                for (String name : List.of("PASSES", "FAILS", "SKIPS", "EMPTY", "UNRUN")) {
                    assertEquals(built.standing(constant(asBuilt, "shop.Promises", name)),
                            fromJar.standing(constant(deployed, "shop.Promises", name)), name);
                }
                assertEquals(Evidence.Standing.PASSING,
                        fromJar.standing(constant(deployed, "shop.Promises", "PASSES")));
                assertEquals(Evidence.Standing.FAILING,
                        fromJar.standing(constant(deployed, "shop.Promises", "FAILS")));
                assertEquals(Evidence.Standing.NO_RESULT,
                        fromJar.standing(constant(deployed, "shop.Promises", "SKIPS")));
                assertEquals(Evidence.Standing.NO_RESULT,
                        fromJar.standing(constant(deployed, "shop.Promises", "EMPTY")));
                assertEquals(Evidence.Standing.NO_RESULT,
                        fromJar.standing(constant(deployed, "shop.Promises", "UNRUN")));
                assertEquals(List.of("shop 1.2.0 · run 42"), fromJar.runs(),
                        "a deployment cannot tell which run its results came from");
            }
        }
    }

    @Test
    @DisplayName("the packed results carry each outcome and nothing else: no message, no trace, "
            + "nothing a test printed, and no class the run did not report")
    @ProvingToolset(ToolsetPromises.A_RUN_IS_PACKED_FAITHFULLY)
    void onlyOutcomesTravel() throws Exception {
        String packed = PackedResults.of(List.of(run("shop")), "shop <1.2> & \"run\"");
        assertTrue(packed.startsWith("<?xml"), packed);
        assertTrue(packed.contains("<testsuites name=\"shop &lt;1.2&gt; &amp; &quot;run&quot;\">"),
                packed);
        assertTrue(packed.contains("<testsuite name=\"shop.PassesTest\" tests=\"2\" failures=\"0\" "
                + "errors=\"0\" skipped=\"0\">"), packed);
        assertTrue(packed.contains("<testcase classname=\"shop.FailsTest\" name=\"check\">"
                + "<failure/></testcase>"), packed);
        assertTrue(packed.contains("<testsuite name=\"shop.EmptyTest\" tests=\"0\" failures=\"0\" "
                + "errors=\"0\" skipped=\"0\"/>"), packed);
        assertFalse(packed.contains("s3cr3t"), "a failure's message travelled with the release");
        assertFalse(packed.contains("trace"), "a failure's trace travelled with the release");
        assertFalse(packed.contains("3801231"), "what a test printed travelled with the release");
        assertFalse(packed.contains("UnrunTest"), "a class the run never reported was invented");
    }

    @Test
    @DisplayName("two products' jars, each with its run's results packed, answer every promise "
            + "of both on one classpath")
    @ProvingToolset(ToolsetPromises.A_RUN_IS_PACKED_FAITHFULLY)
    void twoReleasesAnswerTogether() throws Exception {
        Path shop = packed(product("shop", "REQ-SHOP"), run("shop"), "shop 1.2.0");
        Path clinic = packed(product("clinic", "REQ-CLINIC"), run("clinic"), "clinic 3.0.1");
        try (URLClassLoader both = loader(shop, clinic)) {
            Evidence evidence = Evidence.read(both, List.of());
            assertEquals(Evidence.Standing.PASSING,
                    evidence.standing(constant(both, "shop.Promises", "PASSES")));
            assertEquals(Evidence.Standing.PASSING,
                    evidence.standing(constant(both, "clinic.Promises", "PASSES")));
            assertEquals(Evidence.Standing.FAILING,
                    evidence.standing(constant(both, "clinic.Promises", "FAILS")));
            assertEquals(List.of("shop 1.2.0", "clinic 3.0.1"), evidence.runs());
        }
    }
}

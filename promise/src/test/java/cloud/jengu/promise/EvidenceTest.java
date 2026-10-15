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
import static cloud.jengu.promise.TwoIndexesTest.product;
import static cloud.jengu.promise.TwoIndexesTest.production;
import static cloud.jengu.promise.TwoIndexesTest.source;
import static cloud.jengu.promise.TwoIndexesTest.tests;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The framework reads the evidence: given the citations and the results a run
 * left, each promise's standing, the same answer for a page and a gate.
 */
class EvidenceTest {

    /** A second test class citing BOTH, so a promise has two classes behind it. */
    private static final String SECOND = """
            package one;
            public class OtherTest {
                @Proving(Promises.BOTH)
                void alsoHolds() {}
            }
            """;

    private static Path product(String name, javax.tools.JavaFileObject... moreTests)
            throws Exception {
        Path jar = Files.createTempDirectory("promise-" + name);
        compile(jar, null, TwoIndexesTest.product(name, "REQ-" + name.toUpperCase()),
                List.of(production(name)));
        // A module's tests compile in one pass, as here: the index is that
        // pass's, whole.
        List<javax.tools.JavaFileObject> all = new java.util.ArrayList<>(List.of(tests(name)));
        all.addAll(List.of(moreTests));
        compile(jar, "tested", List.of(), all);
        return jar;
    }

    /** A JUnit report for one class: how many ran, and how each ended. */
    private static void report(Path root, String testClass, String... outcomes)
            throws Exception {
        StringBuilder cases = new StringBuilder();
        int i = 0;
        for (String outcome : outcomes) {
            cases.append("<testcase name=\"t").append(i++).append("\" classname=\"")
                    .append(testClass).append("\">")
                    .append(switch (outcome) {
                        case "fail" -> "<failure message=\"no\"/>";
                        case "error" -> "<error message=\"boom\"/>";
                        case "skip" -> "<skipped/>";
                        default -> "";
                    })
                    .append("</testcase>");
        }
        Files.createDirectories(root);
        Files.writeString(root.resolve("TEST-" + testClass + ".xml"),
                "<?xml version=\"1.0\"?><testsuite name=\"" + testClass + "\" tests=\""
                        + outcomes.length + "\">" + cases + "</testsuite>");
    }

    @Test
    @DisplayName("a promise every citing class of which ran with no failure, error or skip is "
            + "passing, and one no test cites is uncited however much code enforces it")
    @ProvingToolset(ToolsetPromises.STANDING_IS_READ_FROM_THE_RUN)
    void passingAndUncited() throws Exception {
        Path jar = product("one");
        Path run = Files.createTempDirectory("run");
        report(run.resolve("build/test-results/test"), "one.GuardTest", "pass");

        try (URLClassLoader loader = loader(jar)) {
            Evidence evidence = Evidence.read(loader, List.of(run));
            assertEquals(Evidence.Standing.PASSING,
                    evidence.standing(constant(loader, "one.Promises", "TESTED_ONLY")));
            assertEquals(Evidence.Standing.UNCITED,
                    evidence.standing(constant(loader, "one.Promises", "ENFORCED_ONLY")));
            assertEquals(List.of(), evidence.unreadable());
        }
    }

    @Test
    @DisplayName("a promise is failing when any class citing it failed or erred, whatever the "
            + "others did")
    @ProvingToolset(ToolsetPromises.STANDING_IS_READ_FROM_THE_RUN)
    void failingWins() throws Exception {
        Path jar = product("one", source("one.OtherTest", SECOND));
        Path run = Files.createTempDirectory("run");
        report(run, "one.GuardTest", "pass", "error");
        // OtherTest never ran: no result, and still the failure decides.

        Path failed = Files.createTempDirectory("run");
        report(failed, "one.GuardTest", "fail");
        report(failed, "one.OtherTest", "pass");

        try (URLClassLoader loader = loader(jar)) {
            Promise both = constant(loader, "one.Promises", "BOTH");
            assertEquals(Evidence.Standing.FAILING,
                    Evidence.read(loader, List.of(run)).standing(both), "an error");
            assertEquals(Evidence.Standing.FAILING,
                    Evidence.read(loader, List.of(failed)).standing(both), "a failure");
        }
    }

    @Test
    @DisplayName("a promise has no result when a class citing it never ran, held no tests, or "
            + "skipped one")
    @ProvingToolset(ToolsetPromises.STANDING_IS_READ_FROM_THE_RUN)
    void noResult() throws Exception {
        Path jar = product("one", source("one.OtherTest", SECOND));
        Promise both;
        try (URLClassLoader loader = loader(jar)) {
            both = constant(loader, "one.Promises", "BOTH");

            Path neverRan = Files.createTempDirectory("run");
            report(neverRan, "one.GuardTest", "pass");
            assertEquals(Evidence.Standing.NO_RESULT,
                    Evidence.read(loader, List.of(neverRan)).standing(both),
                    "a citing class that never ran");

            Path empty = Files.createTempDirectory("run");
            report(empty, "one.GuardTest", "pass");
            report(empty, "one.OtherTest");
            assertEquals(Evidence.Standing.NO_RESULT,
                    Evidence.read(loader, List.of(empty)).standing(both),
                    "a citing class that held no tests");

            Path skipped = Files.createTempDirectory("run");
            report(skipped, "one.GuardTest", "pass");
            report(skipped, "one.OtherTest", "pass", "skip");
            assertEquals(Evidence.Standing.NO_RESULT,
                    Evidence.read(loader, List.of(skipped)).standing(both),
                    "a citing class that skipped a test");

            Path whole = Files.createTempDirectory("run");
            report(whole, "one.GuardTest", "pass");
            report(whole, "one.OtherTest", "pass");
            assertEquals(Evidence.Standing.PASSING,
                    Evidence.read(loader, List.of(whole)).standing(both));
        }
    }

    @Test
    @DisplayName("results that travel with the code are read from the classpath, with no root "
            + "named at all")
    @ProvingToolset(ToolsetPromises.STANDING_IS_READ_FROM_THE_RUN)
    void resultsFromTheClasspath() throws Exception {
        Path jar = product("one");
        Path packed = Files.createTempDirectory("packed");
        report(packed, "one.GuardTest", "pass");
        Files.createDirectories(jar.resolve(Evidence.RESULTS).getParent());
        Files.move(packed.resolve("TEST-one.GuardTest.xml"), jar.resolve(Evidence.RESULTS));

        try (URLClassLoader loader = loader(jar)) {
            assertEquals(Evidence.Standing.PASSING, Evidence.read(loader, List.of())
                    .standing(constant(loader, "one.Promises", "TESTED_ONLY")));
        }
    }

    @Test
    @DisplayName("an index one product carries that cannot be read against the classpath is "
            + "reported by name, and every other product's evidence is still answered")
    @ProvingToolset(ToolsetPromises.ONE_PRODUCTS_UNREADABLE_EVIDENCE_IS_ITS_OWN)
    void oneProductsUnreadableIndexIsReported() throws Exception {
        Path first = product("one");
        Path second = product("two");
        // A good line before the stale one: the index is read whole or not at all.
        Files.writeString(second.resolve(Proofs.TESTED_INDEX),
                "two.GuardTest#holds=two.Promises:TESTED_ONLY\n"
                        + "two.GuardTest#holds=two.Promises:RENAMED_AWAY\n");
        Path run = Files.createTempDirectory("run");
        report(run, "one.GuardTest", "pass");
        Files.writeString(run.resolve("TEST-broken.xml"), "<testsuite name=\"half");

        try (URLClassLoader loader = loader(first, second)) {
            assertThrows(IllegalStateException.class, () -> Proofs.load(loader),
                    "the strict load stopped refusing a stale index");

            Evidence evidence = Evidence.read(loader, List.of(run));
            assertEquals(2, evidence.unreadable().size(), evidence.unreadable().toString());
            assertTrue(evidence.unreadable().get(0).source().contains(second.getFileName()
                            .toString()) && evidence.unreadable().get(0).reason()
                            .contains("RENAMED_AWAY"),
                    evidence.unreadable().toString());
            assertTrue(evidence.unreadable().get(1).source().endsWith("TEST-broken.xml"),
                    evidence.unreadable().toString());
            assertEquals(Evidence.Standing.PASSING,
                    evidence.standing(constant(loader, "one.Promises", "TESTED_ONLY")),
                    "one product's stale index took another's evidence down");
            assertTrue(evidence.model().promises().stream().anyMatch(promise ->
                            promise.code().startsWith("REQ-TWO")),
                    "the product whose citations could not be read lost its catalogue too");
            assertEquals(Evidence.Standing.UNCITED,
                    evidence.standing(constant(loader, "two.Promises", "TESTED_ONLY")),
                    "half of an index that could not be read was taken");
        }
    }
}

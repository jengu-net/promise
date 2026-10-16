package cloud.jengu.promise;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static cloud.jengu.promise.TwoIndexesTest.constant;
import static cloud.jengu.promise.TwoIndexesTest.loader;
import static cloud.jengu.promise.TwoIndexesTest.source;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A promise names the promises it is based on: a product says what it
 * promises in its own words, and its ground is kept by a composed product's
 * promises, worded for whoever builds that product.
 */
class BasisTest {

    /** The composed product: one promise of each standing a run can leave. */
    private static final List<JavaFileObject> BASE = List.of(
            source("base.BasePromises", """
                    package base;
                    import cloud.jengu.promise.Catalogue;
                    import cloud.jengu.promise.Promise;
                    @Catalogue(namespace = "REQ-BASE")
                    public enum BasePromises implements Promise {
                        HOLDS, BREAKS, UNRUN, UNTESTED;
                        @Override public String text() { return name(); }
                    }
                    """),
            source("base.Proving", """
                    package base;
                    import cloud.jengu.promise.Cites;
                    import java.lang.annotation.*;
                    @Cites @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
                    public @interface Proving { BasePromises[] value(); }
                    """));

    private static final List<JavaFileObject> BASE_TESTS = List.of(
            source("base.HoldsTest", """
                    package base;
                    public class HoldsTest { @Proving(BasePromises.HOLDS) void holds() {} }
                    """),
            source("base.BreaksTest", """
                    package base;
                    public class BreaksTest { @Proving(BasePromises.BREAKS) void breaks() {} }
                    """),
            source("base.UnrunTest", """
                    package base;
                    public class UnrunTest { @Proving(BasePromises.UNRUN) void unrun() {} }
                    """));

    /** The product saying it in its own words, on the base's ground. */
    private static final List<JavaFileObject> TOP = List.of(
            source("top.TopPromises", """
                    package top;
                    import base.BasePromises;
                    import cloud.jengu.promise.Catalogue;
                    import cloud.jengu.promise.Promise;
                    import java.util.List;
                    @Catalogue(namespace = "REQ-TOP")
                    public enum TopPromises implements Promise {
                        IN_OUR_WORDS(List.of(BasePromises.HOLDS)),
                        ON_BROKEN_GROUND(List.of(BasePromises.HOLDS, BasePromises.BREAKS)),
                        ON_UNRUN_GROUND(List.of(BasePromises.UNRUN)),
                        ON_UNSTATED_GROUND(List.of(BasePromises.UNTESTED)),
                        OWN_TEST_FAILS(List.of(BasePromises.HOLDS));
                        private final List<Promise> basis;
                        TopPromises(List<Promise> basis) { this.basis = basis; }
                        @Override public String text() { return name(); }
                        @Override public List<Promise> basis() { return basis; }
                    }
                    """),
            source("top.Proving", """
                    package top;
                    import cloud.jengu.promise.Cites;
                    import java.lang.annotation.*;
                    @Cites @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
                    public @interface Proving { TopPromises[] value(); }
                    """));

    private static final List<JavaFileObject> TOP_TESTS = List.of(source("top.OwnTest", """
            package top;
            public class OwnTest { @Proving(TopPromises.OWN_TEST_FAILS) void fails() {} }
            """));

    /** A product whose promises rest on each other, and so on nothing. */
    private static final List<JavaFileObject> LOOP = List.of(source("loop.LoopPromises", """
            package loop;
            import cloud.jengu.promise.Catalogue;
            import cloud.jengu.promise.Promise;
            import java.util.List;
            @Catalogue(namespace = "REQ-LOOP")
            public enum LoopPromises implements Promise {
                FIRST { @Override public List<Promise> basis() { return List.of(SECOND); } },
                SECOND { @Override public List<Promise> basis() { return List.of(FIRST); } };
                @Override public String text() { return name(); }
            }
            """));

    /** A product nobody's mistakes should take down. */
    private static final List<JavaFileObject> APART = List.of(source("apart.ApartPromises", """
            package apart;
            import cloud.jengu.promise.Catalogue;
            import cloud.jengu.promise.Promise;
            @Catalogue(namespace = "REQ-APART")
            public enum ApartPromises implements Promise {
                STANDS;
                @Override public String text() { return name(); }
            }
            """));

    /** Base and top, each in an output of its own, as two products' jars. */
    private record Products(Path base, Path top) {
    }

    private static Products products() throws Exception {
        Path base = Files.createTempDirectory("promise-base");
        compile(base, null, List.of(), BASE);
        compile(base, "tested", List.of(base), BASE_TESTS);
        Path top = Files.createTempDirectory("promise-top");
        compile(top, null, List.of(base), TOP);
        compile(top, "tested", List.of(base), TOP_TESTS);
        return new Products(base, top);
    }

    /** One run: HoldsTest passed, BreaksTest and top's OwnTest failed, UnrunTest never ran. */
    private static Path run() throws Exception {
        Path run = Files.createTempDirectory("run");
        report(run, "base.HoldsTest", "holds", false);
        report(run, "base.BreaksTest", "breaks", true);
        report(run, "top.OwnTest", "fails", true);
        return run;
    }

    @Test
    @DisplayName("a product's promise names a composed product's promises as its basis, and the "
            + "model answers it, and the inverse")
    @ProvingToolset(ToolsetPromises.A_PROMISE_NAMES_ITS_BASIS)
    void theModelAnswersBothDirections() throws Exception {
        Products products = products();
        try (URLClassLoader loader = loader(products.base(), products.top())) {
            Registry.Model model = Registry.load(loader).model(Proofs.load(loader));
            Promise holds = constant(loader, "base.BasePromises", "HOLDS");
            Promise inOurWords = constant(loader, "top.TopPromises", "IN_OUR_WORDS");
            assertEquals(List.of(holds), model.basis(inOurWords));
            assertEquals(List.of(inOurWords, constant(loader, "top.TopPromises", "ON_BROKEN_GROUND"),
                    constant(loader, "top.TopPromises", "OWN_TEST_FAILS")), model.basedOn(holds));
            assertEquals(List.of(), model.basis(holds), "a basis was invented for a promise "
                    + "that names none");
        }
    }

    @Test
    @DisplayName("a promise based on a failing promise fails; based only on passing ones and "
            + "cited by no test it is kept by its basis, told apart from one proven; its own "
            + "failing test still fails it; and ground without a result leaves it without one")
    @ProvingToolset(ToolsetPromises.STANDING_FOLLOWS_THE_BASIS)
    void standingFollowsTheBasis() throws Exception {
        Products products = products();
        try (URLClassLoader loader = loader(products.base(), products.top())) {
            Evidence evidence = Evidence.read(loader, List.of(run()));
            Registry.Model model = evidence.model();
            Promise inOurWords = constant(loader, "top.TopPromises", "IN_OUR_WORDS");
            assertEquals(Evidence.Standing.PASSING, evidence.standing(inOurWords));
            assertEquals(PromiseStatus.BASED, model.statusOf(inOurWords),
                    "a promise kept by its basis reads as if its own tests proved it");
            assertTrue(model.statusOf(inOurWords).kept());

            assertEquals(Evidence.Standing.FAILING,
                    evidence.standing(constant(loader, "top.TopPromises", "ON_BROKEN_GROUND")));
            Promise ownFails = constant(loader, "top.TopPromises", "OWN_TEST_FAILS");
            assertEquals(Evidence.Standing.FAILING, evidence.standing(ownFails),
                    "a passing basis hid the promise's own failing test");
            assertEquals(PromiseStatus.PROVEN, model.statusOf(ownFails));
            assertEquals(Evidence.Standing.NO_RESULT,
                    evidence.standing(constant(loader, "top.TopPromises", "ON_UNRUN_GROUND")));

            Promise unstated = constant(loader, "top.TopPromises", "ON_UNSTATED_GROUND");
            assertEquals(Evidence.Standing.NO_RESULT, evidence.standing(unstated));
            assertEquals(PromiseStatus.PLANNED, model.statusOf(unstated),
                    "a promise read as kept by ground nothing keeps");
        }
    }

    @Test
    @DisplayName("a basis naming a promise the classpath does not carry fails the strict load "
            + "and is reported as unreadable, and every other product is still answered")
    @ProvingToolset(ToolsetPromises.A_PROMISE_NAMES_ITS_BASIS)
    void aStaleBasisIsRefused() throws Exception {
        Products products = products();
        Path apart = Files.createTempDirectory("promise-apart");
        compile(apart, null, List.of(), APART);
        // The top product without the base it was built on.
        try (URLClassLoader loader = loader(products.top(), apart)) {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> Registry.load(loader).model(Proofs.none()));
            assertTrue(refused.getMessage().contains("top.TopPromises")
                    && refused.getMessage().contains("does not carry"), refused.getMessage());

            Evidence evidence = Evidence.read(loader, List.of());
            assertTrue(evidence.unreadable().stream().anyMatch(u ->
                            u.source().contains(products.top().getFileName().toString())
                                    && u.reason().contains("does not carry")),
                    evidence.unreadable().toString());
            assertTrue(evidence.model().promises().stream()
                    .anyMatch(p -> p.code().equals("REQ-APART-STANDS")),
                    "one product's stale basis took another product down");
        }
    }

    @Test
    @DisplayName("a cycle among bases is refused at load, naming the promises in it, and every "
            + "other product is still answered")
    @ProvingToolset(ToolsetPromises.A_PROMISE_NAMES_ITS_BASIS)
    void aCycleIsRefused() throws Exception {
        Path loop = Files.createTempDirectory("promise-loop");
        compile(loop, null, List.of(), LOOP);
        Path apart = Files.createTempDirectory("promise-apart");
        compile(apart, null, List.of(), APART);
        try (URLClassLoader loader = loader(loop, apart)) {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> Registry.load(loader).model(Proofs.none()));
            assertTrue(refused.getMessage().contains("REQ-LOOP-FIRST")
                    && refused.getMessage().contains("REQ-LOOP-SECOND"), refused.getMessage());

            Evidence evidence = Evidence.read(loader, List.of());
            assertTrue(evidence.unreadable().stream().anyMatch(u ->
                            u.source().equals("loop.LoopPromises")
                                    && u.reason().contains("REQ-LOOP-FIRST")),
                    evidence.unreadable().toString());
            assertTrue(evidence.model().promises().stream()
                    .anyMatch(p -> p.code().equals("REQ-APART-STANDS")),
                    "one product's cycle took another product down");
        }
    }

    @Test
    @DisplayName("the ledger shows each promise's basis under it, the ground with it")
    @ProvingToolset(ToolsetPromises.THE_LEDGER_IS_ONE_PAGE)
    void theLedgerShowsTheBasis() throws Exception {
        Products products = products();
        try (URLClassLoader loader = loader(products.base(), products.top())) {
            String page = HtmlReport.render(Evidence.read(loader, List.of(run())), "top", true);
            assertTrue(page.contains("\"REQ-TOP-IN-OUR-WORDS\":{\"text\":\"IN_OUR_WORDS\","
                    + "\"status\":\"BASED\""), page);
            assertTrue(page.contains("\"basis\":[\"REQ-BASE-HOLDS\",\"REQ-BASE-BREAKS\"]"), page);
            assertTrue(page.contains("\"REQ-BASE-HOLDS\":{"), "the ground is not on the page");
        }
    }

    private static void report(Path run, String testClass, String method, boolean failed)
            throws Exception {
        Files.writeString(run.resolve("TEST-" + testClass + ".xml"), "<testsuite name=\""
                + testClass + "\" tests=\"1\"><testcase name=\"" + method + "\" classname=\""
                + testClass + "\">" + (failed ? "<failure message=\"no\"/>" : "")
                + "</testcase></testsuite>");
    }

    private static void compile(Path out, String index, List<Path> against,
            List<JavaFileObject> sources) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StringBuilder classpath = new StringBuilder(System.getProperty("java.class.path"))
                .append(File.pathSeparator).append(out);
        against.forEach(path -> classpath.append(File.pathSeparator).append(path));
        List<String> options = new ArrayList<>(List.of("-d", out.toString(), "-cp",
                classpath.toString()));
        if (index != null) {
            options.add("-A" + CatalogueProcessor.INDEX_OPTION + "=" + index);
        }
        JavaCompiler.CompilationTask task = compiler.getTask(null,
                compiler.getStandardFileManager(diagnostics, null, null), diagnostics, options,
                null, sources);
        task.setProcessors(List.of(new CatalogueProcessor()));
        assertTrue(task.call(), diagnostics.getDiagnostics().toString());
    }
}

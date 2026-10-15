package cloud.jengu.promise;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A test citing a promise and production code citing it are two claims, and
 * they travel apart: each compilation writes its own index, one jar carries
 * both, and the reading tells them apart — only a test makes a promise proven.
 */
class TwoIndexesTest {

    /** A product: its catalogue, its proving annotation, its production code and its tests. */
    static List<JavaFileObject> product(String pkg, String ns) {
        return List.of(
                source(pkg + ".Promises", """
                        package %1$s;
                        import cloud.jengu.promise.Catalogue;
                        import cloud.jengu.promise.Promise;
                        @Catalogue(namespace = "%2$s")
                        public enum Promises implements Promise {
                            TESTED_ONLY, ENFORCED_ONLY, BOTH;
                            @Override public String text() { return name(); }
                        }
                        """.formatted(pkg, ns)),
                source(pkg + ".Proving", """
                        package %s;
                        import cloud.jengu.promise.Cites;
                        import java.lang.annotation.*;
                        @Cites
                        @Retention(RetentionPolicy.RUNTIME)
                        @Target({ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR})
                        public @interface Proving { Promises[] value(); }
                        """.formatted(pkg)));
    }

    static JavaFileObject production(String pkg) {
        return source(pkg + ".Guard", """
                package %1$s;
                @Proving(Promises.ENFORCED_ONLY)
                public class Guard {
                    @Proving(Promises.BOTH)
                    public Guard() {}
                }
                """.formatted(pkg));
    }

    static JavaFileObject tests(String pkg) {
        return source(pkg + ".GuardTest", """
                package %1$s;
                public class GuardTest {
                    @Proving({Promises.TESTED_ONLY, Promises.BOTH})
                    void holds() {}
                }
                """.formatted(pkg));
    }

    @Test
    @DisplayName("a main compilation and a test compilation write their citations under names of "
            + "their own, so one output carries both and neither overwrites the other")
    void eachCompilationWritesItsOwnIndex() throws Exception {
        Path jar = Files.createTempDirectory("promise-jar");
        compile(jar, null, product("one", "REQ-ONE"), List.of(production("one")));
        compile(jar, "tested", List.of(), List.of(tests("one")));

        assertEquals(List.of("one.Guard#<init>=one.Promises:BOTH",
                        "one.Guard=one.Promises:ENFORCED_ONLY"),
                Files.readAllLines(jar.resolve(Proofs.INDEX)),
                "production citations, a type and a constructor named as the class file "
                        + "names them");
        assertEquals(List.of("one.GuardTest#holds=one.Promises:TESTED_ONLY,BOTH"),
                Files.readAllLines(jar.resolve(Proofs.TESTED_INDEX)));
    }

    @Test
    @DisplayName("the reading tells a test's citation from production code's: a promise only "
            + "production code cites is enforced and not proven, one a test cites is proven")
    void theReadingTellsTheTwoApart() throws Exception {
        Path jar = Files.createTempDirectory("promise-jar");
        compile(jar, null, product("one", "REQ-ONE"), List.of(production("one")));
        compile(jar, "tested", List.of(), List.of(tests("one")));

        try (URLClassLoader loader = loader(jar)) {
            Registry.Model model = Registry.load(loader).model(Proofs.load(loader));
            Promise testedOnly = constant(loader, "one.Promises", "TESTED_ONLY");
            Promise enforcedOnly = constant(loader, "one.Promises", "ENFORCED_ONLY");
            Promise both = constant(loader, "one.Promises", "BOTH");

            assertEquals(PromiseStatus.PROVEN, model.statusOf(testedOnly));
            assertEquals(PromiseStatus.PLANNED, model.statusOf(enforcedOnly),
                    "production code citing a promise proved it");
            assertEquals(Set.of("one.Guard"), model.enforcing(enforcedOnly));
            assertEquals(Set.of(), model.citing(enforcedOnly));
            assertEquals(PromiseStatus.PROVEN, model.statusOf(both));
            assertEquals(List.of(
                            new Proofs.Citation("one.GuardTest#holds", Proofs.Kind.TESTED),
                            new Proofs.Citation("one.Guard#<init>", Proofs.Kind.ENFORCED)),
                    model.citations(both));
        }
    }

    @Test
    @DisplayName("on a classpath holding two products, each from its own jar, the tested "
            + "citations of both are answered")
    void twoProductsAreReadFromOneClasspath() throws Exception {
        Path first = Files.createTempDirectory("promise-first");
        compile(first, null, product("one", "REQ-ONE"), List.of(production("one")));
        compile(first, "tested", List.of(), List.of(tests("one")));
        Path second = Files.createTempDirectory("promise-second");
        compile(second, null, product("two", "REQ-TWO"), List.of(production("two")));
        compile(second, "tested", List.of(), List.of(tests("two")));

        try (URLClassLoader loader = loader(first, second)) {
            Proofs proofs = Proofs.load(loader);
            assertEquals(Set.of("one.GuardTest#holds"),
                    proofs.citing(constant(loader, "one.Promises", "TESTED_ONLY")));
            assertEquals(Set.of("two.GuardTest#holds"),
                    proofs.citing(constant(loader, "two.Promises", "TESTED_ONLY")));
        }
    }

    @Test
    @DisplayName("a tested index naming a constant the classpath does not carry fails the load, "
            + "as the production index does")
    void aStaleTestedIndexFailsTheLoad() throws Exception {
        Path jar = Files.createTempDirectory("promise-jar");
        compile(jar, null, product("one", "REQ-ONE"), List.of());
        Files.createDirectories(jar.resolve(Proofs.TESTED_INDEX).getParent());
        Files.writeString(jar.resolve(Proofs.TESTED_INDEX),
                "one.GuardTest#holds=one.Promises:RENAMED_AWAY\n");

        try (URLClassLoader loader = loader(jar)) {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> Proofs.load(loader));
            assertTrue(refused.getMessage().contains("RENAMED_AWAY"), refused.getMessage());
        }
    }

    @Test
    @DisplayName("an index option the processor does not know is a compile error naming it")
    void anUnknownIndexIsRefused() throws Exception {
        Path out = Files.createTempDirectory("promise-jar");
        List<Diagnostic<? extends JavaFileObject>> said =
                compileExpectingFailure(out, "tests", product("one", "REQ-ONE"));
        assertTrue(said.stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR
                        && d.getMessage(null).contains("'tests'")), said.toString());
        assertFalse(Files.exists(out.resolve(Proofs.INDEX)));
    }

    static Promise constant(ClassLoader loader, String enumName, String name)
            throws Exception {
        for (Object value : Class.forName(enumName, true, loader).getEnumConstants()) {
            if (((Enum<?>) value).name().equals(name)) {
                return (Promise) value;
            }
        }
        throw new AssertionError(enumName + "." + name);
    }

    static URLClassLoader loader(Path... roots) throws Exception {
        URL[] urls = new URL[roots.length];
        for (int i = 0; i < roots.length; i++) {
            urls[i] = roots[i].toUri().toURL();
        }
        return new URLClassLoader(urls, TwoIndexesTest.class.getClassLoader());
    }

    static void compile(Path out, String index, List<JavaFileObject> product,
            List<JavaFileObject> more) throws Exception {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        assertTrue(task(out, index, product, more, diagnostics).call(),
                diagnostics.getDiagnostics().toString());
    }

    private static List<Diagnostic<? extends JavaFileObject>> compileExpectingFailure(Path out,
            String index, List<JavaFileObject> units) throws Exception {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        assertFalse(task(out, index, units, List.of(), diagnostics).call());
        return diagnostics.getDiagnostics();
    }

    private static JavaCompiler.CompilationTask task(Path out, String index,
            List<JavaFileObject> product, List<JavaFileObject> more,
            DiagnosticCollector<JavaFileObject> diagnostics) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null);
        List<String> options = new ArrayList<>(List.of("-d", out.toString(),
                // what was compiled into this output before, as a second
                // compilation of the same module sees its first
                "-cp", System.getProperty("java.class.path") + File.pathSeparator + out));
        if (index != null) {
            options.add("-A" + CatalogueProcessor.INDEX_OPTION + "=" + index);
        }
        List<JavaFileObject> units = new ArrayList<>(product);
        units.addAll(more);
        JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics, options,
                null, units);
        task.setProcessors(List.of(new CatalogueProcessor()));
        return task;
    }

    static JavaFileObject source(String name, String content) {
        return new SimpleJavaFileObject(
                URI.create("string:///" + name.replace('.', '/') + ".java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignore) {
                return content;
            }
        };
    }
}

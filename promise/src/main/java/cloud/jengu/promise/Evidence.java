package cloud.jengu.promise;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * What stands behind each promise, read the one way: the composed graph, every
 * citation told apart, and each promise's standing from the results a test run
 * left.
 *
 * <p>The reading a page draws and a gate decides on, so no product writes its
 * own and two readers never disagree about one run. A page draws what could be
 * read; a gate refuses while {@link #unreadable()} is not empty.
 *
 * <p><b>One product's evidence never takes another's down.</b> Every index is
 * read on its own — each catalogue index, each citation index, each results
 * file — and one that cannot be read against the classpath is reported by
 * where it came from, while everything else is still answered. {@link
 * Registry#load} and {@link Proofs#load} stay strict: a build that wants a
 * stale index to fail the build still gets that.
 */
public final class Evidence {

    /**
     * Where results travel with the code: a JUnit XML document, as a test run
     * writes one, under this name in a jar.
     */
    public static final String RESULTS = "META-INF/promise/results.xml";

    /** A promise's standing in the run the results describe. */
    public enum Standing {

        /** Every test class citing it ran, with no failure, error or skip. */
        PASSING,

        /** A test class citing it failed or erred. */
        FAILING,

        /** A test class citing it never ran, held no tests, or skipped one. */
        NO_RESULT,

        /** No test cites it. */
        UNCITED
    }

    /** What one test class came to, across every results file naming it. */
    public record Result(String testClass, int tests, int failures, int errors, int skipped) {

        Result plus(Result other) {
            return new Result(testClass, tests + other.tests, failures + other.failures,
                    errors + other.errors, skipped + other.skipped);
        }
    }

    /** Something that could not be read, and why — the source named as the loader names it. */
    public record Unreadable(String source, String reason) {
    }

    private final Registry.Model model;
    private final Map<String, Result> results;
    private final List<Unreadable> unreadable;
    private final List<String> runs;

    private Evidence(Registry.Model model, Map<String, Result> results,
            List<Unreadable> unreadable, List<String> runs) {
        this.model = model;
        this.results = results;
        this.unreadable = List.copyOf(unreadable);
        this.runs = List.copyOf(runs);
    }

    /**
     * Everything the loader's classpath declares and cites, against the
     * results under {@code roots} and on the classpath.
     *
     * @param roots where a run left its JUnit XML — a working tree, or wherever
     *              a build put its evidence; every {@code TEST-*.xml} beneath
     *              them is read
     */
    public static Evidence read(ClassLoader loader, Collection<Path> roots) {
        List<Unreadable> unreadable = new ArrayList<>();

        LinkedHashSet<Class<?>> catalogues = new LinkedHashSet<>();
        for (URL index : Registry.indexes(loader)) {
            try {
                List<Class<?>> read = Registry.read(loader, index);
                // A catalogue whose promises name ground this classpath cannot
                // carry is that product's to fix, and stands for nothing here.
                for (Class<?> catalogue : read) {
                    basesOf(catalogue);
                }
                catalogues.addAll(read);
            } catch (RuntimeException e) {
                unreadable.add(new Unreadable(index.toString(), e.getMessage()));
            }
        }
        // A cycle among bases is refused too, for the catalogues declaring it
        // and for nobody else.
        for (List<Promise> cycle = Registry.Model.cycle(promisesOf(catalogues)); cycle != null;
                cycle = Registry.Model.cycle(promisesOf(catalogues))) {
            for (Promise step : cycle) {
                if (step instanceof Enum<?> constant && catalogues.remove(constant.getDeclaringClass())) {
                    unreadable.add(new Unreadable(constant.getDeclaringClass().getName(),
                            Registry.Model.cycleMessage(cycle)));
                }
            }
        }
        Proofs proofs = Proofs.none();
        for (Proofs.Kind kind : Proofs.Kind.values()) {
            for (URL index : Proofs.indexes(loader, kind)) {
                // Read whole on its own first, so an index that fails halfway
                // leaves none of its lines behind.
                Proofs one = Proofs.none();
                try {
                    one.read(loader, kind, index);
                    proofs.absorb(one);
                } catch (RuntimeException e) {
                    unreadable.add(new Unreadable(index.toString(), e.getMessage()));
                }
            }
        }

        Map<String, Result> results = new TreeMap<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith("TEST-") && name.endsWith(".xml");
                }).sorted().toList();
            } catch (IOException e) {
                unreadable.add(new Unreadable(root.toString(), e.getMessage()));
                continue;
            }
            for (Path file : files) {
                try (InputStream in = Files.newInputStream(file)) {
                    take(in, results);
                } catch (Exception e) {
                    unreadable.add(new Unreadable(file.toString(), reason(e)));
                }
            }
        }
        List<String> runs = new ArrayList<>();
        try {
            java.util.Enumeration<URL> packed = loader.getResources(RESULTS);
            while (packed.hasMoreElements()) {
                URL one = packed.nextElement();
                try (InputStream in = one.openStream()) {
                    String run = take(in, results);
                    if (run != null && !run.isEmpty() && !runs.contains(run)) {
                        runs.add(run);
                    }
                } catch (Exception e) {
                    unreadable.add(new Unreadable(one.toString(), reason(e)));
                }
            }
        } catch (IOException e) {
            unreadable.add(new Unreadable(RESULTS, e.getMessage()));
        }

        return new Evidence(Registry.over(catalogues).model(proofs), results, unreadable, runs);
    }

    /** Every promise a catalogue declares, its bases checked; throws as the strict load does. */
    private static void basesOf(Class<?> catalogue) {
        for (Object constant : Registry.constantsOf(catalogue)) {
            if (constant instanceof Promise promise) {
                Registry.Model.basisOf(promise);
            }
        }
    }

    private static List<Promise> promisesOf(java.util.Collection<Class<?>> catalogues) {
        List<Promise> promises = new ArrayList<>();
        for (Class<?> catalogue : catalogues) {
            for (Object constant : Registry.constantsOf(catalogue)) {
                if (constant instanceof Promise promise) {
                    promises.add(promise);
                }
            }
        }
        return promises;
    }

    /** The composed graph, every readable product's catalogue in it. */
    public Registry.Model model() {
        return model;
    }

    /** Every citation of {@code promise}, each saying whether it proves or enforces. */
    public List<Proofs.Citation> citations(Promise promise) {
        return model.citations(promise);
    }

    /** What each test class came to, by its binary name. */
    public Map<String, Result> results() {
        return Map.copyOf(results);
    }

    /**
     * Which runs the results packed on the classpath came from, as each names
     * itself ({@link PackedResults}): a deployed product reads its standing
     * from its release build, and says which.
     */
    public List<String> runs() {
        return runs;
    }

    /** What could not be read. Empty is the only answer a gate passes on. */
    public List<Unreadable> unreadable() {
        return unreadable;
    }

    /**
     * The promise's standing: failing if any test class citing it failed, or
     * any promise it is based on fails; otherwise no result if any of those did
     * not run cleanly to the end; otherwise passing. Judged by the class,
     * because a class is what a run reports — a method citing a promise is held
     * to the class it is in. A promise with neither tests nor a basis is
     * uncited.
     */
    public Standing standing(Promise promise) {
        java.util.Set<String> citing = model.citing(promise);
        List<Promise> basis = model.basis(promise);
        if (citing.isEmpty() && basis.isEmpty()) {
            return Standing.UNCITED;
        }
        boolean complete = true;
        for (String site : citing) {
            Result result = results.get(classOf(site));
            if (result != null && result.failures() + result.errors() > 0) {
                return Standing.FAILING;
            }
            if (result == null || result.tests() == 0 || result.skipped() > 0) {
                complete = false;
            }
        }
        // A promise stands on its basis as much as on its own tests: a basis
        // that fails fails it, and one without a result leaves it without one.
        for (Promise ground : basis) {
            Standing standing = standing(ground);
            if (standing == Standing.FAILING) {
                return Standing.FAILING;
            }
            if (standing != Standing.PASSING) {
                complete = false;
            }
        }
        return complete ? Standing.PASSING : Standing.NO_RESULT;
    }

    private static String classOf(String site) {
        int hash = site.indexOf('#');
        return hash < 0 ? site : site.substring(0, hash);
    }

    /**
     * One JUnit XML document — a {@code testsuite}, or {@code testsuites}
     * holding several — tallied by each test case's class. A suite with no
     * cases still names its class, with nothing run.
     */
    private static String take(InputStream in, Map<String, Result> results) throws Exception {
        Document document = parser().parse(in);
        Element root = document.getDocumentElement();
        if (!root.getTagName().equals("testsuite") && !root.getTagName().equals("testsuites")) {
            throw new IllegalArgumentException("not a JUnit report: its root is <"
                    + root.getTagName() + ">");
        }
        List<Element> suites = new ArrayList<>();
        if (root.getTagName().equals("testsuite")) {
            suites.add(root);
        }
        NodeList nested = root.getElementsByTagName("testsuite");
        for (int i = 0; i < nested.getLength(); i++) {
            suites.add((Element) nested.item(i));
        }
        for (Element suite : suites) {
            String name = suite.getAttribute("name");
            if (!name.isEmpty()) {
                results.merge(name, new Result(name, 0, 0, 0, 0), Result::plus);
            }
            NodeList cases = suite.getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                Element testCase = (Element) cases.item(i);
                if (testCase.getParentNode() != suite) {
                    continue;
                }
                String testClass = testCase.getAttribute("classname").isEmpty()
                        ? name : testCase.getAttribute("classname");
                results.merge(testClass, new Result(testClass, 1,
                        has(testCase, "failure"), has(testCase, "error"),
                        has(testCase, "skipped")), Result::plus);
            }
        }
        return root.getTagName().equals("testsuites") ? root.getAttribute("name") : null;
    }

    private static int has(Element testCase, String child) {
        return testCase.getElementsByTagName(child).getLength() > 0 ? 1 : 0;
    }

    private static DocumentBuilder parser() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // A report is data a build left behind, and nothing in one needs a
        // document type: refused, so a crafted file cannot reach outside it.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private static String reason(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}

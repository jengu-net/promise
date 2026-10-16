package cloud.jengu.promise;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * A run's results, packed to travel with what a release ships.
 *
 * <p>A promise's standing has two halves: which tests cite it, which travels in
 * every jar, and what those tests did, which until now lived only in the build
 * that ran them. This writes the second half as one JUnit document — {@link
 * Evidence#RESULTS} — so a deployed product, and every product composing it,
 * reads each promise's standing as its release build would have.
 *
 * <p><b>Faithful, and nothing else.</b> Every test class the run reported, each
 * of its test cases with its outcome — passed, failed, erred or skipped — and a
 * class that held no tests marked so. Never a message, a stack trace or what a
 * test printed: those can carry what a release must not, and an outcome is all
 * a standing reads. A class the run did not report is absent, not guessed.
 * The document names the run it came from.
 */
public final class PackedResults {

    private PackedResults() {
    }

    /**
     * The run's results as one document.
     *
     * @param roots where the run left its JUnit XML; every {@code TEST-*.xml}
     *              beneath them is read
     * @param run   which run this was, as a reader of the release should be
     *              able to tell: a version, a CI run, a time
     * @throws IllegalStateException naming a report that cannot be read — a
     *         release that packed part of its run would read as a run it was
     *         not
     */
    public static String of(Collection<Path> roots, String run) {
        Map<String, List<Element>> suites = new TreeMap<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            List<Path> reports;
            try (Stream<Path> walk = Files.walk(root)) {
                reports = walk.filter(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith("TEST-") && name.endsWith(".xml");
                }).sorted().toList();
            } catch (IOException e) {
                throw new IllegalStateException("could not list " + root, e);
            }
            for (Path report : reports) {
                try (InputStream in = Files.newInputStream(report)) {
                    Element root1 = parse(in).getDocumentElement();
                    List<Element> found = new ArrayList<>();
                    if (root1.getTagName().equals("testsuite")) {
                        found.add(root1);
                    }
                    NodeList nested = root1.getElementsByTagName("testsuite");
                    for (int i = 0; i < nested.getLength(); i++) {
                        found.add((Element) nested.item(i));
                    }
                    for (Element suite : found) {
                        suites.computeIfAbsent(suite.getAttribute("name"), n -> new ArrayList<>())
                                .add(suite);
                    }
                } catch (Exception e) {
                    throw new IllegalStateException("could not read " + report + ": "
                            + e.getMessage(), e);
                }
            }
        }

        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<testsuites name=\"").append(attribute(run)).append("\">\n");
        for (Map.Entry<String, List<Element>> entry : suites.entrySet()) {
            StringBuilder cases = new StringBuilder();
            int tests = 0;
            int failures = 0;
            int errors = 0;
            int skipped = 0;
            for (Element suite : entry.getValue()) {
                NodeList testCases = suite.getElementsByTagName("testcase");
                for (int i = 0; i < testCases.getLength(); i++) {
                    Element testCase = (Element) testCases.item(i);
                    if (testCase.getParentNode() != suite) {
                        continue;
                    }
                    String outcome = has(testCase, "failure") ? "failure"
                            : has(testCase, "error") ? "error"
                            : has(testCase, "skipped") ? "skipped" : null;
                    tests++;
                    failures += "failure".equals(outcome) ? 1 : 0;
                    errors += "error".equals(outcome) ? 1 : 0;
                    skipped += "skipped".equals(outcome) ? 1 : 0;
                    cases.append("    <testcase classname=\"")
                            .append(attribute(testCase.getAttribute("classname").isEmpty()
                                    ? entry.getKey() : testCase.getAttribute("classname")))
                            .append("\" name=\"").append(attribute(testCase.getAttribute("name")))
                            .append(outcome == null ? "\"/>\n" : "\"><" + outcome + "/></testcase>\n");
                }
            }
            out.append("  <testsuite name=\"").append(attribute(entry.getKey()))
                    .append("\" tests=\"").append(tests).append("\" failures=\"").append(failures)
                    .append("\" errors=\"").append(errors).append("\" skipped=\"").append(skipped)
                    .append(cases.isEmpty() ? "\"/>\n" : "\">\n" + cases + "  </testsuite>\n");
        }
        return out.append("</testsuites>\n").toString();
    }

    /**
     * Writes the run's results.
     *
     * <pre>
     * --out file       the document to write (required)
     * --run text       which run this was (required)
     * --results dir    where the run left its JUnit XML; repeatable
     * </pre>
     */
    public static void main(String[] args) throws IOException {
        Path out = null;
        String run = null;
        List<Path> roots = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> out = Path.of(args[++i]);
                case "--run" -> run = args[++i];
                case "--results" -> roots.add(Path.of(args[++i]));
                default -> throw new IllegalArgumentException("unknown argument " + args[i]
                        + " — expected --out, --run or --results");
            }
        }
        if (out == null || run == null) {
            throw new IllegalArgumentException("--out names the document to write, and --run "
                    + "the run it is of");
        }
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        Files.writeString(out, of(roots, run), StandardCharsets.UTF_8);
        System.out.println("packed results of " + run + ": " + out.toAbsolutePath());
    }

    private static Document parse(InputStream in) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // A report is data a build left behind, and nothing in one needs a
        // document type: refused, so a crafted file cannot reach outside it.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(in);
    }

    private static boolean has(Element testCase, String child) {
        return testCase.getElementsByTagName(child).getLength() > 0;
    }

    private static String attribute(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

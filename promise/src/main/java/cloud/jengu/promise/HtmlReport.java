package cloud.jengu.promise;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The ledger: what a product promises and what proves it, as one HTML page.
 *
 * <p>One self-contained file — its styles, its script and the composed model
 * inside it — so it opens from a build's output, a CI artifact or a static
 * site with nothing to fetch. The page is a template shipped in this jar; this
 * class only writes the model into it, as JSON, so the reading behind the page
 * is the reading every other report uses.
 *
 * <p>Given {@link Evidence}, each promise carries its standing in the run and
 * each citing test its class's result. Given only a {@link Registry.Model},
 * the page shows status alone.
 *
 * <p><b>A product's ledger is the product's.</b> The framework is on every
 * product's classpath and composes its own catalogue with theirs, so a
 * product's page would otherwise lead with the toolset's promises, none of
 * whose tests that product ran. {@link #render(Evidence, String, boolean)}
 * leaves them out, except a promise of the toolset's that one of the product's
 * own groups lists — that one the product rests on, and shows.
 */
public final class HtmlReport {

    /** The page, beside this class in the jar. */
    static final String TEMPLATE = "ledger.html";

    private HtmlReport() {
    }

    /** The ledger of the evidence: the model, each promise's standing, each test class's result. */
    public static String render(Evidence evidence, String title) {
        return render(evidence, title, true);
    }

    /**
     * The same, with the toolset's own catalogue or without it: without, only
     * the toolset's promises that the product's own groups list are shown.
     */
    public static String render(Evidence evidence, String title, boolean withToolset) {
        return page(json(evidence.model(), evidence, title, withToolset), title);
    }

    /** The ledger of the model alone, without a run. */
    public static String render(Registry.Model model, String title) {
        return page(json(model, null, title, true), title);
    }

    /**
     * A short account of the same, in markdown: the line a CI job's summary
     * leads with, and the groups that are not kept or not passing.
     */
    public static String summary(Evidence evidence, String title) {
        return summary(evidence, title, true);
    }

    /** The summary of the ledger {@link #render(Evidence, String, boolean)} draws. */
    public static String summary(Evidence evidence, String title, boolean withToolset) {
        Registry.Model model = evidence.model();
        Map<String, Promise> promises = promises(model, withToolset);
        long kept = promises.values().stream().filter(p -> kept(model, p)).count();
        Map<Evidence.Standing, Long> run = new java.util.EnumMap<>(Evidence.Standing.class);
        promises.values().forEach(p -> run.merge(evidence.standing(p), 1L, Long::sum));

        StringBuilder out = new StringBuilder("## ").append(title).append(": ")
                .append(percent(kept, promises.size())).append(" kept\n\n")
                .append("**").append(kept).append(" of ").append(promises.size())
                .append("** promises kept · last run: ")
                .append(run.getOrDefault(Evidence.Standing.PASSING, 0L)).append(" passing, ")
                .append(run.getOrDefault(Evidence.Standing.FAILING, 0L)).append(" failing, ")
                .append(run.getOrDefault(Evidence.Standing.NO_RESULT, 0L)).append(" without a result, ")
                .append(run.getOrDefault(Evidence.Standing.UNCITED, 0L)).append(" uncited\n\n");

        out.append("| | groups | kept | passing |\n|---|---|---|---|\n");
        Map<String, List<Classified>> byKind = new LinkedHashMap<>();
        for (String kind : List.of("Stories", "Features", "Qualities", "Constraints")) {
            byKind.put(kind, new ArrayList<>());
        }
        for (Classified classified : groups(model, withToolset)) {
            byKind.get(kindName(classified)).add(classified);
        }
        List<String> attention = new ArrayList<>();
        for (Map.Entry<String, List<Classified>> kind : byKind.entrySet()) {
            if (kind.getValue().isEmpty()) {
                continue;
            }
            java.util.Set<Promise> in = new java.util.LinkedHashSet<>();
            kind.getValue().forEach(c -> in.addAll(c.promises()));
            long k = in.stream().filter(p -> kept(model, p)).count();
            long passing = in.stream()
                    .filter(p -> evidence.standing(p) == Evidence.Standing.PASSING).count();
            out.append("| ").append(kind.getKey()).append(" | ").append(kind.getValue().size())
                    .append(" | ").append(k).append(" / ").append(in.size())
                    .append(" | ").append(passing).append(" / ").append(in.size()).append(" |\n");
            for (Classified classified : kind.getValue()) {
                long groupKept = classified.promises().stream().filter(p -> kept(model, p)).count();
                long failing = classified.promises().stream()
                        .filter(p -> evidence.standing(p) == Evidence.Standing.FAILING).count();
                if (groupKept < classified.promises().size() || failing > 0) {
                    attention.add("- `" + classified.code() + "` " + classified.title() + " — "
                            + groupKept + " / " + classified.promises().size() + " kept"
                            + (failing > 0 ? ", " + failing + " failing" : ""));
                }
            }
        }
        if (!attention.isEmpty()) {
            out.append("\nNot yet kept, or failing:\n\n");
            attention.stream().limit(20).forEach(line -> out.append(line).append('\n'));
            if (attention.size() > 20) {
                out.append("- and ").append(attention.size() - 20).append(" more\n");
            }
        }
        if (!evidence.unreadable().isEmpty()) {
            out.append("\n**Could not read:**\n\n");
            evidence.unreadable().forEach(u -> out.append("- `").append(u.source()).append("`: ")
                    .append(u.reason()).append('\n'));
        }
        return out.toString();
    }

    /**
     * Writes the ledger of everything on this process's classpath against the
     * results under the roots given.
     *
     * <pre>
     * --out file        the HTML page to write (required)
     * --summary file    the markdown summary to write, if wanted
     * --title text      what the page is the ledger of (default "Promise ledger")
     * --results dir     a directory to read JUnit XML from; repeatable
     * --strict          exit 1, after writing, when anything could not be read
     * --without-toolset leave the toolset's own catalogue out, but for what the
     *                   product's groups list
     * </pre>
     */
    public static void main(String[] args) throws IOException {
        Path out = null;
        Path summary = null;
        String title = "Promise ledger";
        List<Path> roots = new ArrayList<>();
        boolean strict = false;
        boolean withToolset = true;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> out = Path.of(args[++i]);
                case "--summary" -> summary = Path.of(args[++i]);
                case "--title" -> title = args[++i];
                case "--results" -> roots.add(Path.of(args[++i]));
                case "--strict" -> strict = true;
                case "--without-toolset" -> withToolset = false;
                default -> throw new IllegalArgumentException("unknown argument " + args[i]
                        + " — expected --out, --summary, --title, --results, --strict or "
                        + "--without-toolset");
            }
        }
        if (out == null) {
            throw new IllegalArgumentException("--out names the page to write");
        }
        Evidence evidence = Evidence.read(HtmlReport.class.getClassLoader(), roots);
        write(out, render(evidence, title, withToolset));
        if (summary != null) {
            write(summary, summary(evidence, title, withToolset));
        }
        System.out.println("promise ledger: " + out.toAbsolutePath());
        evidence.unreadable().forEach(u ->
                System.err.println("could not read " + u.source() + ": " + u.reason()));
        if (strict && !evidence.unreadable().isEmpty()) {
            System.exit(1);
        }
    }

    private static void write(Path file, String text) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(file, text);
    }

    private static String page(String json, String title) {
        String template;
        try (InputStream in = HtmlReport.class.getResourceAsStream(TEMPLATE)) {
            if (in == null) {
                throw new IllegalStateException(TEMPLATE + " is missing from the framework's jar");
            }
            template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return template.replace("/*TITLE*/", escapeHtml(title)).replace("/*MODEL*/", json);
    }

    // ── the model as the page reads it ──────────────────────────────────

    private static String json(Registry.Model model, Evidence evidence, String title,
            boolean withToolset) {
        Map<String, Promise> promises = promises(model, withToolset);
        StringBuilder out = new StringBuilder("{");
        out.append("\"title\":").append(q(title));
        out.append(",\"generated\":").append(q(Instant.now().toString()));
        out.append(",\"promises\":{");
        boolean first = true;
        for (Map.Entry<String, Promise> entry : promises.entrySet()) {
            Promise promise = entry.getValue();
            out.append(first ? "" : ",").append(q(entry.getKey())).append(":{");
            first = false;
            out.append("\"text\":").append(q(promise.text()));
            out.append(",\"status\":").append(q(model.statusOf(promise).name()));
            out.append(",\"gap\":").append(promise.gap());
            out.append(",\"catalogue\":").append(q(namespace(promise)));
            if (promise.assurance() != null) {
                out.append(",\"assurance\":").append(q(promise.assurance()));
            }
            if (evidence != null) {
                out.append(",\"standing\":").append(q(evidence.standing(promise).name()));
            }
            out.append(",\"tested\":").append(list(new ArrayList<>(model.citing(promise))));
            out.append(",\"enforced\":").append(list(new ArrayList<>(model.enforcing(promise))));
            out.append('}');
        }
        out.append("},\"groups\":[");
        first = true;
        for (Classified classified : groups(model, withToolset)) {
            out.append(first ? "" : ",").append('{');
            first = false;
            out.append("\"kind\":").append(q(kind(classified)));
            out.append(",\"code\":").append(q(classified.code()));
            out.append(",\"title\":").append(q(classified.title()));
            if (classified.description() != null) {
                out.append(",\"description\":").append(q(classified.description()));
            }
            List<String> codes = new ArrayList<>();
            classified.promises().forEach(p -> codes.add(model.codeOf(p)));
            out.append(",\"promises\":").append(list(codes)).append('}');
        }
        out.append("],\"areas\":[");
        first = true;
        for (Area area : model.areas()) {
            out.append(first ? "" : ",").append('{');
            first = false;
            out.append("\"code\":").append(q(area.code()));
            out.append(",\"title\":").append(q(area.title()));
            if (area.description() != null) {
                out.append(",\"description\":").append(q(area.description()));
            }
            List<String> covers = new ArrayList<>();
            area.covers().forEach(c -> covers.add(c.code()));
            out.append(",\"covers\":").append(list(covers)).append('}');
        }
        out.append(']');
        if (evidence != null) {
            out.append(",\"results\":{");
            first = true;
            for (Evidence.Result result : new TreeMap<>(evidence.results()).values()) {
                out.append(first ? "" : ",").append(q(result.testClass())).append(":{")
                        .append("\"tests\":").append(result.tests())
                        .append(",\"failures\":").append(result.failures())
                        .append(",\"errors\":").append(result.errors())
                        .append(",\"skipped\":").append(result.skipped()).append('}');
                first = false;
            }
            out.append("},\"unreadable\":[");
            first = true;
            for (Evidence.Unreadable unreadable : evidence.unreadable()) {
                out.append(first ? "" : ",").append("{\"source\":").append(q(unreadable.source()))
                        .append(",\"reason\":").append(q(String.valueOf(unreadable.reason())))
                        .append('}');
                first = false;
            }
            out.append(']');
        }
        return out.append('}').toString();
    }

    /**
     * Every promise of the graph by its code, the gaps its groups declare
     * included — without the toolset's own, but for those the shown groups list.
     */
    private static Map<String, Promise> promises(Registry.Model model, boolean withToolset) {
        Map<String, Promise> promises = new LinkedHashMap<>();
        for (Promise promise : model.promises()) {
            if (withToolset || !(promise instanceof ToolsetPromises)) {
                promises.put(model.codeOf(promise), promise);
            }
        }
        for (Classified classified : groups(model, withToolset)) {
            for (Promise promise : classified.promises()) {
                promises.putIfAbsent(model.codeOf(promise), promise);
            }
        }
        return promises;
    }

    /** The groups shown: all of them, or all but the toolset's own. */
    private static List<Classified> groups(Registry.Model model, boolean withToolset) {
        return model.classifications().stream()
                .filter(c -> withToolset || !(c instanceof ToolsetFeatures))
                .toList();
    }

    /** Kept: proven by a citing test, or assured by a reason recorded on the constant. */
    private static boolean kept(Registry.Model model, Promise promise) {
        PromiseStatus status = model.statusOf(promise);
        return status == PromiseStatus.PROVEN || status == PromiseStatus.ASSURED;
    }

    private static String kind(Classified classified) {
        if (classified instanceof Story) {
            return "story";
        }
        if (classified instanceof Feature) {
            return "feature";
        }
        if (classified instanceof Quality) {
            return "quality";
        }
        if (classified instanceof Constraint) {
            return "constraint";
        }
        return "feature";
    }

    private static String kindName(Classified classified) {
        return switch (kind(classified)) {
            case "story" -> "Stories";
            case "quality" -> "Qualities";
            case "constraint" -> "Constraints";
            default -> "Features";
        };
    }

    private static String namespace(Promise promise) {
        if (!(promise instanceof Enum<?> constant)) {
            return "gap";
        }
        Catalogue catalogue = constant.getDeclaringClass().getAnnotation(Catalogue.class);
        return catalogue == null ? "" : catalogue.namespace();
    }

    private static String percent(long part, long whole) {
        if (whole == 0) {
            return "0%";
        }
        double value = Math.round(1000.0 * part / whole) / 10.0;
        return (value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value)) + "%";
    }

    private static String list(List<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            out.append(i == 0 ? "" : ",").append(q(values.get(i)));
        }
        return out.append(']').toString();
    }

    /**
     * A JSON string that is also safe inside the page's script element: '&lt;'
     * written as an escape, so no text a catalogue holds can close the element
     * and run as markup.
     */
    static String q(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c");
                case '>' -> out.append("\\u003e");
                case '&' -> out.append("\\u0026");
                case ' ' -> out.append("\\u2028");
                case ' ' -> out.append("\\u2029");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

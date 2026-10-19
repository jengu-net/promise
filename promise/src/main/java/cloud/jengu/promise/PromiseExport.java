package cloud.jengu.promise;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The composed graph and its evidence written out as one JSON document, for
 * readers that cannot load the catalogues themselves: a tool, a script, a
 * server answering questions about the project.
 *
 * <p>A projection, never a source: it is {@link Evidence}, the same reading
 * the ledger draws and a gate decides on. It holds the areas asked for — every
 * area of the graph unless a product names its own — their groups, and every
 * promise those groups list or rest on, their bases included, each once.
 *
 * <pre>
 * {
 *   "generatedAt": "…",
 *   "areas":    [{"code", "title", "description", "groups": [code…]}],
 *   "groups":   [{"code", "kind", "title", "description", "document", "area",
 *                 "promises": [code…]}],
 *   "promises": [{"code", "text", "catalogue", "status", "standing",
 *                 "citingTests", "basis", "basedOnBy", "groups", "assurance"?}],
 *   "unreadable": ["source: reason"…]
 * }
 * </pre>
 *
 * {@code kind} is {@code story}, {@code feature}, {@code quality} or
 * {@code constraint}; {@code status} is a {@link PromiseStatus};
 * {@code standing} an {@link Evidence.Standing}; {@code document} the page
 * {@link Classified#document()} names, or null.
 */
public final class PromiseExport {

    private PromiseExport() {
    }

    /**
     * Writes the export.
     *
     * <pre>
     * --out FILE       the document to write (required)
     * --results DIR    where a run left its JUnit XML; repeatable
     * --areas CLASS    an enum of areas to export, instead of every area of the
     *                  graph; repeatable
     * --strict         exit 1, after writing, when anything could not be read
     * </pre>
     */
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        if (!run(args, PromiseExport.class.getClassLoader())) {
            System.exit(1);
        }
    }

    /** Writes the export from a classpath; false when strict and something could not be read. */
    static boolean run(String[] args, ClassLoader loader) throws IOException, ClassNotFoundException {
        Path out = null;
        List<Path> roots = new ArrayList<>();
        List<Area> areas = new ArrayList<>();
        boolean strict = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out" -> out = Path.of(args[++i]);
                case "--results" -> roots.add(Path.of(args[++i]));
                case "--areas" -> areas.addAll(areasOf(Class.forName(args[++i], true, loader)));
                case "--strict" -> strict = true;
                default -> throw new IllegalArgumentException("unknown argument " + args[i]
                        + " — expected --out, --results, --areas or --strict");
            }
        }
        if (out == null) {
            throw new IllegalArgumentException("--out names the document to write");
        }
        Evidence evidence = Evidence.read(loader, roots);
        Path parent = out.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(out, areas.isEmpty() ? json(evidence) : json(evidence, areas),
                StandardCharsets.UTF_8);
        System.out.println("promise export: " + out.toAbsolutePath());
        evidence.unreadable().forEach(u ->
                System.err.println("could not read " + u.source() + ": " + u.reason()));
        return !strict || evidence.unreadable().isEmpty();
    }

    /** Every area of the graph, its groups and the promises they reach. */
    public static String json(Evidence evidence) {
        return json(evidence, evidence.model().areas());
    }

    /** The areas named, their groups and the promises they reach. */
    public static String json(Evidence evidence, Collection<? extends Area> areas) {
        Registry.Model model = evidence.model();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedAt", Instant.now().toString());

        List<Object> areaList = new ArrayList<>();
        Map<Classified, String> groups = new LinkedHashMap<>();
        for (Area area : areas) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("code", area.code());
            a.put("title", area.title());
            a.put("description", area.description());
            a.put("groups", area.covers().stream().map(Classified::code).toList());
            areaList.add(a);
            area.covers().forEach(c -> groups.putIfAbsent(c, area.code()));
        }
        root.put("areas", areaList);

        Set<Promise> promises = new LinkedHashSet<>();
        List<Object> groupList = new ArrayList<>();
        for (Map.Entry<Classified, String> entry : groups.entrySet()) {
            Classified c = entry.getKey();
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("code", c.code());
            g.put("kind", HtmlReport.kind(c));
            g.put("title", c.title());
            g.put("description", c.description());
            g.put("document", c.document());
            g.put("area", entry.getValue());
            g.put("promises", c.promises().stream().map(model::codeOf).toList());
            groupList.add(g);
            c.promises().forEach(p -> reach(model, p, promises));
        }
        root.put("groups", groupList);

        List<Object> promiseList = new ArrayList<>();
        for (Promise p : promises) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", model.codeOf(p));
            m.put("text", p.text());
            m.put("catalogue", HtmlReport.namespace(p));
            m.put("status", model.statusOf(p).name());
            m.put("standing", evidence.standing(p).name());
            m.put("citingTests", model.citing(p).stream().sorted().toList());
            m.put("basis", model.basis(p).stream().map(model::codeOf).toList());
            m.put("basedOnBy", model.basedOn(p).stream().map(model::codeOf).toList());
            m.put("groups", groups.keySet().stream().filter(c -> c.promises().contains(p))
                    .map(Classified::code).toList());
            if (p.assurance() != null) {
                m.put("assurance", p.assurance());
            }
            promiseList.add(m);
        }
        root.put("promises", promiseList);
        root.put("unreadable", evidence.unreadable().stream()
                .map(u -> u.source() + ": " + u.reason()).toList());
        StringBuilder sb = new StringBuilder();
        write(root, sb, 0);
        return sb.append('\n').toString();
    }

    /** The areas an enum declares. */
    private static List<Area> areasOf(Class<?> type) {
        if (!type.isEnum() || !Area.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException(type.getName() + " is not an enum of areas");
        }
        List<Area> areas = new ArrayList<>();
        for (Object constant : type.getEnumConstants()) {
            areas.add((Area) constant);
        }
        return areas;
    }

    /** A promise and everything its basis reaches, each once. */
    private static void reach(Registry.Model model, Promise p, Set<Promise> into) {
        if (into.add(p)) {
            for (Promise b : model.basis(p)) {
                reach(model, b, into);
            }
        }
    }

    // ── a JSON writer for maps, lists, strings, numbers and booleans ──────

    private static void write(Object v, StringBuilder sb, int depth) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            sb.append('"');
            for (char ch : s.toCharArray()) {
                switch (ch) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (ch < 0x20) {
                            sb.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                        } else {
                            sb.append(ch);
                        }
                    }
                }
            }
            sb.append('"');
        } else if (v instanceof Boolean || v instanceof Number) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                sb.append(first ? "\n" : ",\n").append(" ".repeat(depth + 1));
                write(String.valueOf(e.getKey()), sb, depth + 1);
                sb.append(": ");
                write(e.getValue(), sb, depth + 1);
                first = false;
            }
            sb.append(first ? "}" : "\n" + " ".repeat(depth) + "}");
        } else if (v instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                write(l.get(i), sb, depth + 1);
            }
            sb.append(']');
        } else {
            write(String.valueOf(v), sb, depth);
        }
    }
}

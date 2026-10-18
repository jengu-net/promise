package cloud.jengu.promise.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Projects the skill-blocks a project's documents carry into a plugin an agent
 * installs: one {@code SKILL.md} per block, the plugin's manifest, its
 * marketplace and a README naming each skill's source.
 *
 * <p>A block is written once, in the page that states its rules:
 *
 * <pre>
 * &lt;!-- skill: shop-refunds --&gt;
 * ```yaml
 * name: shop-refunds
 * applies-when: &gt;-
 *   Changing how a refund is computed or approved.
 * reference: docs/refunds.md
 * ```
 * **Rules**
 * - MUST refund no more than was paid.
 * &lt;!-- /skill --&gt;
 * </pre>
 *
 * <p>The body follows a {@code **Rules**} marker, or a {@code **Reference**}
 * marker for a skill that maps what is offered rather than what is forbidden.
 * Links in the body are written from the page; the projection rewrites them
 * to reach the same files from where the skill is read.
 *
 * <p>A page may also mark a prose region ({@code <!-- claude:begin -->} …
 * {@code <!-- claude:end -->}) that is copied into an agent's instructions
 * file between its {@code <!-- rules:begin -->} and {@code <!-- rules:end -->}
 * markers, so the file an agent reads first cannot drift from the page.
 *
 * <p>Pure: it reads the documents and answers what the projection is, file by
 * file; writing it and comparing it are the tasks' work.
 */
final class SkillProjection {

    static final Pattern BLOCK = Pattern.compile(
            "<!--\\s*skill:\\s*(?<name>[\\w-]+)\\s*-->(?<body>.*?)<!--\\s*/skill\\s*-->", Pattern.DOTALL);
    private static final Pattern YAML = Pattern.compile("```ya?ml\\s*(?<yaml>.*?)```", Pattern.DOTALL);
    private static final Pattern KEY = Pattern.compile("^(?<key>[\\w-]+):\\s*(?<value>.*)$");
    private static final Pattern MARKER = Pattern.compile("\\*\\*(?<kind>Rules|Reference)\\*\\*\\s*\\n(?<body>.*)",
            Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile("(\\]!?)\\(([^)#\\s]+)(#[^)\\s]*)?\\)");
    static final Pattern REGION = Pattern.compile(
            "<!--\\s*claude:begin\\s*-->\\n(?<body>.*?)<!--\\s*claude:end\\s*-->", Pattern.DOTALL);
    static final Pattern TARGET = Pattern.compile(
            "(?<open><!--\\s*rules:begin[^>]*-->\\n).*?(?<close><!--\\s*rules:end\\s*-->)", Pattern.DOTALL);

    /** One skill-block, as its page declares it. */
    record Block(String name, String appliesWhen, String reference, String kind, String body, Path page) {
    }

    /** What the plugin is called and who it is from. */
    record Plugin(String name, String description, String owner, String baseVersion, String linkBase) {
    }

    private SkillProjection() {
    }

    /**
     * Every block under {@code docs}, in page order. A block that cannot be
     * read is refused rather than skipped: a skill that silently vanishes is
     * the drift the projection exists to prevent.
     */
    static List<Block> read(Path root, Path docs) {
        List<Block> blocks = new ArrayList<>();
        Map<String, Path> seen = new TreeMap<>();
        for (Path page : pages(docs)) {
            String text = readString(page);
            Matcher m = BLOCK.matcher(text);
            while (m.find()) {
                String name = m.group("name");
                String where = root.relativize(page) + ": skill " + name;
                Matcher yaml = YAML.matcher(m.group("body"));
                if (!yaml.find()) {
                    throw new IllegalStateException(where + " has no yaml header");
                }
                Map<String, String> meta = meta(yaml.group("yaml"));
                if (!name.equals(meta.get("name"))) {
                    throw new IllegalStateException(where + " is named " + meta.get("name") + " in its header");
                }
                String appliesWhen = meta.get("applies-when");
                if (appliesWhen == null || appliesWhen.isBlank()) {
                    throw new IllegalStateException(where + " says nothing under applies-when, so it would never "
                            + "be read");
                }
                Matcher marker = MARKER.matcher(m.group("body").substring(yaml.end()));
                if (!marker.find() || marker.group("body").isBlank()) {
                    throw new IllegalStateException(where + " has neither **Rules** nor **Reference** under its "
                            + "header, so it would project an empty skill");
                }
                Path first = seen.putIfAbsent(name, page);
                if (first != null) {
                    throw new IllegalStateException(where + " is declared again; the first is in "
                            + root.relativize(first));
                }
                blocks.add(new Block(name, appliesWhen, meta.getOrDefault("reference", ""),
                        marker.group("kind"), marker.group("body").strip(), page));
            }
        }
        return blocks;
    }

    /**
     * The projection, as files relative to {@code output}: each skill, the
     * plugin's manifest and README, and — under its own path relative to the
     * project root, keyed with a leading {@code /} — the marketplace.
     */
    static Map<String, String> render(Path root, Path output, Path marketplace, Plugin plugin,
            List<Block> blocks) {
        Map<String, String> skills = new LinkedHashMap<>();
        for (Block block : blocks) {
            Path skill = output.resolve("skills").resolve(block.name()).resolve("SKILL.md");
            skills.put("skills/" + block.name() + "/SKILL.md", skill(root, skill, plugin, block));
        }
        Map<String, String> files = new LinkedHashMap<>(skills);
        files.put(".claude-plugin/plugin.json", manifest(plugin, version(plugin, skills)));
        files.put("README.md", readme(root, plugin, blocks));
        String source = "./" + slashes(marketplace.getParent().getParent().relativize(output));
        files.put("/" + slashes(root.relativize(marketplace)), marketplaceJson(plugin, source.equals("./") ? "./"
                : source));
        return files;
    }

    /**
     * The plugin's version: the base the project names, then a digest of every
     * skill. An installed plugin is served from a cache keyed by its version,
     * so a skill that changed under an unchanged version would never reach an
     * installed copy.
     */
    static String version(Plugin plugin, Map<String, String> skills) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, String> skill : new TreeMap<>(skills).entrySet()) {
                digest.update(skill.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(skill.getValue().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return plugin.baseVersion() + "-" + hex.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The marked prose region, if a page marks one; two marked regions are refused. */
    static Optional<String> region(Path root, Path docs) {
        String found = null;
        Path where = null;
        for (Path page : pages(docs)) {
            Matcher m = REGION.matcher(readString(page));
            if (m.find()) {
                if (found != null) {
                    throw new IllegalStateException(root.relativize(page) + " marks a claude:begin region, and so "
                            + "does " + root.relativize(where) + "; the instructions file takes one");
                }
                found = m.group("body");
                where = page;
            }
        }
        return Optional.ofNullable(found);
    }

    /** {@code instructions} with its marked region replaced by {@code region}, or empty when it has no markers. */
    static Optional<String> withRegion(String instructions, String region) {
        Matcher m = TARGET.matcher(instructions);
        if (!m.find()) {
            return Optional.empty();
        }
        return Optional.of(instructions.substring(0, m.start()) + m.group("open") + region.strip() + "\n"
                + m.group("close") + instructions.substring(m.end()));
    }

    private static String skill(Path root, Path skill, Plugin plugin, Block block) {
        String heading = block.kind().equals("Reference") ? "Reference" : "Rules";
        return "---\n"
                + "name: " + block.name() + "\n"
                + "description: " + block.appliesWhen() + "\n"
                + "---\n\n"
                + "# " + block.name() + "\n\n"
                + "> **Generated from its source page — do not edit.** Change the skill-block in the\n"
                + "> page and run `generateSkills`.\n\n"
                + "**Apply when:** " + block.appliesWhen() + "\n\n"
                + "## " + heading + "\n\n"
                + links(root, skill, plugin, block.page(), block.body()) + "\n\n"
                + "---\n\n"
                + (block.reference().isEmpty() ? "Where this is stated and argued: `" + slashes(root.relativize(
                        block.page())) + "`\n"
                        : "Where this is stated and argued: [`" + block.reference() + "`]("
                                + target(root, skill, plugin, root.resolve(block.reference()).normalize(), "")
                                + ")\n");
    }

    /** Links written from the page, rewritten to reach the same files from the skill. */
    private static String links(Path root, Path skill, Plugin plugin, Path page, String body) {
        Matcher m = LINK.matcher(body);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String to = m.group(2);
            String replacement;
            if (to.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") || to.startsWith("/")) {
                replacement = m.group(0);
            } else {
                Path file = page.getParent().resolve(to).normalize();
                replacement = m.group(1) + "(" + target(root, skill, plugin, file,
                        m.group(3) == null ? "" : m.group(3)) + ")";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String target(Path root, Path skill, Plugin plugin, Path file, String anchor) {
        if (plugin.linkBase() != null && !plugin.linkBase().isBlank()) {
            String base = plugin.linkBase().endsWith("/") ? plugin.linkBase() : plugin.linkBase() + "/";
            return base + slashes(root.relativize(file)) + anchor;
        }
        return slashes(skill.getParent().relativize(file)) + anchor;
    }

    private static String manifest(Plugin plugin, String version) {
        return "{\n"
                + "  \"$schema\": \"https://json.schemastore.org/claude-code-plugin-manifest.json\",\n"
                + "  \"name\": " + json(plugin.name()) + ",\n"
                + "  \"version\": " + json(version) + ",\n"
                + "  \"description\": " + json(plugin.description()) + ",\n"
                + "  \"author\": {\n    \"name\": " + json(plugin.owner()) + "\n  }\n"
                + "}\n";
    }

    private static String marketplaceJson(Plugin plugin, String source) {
        return "{\n"
                + "  \"name\": " + json(plugin.name()) + ",\n"
                + "  \"owner\": {\n    \"name\": " + json(plugin.owner()) + "\n  },\n"
                + "  \"plugins\": [\n    {\n      \"name\": " + json(plugin.name()) + ",\n"
                + "      \"source\": " + json(source) + "\n    }\n  ]\n"
                + "}\n";
    }

    private static String readme(Path root, Plugin plugin, List<Block> blocks) {
        StringBuilder rows = new StringBuilder();
        for (Block block : blocks) {
            rows.append("- **").append(block.name()).append("** ← `")
                    .append(slashes(root.relativize(block.page()))).append("`\n");
        }
        return "# " + plugin.name() + "\n\n"
                + "> **Nothing here is edited by hand.** Each skill is projected from the skill-block in the\n"
                + "> page that owns its rules. Change the block and run `generateSkills`; `verifySkills`\n"
                + "> fails the build while this folder and the pages disagree.\n\n"
                + (plugin.description().isBlank() ? "" : plugin.description() + "\n\n")
                + "## Skills\n\n"
                + rows + "\n"
                + "## Installing\n\n"
                + "Add the marketplace this projection writes, by its repository or its folder, and enable\n"
                + "the plugin. Its version follows the skills' content, so an installed copy takes every\n"
                + "change.\n";
    }

    private static Map<String, String> meta(String yaml) {
        Map<String, String> out = new LinkedHashMap<>();
        String[] lines = yaml.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = KEY.matcher(lines[i]);
            if (!m.matches()) {
                continue;
            }
            String value = m.group("value").strip();
            if (value.equals(">-") || value.equals(">") || value.equals("|") || value.equals("|-")) {
                StringBuilder folded = new StringBuilder();
                while (i + 1 < lines.length && (lines[i + 1].startsWith(" ") || lines[i + 1].startsWith("\t")
                        || lines[i + 1].isBlank())) {
                    i++;
                    if (!lines[i].isBlank()) {
                        folded.append(folded.length() == 0 ? "" : " ").append(lines[i].strip());
                    }
                }
                value = folded.toString().replaceAll("\\s+", " ");
            }
            out.put(m.group("key"), value);
        }
        return out;
    }

    private static List<Path> pages(Path docs) {
        if (!Files.isDirectory(docs)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(docs)) {
            return walk.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String readString(Path page) {
        try {
            return Files.readString(page);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String json(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\t' -> out.append("\\t");
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
}

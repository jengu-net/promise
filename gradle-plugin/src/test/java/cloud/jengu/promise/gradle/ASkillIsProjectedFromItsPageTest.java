package cloud.jengu.promise.gradle;

import cloud.jengu.promise.ToolsetPromises;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A project's skill-blocks become an installable plugin, the build refuses a
 * projection that has drifted from its pages, and the plugin's version moves
 * with its skills.
 *
 * <p>Real builds, run by Gradle's own test kit, each in a directory of its own.
 */
class ASkillIsProjectedFromItsPageTest {

    private static final String REFUNDS = """
            # Refunds

            Source of truth for how a refund is decided.

            <!-- skill: shop-refunds -->
            ```yaml
            name: shop-refunds
            applies-when: >-
              Changing how a refund is computed
              or approved.
            reference: docs/rules/refunds.md
            ```
            **Rules**
            - MUST refund no more than was paid ([orders](../orders.md#totals)).
            - MUST NOT refund an order that was never paid.
            <!-- /skill -->
            """;

    private static final String CATALOGUE = """
            # What the shop offers

            <!-- claude:begin -->
            Never change a paid order; correct it with a new one.
            <!-- claude:end -->

            <!-- skill: shop-catalogue -->
            ```yaml
            name: shop-catalogue
            applies-when: >-
              Building on the shop rather than in it.
            reference: docs/catalogue.md
            ```
            **Reference**
            - Orders, refunds and their totals, each with its own page.
            <!-- /skill -->
            """;

    @Test
    @DisplayName("every skill-block in a project's pages becomes a skill of an installable plugin, its links "
            + "reaching what the page linked, and the instructions file takes the page's marked region")
    @ProvingToolset(ToolsetPromises.A_SKILL_IS_PROJECTED_FROM_ITS_PAGE)
    void everyBlockBecomesASkill() throws IOException {
        Path build = shop();
        run(build, "generateSkills");

        Path refunds = build.resolve("tools/shop-conventions/skills/shop-refunds/SKILL.md");
        String skill = Files.readString(refunds);
        assertTrue(skill.startsWith("---\nname: shop-refunds\n"
                + "description: Changing how a refund is computed or approved.\n---\n"), skill);
        assertTrue(skill.contains("## Rules\n\n- MUST refund no more than was paid"), skill);
        Matcher link = Pattern.compile("\\[orders\\]\\(([^)#]+)#totals\\)").matcher(skill);
        assertTrue(link.find(), skill);
        assertTrue(Files.isRegularFile(refunds.getParent().resolve(link.group(1)).normalize()),
                "the rewritten link reaches the page the block linked: " + link.group(1));

        String catalogue = Files.readString(build.resolve("tools/shop-conventions/skills/shop-catalogue/SKILL.md"));
        assertTrue(catalogue.contains("## Reference\n\n- Orders, refunds"), catalogue);

        String manifest = Files.readString(build.resolve("tools/shop-conventions/.claude-plugin/plugin.json"));
        assertTrue(manifest.contains("\"name\": \"shop-conventions\""), manifest);
        String marketplace = Files.readString(build.resolve(".claude-plugin/marketplace.json"));
        assertTrue(marketplace.contains("\"source\": \"./tools/shop-conventions\""), marketplace);

        String instructions = Files.readString(build.resolve("CLAUDE.md"));
        assertTrue(instructions.contains("<!-- rules:begin -->\nNever change a paid order; correct it with a new "
                + "one.\n<!-- rules:end -->"), instructions);
        assertTrue(instructions.startsWith("# The shop\n"), "the rest of the file is the author's: " + instructions);

        run(build, "check");
    }

    @Test
    @DisplayName("a skill-block the projection cannot read fails the build, naming the page and the skill, "
            + "rather than vanishing from the plugin")
    @ProvingToolset(ToolsetPromises.A_SKILL_IS_PROJECTED_FROM_ITS_PAGE)
    void anUnreadableBlockFailsTheBuild() throws IOException {
        Path build = shop();
        write(build, "docs/rules/refunds.md", REFUNDS.replace("applies-when: >-\n  Changing how a refund is "
                + "computed\n  or approved.\n", ""));

        BuildResult failed = runner(build, "generateSkills").buildAndFail();

        assertTrue(failed.getOutput().contains("docs/rules/refunds.md: skill shop-refunds says nothing under "
                + "applies-when"), failed.getOutput());
        assertFalse(Files.exists(build.resolve("tools/shop-conventions/skills/shop-refunds/SKILL.md")));
    }

    @Test
    @DisplayName("the check fails while the committed skills differ from their pages, naming each skill that "
            + "differs or has lost its block, and passes once they are projected again")
    @ProvingToolset(ToolsetPromises.A_STALE_PROJECTION_FAILS_THE_CHECK)
    void aStaleProjectionFailsTheCheck() throws IOException {
        Path build = shop();
        run(build, "generateSkills");
        write(build, "docs/rules/refunds.md", REFUNDS.replace("never paid", "never paid in full"));
        write(build, "docs/catalogue.md", CATALOGUE.substring(0, CATALOGUE.indexOf("<!-- skill:")));

        BuildResult failed = runner(build, "check").buildAndFail();

        assertTrue(failed.getOutput().contains("tools/shop-conventions/skills/shop-refunds/SKILL.md differs from "
                + "its source"), failed.getOutput());
        assertTrue(failed.getOutput().contains("tools/shop-conventions/skills/shop-catalogue has no block any "
                + "more"), failed.getOutput());

        run(build, "generateSkills");
        assertFalse(Files.exists(build.resolve("tools/shop-conventions/skills/shop-catalogue")),
                "a skill whose block is gone is removed");
        run(build, "check");
    }

    @Test
    @DisplayName("the plugin's version is its base followed by a digest of its skills: unchanged while they are, "
            + "new as soon as one changes")
    @ProvingToolset(ToolsetPromises.AN_INSTALLED_PLUGIN_SEES_EVERY_CHANGE)
    void theVersionFollowsTheSkills() throws IOException {
        Path build = shop();
        run(build, "generateSkills");
        String first = version(build);
        run(build, "generateSkills");
        String again = version(build);
        write(build, "docs/rules/refunds.md", REFUNDS.replace("never paid", "never paid in full"));
        run(build, "generateSkills");
        String changed = version(build);

        assertTrue(first.matches("2\\.1-[0-9a-f]{12}"), first);
        assertEquals(first, again);
        assertNotEquals(first, changed);
        assertTrue(changed.startsWith("2.1-"), changed);
    }

    /** A shop whose pages carry two skill-blocks and a marked region, and an instructions file to take it. */
    private static Path shop() throws IOException {
        Path build = Files.createTempDirectory("promise-skills");
        write(build, "settings.gradle.kts", "rootProject.name = \"shop\"\n");
        write(build, "build.gradle.kts", """
                plugins { id("cloud.jengu.promise.skills") }
                skills {
                    description.set("How the shop is built.")
                    owner.set("the shop")
                    baseVersion.set("2.1")
                    marketplace.set(layout.projectDirectory.file(".claude-plugin/marketplace.json"))
                    instructions.set(layout.projectDirectory.file("CLAUDE.md"))
                }
                """);
        write(build, "docs/rules/refunds.md", REFUNDS);
        write(build, "docs/catalogue.md", CATALOGUE);
        write(build, "docs/orders.md", "# Orders\n\n## Totals\n\nAn order's total is the sum of its lines.\n");
        write(build, "CLAUDE.md", "# The shop\n\n<!-- rules:begin -->\nold\n<!-- rules:end -->\n\nThe end.\n");
        return build;
    }

    private static String version(Path build) throws IOException {
        Matcher m = Pattern.compile("\"version\": \"([^\"]+)\"").matcher(
                Files.readString(build.resolve("tools/shop-conventions/.claude-plugin/plugin.json")));
        assertTrue(m.find());
        return m.group(1);
    }

    private static void run(Path build, String task) {
        runner(build, task).build();
    }

    private static GradleRunner runner(Path build, String task) {
        return GradleRunner.create()
                .withProjectDir(build.toFile())
                .withPluginClasspath()
                .withArguments(task, "--stacktrace")
                .forwardOutput();
    }

    private static void write(Path build, String path, String content) throws IOException {
        Path file = build.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

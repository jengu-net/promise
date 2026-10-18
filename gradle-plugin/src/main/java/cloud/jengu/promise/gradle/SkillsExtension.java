package cloud.jengu.promise.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

/** What a build may say about the plugin its skill-blocks project into. */
public interface SkillsExtension {

    /** Where the pages carrying skill-blocks are. {@code docs} unless said. */
    DirectoryProperty getDocs();

    /** Where the plugin is projected to. {@code tools/<name>} unless said. */
    DirectoryProperty getOutput();

    /**
     * Where the marketplace naming the plugin is written. Beside the plugin
     * unless said; a repository installed by its address carries it at its
     * root, {@code .claude-plugin/marketplace.json}.
     */
    RegularFileProperty getMarketplace();

    /** The plugin's name. {@code <project>-conventions} unless said. */
    Property<String> getName();

    /** One sentence on what the skills are for. */
    Property<String> getDescription();

    /** Who the plugin is from. */
    Property<String> getOwner();

    /**
     * The version the plugin's own is built on. A digest of the skills is
     * added to it, so each change to a skill is a new version. {@code 1.0}
     * unless said.
     */
    Property<String> getBaseVersion();

    /**
     * Where a skill's links point, when they should outlive the working tree:
     * a base address the repository-relative path is appended to. A skill is
     * read from wherever it was installed, where a relative link reaches
     * nothing. Relative links unless said.
     */
    Property<String> getLinkBase();

    /**
     * The agent instructions file a page's marked prose region is copied
     * into, between its {@code rules:begin} and {@code rules:end} markers.
     * None unless said.
     */
    RegularFileProperty getInstructions();
}

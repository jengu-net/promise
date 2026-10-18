package cloud.jengu.promise.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** What projecting the skills and checking a projection share: the source, the target, and the projection. */
@DisableCachingByDefault(because = "Its subclasses read and write a working tree, which is not cacheable output.")
public abstract class SkillsTask extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getRoot();

    @Internal
    public abstract DirectoryProperty getDocs();

    @Internal
    public abstract DirectoryProperty getOutput();

    @Internal
    public abstract RegularFileProperty getMarketplace();

    @Internal
    public abstract Property<String> getPluginName();

    @Internal
    public abstract Property<String> getPluginDescription();

    @Internal
    public abstract Property<String> getOwner();

    @Internal
    public abstract Property<String> getBaseVersion();

    @Internal
    public abstract Property<String> getLinkBase();

    @Internal
    public abstract RegularFileProperty getInstructions();

    /** Each file of the projection, keyed by its path under the output, or by {@code /}-prefixed root path. */
    protected Map<String, String> projection() {
        Path root = getRoot().get().getAsFile().toPath();
        Path docs = getDocs().get().getAsFile().toPath();
        List<SkillProjection.Block> blocks = SkillProjection.read(root, docs);
        SkillProjection.Plugin plugin = new SkillProjection.Plugin(getPluginName().get(),
                getPluginDescription().getOrElse(""), getOwner().getOrElse(getPluginName().get()),
                getBaseVersion().get(), getLinkBase().getOrNull());
        return SkillProjection.render(root, getOutput().get().getAsFile().toPath(),
                getMarketplace().get().getAsFile().toPath(), plugin, blocks);
    }

    /** Where a projected file lives. */
    protected Path file(String key) {
        if (key.startsWith("/")) {
            return getRoot().get().getAsFile().toPath().resolve(key.substring(1));
        }
        return getOutput().get().getAsFile().toPath().resolve(key);
    }
}

package cloud.jengu.promise.gradle;

import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes the projection: every skill, the plugin's manifest and README, the
 * marketplace, and the instructions file's marked region. A skill whose block
 * is gone is removed, so a deleted rule does not survive as a committed file.
 */
@UntrackedTask(because = "Cheap, and its inputs are a whole documents tree: it always runs.")
public abstract class GenerateSkills extends SkillsTask {

    @TaskAction
    public void generate() throws IOException {
        Map<String, String> files = projection();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path file = file(entry.getKey());
            Files.createDirectories(file.getParent());
            if (!Files.exists(file) || !Files.readString(file).equals(entry.getValue())) {
                Files.writeString(file, entry.getValue());
            }
        }
        prune(files.keySet());
        instructions();
        getLogger().lifecycle("Projected {} skill(s) into {}",
                files.keySet().stream().filter(k -> k.startsWith("skills/")).count(),
                getRoot().get().getAsFile().toPath().relativize(getOutput().get().getAsFile().toPath()));
    }

    private void prune(Set<String> keep) throws IOException {
        Path skills = getOutput().get().getAsFile().toPath().resolve("skills");
        if (!Files.isDirectory(skills)) {
            return;
        }
        Set<String> names = keep.stream().filter(k -> k.startsWith("skills/"))
                .map(k -> k.split("/")[1]).collect(Collectors.toSet());
        try (Stream<Path> children = Files.list(skills)) {
            for (Path child : children.toList()) {
                if (Files.isDirectory(child) && !names.contains(child.getFileName().toString())) {
                    try (Stream<Path> walk = Files.walk(child)) {
                        walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                    }
                    getLogger().lifecycle("Removed skill {}: its block is gone", child.getFileName());
                }
            }
        }
    }

    private void instructions() throws IOException {
        if (!getInstructions().isPresent()) {
            return;
        }
        Path root = getRoot().get().getAsFile().toPath();
        Path target = getInstructions().get().getAsFile().toPath();
        var region = SkillProjection.region(root, getDocs().get().getAsFile().toPath());
        if (region.isEmpty() || !Files.exists(target)) {
            return;
        }
        String text = Files.readString(target);
        var updated = SkillProjection.withRegion(text, region.get());
        if (updated.isPresent() && !updated.get().equals(text)) {
            Files.writeString(target, updated.get());
        }
    }
}

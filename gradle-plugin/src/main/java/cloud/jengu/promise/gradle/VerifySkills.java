package cloud.jengu.promise.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.UntrackedTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fails when the committed projection and the pages disagree: a skill that
 * differs, one that is missing, one whose block is gone, or an instructions
 * file whose marked region is not the page's. Each difference is named.
 */
@UntrackedTask(because = "A check of committed files against a whole documents tree: it always runs.")
public abstract class VerifySkills extends SkillsTask {

    @TaskAction
    public void verify() throws IOException {
        Map<String, String> files = projection();
        Path root = getRoot().get().getAsFile().toPath();
        List<String> differences = new ArrayList<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path file = file(entry.getKey());
            String where = root.relativize(file).toString().replace('\\', '/');
            if (!Files.exists(file)) {
                differences.add(where + " is missing");
            } else if (!Files.readString(file).equals(entry.getValue())) {
                differences.add(where + " differs from its source");
            }
        }
        Path skills = getOutput().get().getAsFile().toPath().resolve("skills");
        if (Files.isDirectory(skills)) {
            Set<String> names = files.keySet().stream().filter(k -> k.startsWith("skills/"))
                    .map(k -> k.split("/")[1]).collect(Collectors.toSet());
            try (Stream<Path> children = Files.list(skills)) {
                for (Path child : children.sorted().toList()) {
                    if (Files.isDirectory(child) && !names.contains(child.getFileName().toString())) {
                        differences.add(root.relativize(child).toString().replace('\\', '/')
                                + " has no block any more");
                    }
                }
            }
        }
        if (getInstructions().isPresent()) {
            Path target = getInstructions().get().getAsFile().toPath();
            var region = SkillProjection.region(root, getDocs().get().getAsFile().toPath());
            if (region.isPresent() && Files.exists(target)) {
                String text = Files.readString(target);
                var updated = SkillProjection.withRegion(text, region.get());
                if (updated.isPresent() && !updated.get().equals(text)) {
                    differences.add(root.relativize(target) + "'s marked region differs from its source");
                }
            }
        }
        if (!differences.isEmpty()) {
            throw new GradleException("The skills disagree with their pages — run generateSkills, never edit "
                    + "the projection:\n  " + String.join("\n  ", differences));
        }
    }
}

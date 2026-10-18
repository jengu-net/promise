package cloud.jengu.promise.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

/**
 * Projects a project's skill-blocks into a plugin an agent installs, and holds
 * the build to it.
 *
 * <ul>
 *   <li>{@code generateSkills} writes the projection: one skill per block, the
 *       plugin's manifest — its version following the skills' content — its
 *       marketplace and README, and the instructions file's marked region.</li>
 *   <li>{@code verifySkills} fails while the committed projection and the
 *       pages disagree, and runs with {@code check}.</li>
 * </ul>
 */
public class SkillsPlugin implements Plugin<Project> {

    static final String ID = "cloud.jengu.promise.skills";
    public static final String GENERATE_TASK = "generateSkills";
    public static final String VERIFY_TASK = "verifySkills";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(BasePlugin.class);
        SkillsExtension extension = project.getExtensions().create("skills", SkillsExtension.class);
        extension.getDocs().convention(project.getLayout().getProjectDirectory().dir("docs"));
        extension.getName().convention(project.getName() + "-conventions");
        extension.getOutput().convention(project.getLayout().getProjectDirectory().dir(
                extension.getName().map(name -> "tools/" + name)));
        extension.getMarketplace().convention(extension.getOutput().file(".claude-plugin/marketplace.json"));
        extension.getBaseVersion().convention("1.0");

        project.getTasks().register(GENERATE_TASK, GenerateSkills.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Projects the documents' skill-blocks into an installable plugin");
            wire(project, extension, task);
        });
        var verify = project.getTasks().register(VERIFY_TASK, VerifySkills.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Fails while the projected skills disagree with the documents' skill-blocks");
            wire(project, extension, task);
        });
        project.getTasks().named(LifecycleBasePlugin.CHECK_TASK_NAME, check -> check.dependsOn(verify));
    }

    private static void wire(Project project, SkillsExtension extension, SkillsTask task) {
        task.getRoot().set(project.getLayout().getProjectDirectory());
        task.getDocs().set(extension.getDocs());
        task.getOutput().set(extension.getOutput());
        task.getMarketplace().set(extension.getMarketplace());
        task.getPluginName().set(extension.getName());
        task.getPluginDescription().set(extension.getDescription());
        task.getOwner().set(extension.getOwner());
        task.getBaseVersion().set(extension.getBaseVersion());
        task.getLinkBase().set(extension.getLinkBase());
        task.getInstructions().set(extension.getInstructions());
    }
}

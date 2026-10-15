package cloud.jengu.promise.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.tasks.Jar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Wires the promise framework into a Java module.
 *
 * <ul>
 *   <li>The framework is a dependency of the module, so its catalogues and its
 *       proving annotation compile against it.</li>
 *   <li>The framework is the annotation processor of both compilations, so the
 *       catalogues the module declares and the promises it cites are indexed
 *       where they are compiled.</li>
 *   <li>The test compilation is told it compiles tests, so its citations are
 *       written as tests' — the claim that proves — and never under the name
 *       production code's are written under.</li>
 *   <li>The module's jar carries its tests' citations beside its classes, so a
 *       product composing it reads which tests stand behind each promise from
 *       what it ships rather than from a working tree.</li>
 *   <li>{@code promiseReport} draws the module's ledger: one HTML page and a
 *       markdown summary, from its jar, its runtime classpath and its tests'
 *       results.</li>
 * </ul>
 *
 * <p>The jar waits for the test COMPILATION, never for the tests: the
 * citations are fixed when tests compile, and a jar that waited on a test run
 * would put a cycle under every build whose tests run on the jar.
 *
 * <p>Applied to a root project that is not itself a Java module, it draws one
 * ledger over every subproject that applies it, as a product of several
 * modules is read.
 */
public class PromisePlugin implements Plugin<Project> {

    /** The resource a test compilation writes its citations to, and the jar carries. */
    static final String TESTED_INDEX = "META-INF/promise/proofs-tested";

    /** The task that draws the ledger. */
    public static final String REPORT_TASK = "promiseReport";

    static final String ID = "cloud.jengu.promise";

    @Override
    public void apply(Project project) {
        PromiseExtension extension = project.getExtensions().create("promise", PromiseExtension.class);
        extension.getTitle().convention(project.getName());
        extension.getStrict().convention(false);
        project.getPlugins().withType(JavaPlugin.class, java -> wire(project, extension));
        if (project == project.getRootProject()) {
            // Once every project is configured: the root is evaluated before its
            // subprojects, and which of them apply the plugin is theirs to say.
            project.getGradle().projectsEvaluated(gradle -> {
                if (!project.getPlugins().hasPlugin(JavaPlugin.class)) {
                    composed(project, extension);
                }
            });
        }
    }

    private void wire(Project project, PromiseExtension extension) {
        String framework = framework();
        // What a catalogue implements and a citation is checked against, at
        // compile time and at run time alike.
        project.getDependencies().add(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, framework);
        project.getDependencies().add(JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
                framework);
        project.getDependencies().add(JavaPlugin.TEST_ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
                framework);

        var compileTests = project.getTasks()
                .named(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME, JavaCompile.class);
        compileTests.configure(compile ->
                compile.getOptions().getCompilerArgs().add("-Apromise.index=tested"));

        TaskProvider<Jar> jar = project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class);
        jar.configure(task -> task.from(compileTests.flatMap(JavaCompile::getDestinationDirectory),
                spec -> spec.include(TESTED_INDEX)));

        // What the module ships, read as a product composing it reads it: the
        // jar and what it runs with, never the test directories.
        ConfigurableFileCollection classpath = project.files(jar.flatMap(Jar::getArchiveFile),
                project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        report(project, extension, classpath,
                List.of(project.getLayout().getBuildDirectory().dir("test-results").get().getAsFile()));
    }

    /** One ledger over every subproject applying the plugin. */
    private void composed(Project root, PromiseExtension extension) {
        Configuration ledger = root.getConfigurations().create("promiseLedger", c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setDescription("What the composed ledger reads: every module applying the plugin, "
                    + "as it ships.");
        });
        root.getDependencies().add(ledger.getName(), framework());
        List<File> results = new ArrayList<>();
        for (Project sub : root.getSubprojects()) {
            if (sub.getPlugins().hasPlugin(ID) && sub.getPlugins().hasPlugin(JavaPlugin.class)) {
                root.getDependencies().add(ledger.getName(), root.getDependencies().project(
                        java.util.Map.of("path", sub.getPath())));
                results.add(sub.getLayout().getBuildDirectory().dir("test-results").get().getAsFile());
            }
        }
        report(root, extension, root.files(ledger), results);
    }

    private void report(Project project, PromiseExtension extension,
            org.gradle.api.file.FileCollection classpath, List<File> results) {
        File dir = project.getLayout().getBuildDirectory().dir("reports/promise").get().getAsFile();
        File page = new File(dir, "index.html");
        File summary = new File(dir, "summary.md");
        File badge = new File(dir, "badge.svg");
        project.getTasks().register(REPORT_TASK, JavaExec.class, task -> {
            task.setGroup("verification");
            task.setDescription("Draws the ledger: what this build promises, and what its last "
                    + "test run proved.");
            task.getMainClass().set("cloud.jengu.promise.HtmlReport");
            task.setClasspath(classpath);
            task.getOutputs().files(page, summary, badge);
            // The results are whatever the last run left, which no input
            // declaration can describe: drawn every time it is asked for.
            task.getOutputs().upToDateWhen(t -> false);
            // After the tests when both are asked for, and never a reason to
            // run them: a ledger of a red run is the one most worth reading.
            task.mustRunAfter(project.getTasks().withType(Test.class));
            task.getArgumentProviders().add(() -> {
                List<String> args = new ArrayList<>(List.of("--out", page.getPath(),
                        "--summary", summary.getPath(), "--badge", badge.getPath(),
                        "--title", extension.getTitle().get()));
                for (File root : results) {
                    args.add("--results");
                    args.add(root.getPath());
                }
                if (extension.getStrict().get()) {
                    args.add("--strict");
                }
                // A product's ledger is the product's: the toolset's own
                // promises appear only where the product's groups list them.
                args.add("--without-toolset");
                return args;
            });
            task.doLast(t -> stepSummary(summary));
        });
    }

    /**
     * In a GitHub Actions job, the summary joins the job's own summary page,
     * where a reader of the run sees it without opening anything.
     */
    static void stepSummary(File summary) {
        String target = System.getenv("GITHUB_STEP_SUMMARY");
        if (target == null || target.isBlank() || !summary.isFile()) {
            return;
        }
        try {
            Files.writeString(new File(target).toPath(),
                    Files.readString(summary.toPath(), StandardCharsets.UTF_8) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("could not add the ledger's summary to " + target, e);
        }
    }

    private static String framework() {
        return "cloud.jengu.promise:promise:" + version();
    }

    /** The version this plugin was released as: the framework it wires in is the same one. */
    static String version() {
        Properties properties = new Properties();
        try (InputStream in = PromisePlugin.class.getResourceAsStream("promise-plugin.properties")) {
            if (in == null) {
                throw new IllegalStateException("this plugin was built without its version");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties.getProperty("version");
    }
}

package cloud.jengu.promise.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.model.ObjectFactory;
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
 *   <li>{@code promiseExport} writes the same reading as one JSON document,
 *       for a reader that cannot load the catalogues.</li>
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

    /** The task that writes the graph and its evidence as one JSON document. */
    public static final String EXPORT_TASK = "promiseExport";

    static final String ID = "cloud.jengu.promise";

    @Override
    public void apply(Project project) {
        PromiseExtension extension = project.getExtensions().create("promise", PromiseExtension.class);
        extension.getTitle().convention(project.getName());
        extension.getStrict().convention(false);
        extension.getAreas().convention(List.of());
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
        packResults(project, extension, jar);
    }

    /** The task a release asks for, to carry its run's results in what it ships. */
    public static final String PACK_TASK = "promisePackResults";

    /**
     * Packs the run's results into the jar the product ships, when asked.
     *
     * <p>Never part of the normal build, and never a reason for a jar to wait
     * for its tests: a jar is an input to tests, and one that waited on them
     * would put a cycle under every build whose tests run on jars. So the
     * results are written into the finished jar after the tests, in place, by
     * a task that runs only when a release asks for it — and publishing, when
     * asked for beside it, waits for it.
     */
    private void packResults(Project project, PromiseExtension extension, TaskProvider<Jar> jar) {
        extension.getResultsInto().convention(jar.flatMap(Jar::getArchiveFile));
        extension.getRun().convention(project.provider(() -> runOf(project)));
        File packed = project.getLayout().getBuildDirectory()
                .file("promise/" + RESULTS_NAME).get().getAsFile();
        TaskProvider<JavaExec> pack = project.getTasks().register(PACK_TASK, JavaExec.class, task -> {
            task.setGroup("publishing");
            task.setDescription("Packs the last test run's results into the jar this module "
                    + "ships, so a deployment reads its promises' standing as this build did.");
            task.getMainClass().set("cloud.jengu.promise.PackedResults");
            task.setClasspath(project.getConfigurations()
                    .getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
            task.getOutputs().upToDateWhen(t -> false);
            task.mustRunAfter(project.getTasks().withType(Test.class));
            task.mustRunAfter(jar);
            task.getArgumentProviders().add(() -> {
                List<String> args = new ArrayList<>(List.of("--out", packed.getPath(),
                        "--run", extension.getRun().get()));
                java.util.Set<File> roots = extension.getResults().getFiles();
                if (roots.isEmpty()) {
                    roots = java.util.Set.of(project.getLayout().getBuildDirectory()
                            .dir("test-results").get().getAsFile());
                }
                for (File root : roots) {
                    args.add("--results");
                    args.add(root.getPath());
                }
                return args;
            });
            task.doLast(t -> into(extension.getResultsInto().get().getAsFile(), packed));
        });
        project.getTasks().withType(
                org.gradle.api.publish.maven.tasks.AbstractPublishToMaven.class)
                .configureEach(publish -> publish.mustRunAfter(pack));
    }

    /** The document's name inside a jar, where the framework reads it. */
    static final String RESULTS_NAME = "results.xml";
    static final String RESULTS = "META-INF/promise/" + RESULTS_NAME;

    /** Writes the packed results into the jar, replacing any a previous release left there. */
    static void into(File jar, File packed) {
        if (!jar.isFile()) {
            throw new IllegalStateException("there is no " + jar + " to pack the results into: "
                    + "build it first, or name the jar the product ships with promise.resultsInto");
        }
        try (java.nio.file.FileSystem zip = java.nio.file.FileSystems.newFileSystem(jar.toPath())) {
            java.nio.file.Path target = zip.getPath(RESULTS);
            Files.createDirectories(target.getParent());
            Files.copy(packed.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("could not pack the results into " + jar, e);
        }
    }

    /** The version, with the CI run and commit that built it where there is one. */
    static String runOf(Project project) {
        String run = project.getName() + " " + project.getVersion();
        String id = System.getenv("GITHUB_RUN_ID");
        if (id != null && !id.isBlank()) {
            String sha = System.getenv("GITHUB_SHA");
            return run + " · GitHub run " + id
                    + (sha != null && sha.length() >= 7 ? " (" + sha.substring(0, 7) + ")" : "");
        }
        return run + " · built " + java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    }

    /** One ledger over every subproject applying the plugin. */
    private void composed(Project root, PromiseExtension extension) {
        Configuration ledger = root.getConfigurations().create("promiseLedger", c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setDescription("What the composed ledger reads: every module applying the plugin, "
                    + "as it ships.");
            // Asked for as a Java runtime classpath asks: without it, a
            // library published in several variants — a jre and an android
            // build, say — is one no resolution can choose between.
            ObjectFactory objects = root.getObjects();
            c.attributes(a -> {
                a.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.class, Usage.JAVA_RUNTIME));
                a.attribute(Category.CATEGORY_ATTRIBUTE,
                        objects.named(Category.class, Category.LIBRARY));
                a.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        objects.named(LibraryElements.class, LibraryElements.JAR));
                a.attribute(Bundling.BUNDLING_ATTRIBUTE,
                        objects.named(Bundling.class, Bundling.EXTERNAL));
                a.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                        objects.named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM));
            });
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
        File export = new File(dir, "export.json");
        project.getTasks().register(EXPORT_TASK, JavaExec.class, task -> {
            task.setGroup("documentation");
            task.setDescription("Writes what this build promises, and what its last test run "
                    + "proved, as one JSON document.");
            task.getMainClass().set("cloud.jengu.promise.PromiseExport");
            task.setClasspath(classpath);
            task.getOutputs().file(export);
            task.getOutputs().upToDateWhen(t -> false);
            task.mustRunAfter(project.getTasks().withType(Test.class));
            task.getArgumentProviders().add(() -> {
                List<String> args = new ArrayList<>(List.of("--out", export.getPath()));
                java.util.Collection<File> roots = extension.getResults().isEmpty()
                        ? results : extension.getResults().getFiles();
                for (File root : roots) {
                    args.add("--results");
                    args.add(root.getPath());
                }
                for (String areas : extension.getAreas().get()) {
                    args.add("--areas");
                    args.add(areas);
                }
                if (extension.getStrict().get()) {
                    args.add("--strict");
                }
                return args;
            });
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

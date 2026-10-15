package cloud.jengu.promise.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.tasks.Jar;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
 * </ul>
 *
 * <p>The jar waits for the test COMPILATION, never for the tests: the
 * citations are fixed when tests compile, and a jar that waited on a test run
 * would put a cycle under every build whose tests run on the jar.
 */
public class PromisePlugin implements Plugin<Project> {

    /** The resource a test compilation writes its citations to, and the jar carries. */
    static final String TESTED_INDEX = "META-INF/promise/proofs-tested";

    @Override
    public void apply(Project project) {
        project.getPlugins().withType(JavaPlugin.class, java -> wire(project));
    }

    private void wire(Project project) {
        String framework = "cloud.jengu.promise:promise:" + version();
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

        project.getTasks().named(JavaPlugin.JAR_TASK_NAME, Jar.class).configure(jar ->
                jar.from(compileTests.flatMap(JavaCompile::getDestinationDirectory),
                        spec -> spec.include(TESTED_INDEX)));
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

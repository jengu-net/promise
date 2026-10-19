package cloud.jengu.promise.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/** What a build may say about its ledger. */
public interface PromiseExtension {

    /** What the ledger is of, as its page names it. The project's name unless said. */
    Property<String> getTitle();

    /**
     * Whether evidence that cannot be read fails the report, after it is
     * written. A gate wants it; a page that draws what it can does not.
     */
    Property<Boolean> getStrict();

    /**
     * The jar {@code promisePackResults} packs the run's results into: what the
     * product ships. The module's own jar unless said — an assembly that is
     * what gets deployed names its own.
     */
    RegularFileProperty getResultsInto();

    /** Where the run left its JUnit XML. The module's {@code build/test-results} unless said. */
    ConfigurableFileCollection getResults();

    /**
     * Which run the packed results are of, as a reader of the release sees
     * it. The version, and the CI run and commit where there is one, unless
     * said.
     */
    Property<String> getRun();

    /**
     * The enums of areas {@code promiseExport} writes, by class name: a
     * product's own, when the graph it composes holds others'. Every area of
     * the graph unless said.
     */
    ListProperty<String> getAreas();
}

package cloud.jengu.promise.gradle;

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
}

package cloud.jengu.promise;

import java.util.List;

/** What this toolset does for its adopters, each as the promises that fulfil it. */
@Catalogue(namespace = "FEAT-PROMISE")
public enum ToolsetFeatures implements Feature {

    DECLARING("Declaring promises: once, by name, in catalogues that compose",
            "A promise is an enum constant whose name is its code, so nothing can cite one "
            + "that does not exist. Catalogues register themselves as they compile, and "
            + "several products' catalogues compose into one graph, groups and areas "
            + "included. What nobody can state yet is declared as a gap and counted.",
            List.of(
            ToolsetPromises.NAME_IS_THE_CODE,
            ToolsetPromises.GAP_IS_FIRST_CLASS,
            ToolsetPromises.REGISTERED_AT_COMPILE_TIME,
            ToolsetPromises.CATALOGUE_READ_WHOLE,
            ToolsetPromises.DOWN_LINKS_ONLY,
            ToolsetPromises.AREAS_MERGE_BY_CODE)),

    CITING("Citing promises: from tests that prove them and code that enforces them",
            "A test says which promises it proves with its product's own annotation, and "
            + "production code may say where it enforces one. Both are indexed as they "
            + "compile, under names of their own, and travel in the module's jar. Only a "
            + "test's citation proves.",
            List.of(
            ToolsetPromises.CITATION_IS_TYPED,
            ToolsetPromises.PROOFS_INDEXED_AT_COMPILE_TIME,
            ToolsetPromises.TESTS_CITATIONS_TRAVEL_APART,
            ToolsetPromises.ONLY_A_TEST_PROVES)),

    READING("Reading the graph and the evidence behind it",
            "From a classpath, the composed graph: every promise's status, every group's "
            + "coverage, and with a run's JUnit results each promise's standing. One "
            + "product's unreadable evidence is reported and never takes another's down. "
            + "The same reading draws the markdown report and the HTML ledger.",
            List.of(
            ToolsetPromises.STATUS_IS_DERIVED,
            ToolsetPromises.COVERAGE_IS_A_FOLD,
            ToolsetPromises.STANDING_IS_READ_FROM_THE_RUN,
            ToolsetPromises.ONE_PRODUCTS_UNREADABLE_EVIDENCE_IS_ITS_OWN,
            ToolsetPromises.THE_LEDGER_IS_ONE_PAGE,
            ToolsetPromises.THE_BADGE_SAYS_WHAT_THE_LEDGER_SAYS)),

    BUILDING("Wiring a product's build",
            "One Gradle plugin is all a module applies: it indexes the module's catalogues "
            + "and citations, packs the tests' citations into its jar, and draws the ledger "
            + "of what the module promises and what its last run proved.",
            List.of(
            ToolsetPromises.APPLYING_THE_PLUGIN_IS_ALL_A_MODULE_DOES,
            ToolsetPromises.THE_PLUGIN_DRAWS_THE_LEDGER));

    private final String title;
    private final String description;
    private final List<Promise> promises;

    ToolsetFeatures(String title, String description, List<Promise> promises) {
        this.title = title;
        this.description = description;
        this.promises = promises;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<Promise> promises() {
        return promises;
    }
}

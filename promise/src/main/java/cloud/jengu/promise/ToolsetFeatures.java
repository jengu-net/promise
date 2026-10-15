package cloud.jengu.promise;

import java.util.List;

/** What this toolset does for its adopters, each as the promises that fulfil it. */
@Catalogue(namespace = "FEAT-PROMISE")
public enum ToolsetFeatures implements Feature {

    DECLARING("Declaring promises: once, by name, in catalogues that compose", List.of(
            ToolsetPromises.NAME_IS_THE_CODE,
            ToolsetPromises.GAP_IS_FIRST_CLASS,
            ToolsetPromises.REGISTERED_AT_COMPILE_TIME,
            ToolsetPromises.CATALOGUE_READ_WHOLE,
            ToolsetPromises.DOWN_LINKS_ONLY,
            ToolsetPromises.AREAS_MERGE_BY_CODE)),

    CITING("Citing promises: from tests that prove them and code that enforces them", List.of(
            ToolsetPromises.CITATION_IS_TYPED,
            ToolsetPromises.PROOFS_INDEXED_AT_COMPILE_TIME,
            ToolsetPromises.TESTS_CITATIONS_TRAVEL_APART,
            ToolsetPromises.ONLY_A_TEST_PROVES)),

    READING("Reading the graph and the evidence behind it", List.of(
            ToolsetPromises.STATUS_IS_DERIVED,
            ToolsetPromises.COVERAGE_IS_A_FOLD,
            ToolsetPromises.STANDING_IS_READ_FROM_THE_RUN,
            ToolsetPromises.ONE_PRODUCTS_UNREADABLE_EVIDENCE_IS_ITS_OWN)),

    BUILDING("Wiring a product's build", List.of(
            ToolsetPromises.APPLYING_THE_PLUGIN_IS_ALL_A_MODULE_DOES));

    private final String title;
    private final List<Promise> promises;

    ToolsetFeatures(String title, List<Promise> promises) {
        this.title = title;
        this.promises = promises;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public List<Promise> promises() {
        return promises;
    }
}

package cloud.jengu.promise;

/**
 * What this toolset promises its adopters, kept with the toolset itself and
 * proven by its own tests.
 *
 * <p>Shipped in the framework's jar, so a product composing its catalogue
 * with the framework's reads these beside its own — and reads them proven,
 * from the citations the framework's jar carries.
 */
@Catalogue(namespace = "REQ-PROMISE")
public enum ToolsetPromises implements Promise {

    NAME_IS_THE_CODE(
            "A promise is declared exactly once, as an enum constant; its code derives "
            + "from the constant's name and its catalogue's namespace, so a citation "
            + "cannot drift from a declaration — there is no string to mistype and no "
            + "generator to trust."),

    GAP_IS_FIRST_CLASS(
            "Unstated ground is declared as a gap with plain text; a gap registers, "
            + "carries a stable code, and counts against coverage until promoted to a "
            + "named promise."),

    REGISTERED_AT_COMPILE_TIME(
            "An annotated catalogue is registered during its own component's "
            + "compilation — no classpath is swept, and a registration regenerated on "
            + "every compile cannot drift or be lost."),

    CATALOGUE_READ_WHOLE(
            "The registry reads a catalogue's constants whole — proven, planned and gap "
            + "alike — never as a side effect of what happened to be class-loaded."),

    DOWN_LINKS_ONLY(
            "A classification declares the promises that fulfil it; a promise never "
            + "names its classifications; the inverse is derived. One direction, one "
            + "truth."),

    AREAS_MERGE_BY_CODE(
            "Composition merges same-code areas across catalogues and refuses two with "
            + "conflicting prose rather than picking one."),

    CITATION_IS_TYPED(
            "A test cites promises through its product's own enum-typed annotation, "
            + "recognised by meta-annotation — a mistyped citation is a compile error, "
            + "and the framework never learns a product's types."),

    PROOFS_INDEXED_AT_COMPILE_TIME(
            "Citation sites are indexed during the product's own compilation; a renamed "
            + "or deleted proof site cannot leave a stale citation behind, and an index "
            + "naming a constant the classpath does not carry fails the strict load."),

    TESTS_CITATIONS_TRAVEL_APART(
            "A module's tests' citations are indexed under a name of their own, so its "
            + "jar carries them beside its production code's citations, neither "
            + "overwriting the other, and a classpath of several products' jars answers "
            + "for every one of them."),

    ONLY_A_TEST_PROVES(
            "A promise is proven only by a test citing it; production code citing a "
            + "promise says where it is enforced, and proves nothing."),

    STATUS_IS_DERIVED(
            "A promise's status is computed — cited by a test is proven, named-uncited "
            + "is planned, assurance is declared on the constant, a gap is a gap — never "
            + "asserted at a proof site."),

    COVERAGE_IS_A_FOLD(
            "A classification's coverage is the fold of its declared promises' "
            + "statuses, gaps included; an area's is the fold of its classifications."),

    STANDING_IS_READ_FROM_THE_RUN(
            "Each promise's standing is read from a run's JUnit results: passing only "
            + "when every test class citing it ran with no failure, error or skip; "
            + "failing when one failed or erred; no result when one never ran, held no "
            + "tests or skipped one; uncited when no test cites it."),

    ONE_PRODUCTS_UNREADABLE_EVIDENCE_IS_ITS_OWN(
            "Reading the evidence, an index or a report that cannot be read is reported "
            + "by where it came from, read whole or not at all, and every other "
            + "product's evidence is still answered."),

    THE_LEDGER_IS_ONE_PAGE(
            "The ledger is one self-contained HTML page drawn from the composed model and, "
            + "given a run, its evidence: every kind of group as a tab, each group with its "
            + "description, its promises and how many are kept and passing, and nothing a "
            + "catalogue holds written into the page as markup."),

    THE_BADGE_SAYS_WHAT_THE_LEDGER_SAYS(
            "Beside the page, the ledger draws a badge saying how much is kept and how the "
            + "last run went: green when everything is kept and nothing fails, amber while "
            + "promises wait for a test, red when a test citing one failed."),

    THE_PLUGIN_DRAWS_THE_LEDGER(
            "A module applying the Gradle plugin draws its ledger and a markdown summary "
            + "from its jar, its runtime classpath and its tests' results; in a GitHub "
            + "Actions job the summary is added to the job's own summary.") {
        @Override
        public String assurance() {
            return "Proven by APluginPacksAModulesCitationsTest in the plugin's own module, "
                    + "whose jar carries those citations; a classpath holding the framework "
                    + "without the plugin reads this note instead.";
        }
    },

    APPLYING_THE_PLUGIN_IS_ALL_A_MODULE_DOES(
            "A module applying the Gradle plugin has its catalogues indexed, its "
            + "production code's and its tests' citations indexed apart, and its tests' "
            + "citations packed into its jar — waiting for the tests to compile and "
            + "never for them to run — with no build code of its own; a module that "
            + "does not apply it is unchanged.") {
        @Override
        public String assurance() {
            return "Proven by APluginPacksAModulesCitationsTest in the plugin's own module, "
                    + "whose jar carries those citations; a classpath holding the framework "
                    + "without the plugin reads this note instead.";
        }
    };

    private final String text;

    ToolsetPromises(String text) {
        this.text = text;
    }

    @Override
    public String text() {
        return text;
    }
}

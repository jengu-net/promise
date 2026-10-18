<!-- Generated from the toolset's own catalogue by TheToolsetKeepsItsPromisesTest. Never edit it: change a promise or a citation and run ./gradlew :promise:test -Ppromise.rerecord -->

# Promise report

## (no area)

### FEAT-PROMISE-DECLARING — Declaring promises: once, by name, in catalogues that compose

A promise is an enum constant whose name is its code, so nothing can cite one that does not exist. Catalogues register themselves as they compile, and several products' catalogues compose into one graph, groups and areas included. What nobody can state yet is declared as a gap and counted.

coverage: 7/7 — {PROVEN=7}

| promise | status | proven by |
|---|---|---|
| REQ-PROMISE-NAME-IS-THE-CODE | PROVEN | cloud.jengu.promise.CodeAndGapTest#bodiedConstantReportsTheSameCode<br>cloud.jengu.promise.CodeAndGapTest#nameIsTheCode |
| REQ-PROMISE-GAP-IS-FIRST-CLASS | PROVEN | cloud.jengu.promise.CodeAndGapTest#aGapNeedsItsText<br>cloud.jengu.promise.CodeAndGapTest#gapCodesAreStableAndDistinct<br>cloud.jengu.promise.ModelTest#readWhole<br>cloud.jengu.promise.StatusAndCoverageTest#coverageIsAFold |
| REQ-PROMISE-REGISTERED-AT-COMPILE-TIME | PROVEN | cloud.jengu.promise.CatalogueProcessorTest#nonEnumIsACompileError<br>cloud.jengu.promise.CatalogueProcessorTest#registrationIsWrittenAtCompileTime<br>cloud.jengu.promise.CatalogueProcessorTest#registryLoadsWhatCompilationLeftBehind |
| REQ-PROMISE-CATALOGUE-READ-WHOLE | PROVEN | cloud.jengu.promise.CatalogueProcessorTest#registryLoadsWhatCompilationLeftBehind<br>cloud.jengu.promise.ModelTest#readWhole |
| REQ-PROMISE-DOWN-LINKS-ONLY | PROVEN | cloud.jengu.promise.ModelTest#downLinksOnly |
| REQ-PROMISE-AREAS-MERGE-BY-CODE | PROVEN | cloud.jengu.promise.ModelTest#areasMergeByCode<br>cloud.jengu.promise.ModelTest#conflictingAreasRefused |
| REQ-PROMISE-A-PROMISE-NAMES-ITS-BASIS | PROVEN | cloud.jengu.promise.BasisTest#aCycleIsRefused<br>cloud.jengu.promise.BasisTest#aStaleBasisIsRefused<br>cloud.jengu.promise.BasisTest#theModelAnswersBothDirections |

### FEAT-PROMISE-CITING — Citing promises: from tests that prove them and code that enforces them

A test says which promises it proves with its product's own annotation, and production code may say where it enforces one. Both are indexed as they compile, under names of their own, and travel in the module's jar. Only a test's citation proves.

coverage: 4/4 — {PROVEN=4}

| promise | status | proven by |
|---|---|---|
| REQ-PROMISE-CITATION-IS-TYPED | PROVEN | cloud.jengu.promise.CitationIndexTest#citationsAreIndexedAtCompileTime |
| REQ-PROMISE-PROOFS-INDEXED-AT-COMPILE-TIME | PROVEN | cloud.jengu.promise.CitationIndexTest#citationsAreIndexedAtCompileTime<br>cloud.jengu.promise.CitationIndexTest#proofsLoadResolvesConstants<br>cloud.jengu.promise.TwoIndexesTest#aStaleTestedIndexFailsTheLoad |
| REQ-PROMISE-TESTS-CITATIONS-TRAVEL-APART | PROVEN | cloud.jengu.promise.TwoIndexesTest#anUnknownIndexIsRefused<br>cloud.jengu.promise.TwoIndexesTest#eachCompilationWritesItsOwnIndex<br>cloud.jengu.promise.TwoIndexesTest#twoProductsAreReadFromOneClasspath |
| REQ-PROMISE-ONLY-A-TEST-PROVES | PROVEN | cloud.jengu.promise.TwoIndexesTest#theReadingTellsTheTwoApart |

### FEAT-PROMISE-READING — Reading the graph and the evidence behind it

From a classpath, the composed graph: every promise's status, every group's coverage, and with a run's JUnit results each promise's standing. One product's unreadable evidence is reported and never takes another's down. The same reading draws the markdown report and the HTML ledger.

coverage: 8/8 — {PROVEN=8}

| promise | status | proven by |
|---|---|---|
| REQ-PROMISE-STATUS-IS-DERIVED | PROVEN | cloud.jengu.promise.StatusAndCoverageTest#statusIsDerived<br>cloud.jengu.promise.TwoIndexesTest#theReadingTellsTheTwoApart |
| REQ-PROMISE-COVERAGE-IS-A-FOLD | PROVEN | cloud.jengu.promise.StatusAndCoverageTest#coverageIsAFold |
| REQ-PROMISE-STANDING-IS-READ-FROM-THE-RUN | PROVEN | cloud.jengu.promise.EvidenceTest#failingWins<br>cloud.jengu.promise.EvidenceTest#noResult<br>cloud.jengu.promise.EvidenceTest#passingAndUncited<br>cloud.jengu.promise.EvidenceTest#resultsFromTheClasspath |
| REQ-PROMISE-STANDING-FOLLOWS-THE-BASIS | PROVEN | cloud.jengu.promise.BasisTest#standingFollowsTheBasis |
| REQ-PROMISE-A-RUN-IS-PACKED-FAITHFULLY | PROVEN | cloud.jengu.promise.PackedResultsTest#onlyOutcomesTravel<br>cloud.jengu.promise.PackedResultsTest#packedResultsAnswerAsTheRunWould<br>cloud.jengu.promise.PackedResultsTest#twoReleasesAnswerTogether |
| REQ-PROMISE-ONE-PRODUCTS-UNREADABLE-EVIDENCE-IS-ITS-OWN | PROVEN | cloud.jengu.promise.EvidenceTest#oneProductsUnreadableIndexIsReported |
| REQ-PROMISE-THE-LEDGER-IS-ONE-PAGE | PROVEN | cloud.jengu.promise.BasisTest#theLedgerShowsTheBasis<br>cloud.jengu.promise.HtmlReportTest#aCatalogueCannotWriteMarkupIntoThePage<br>cloud.jengu.promise.HtmlReportTest#theLedgerCarriesTheModelAndTheRun<br>cloud.jengu.promise.HtmlReportTest#theSummaryLeadsWithWhatIsKept<br>cloud.jengu.promise.HtmlReportTest#withoutARunThereIsNoStanding |
| REQ-PROMISE-THE-BADGE-SAYS-WHAT-THE-LEDGER-SAYS | PROVEN | cloud.jengu.promise.HtmlReportTest#theBadgeSaysWhatTheLedgerSays |

### FEAT-PROMISE-BUILDING — Wiring a product's build

One Gradle plugin is all a module applies: it indexes the module's catalogues and citations, packs the tests' citations into its jar, and draws the ledger of what the module promises and what its last run proved.

coverage: 3/3 — {ASSURED=3}

| promise | status | proven by |
|---|---|---|
| REQ-PROMISE-APPLYING-THE-PLUGIN-IS-ALL-A-MODULE-DOES | ASSURED |  |
| REQ-PROMISE-THE-PLUGIN-DRAWS-THE-LEDGER | ASSURED |  |
| REQ-PROMISE-A-RELEASE-PACKS-ITS-RESULTS | ASSURED |  |

### FEAT-PROMISE-PROJECTING-SKILLS — Projecting skills

The rules an agent follows are written once, in the pages that state them, and projected into a plugin it installs; the build refuses a projection that has drifted from its pages, and every change reaches an installed copy.

coverage: 3/3 — {ASSURED=3}

| promise | status | proven by |
|---|---|---|
| REQ-PROMISE-A-SKILL-IS-PROJECTED-FROM-ITS-PAGE | ASSURED |  |
| REQ-PROMISE-A-STALE-PROJECTION-FAILS-THE-CHECK | ASSURED |  |
| REQ-PROMISE-AN-INSTALLED-PLUGIN-SEES-EVERY-CHANGE | ASSURED |  |

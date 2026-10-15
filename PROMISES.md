<!-- Generated from the toolset's own catalogue by TheToolsetKeepsItsPromisesTest. Never edit it: change a promise or a citation and run ./gradlew :promise:test -Ppromise.rerecord -->

# Promise report

## (no area)

### FEAT-PROMISE-DECLARING — Declaring promises: once, by name, in catalogues that compose

coverage: 6/6 — {PROVEN=6}

| promise | status | proven by |
|---|---|---|
| REQ-PROMISE-NAME-IS-THE-CODE | PROVEN | cloud.jengu.promise.CodeAndGapTest#bodiedConstantReportsTheSameCode<br>cloud.jengu.promise.CodeAndGapTest#nameIsTheCode |
| REQ-PROMISE-GAP-IS-FIRST-CLASS | PROVEN | cloud.jengu.promise.CodeAndGapTest#aGapNeedsItsText<br>cloud.jengu.promise.CodeAndGapTest#gapCodesAreStableAndDistinct<br>cloud.jengu.promise.ModelTest#readWhole<br>cloud.jengu.promise.StatusAndCoverageTest#coverageIsAFold |
| REQ-PROMISE-REGISTERED-AT-COMPILE-TIME | PROVEN | cloud.jengu.promise.CatalogueProcessorTest#nonEnumIsACompileError<br>cloud.jengu.promise.CatalogueProcessorTest#registrationIsWrittenAtCompileTime<br>cloud.jengu.promise.CatalogueProcessorTest#registryLoadsWhatCompilationLeftBehind |
| REQ-PROMISE-CATALOGUE-READ-WHOLE | PROVEN | cloud.jengu.promise.CatalogueProcessorTest#registryLoadsWhatCompilationLeftBehind<br>cloud.jengu.promise.ModelTest#readWhole |
| REQ-PROMISE-DOWN-LINKS-ONLY | PROVEN | cloud.jengu.promise.ModelTest#downLinksOnly |
| REQ-PROMISE-AREAS-MERGE-BY-CODE | PROVEN | cloud.jengu.promise.ModelTest#areasMergeByCode<br>cloud.jengu.promise.ModelTest#conflictingAreasRefused |

### FEAT-PROMISE-CITING — Citing promises: from tests that prove them and code that enforces them

coverage: 4/4 — {PROVEN=4}

| promise | status | proven by |
|---|---|---|
| REQ-PROMISE-CITATION-IS-TYPED | PROVEN | cloud.jengu.promise.CitationIndexTest#citationsAreIndexedAtCompileTime |
| REQ-PROMISE-PROOFS-INDEXED-AT-COMPILE-TIME | PROVEN | cloud.jengu.promise.CitationIndexTest#citationsAreIndexedAtCompileTime<br>cloud.jengu.promise.CitationIndexTest#proofsLoadResolvesConstants<br>cloud.jengu.promise.TwoIndexesTest#aStaleTestedIndexFailsTheLoad |
| REQ-PROMISE-TESTS-CITATIONS-TRAVEL-APART | PROVEN | cloud.jengu.promise.TwoIndexesTest#anUnknownIndexIsRefused<br>cloud.jengu.promise.TwoIndexesTest#eachCompilationWritesItsOwnIndex<br>cloud.jengu.promise.TwoIndexesTest#twoProductsAreReadFromOneClasspath |
| REQ-PROMISE-ONLY-A-TEST-PROVES | PROVEN | cloud.jengu.promise.TwoIndexesTest#theReadingTellsTheTwoApart |

### FEAT-PROMISE-READING — Reading the graph and the evidence behind it

coverage: 4/4 — {PROVEN=4}

| promise | status | proven by |
|---|---|---|
| REQ-PROMISE-STATUS-IS-DERIVED | PROVEN | cloud.jengu.promise.StatusAndCoverageTest#statusIsDerived<br>cloud.jengu.promise.TwoIndexesTest#theReadingTellsTheTwoApart |
| REQ-PROMISE-COVERAGE-IS-A-FOLD | PROVEN | cloud.jengu.promise.StatusAndCoverageTest#coverageIsAFold |
| REQ-PROMISE-STANDING-IS-READ-FROM-THE-RUN | PROVEN | cloud.jengu.promise.EvidenceTest#failingWins<br>cloud.jengu.promise.EvidenceTest#noResult<br>cloud.jengu.promise.EvidenceTest#passingAndUncited<br>cloud.jengu.promise.EvidenceTest#resultsFromTheClasspath |
| REQ-PROMISE-ONE-PRODUCTS-UNREADABLE-EVIDENCE-IS-ITS-OWN | PROVEN | cloud.jengu.promise.EvidenceTest#oneProductsUnreadableIndexIsReported |

### FEAT-PROMISE-BUILDING — Wiring a product's build

coverage: 1/1 — {ASSURED=1}

| promise | status | proven by |
|---|---|---|
| REQ-PROMISE-APPLYING-THE-PLUGIN-IS-ALL-A-MODULE-DOES | ASSURED |  |

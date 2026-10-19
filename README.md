# promise

[![Promise ledger](https://jengu-net.github.io/promise/main/badge.svg)](https://jengu-net.github.io/promise/main/)

Requirements as code: promises declared once, kept close to the code that
keeps them, and proven by the tests that check them.

## What a promise is

A promise is a short, well-phrased sentence that describes something you want
to keep.

*Well-phrased* matters, because a promise has to be **unambiguous**: every
reader has to understand it the same way, whether that reader is a developer,
a product owner, an auditor or a newcomer a year from now. A promise that two
people can read differently is two promises, and only one of them is being
kept.

Because promises are short and clear, they also work well as **prompts**. A
list of the promises a change touches tells a language model exactly what it
must not break, in sentences it reads the way a person does.

A promise is written in code:

```java
@Catalogue(namespace = "REQ-SHOP")
public enum ShopPromises implements Promise {

    ORDER_TOTAL_IS_THE_SUM_OF_ITS_LINES(
            "An order's total is the sum of its lines, and nothing else."),

    A_PAID_ORDER_IS_NEVER_CHANGED(
            "An order that has been paid for is never changed; a correction is a new order."),

    A_REFUND_NEVER_EXCEEDS_WHAT_WAS_PAID(
            "A refund never exceeds what was paid for the order it refunds.");

    private final String text;

    ShopPromises(String text) {
        this.text = text;
    }

    @Override
    public String text() {
        return text;
    }
}
```

The constant's name is the promise's code, prefixed by the catalogue's
namespace: `REQ-SHOP-A-PAID-ORDER-IS-NEVER-CHANGED`. There is no second
string to mistype and no generator to trust.

### Why in code

If you really want to keep promises, you keep them close: where you will
read them, every time you change the code that keeps them. A promise in a
wiki is read when somebody remembers the wiki.

And promises in code are **typed**. A test or a refusal cites a promise by
its constant, so citing one that does not exist, or was renamed, is a compile
error. The compiler gives you the first review for free.

### Grouping promises

Promises can be grouped into something bigger: a **feature**, a **user
story**, a **quality** or a **constraint**. A group declares the promises that
fulfil it. A promise never names its groups, so the link has one direction
and nothing to drift.

```java
@Catalogue(namespace = "FEAT-SHOP")
public enum ShopFeatures implements Feature {

    CHECKOUT("Checkout", List.of(
            ShopPromises.ORDER_TOTAL_IS_THE_SUM_OF_ITS_LINES,
            ShopPromises.A_PAID_ORDER_IS_NEVER_CHANGED)),

    REFUNDS("Refunds", List.of(
            ShopPromises.A_REFUND_NEVER_EXCEEDS_WHAT_WAS_PAID));

    private final String title;
    private final List<Promise> promises;

    ShopFeatures(String title, List<Promise> promises) {
        this.title = title;
        this.promises = promises;
    }

    @Override public String title() { return title; }
    @Override public List<Promise> promises() { return promises; }
}
```

`Story`, `Quality` and `Constraint` are declared the same way. An `Area`
groups the groups: a regulation, say, covered paragraph by paragraph as
constraints. The same area may span several products' catalogues.

A group that has a page of its own names it with `document()`, a path from
the repository's root, so a reader holding only the catalogue or its export
can follow it there.

### When you cannot say it yet

If you cannot describe a promise well enough yet, that is not a big issue.
Declare it as **unknown**, in the group where you noticed the hole:

```java
CHECKOUT("Checkout", List.of(
        ShopPromises.ORDER_TOTAL_IS_THE_SUM_OF_ITS_LINES,
        ShopPromises.A_PAID_ORDER_IS_NEVER_CHANGED,
        Promise.gap("what happens when the payment provider times out"))),
```

A gap compiles, has a stable code, and counts against its group until
somebody states it as a promise. Unknown ground is named, never silent.

### A promise based on others

A product that composes another product's catalogue can say what it promises
in its own words, at the level its readers think in, while its ground is kept
by the composed product's promises, which are worded for whoever builds that
product. Such a promise names the promises it is **based on**:

```java
@Catalogue(namespace = "REQ-CLINIC")
public enum ClinicPromises implements Promise {

    A_PATIENT_SEES_ONLY_THEIR_OWN_RECORDS(
            "A patient sees their own records and nobody else's.",
            List.of(StorePromises.A_TENANT_READS_ONLY_ITS_OWN_DATA,
                    StorePromises.A_TOKEN_NAMES_ITS_SUBJECT));

    private final String text;
    private final List<Promise> basis;

    ClinicPromises(String text, List<Promise> basis) {
        this.text = text;
        this.basis = basis;
    }

    @Override public String text() { return text; }
    @Override public List<Promise> basis() { return basis; }
}
```

The basis is any catalogue's constants, the product's own or a composed
product's. The link runs one way, like a group's: a promise names what it
rests on, and the model derives the inverse. Naming a later constant of the
same catalogue from the constructor gives null, which the model refuses; name
it from an overriding `basis()` method instead.

- **Status:** a promise cited by no test of its own, whose basis is all kept,
  is **kept by its basis** (`BASED`). It counts as kept, and the reader can
  tell it apart from one its own tests prove.
- **Standing:** it passes only when its own citing tests, if any, passed and
  every promise it is based on passes. It fails when any of them fails, and
  has no result when any has none.
- **Refused at load:** a basis naming a constant the classpath does not carry,
  or a cycle among bases, is refused by name. `Evidence` reports that
  product's catalogue as unreadable and answers every other product.
- **The ledger** shows each promise's basis under it, with the basis
  promises' own status and standing.

## Keeping promises: proving

Now there is a set of promises, and they need keeping. In code, the easiest
way to keep a promise is to be reminded of it automatically, every time it
might break. That is what a test is, and a test that checks a promise
**proves** it, and says so.

A product has one proving annotation of its own, typed to its own catalogue:

```java
@Cites
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR})
public @interface Proving {
    ShopPromises[] value();
}
```

and a test declares what it proves:

```java
@Test
@Proving(ShopPromises.A_PAID_ORDER_IS_NEVER_CHANGED)
void aPaidOrderRefusesAChange() {
    Order order = shop.pay(shop.order(line("book", 12)));
    assertThrows(OrderPaidException.class, () -> shop.addLine(order, line("pen", 2)));
}
```

`promise-proving` adds assertions that name the promise they check, so a
failure says which promise broke rather than "expected true but was false":

```java
Proves.that(ShopPromises.A_REFUND_NEVER_EXCEEDS_WHAT_WAS_PAID,
        refund.amount() <= order.paid(), "refunded " + refund.amount());
```

Production code may cite a promise too, with the same annotation on the type,
constructor or method that **enforces** it. That says where a promise is kept.
It never proves it; only a test does.

When every promise of a feature or a user story is proven, the feature or
story is proven. One that still holds a gap is not, and says so.

## How it works

Three artifacts, group `cloud.jengu.promise`:

| Artifact | What it is |
|---|---|
| `promise` | The model, an annotation processor that indexes catalogues and citations at compile time, and the reading of the graph and the evidence. No runtime dependencies. Also an OSGi bundle. |
| `promise-proving` | JUnit assertions that name the promise they prove. Test scope only. |
| `promise-gradle-plugin` | The Gradle plugin `cloud.jengu.promise`, which wires the rest into a module's build. |

### The build

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://repo.jengu.cloud/repository/maven-releases/")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://repo.jengu.cloud/repository/maven-releases/")
        mavenCentral()
    }
}
```

```kotlin
// a module's build.gradle.kts
plugins {
    java
    id("cloud.jengu.promise") version "<version>"
}

dependencies {
    // only for the assertions that name what they prove
    testImplementation("cloud.jengu.promise:promise-proving:<version>")
}
```

Applying the plugin is the whole of it. For the module, it:

- depends on the framework of the plugin's own version;
- runs the framework's annotation processor in both compilations. The main
  compilation writes the module's catalogues to `META-INF/promise/catalogues`
  and production code's citations to `META-INF/promise/proofs`. Nothing is
  scanned at run time: the compiler already knows what is annotated;
- tells the test compilation that it compiles tests, so its citations go to
  `META-INF/promise/proofs-tested`, a name of their own;
- packs the tests' citations into the module's jar, waiting for the tests to
  compile and never for them to run.

So a module's jar says which tests stand behind each of its promises, and a
product composing several modules, or several products, reads all of it from
what they ship. A module that does not apply the plugin is unchanged.

By hand, the same is `annotationProcessor` and `testAnnotationProcessor` on
`cloud.jengu.promise:promise`, plus `-Apromise.index=tested` on the test
compilation. Without that option, a test's citations are read as production
code's, and prove nothing.

### The model

Everything on a classpath composes into one graph:

```java
ClassLoader loader = ...; // the product's jars, or a test's own class loader
Registry.Model model = Registry.load(loader).model(Proofs.load(loader));

model.promises();                        // every promise of every catalogue, gaps included
model.statusOf(promise);                 // PROVEN, ASSURED, BASED, PLANNED or GAP
model.citing(promise);                   // the tests that prove it
model.enforcing(promise);                // the production code that keeps it
model.declaring(promise);                // the features and stories it belongs to
model.basis(promise);                    // the promises it is based on
model.basedOn(promise);                  // the promises based on it, derived
model.coverage(ShopFeatures.CHECKOUT);   // {PROVEN=2, GAP=1}
Report.render(model);                    // a markdown table: promise, status, proven by
```

A status is derived, never asserted. A promise a test cites is *proven*; one
nothing cites is *planned*; one carrying a recorded reason that is not a test
is *assured*; one cited by no test of its own but based on kept promises is
*kept by its basis*; an unknown is a *gap*. `Registry.load` and `Proofs.load` are
strict: an index naming a catalogue or a constant the classpath does not
carry fails the load, because a stale index is a build defect.

### The evidence: adding the test results

Citations say which tests claim to prove a promise; whether they passed is in
the JUnit XML reports a test run leaves. Gradle writes them to
`build/test-results/<task>/TEST-*.xml`, as do Maven Surefire and most CI
tools. `Evidence` reads both halves together:

```java
Evidence evidence = Evidence.read(loader, List.of(Path.of("build/test-results")));

evidence.standing(promise);     // PASSING, FAILING, NO_RESULT or UNCITED
evidence.citations(promise);    // each citation, TESTED or ENFORCED
evidence.results();             // what each test class came to
evidence.unreadable();          // what could not be read, by name
```

A promise is:

- **passing** when every test class citing it ran with no failure, error or skip;
- **failing** when one of them failed or erred;
- **no result** when one of them never ran, held no tests, or skipped one;
- **uncited** when no test cites it.

A feature or story is passing when every promise it declares is:

```java
boolean checkoutHolds = ShopFeatures.CHECKOUT.promises().stream()
        .allMatch(promise -> evidence.standing(promise) == Evidence.Standing.PASSING);
```

`Evidence.read` takes as many roots as a build has, such as a working tree,
or wherever CI collected its reports. Reports can also travel with the code:
every `META-INF/promise/results.xml` on the classpath is read as a JUnit
report.

Unlike the strict loads, `Evidence` reads every index and every report on its
own. One that cannot be read is reported in `unreadable()`, by where it came
from, and every other product's evidence is still answered. So a page draws
what it can, and a gate refuses while `unreadable()` is not empty. Both get
the same answer from the same run.

### The ledger: one page, and the job that shows it

```sh
./gradlew build promiseReport
```

`promiseReport` draws the **ledger**: one self-contained HTML page of what the
module promises and what its last test run proved. It has a tab for each kind
of group (stories, features, qualities, constraints and areas), and each group
shows its description, its promises and a progress bar of how many are kept
and passing. It writes `build/reports/promise/index.html`, a short
`summary.md`, and `badge.svg`: how much is kept and how the last run went, in
green when everything is kept and nothing fails, amber while promises wait for
a test, and red when a test citing one failed.

- It reads what the module ships, its jar and runtime classpath, against
  `build/test-results`. It runs after the tests when both are asked for, and
  never runs them itself, so it can still draw the ledger of a failed run.
- Applied to a root project that is not a Java module, the plugin draws one
  ledger over every subproject that applies it.
- The framework's own promises are left out of a product's ledger, except
  where one of the product's groups lists them.
- `promise { title.set("…"); strict.set(true) }` names the page, and makes
  unreadable evidence fail the task.

In a GitHub Actions job, the summary is added to the job's own summary page by
itself, and the page can be kept with the run or published:

```yaml
      - run: ./gradlew build
      - name: Draw the ledger
        if: always()
        run: ./gradlew promiseReport
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: promise-ledger
          path: build/reports/promise/
```

To open the ledger as a page from the build result, add the reusable
workflow beside the build job:

```yaml
  ledger-page:
    needs: build
    if: always()
    permissions:
      contents: write
      statuses: write
    uses: jengu-net/promise/.github/workflows/ledger-page.yml@<version>
```

It publishes each run's ledger to the repository's `gh-pages` branch, served
by GitHub Pages: `/runs/<run id>/` for every run and `/main/` for the default
branch, with the newest 40 runs kept. It links the page from the build result
in two places:

- a **promise ledger** commit status beside the other checks, reading for
  example "100% kept · 17 passing, 0 failing", whose *Details* open the page;
- an *Open the promise ledger* link on the run's summary.

The badge of the default branch's latest build is then at
`https://<owner>.github.io/<repository>/main/badge.svg`. A README shows it,
linked to the page, as this one does at its top:

```markdown
[![Promise ledger](https://<owner>.github.io/<repository>/main/badge.svg)](https://<owner>.github.io/<repository>/main/)
```

It runs none of the build's code, and it is the only job given write
permission. A pull request from a fork cannot write, so its ledger stays an
artifact. Pages serves the `gh-pages` branch once the branch exists; if it
does not, choose that branch under the repository's Pages settings. This
repository's own build calls it the same way.

The same page is available in code: `HtmlReport.render(evidence, title)`, or
`HtmlReport.render(model, title)` without a run. A group or an area can carry a
`description()` beside its title, and both reports show it.

### Results that travel with a release

A promise's standing has two halves: which tests cite it, which every jar
carries, and what those tests did, which otherwise lives only in the build
that ran them. A deployed product would read every promise as *no result*,
however green its release was. A release can carry its run with it:

```sh
./gradlew build promisePackResults publish
```

`promisePackResults` writes the run's results into the jar the module
ships, as one JUnit document, `META-INF/promise/results.xml`. `Evidence`
reads every such document on a classpath, so a deployment, and every product
composing it, reads each promise's standing as the release build did.

- **Faithful:** it keeps every test class's outcome as the run left it,
  failures and skips included, and a class that held no tests. It keeps
  nothing else: no failure messages, no traces, nothing a test printed, since
  those can carry what a release must not. A class the run did not report is
  absent.
- **Named:** the document names its run, by default the module's version and
  the GitHub run and commit that built it. `Evidence.runs()` answers it, and
  the ledger shows it.
- **Asked for, never part of the normal build:** the task runs after the
  tests and writes into the finished jar. No jar ever waits for its tests,
  and publishing, when asked for beside it, waits for the results.
- **Configurable:** `promise { resultsInto.set(...) }` names another jar,
  such as the assembly a product deploys. `results.from(...)` names where the
  run's reports are, and `run.set(...)` names the run.

### The export: one JSON document

```sh
./gradlew build promiseExport
```

`promiseExport` writes the same reading as the ledger to
`build/reports/promise/export.json`, for a reader that cannot load the
catalogues: a script, a check in CI, a server answering an agent's questions
about the project. It holds the areas, each group with its kind, title,
description, page and area, and every promise those groups list or rest on,
with its text, status, standing, citing tests, its basis and what rests on
it. A gap appears as a promise of status `GAP`.

By default it holds every area of the graph. A product composing others'
catalogues names its own with `promise { areas.add("shop.ShopAreas") }`, and
their promises' bases are still exported, wherever they are declared.
`results.from(...)` names where the run's reports are. In code it is
`PromiseExport.json(evidence)`, or `PromiseExport.json(evidence, areas)`.

### Skills: the rules an agent follows

A rule an agent is to follow while it works on a project — how a refund is
decided, how a test is named — is written once, as a *skill-block* at the end
of the page that states it:

````markdown
<!-- skill: shop-refunds -->
```yaml
name: shop-refunds
applies-when: >-
  Changing how a refund is computed or approved.
reference: docs/refunds.md
```
**Rules**
- MUST refund no more than was paid.
<!-- /skill -->
````

The second plugin, `cloud.jengu.promise.skills`, projects every block under
`docs/` into a plugin an agent installs: one skill per block, its manifest, its
marketplace and a README naming each skill's page. `applies-when` becomes the
skill's description, which decides whether it is read at all; a body under
`**Reference**` instead of `**Rules**` maps what is offered rather than what is
forbidden. Links in a block are written from its page and rewritten to reach
the same files from the skill.

```kotlin
plugins { id("cloud.jengu.promise.skills") version "<version>" }

skills {
    description.set("How the shop is built.")
    owner.set("the shop")
    // At the repository's root, so the repository installs by its address.
    marketplace.set(layout.projectDirectory.file(".claude-plugin/marketplace.json"))
}
```

`generateSkills` writes the projection into `tools/<name>`, and removes a skill
whose block is gone. `verifySkills` runs with `check` and fails while the
committed projection and the pages disagree, naming each difference. The
plugin's version is `baseVersion` followed by a digest of its skills, so an
installed copy takes every change. A page may also mark a prose region,
`<!-- claude:begin -->` … `<!-- claude:end -->`, which is copied into the
instructions file `instructions` names, between its `<!-- rules:begin -->` and
`<!-- rules:end -->` markers.

## The toolset's own promises

The toolset keeps its promises the way it asks a product to keep its own.
They are declared in its catalogue, `ToolsetPromises` (`REQ-PROMISE-…`),
grouped by `ToolsetFeatures`, and proven by its own tests, whose citations
travel in its jar. A product composing the framework reads them beside its
own, proven. [PROMISES.md](PROMISES.md) is their page, generated from the
catalogue and the citations, and a test refuses a page that has gone stale.

## Building

```sh
./gradlew build
```

Java 25 for the framework; the plugin is compiled for Java 17, the oldest JVM
Gradle runs on. Snapshots publish from `main`, releases from `v*` tags.

## Licence

MIT, see [LICENSE](LICENSE).

# promise

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
model.statusOf(promise);                 // PROVEN, PLANNED, ASSURED or GAP
model.citing(promise);                   // the tests that prove it
model.enforcing(promise);                // the production code that keeps it
model.declaring(promise);                // the features and stories it belongs to
model.coverage(ShopFeatures.CHECKOUT);   // {PROVEN=2, GAP=1}
Report.render(model);                    // a markdown table: promise, status, proven by
```

A status is derived, never asserted. A promise a test cites is *proven*; one
nothing cites is *planned*; one carrying a recorded reason that is not a test
is *assured*; an unknown is a *gap*. `Registry.load` and `Proofs.load` are
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
and passing. It writes `build/reports/promise/index.html` and a short
`summary.md`.

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

To read the ledger online, hand `build/reports/promise/` to
`actions/upload-pages-artifact` and `actions/deploy-pages`. This repository
does that for its own ledger from `main`.

The same page is available in code: `HtmlReport.render(evidence, title)`, or
`HtmlReport.render(model, title)` without a run. A group or an area can carry a
`description()` beside its title, and both reports show it.

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

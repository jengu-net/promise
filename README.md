# promise

Requirements as code. A product declares what it promises once, as enum
constants in a catalogue; tests cite the promises they prove, and production
code may cite the promises it enforces; the catalogue, the citations and the
evidence behind them are read back from the classpath, and catalogues compose
across products.

Three artifacts, group `cloud.jengu.promise`:

| Artifact | What it is |
|---|---|
| `promise` | The model, an annotation processor that indexes catalogues and citations at compile time, and the reading of the graph and the evidence. No runtime dependencies. Also an OSGi bundle. |
| `promise-proving` | JUnit assertions that name the promise they prove. Test scope only. |
| `promise-gradle-plugin` | The Gradle plugin `cloud.jengu.promise`, which wires the rest into a module's build. |

## Using it

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
```

Applying the plugin is the whole of it. For the module it:

- depends on the framework of the plugin's own version;
- puts the framework's processor on both compilations, so the catalogues the
  module declares and the promises it cites are indexed where they compile;
- tells the test compilation it compiles tests, so their citations are
  written as tests' (`META-INF/promise/proofs-tested`) and never under the
  name production code's citations are written under
  (`META-INF/promise/proofs`);
- packs the tests' citations into the module's jar, waiting for the tests to
  compile and never for them to run.

A module that does not apply it is unchanged. By hand, the same is
`annotationProcessor` and `testAnnotationProcessor` on the framework, and
`-Apromise.index=tested` on the test compilation; without the option a test's
citations are read as production code's, and prove nothing.

A catalogue is an enum annotated `@Catalogue(namespace = "...")` implementing
`Promise` (or `Story`, `Feature`, `Quality`, `Constraint`, `Area`). A product's
proving annotation is meta-annotated `@Cites`; a test cites with it, and
production code may cite with it on a type, a constructor or a method.

## Reading the evidence

```java
Evidence evidence = Evidence.read(loader, List.of(Path.of("build/test-results")));
evidence.standing(promise);   // PASSING, FAILING, NO_RESULT or UNCITED
evidence.citations(promise);  // each saying TESTED or ENFORCED
evidence.unreadable();        // empty is the only answer a gate passes on
```

A promise is passing when every test class citing it ran with no failure,
error or skip; failing when one failed; without a result when one never ran,
held no tests or skipped one; uncited when no test cites it. Results are the
JUnit XML a run left under the roots named, and any
`META-INF/promise/results.xml` the classpath carries. Every index and results
file is read on its own: one that cannot be read is reported by name, and
every other product's evidence is still answered. `Registry.load` and
`Proofs.load` stay strict, for a build that wants a stale index to fail it.

## Building

```sh
./gradlew build
```

Java 25 for the framework; the plugin is compiled for Java 17, the oldest JVM
Gradle runs on. Snapshots publish from `main`, releases from `v*` tags.

## Licence

MIT, see [LICENSE](LICENSE).

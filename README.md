# promise

Requirements as code. A product declares what it promises once, as enum
constants in a catalogue; tests cite the promises they prove; the catalogue,
the citations and the derived status are read back from the classpath, and
catalogues compose across products.

Two artifacts, group `cloud.jengu.promise`:

| Artifact | What it is |
|---|---|
| `promise` | The model, an annotation processor that indexes catalogues and citations at compile time, and the registry that reads them. No runtime dependencies. Also an OSGi bundle. |
| `promise-proving` | JUnit assertions that name the promise they prove. Test scope only. |

## Using it

```kotlin
repositories {
    maven("https://repo.jengu.cloud/repository/maven-releases/")
}

dependencies {
    implementation("cloud.jengu.promise:promise:0.1.0")
    // indexes the catalogues this module declares
    annotationProcessor("cloud.jengu.promise:promise:0.1.0")
    // indexes the promises this module's tests cite
    testAnnotationProcessor("cloud.jengu.promise:promise:0.1.0")
    testImplementation("cloud.jengu.promise:promise-proving:0.1.0")
}
```

Without the processor on a compilation, its index is absent and nothing says
so: a catalogue reads as undeclared and a citation as missing.

A catalogue is an enum annotated `@Catalogue(namespace = "...")` implementing
`Promise` (or `Story`, `Feature`, `Quality`, `Constraint`, `Area`). A product's
proving annotation is meta-annotated `@Cites`; a test cites with it.
`Registry.load(loader).model(Proofs.load(loader))` reads everything the loader
can see.

## Building

```sh
./gradlew build
```

Java 25. Snapshots publish from `main`, releases from `v*` tags.

## Licence

MIT, see [LICENSE](LICENSE).

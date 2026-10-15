// The framework: the model, a small annotation processor and a registry.
// ZERO runtime dependencies on purpose — any dependency here becomes every
// adopter's dependency.

plugins {
    id("biz.aQute.bnd.builder")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // The toolset's own tests cite its own promises, indexed by its own
    // processor — from the compiled classes rather than the jar, because the
    // jar carries what this compilation indexes, and waiting on the jar here
    // would be a cycle.
    testAnnotationProcessor(files(sourceSets.main.get().output))
}

// What the plugin does for a product, done by hand for the module that the
// plugin is built from: the tests' citations are tests', and travel in the jar.
tasks.compileTestJava {
    options.compilerArgs.add("-Apromise.index=tested")
}
tasks.jar {
    from(tasks.compileTestJava.flatMap { it.destinationDirectory }) {
        include("META-INF/promise/proofs-tested")
    }
}

tasks.test {
    useJUnitPlatform()
    // The page of the toolset's own promises lives at the repository's root,
    // and is written rather than compared when asked: -Ppromise.rerecord.
    systemProperty("promise.page", rootProject.file("PROMISES.md").absolutePath)
    systemProperty("promise.rerecord", (findProperty("promise.rerecord") != null).toString())
    inputs.files(rootProject.file("PROMISES.md"))
}

// A bundle as well as a jar: an OSGi runtime composing catalogues installs it
// beside the bundles that cite promises.
tasks.jar {
    bundle {
        bnd(mapOf(
            "Bundle-SymbolicName" to "cloud.jengu.promise",
            "Export-Package" to "cloud.jengu.promise;version=0.1.0",
        ))
    }
}

publishing.publications.named<MavenPublication>("maven") {
    artifactId = "promise"
}

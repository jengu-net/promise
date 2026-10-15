// The framework: the model, a small annotation processor and a registry.
// ZERO runtime dependencies on purpose — any dependency here becomes every
// adopter's dependency.

plugins {
    id("biz.aQute.bnd.builder")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
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

// The framework's own tooling for a product's build: applying it is the only
// thing a module does to have its catalogues indexed and its tests' citations
// travel in its jar.

plugins {
    id("java-gradle-plugin")
}

// It runs inside the consumer's Gradle, so it is compiled for the oldest JVM
// that Gradle runs on rather than the newest this repository builds with. Its
// tests are not: they read jars with the framework, which is built for 25.
tasks.named<JavaCompile>("compileJava") {
    options.release.set(17)
}
java {
    disableAutoTargetJvm()
}

gradlePlugin {
    website.set("https://github.com/jengu-net/promise")
    vcsUrl.set("https://github.com/jengu-net/promise")
    plugins {
        register("promise") {
            id = "cloud.jengu.promise"
            implementationClass = "cloud.jengu.promise.gradle.PromisePlugin"
            displayName = "Promise"
            description = "Indexes a module's promise catalogues and citations, and packs its " +
                "tests' citations into its jar."
        }
    }
}

// Which framework the plugin wires in: the one released beside it, so a
// product names one version, the plugin's, and gets both.
val versionResource = tasks.register("versionResource") {
    val out = layout.buildDirectory.dir("generated/version")
    val version = project.version.toString()
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val file = out.get().file("cloud/jengu/promise/gradle/promise-plugin.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$version\n")
    }
}
sourceSets.main {
    resources.srcDir(versionResource)
}

dependencies {
    // To read back what a fixture's jars carry, as a product composing them would.
    testImplementation(project(":promise"))
    // The plugin's tests cite the toolset's promise about it, indexed as tests'
    // and carried in this jar, as the plugin itself would arrange.
    testAnnotationProcessor(project(":promise"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

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
    // The fixtures resolve the framework this build made, from a repository
    // laid out as the project's is.
    dependsOn(":promise:publishMavenPublicationToFunctionalRepository")
    systemProperty("promise.repo",
        rootProject.layout.buildDirectory.dir("functional-repo").get().asFile.absolutePath)
    systemProperty("promise.version", project.version.toString())
}

publishing.publications.withType<MavenPublication>().configureEach {
    if (name == "pluginMaven") {
        artifactId = "promise-gradle-plugin"
    }
}

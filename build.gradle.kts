// Requirements as code: promises declared once, cited everywhere, composed
// across products. Three artifacts — the framework, its JUnit assertions and
// its Gradle plugin — published together under one version.

val projectUrl = "https://github.com/jengu-net/promise"

val blurbs = mapOf(
    "promise" to "Requirements as code: promises declared once, cited everywhere, composed across products.",
    "promise-proving" to "JUnit assertions that name the promise they prove.",
    "promise-gradle-plugin" to "Wires the promise framework into a module's build, and packs its tests' citations into its jar.",
)

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")

    the<JavaPluginExtension>().toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    group = "cloud.jengu.promise"
    // Snapshots on main; a release build passes -Ppromise.version=X.Y.Z, which
    // the workflow derives from the v-tag.
    version = (findProperty("promise.version") as String?) ?: "0.1.0-SNAPSHOT"

    // Central requires both beside every artifact, and the API is small enough
    // that reading it is how it is learned.
    the<JavaPluginExtension>().withSourcesJar()
    the<JavaPluginExtension>().withJavadocJar()

    configure<PublishingExtension> {
        repositories {
            // The project's own public repository, as the store's artifacts
            // are published. Maven Central is the destination once its
            // namespace and signing are set up here; the coordinates and the
            // signing below are already what it needs.
            maven {
                name = "JenguRepo"
                val snapshot = version.toString().endsWith("SNAPSHOT")
                val repoHost = System.getenv("REPO_HOST")?.takeIf { it.isNotBlank() }
                    ?: "repo.jengu.cloud"
                url = uri(
                    if (snapshot) "https://$repoHost/repository/maven-snapshots/"
                    else "https://$repoHost/repository/maven-releases/"
                )
                credentials {
                    username = System.getenv("NEXUS_USERNAME")
                        ?: findProperty("jengu.repo.user") as String? ?: ""
                    password = System.getenv("NEXUS_PASSWORD")
                        ?: findProperty("jengu.repo.key") as String? ?: ""
                }
            }
        }
        repositories {
            // Inside this build only: where the plugin's functional tests
            // resolve the framework from, as a product's build resolves it
            // from the project's repository.
            maven {
                name = "Functional"
                url = uri(rootProject.layout.buildDirectory.dir("functional-repo"))
            }
        }
        publications {
            // A plugin module gets its publications from java-gradle-plugin —
            // the artifact and the marker a plugin id resolves through — and
            // a second one from the same component would publish it twice.
            if (project.name != "gradle-plugin") {
                register<MavenPublication>("maven") {
                    from(components["java"])
                }
            }
            withType<MavenPublication>().configureEach {
                pom {
                    name.set(artifactId)
                    description.set(provider { blurbs[artifactId] ?: blurbs.getValue("promise") })
                    url.set(projectUrl)
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("$projectUrl/blob/main/LICENSE")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("jengu")
                            name.set("Jengu Net")
                            url.set(projectUrl)
                        }
                    }
                    scm {
                        url.set(projectUrl)
                        connection.set("scm:git:${projectUrl}.git")
                        developerConnection.set("scm:git:${projectUrl}.git")
                    }
                }
            }
        }
    }

    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    if (!signingKey.isNullOrBlank()) {
        configure<SigningExtension> {
            useInMemoryPgpKeys(signingKey, signingPassword)
            sign(the<PublishingExtension>().publications)
        }
    }
}

// ── the toolset's own ledger ─────────────────────────────────────────────
// What the plugin draws for a product, drawn here by hand: this is the one
// build that cannot apply the plugin, because it is the build that makes it.
// It reads what the two modules ship — the framework, and the plugin's jar
// with its tests' citations — against both modules' test results, with the
// toolset's own catalogue in, since here the toolset is the subject.
evaluationDependsOnChildren()

tasks.register<JavaExec>("promiseReport") {
    group = "verification"
    description = "Draws the toolset's ledger: what it promises, and what its last test run proved."
    val reports = layout.buildDirectory.dir("reports/promise").get().asFile
    val page = reports.resolve("index.html")
    val summary = reports.resolve("summary.md")
    classpath = files(project(":promise").tasks.named("jar"),
        project(":gradle-plugin").tasks.named("jar"))
    mainClass.set("cloud.jengu.promise.HtmlReport")
    args("--out", page.path, "--summary", summary.path, "--title", "promise", "--strict",
        "--results", project(":promise").layout.buildDirectory.dir("test-results").get().asFile.path,
        "--results", project(":gradle-plugin").layout.buildDirectory.dir("test-results").get().asFile.path)
    outputs.files(page, summary)
    // Whatever the last run left, which no input declaration describes.
    outputs.upToDateWhen { false }
    subprojects.forEach { sub -> mustRunAfter(sub.tasks.withType<Test>()) }
    doLast {
        // In a GitHub Actions job, the summary joins the job's own page.
        System.getenv("GITHUB_STEP_SUMMARY")?.takeIf { it.isNotBlank() }?.let { target ->
            java.io.File(target).appendText(summary.readText() + "\n")
        }
    }
}

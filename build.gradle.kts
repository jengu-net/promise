// Requirements as code: promises declared once, cited everywhere, composed
// across products. Two artifacts, published together under one version.

val projectUrl = "https://github.com/jengu-net/promise"

val blurbs = mapOf(
    "promise" to "Requirements as code: promises declared once, cited everywhere, composed across products.",
    "promise-proving" to "JUnit assertions that name the promise they prove.",
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
        publications {
            register<MavenPublication>("maven") {
                from(components["java"])
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
            sign(the<PublishingExtension>().publications["maven"])
        }
    }
}

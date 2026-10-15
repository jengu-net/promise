pluginManagement {
    plugins {
        id("biz.aQute.bnd.builder") version "7.4.0"
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "promise-build"

// The framework, the assertions beside it that need JUnit, and the Gradle
// plugin that wires the framework into a product's build.
include("promise", "proving", "gradle-plugin")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

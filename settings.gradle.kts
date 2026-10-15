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

// The framework, and the assertions beside it that need JUnit.
include("promise", "proving")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

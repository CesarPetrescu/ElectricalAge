pluginManagement {
    repositories {
        maven {
            name = "GTNH Maven"
            url = uri("https://nexus.gtnewhorizons.com/repository/public/")
            mavenContent { includeGroupByRegex("com\\.gtnewhorizons.*") }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // Lets Gradle fetch the JDK 8 / JDK 17 toolchains the build needs.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ElectricalAge"

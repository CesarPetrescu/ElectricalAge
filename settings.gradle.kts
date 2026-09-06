pluginManagement {
    val kotlinVersion: String by settings
    plugins {
        // Must match the Kotlin runtime Kotlin for Forge ships (gradle.properties: kffVersion).
        id("org.jetbrains.kotlin.jvm") version kotlinVersion
        id("net.neoforged.moddev") version "2.0.146"
    }
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
        }
    }
}

plugins {
    // Provisions the JDK 21 toolchain when the host has none.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ElectricalAge"

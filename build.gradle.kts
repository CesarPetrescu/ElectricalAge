import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("com.gtnewhorizons.retrofuturagradle") version "2.0.3"
    id("com.gradleup.shadow") version "9.6.1"
}

group = properties["modGroup"] as String
version = "2.0.0-port"

// ---------------------------------------------------------------- toolchains
// The mod targets Java 8 bytecode (Minecraft 1.12.2), but compiles on a JDK 17
// toolchain: Kotlin 2.2's compiler no longer runs on a JDK 8 launcher, and
// javac's --release 8 gives the same API boundary a JDK 8 toolchain would.
// RFG keeps its own decompile/recompile tasks on JDK 8 via jvmLanguageVersion.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
        vendor.set(JvmVendorSpec.AZUL)
    }
}

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

// Only the mod's own sources: RFG compiles Minecraft on a real JDK 8 toolchain,
// whose javac predates --release.
listOf("compileJava", "compileTestJava").forEach { n ->
    tasks.matching { it.name == n }.configureEach {
        (this as JavaCompile).options.release.set(8)
        // Report every error, not javac's default first 100: the port fixes them by histogram.
        options.compilerArgs.add("-Xmaxerrs")
        options.compilerArgs.add("10000")
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
        freeCompilerArgs.add("-Xjvm-default=all-compatibility")
    }
}

// The Kotlin plugin attaches a compile task to every source set, including the
// ones RFG generates (mcLauncher, patchedMc, injectedTags, ...). Those hold no
// Kotlin, and Gradle 9 fails the build when they read generated sources without a
// declared producer dependency. Only main and test carry Kotlin here.
tasks.withType<KotlinJvmCompile>().configureEach {
    if (name !in setOf("compileKotlin", "compileTestKotlin")) {
        enabled = false
    }
}

// -------------------------------------------------------------- minecraft/RFG
minecraft {
    mcVersion.set("1.12.2")             // RFG pairs this with Forge 14.23.5.2847, MCP stable_39
    username.set("Developer")
    injectedTags.put("VERSION", project.version)
    extraRunJvmArguments.add("-ea:${project.group}")
}

tasks.injectTags.configure {
    outputClassName.set("mods.eln.Tags")
}

// ------------------------------------------------------------------ resources
tasks.processResources.configure {
    val projVersion = project.version.toString()
    inputs.property("version", projVersion)
    filesMatching("mcmod.info") { expand(mapOf("modVersion" to projVersion)) }
    exclude(
        "**/_Common", "**/_TEMPLATES", "**/export_*.png", "**/*.blend*",
        "**/temp/**/*", "**/model-to-be-integrated/**/*", "**/unused-models/**/*",
    )
}

// --------------------------------------------------------------- dependencies
repositories {
    maven {
        name = "OpenComputers"
        url = uri("https://maven.cil.li/")
        content { includeGroup("li.cil.oc") }
    }
    maven {
        name = "SquidDev (CC: Tweaked)"
        url = uri("https://squiddev.cc/maven/")
        content { includeGroup("org.squiddev") }
    }
    maven {
        name = "IC2"
        url = uri("https://maven.ic2.player.to/")
        content { includeGroup("net.industrial-craft") }
    }
    maven {
        name = "CurseMaven"
        url = uri("https://cursemaven.com")
        content { includeGroup("curse.maven") }
    }
}

// Dependencies shaded into the jar under mods.eln.shaded.* so they cannot clash
// with another mod's copy (notably Forgelin's Kotlin runtime).
val shade: Configuration by configurations.creating
configurations.implementation.get().extendsFrom(shade)

dependencies {
    shade(kotlin("stdlib-jdk8"))
    shade("org.apache.commons:commons-math3:3.6.1")
    shade("org.apache.commons:commons-numbers-core:1.2")
    shade("org.semver4j:semver4j:4.3.0")

    compileOnly("com.fazecast:jSerialComm:2.6.2")
    compileOnly(rfg.deobf("curse.maven:hwyla-253449:2568751"))
    compileOnly(rfg.deobf("li.cil.oc:OpenComputers:MC1.12.2-1.7.5.192:api"))
    compileOnly(rfg.deobf("org.squiddev:cc-tweaked-1.12.2:1.89.2"))
    compileOnly(rfg.deobf("net.industrial-craft:industrialcraft-2:2.8.222-ex112:api"))

    // Build-time only: the lang-file generator parses the mod's own sources for tr()
    // and TR_NAME() call sites. Never published, never loaded at runtime.
    compileOnly("com.github.javaparser:javaparser-core:3.26.3")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")
    runtimeOnly("com.github.javaparser:javaparser-core:3.26.3")
    runtimeOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.21")

    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
}

// ---------------------------------------------------------------------- tests
tasks.named<Test>("test") {
    exclude("**/*BenchmarkTest.*", "**/*ProfilingTest.*")
}

tasks.register<Test>("benchmarkTest") {
    description = "Runs benchmark and profiling tests separately from the correctness suite."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*BenchmarkTest.*", "**/*ProfilingTest.*")
    shouldRunAfter(tasks.named("test"))
}

tasks.named("check") { dependsOn(tasks.named("test")) }

// ------------------------------------------------------------------ packaging
tasks.shadowJar {
    configurations = listOf(shade)
    archiveClassifier.set("")
    listOf(
        "kotlin", "org.jetbrains.annotations", "org.intellij.lang.annotations",
        "org.apache.commons.math3", "org.apache.commons.numbers", "org.semver4j",
    ).forEach { relocate(it, "mods.eln.shaded.$it") }
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/maven/**")
    mergeServiceFiles()
    minimize()
}

tasks.named<Jar>("jar") { archiveClassifier.set("dev") }
tasks.reobfJar { inputJar.set(tasks.shadowJar.flatMap { it.archiveFile }) }
tasks.named("assemble") { dependsOn(tasks.shadowJar) }

// ------------------------------------------------------------------------ i18n
tasks.register<JavaExec>("generateLangFiles") {
    group = "build"
    description = "Regenerates the language files from tr()/TR_NAME() call sites."
    mainClass.set("mods.eln.i18n.LanguageFileUpdater")
    args("src/main", "src/main/resources/assets/eln/lang")
    classpath = sourceSets.main.get().runtimeClasspath + sourceSets.main.get().output
    dependsOn("classes")
}

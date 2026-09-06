import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    id("net.neoforged.moddev")
}

group = properties["modGroup"] as String
version = "3.0.0-port"

// ---------------------------------------------------------------- toolchains
// Minecraft 1.21.1 runs on Java 21; the mod compiles and runs on the same toolchain.
java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // Report every error, not javac's default first 100: the port fixes them by histogram.
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000"))
}

// ------------------------------------------------------------ port include list
// The 1.21.1 port compiles an explicit list of files and grows it (tools/port/include-1.21.txt).
// Everything not listed is still in the tree, untouched, and does not reach the compiler until it
// has been ported. `./gradlew portStatus` prints the count.
fun portIncludes(listFile: String): List<String> =
    file("tools/port/$listFile").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

// Lines starting with "!" are excludes (a file inside an included package that is not ported yet).
val mainIncludes = portIncludes("include-1.21.txt").filterNot { it.startsWith("!") }
val mainExcludes = portIncludes("include-1.21.txt").filter { it.startsWith("!") }.map { it.substring(1) }
val testIncludes = portIncludes("include-1.21-test.txt").filterNot { it.startsWith("!") }
val testExcludes = portIncludes("include-1.21-test.txt").filter { it.startsWith("!") }.map { it.substring(1) }

sourceSets {
    main {
        java.setIncludes(mainIncludes)
        java.setExcludes(mainExcludes)
        // Data generation writes here; the run task below points --output at it.
        resources.srcDir("src/generated/resources")
    }
    test {
        java.setIncludes(testIncludes)
        java.setExcludes(testExcludes)
    }
}
kotlin.sourceSets {
    getByName("main").kotlin.setIncludes(mainIncludes)
    getByName("main").kotlin.setExcludes(mainExcludes)
    getByName("test").kotlin.setIncludes(testIncludes)
    getByName("test").kotlin.setExcludes(testExcludes)
}

tasks.register("portStatus") {
    group = "port"
    description = "How much of the source tree the 1.21.1 build currently compiles."
    doLast {
        val all = fileTree("src/main") { include("**/*.java", "**/*.kt") }.files.size
        val included = sourceSets.main.get().let { it.java.files.size + kotlin.sourceSets.getByName("main").kotlin.files.size }
        val kotlinOnly = kotlin.sourceSets.getByName("main").kotlin.files.count { it.extension == "kt" }
        val javaOnly = sourceSets.main.get().java.files.size
        println("port/1.21.1: $javaOnly java + $kotlinOnly kotlin files included of $all in src/main")
    }
}

// ---------------------------------------------------------------- NeoForge/MDG
neoForge {
    version = property("neoVersion") as String

    parchment {
        minecraftVersion = property("parchmentMinecraftVersion") as String
        mappingsVersion = property("parchmentMappingsVersion") as String
    }

    validateAccessTransformers = true

    // The unit tests run through FML's JUnit launcher: NeoForge's patched Bootstrap needs a
    // loaded mod list (FeatureFlags reads it), so a plain Bootstrap.bootStrap() cannot work. The
    // launcher loads the mod itself, so tests see the registered content.

    runs {
        create("client") {
            client()
            // The Gradle daemon is usually started without DISPLAY, and JavaExec inherits the
            // daemon's environment - so a headless X server has to be handed to the run explicitly.
            System.getenv("DISPLAY")?.let { environment("DISPLAY", it) }
        }
        create("server") {
            server()
            programArgument("--nogui")
        }
        // ./gradlew runData: recipes, tags, loot tables and ore worldgen from the mod's own
        // registrations (mods.eln.data.ElnDataGenerator).
        create("data") {
            data()
            programArguments.addAll(
                "--mod", "eln", "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources").absolutePath,
            )
        }
        create("gameTestServer") {
            type = "gameTestServer"
        }
        configureEach {
            // One game directory per run type: the server keeps its world, the client its options.
            gameDirectory.set(file("run/$name"))
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
            jvmArgument("-ea:${project.group}")
            if (project.hasProperty("traceClasses")) jvmArgument("-verbose:class")
            if (project.hasProperty("dumpRegistry")) systemProperty("eln.dumpRegistry", "true")
            if (project.hasProperty("smokeTest")) systemProperty("eln.smokeTest", project.property("smokeTest").toString())
            if (project.hasProperty("stopAfterStart")) systemProperty("eln.stopAfterStart", project.property("stopAfterStart").toString())
            if (project.hasProperty("stopAtTitle")) systemProperty("eln.stopAtTitle", "true")
            if (project.hasProperty("smokeClient")) systemProperty("eln.smokeClient", project.property("smokeClient").toString())
        }
    }

    mods {
        create("eln") { sourceSet(sourceSets.main.get()) }
    }

    unitTest {
        enable()
        testedMod = mods.getByName("eln")
    }
}

// ------------------------------------------------------------------ resources
// The `.lang` files (generated from tr()/TR_NAME() call sites by generateLangFiles) stay the
// source of truth; 1.13+ loads JSON, so they are converted at build time. Keys are copied
// verbatim - I18N.tr looks them up in their escaped form ("Can_create\:") exactly as before.
val generateLangJson by tasks.registering {
    group = "build"
    description = "Converts assets/eln/lang/*.lang into the JSON lang files Minecraft 1.13+ loads."
    val src = layout.projectDirectory.dir("src/main/resources/assets/eln/lang")
    val out = layout.buildDirectory.dir("generated/lang")
    inputs.dir(src)
    outputs.dir(out)
    doLast {
        val dir = out.get().dir("assets/eln/lang").asFile
        dir.mkdirs()
        fun esc(s: String) = buildString {
            s.forEach { c ->
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (c < ' ') append(String.format("\\u%04x", c.code)) else append(c)
                }
            }
        }
        src.asFile.listFiles { f -> f.extension == "lang" }!!.sorted().forEach { f ->
            val entries = LinkedHashMap<String, String>()
            f.readLines(Charsets.UTF_8).forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                // First '=' not escaped as "\=" (encodeLangKey escapes '=' inside keys).
                val i = Regex("(?<!\\\\)=").find(line)?.range?.first ?: return@forEach
                entries[line.substring(0, i)] = line.substring(i + 1)
            }
            File(dir, f.nameWithoutExtension.lowercase() + ".json")
                .writeText(entries.entries.joinToString(",\n", "{\n", "\n}\n") { "  \"${esc(it.key)}\": \"${esc(it.value)}\"" }, Charsets.UTF_8)
        }
    }
}
sourceSets.main { resources.srcDir(generateLangJson) }

// mods.eln.Tags (the version constant Version.kt reads) was injected by RetroFuturaGradle; here it
// is a generated source.
val generateTags by tasks.registering {
    group = "build"
    val out = layout.buildDirectory.dir("generated/tags")
    val version = project.version.toString()
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val f = out.get().file("mods/eln/Tags.java").asFile
        f.parentFile.mkdirs()
        f.writeText("package mods.eln;\n\n/** Generated by build.gradle.kts (generateTags). */\npublic final class Tags {\n    private Tags() {}\n    public static final String VERSION = \"$version\";\n}\n")
    }
}
sourceSets.main { java.srcDir(generateTags) }

tasks.processResources {
    exclude("**/*.lang")
    val props = mapOf(
        "version" to project.version.toString(),
        "modId" to project.property("modId").toString(),
        "modName" to project.property("modName").toString(),
        "minecraftVersion" to project.property("minecraftVersion").toString(),
        "neoVersion" to project.property("neoVersion").toString(),
        "kffVersion" to project.property("kffVersion").toString(),
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
    exclude(
        "mcmod.info",
        "**/_Common", "**/_TEMPLATES", "**/export_*.png", "**/*.blend*", "**/*.xcf",
        "**/temp/**/*", "**/model-to-be-integrated/**/*", "**/unused-models/**/*",
    )
}

// --------------------------------------------------------------- dependencies
repositories {
    maven {
        name = "Kotlin for Forge"
        url = uri("https://thedarkcolour.github.io/KotlinForForge/")
        content { includeGroup("thedarkcolour") }
    }
    maven {
        name = "Modrinth"
        url = uri("https://api.modrinth.com/maven")
        content { includeGroup("maven.modrinth") }
    }
}

dependencies {
    // Kotlin runtime comes from the Kotlin for Forge mod at run time (no shading, no relocation).
    implementation("thedarkcolour:kotlinforforge-neoforge:${property("kffVersion")}")

    // Libraries the mod carries (jar-in-jar; Minecraft ships none of them).
    // additionalRuntimeClasspath: on 1.21.1, dev runs only load a plain library jar if it is
    // named there (FML loads mods and FMLModType jars from the classpath, nothing else).
    for (lib in listOf("org.apache.commons:commons-math3:3.6.1", "org.apache.commons:commons-numbers-core:1.2", "org.semver4j:semver4j:4.3.0")) {
        implementation(lib)
        jarJar(lib)
        "additionalRuntimeClasspath"(lib)
    }
    compileOnly("com.fazecast:jSerialComm:2.6.2")

    // Jade (Waila's successor) for the hover overlay: compiled against, and loaded as a mod in the
    // dev runs so the overlay can be looked at; optional at run time (mods.eln.integration.jade).
    implementation("maven.modrinth:jade:${property("jadeVersion")}")

    // Build-time only: the lang-file generator parses the mod's own sources for tr() and
    // TR_NAME() call sites. Never shipped, never loaded at run time.
    compileOnly("com.github.javaparser:javaparser-core:3.26.3")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${property("kotlinVersion")}")

    // The tests are JUnit 4; they run on the JUnit Platform (vintage engine) because FML's JUnit
    // launcher (junit-fml, added by ModDevGradle's unitTest) is a platform LauncherSessionListener.
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.2")
}

// ---------------------------------------------------------------------- tests
tasks.named<Test>("test") {
    useJUnitPlatform()
    exclude("**/*BenchmarkTest.*", "**/*ProfilingTest.*")
    // The FML launcher runs the tests from build/minecraft-junit; tests that read repository
    // files (docs/examples, biomes.json) resolve them against this.
    systemProperty("eln.projectDir", projectDir.absolutePath)
}

tasks.register<Test>("benchmarkTest") {
    description = "Runs benchmark and profiling tests separately from the correctness suite."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    include("**/*BenchmarkTest.*", "**/*ProfilingTest.*")
    shouldRunAfter(tasks.named("test"))
}

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

val mainIncludes = portIncludes("include-1.21.txt")
val testIncludes = portIncludes("include-1.21-test.txt")

sourceSets {
    main {
        java.setIncludes(mainIncludes)
        // Data generation writes here; the run task below points --output at it.
        resources.srcDir("src/generated/resources")
    }
    test { java.setIncludes(testIncludes) }
}
kotlin.sourceSets {
    getByName("main").kotlin.setIncludes(mainIncludes)
    getByName("test").kotlin.setIncludes(testIncludes)
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
        }
    }

    mods {
        create("eln") { sourceSet(sourceSets.main.get()) }
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
}

dependencies {
    // Kotlin runtime comes from the Kotlin for Forge mod at run time (no shading, no relocation).
    implementation("thedarkcolour:kotlinforforge-neoforge:${property("kffVersion")}")

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

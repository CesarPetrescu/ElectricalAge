package mods.eln

import java.io.File

internal fun disableLog4jJmx() {
    System.setProperty("log4j2.disable.jmx", "true")
}

/**
 * Blocks/Items/SoundEvents live behind Bootstrap. The tests run through FML's JUnit launcher
 * (ModDevGradle `unitTest`), which has bootstrapped the game and loaded the mod before any test
 * runs; this is kept as the one place tests declare that dependency. Idempotent.
 */
fun bootstrapMinecraft() {
    disableLog4jJmx()
    check(net.neoforged.fml.loading.FMLLoader.getLoadingModList() != null) { "the tests must run through the FML JUnit launcher (./gradlew test)" }
}

/** A file of the repository ("docs/examples"); the launcher's working directory is build/minecraft-junit. */
fun projectFile(relative: String): File =
    File(System.getProperty("eln.projectDir") ?: ".", relative)

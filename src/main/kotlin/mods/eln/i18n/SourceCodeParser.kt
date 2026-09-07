package mods.eln.i18n

import java.io.File
import java.io.IOException
import java.util.TreeMap
import java.util.TreeSet

internal object SourceCodeParser {
    private const val MULTIPLE_LOCATIONS = "Appearing in multiple source files"
    private val kotlinParser = KotlinAstParser()

    @Throws(IOException::class)
    @JvmStatic
    fun parseSourceFolder(file: File): Map<String, Set<TranslationItem>> {
        val strings = TreeMap<String, MutableSet<TranslationItem>>()
        strings[MULTIPLE_LOCATIONS] = TreeSet()
        // Jade owns these keys; they are not produced by ELN's tr()/TR_NAME() encoding.
        strings["Jade integration settings"] = sortedSetOf(
            TranslationItem("config.jade.plugin_eln.six_node", "Surface-mounted devices"),
            TranslationItem("config.jade.plugin_eln.transparent_node", "Machines"),
            TranslationItem("config.jade.plugin_eln.ghost_node", "Multiblock parts")
        )
        parseSourceFolderRecursive(file, strings)
        return strings
    }

    @Throws(IOException::class)
    private fun parseSourceFolderRecursive(folder: File?, strings: MutableMap<String, MutableSet<TranslationItem>>) {
        if (folder != null && folder.exists()) {
            val files = folder.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    parseSourceFolderRecursive(file, strings)
                } else if (file.isFile && file.name.endsWith(".java")) {
                    println("Parsing Java source file: ${file.name}...")
                    parseJavaFile(file, strings)
                } else if (file.isFile && file.name.endsWith(".kt")) {
                    println("Parsing Kotlin source file: ${file.name}...")
                    parseKotlinFile(file, strings)
                }
            }
        }
    }

    private fun parseJavaFile(file: File, strings: MutableMap<String, MutableSet<TranslationItem>>) {
        val texts = JavaAstParser.parseFile(file)
        for (item in texts) {
            println("  ${item.key}")
            if (!isStringAlreadyPresent(item, strings)) {
                strings.getOrPut(file.path) { TreeSet() }.add(item)
            }
        }
    }

    private fun parseKotlinFile(file: File, strings: MutableMap<String, MutableSet<TranslationItem>>) {
        val texts = kotlinParser.parseFile(file)
        for (item in texts) {
            println("  ${item.key}")
            if (!isStringAlreadyPresent(item, strings)) {
                strings.getOrPut(file.path) { TreeSet() }.add(item)
            }
        }
    }

    private fun isStringAlreadyPresent(
        string: TranslationItem,
        strings: MutableMap<String, MutableSet<TranslationItem>>
    ): Boolean {
        for (fileName in strings.keys) {
            if (MULTIPLE_LOCATIONS == fileName) {
                if (strings[fileName]!!.contains(string))
                    return true
            } else {
                if (strings[fileName]!!.remove(string)) {
                    strings[MULTIPLE_LOCATIONS]!!.add(string)
                    return true
                }
            }
        }
        return false
    }
}

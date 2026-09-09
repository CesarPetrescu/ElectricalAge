import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.io.File

/** Syntax only. Does not claim resolution of Minecraft or NeoForge types. */
fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        val env = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val factory = KtPsiFactory(env.project, false)
        val files = args.flatMap { File(it).walkTopDown().filter { f -> f.isFile && f.extension == "kt" }.toList() }
        val errors = mutableListOf<String>()
        for (file in files) {
            val tree = factory.createFile(file.name, file.readText())
            for (error in PsiTreeUtil.collectElementsOfType(tree, PsiErrorElement::class.java)) {
                errors += "${file.path}:${error.textOffset}: ${error.errorDescription}"
            }
        }
        errors.forEach(::println)
        check(errors.isEmpty()) { "Kotlin syntax errors: ${errors.size}" }
        println("Kotlin syntax: ${files.size} source files parsed; NOT a typecheck or Minecraft build")
    } finally {
        Disposer.dispose(disposable)
    }
}

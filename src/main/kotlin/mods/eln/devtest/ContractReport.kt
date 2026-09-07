package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.Eln
import java.nio.file.Files
import java.nio.file.Path

/** Machine-readable checks; skipped means untested, never passed. */
class ContractReport(val suite: String) {
    data class Result(val id: String, val check: String, val status: String, val detail: String, val milliseconds: Long)
    val results = mutableListOf<Result>()
    val failures get() = results.count { it.status == "failed" }
    fun test(id: String, check: String, body: () -> Unit): Boolean {
        val start = System.nanoTime()
        var error: Throwable? = null
        try { body() } catch (t: Throwable) { error = t }
        val result = Result(id, check, if (error == null) "passed" else "failed", error?.toString() ?: "", (System.nanoTime() - start) / 1_000_000)
        results.add(result)
        if (error == null) Eln.logger.info("CONTRACT PASS {} / {}", id, check)
        else Eln.logger.error("CONTRACT FAIL {} / {}", id, check, error)
        return error == null
    }
    fun skip(id: String, check: String, reason: String) { results.add(Result(id, check, "skipped", reason, 0)) }
    fun write(complete: Boolean) {
        val dir = Path.of("../../build/smoke-artifacts/contracts")
        Files.createDirectories(dir)
        val report = mapOf("suite" to suite, "complete" to complete, "failures" to failures, "results" to results)
        Files.writeString(dir.resolve("$suite.json"), GsonBuilder().setPrettyPrinting().create().toJson(report))
        fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
        val xml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<testsuite name=\"${escape(suite)}\" tests=\"${results.size}\" failures=\"$failures\" skipped=\"${results.count { it.status == "skipped" }}\">\n")
            for (r in results) {
                append("<testcase classname=\"${escape(r.id)}\" name=\"${escape(r.check)}\" time=\"${r.milliseconds / 1000.0}\">")
                if (r.status != "passed") append("<${if (r.status == "failed") "failure" else "skipped"} message=\"${escape(r.detail)}\"/>")
                append("</testcase>\n")
            }
            append("</testsuite>\n")
        }
        Files.writeString(dir.resolve("$suite.xml"), xml)
    }
}

package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import mods.eln.Eln
import net.minecraft.core.BlockPos
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Opt-in CI control channel. Files schedule actions; game state is observed inside each JVM. */
abstract class MultiplayerProbe(private val role: String) {
    protected val directory: Path = Path.of(System.getProperty("eln.multiplayerDirectory")).toAbsolutePath()
    private var lastId = ""
    private var active: JsonObject? = null
    private var started = 0L
    protected var ticks = 0
    protected open fun begin(command: JsonObject) {}
    protected abstract fun observe(command: JsonObject): JsonObject?
    protected open fun capture(id: String) {}
    protected open fun finish(command: JsonObject) {}

    fun tick() {
        val path = directory.resolve("$role-command.json")
        if (active == null && Files.exists(path)) {
            val command = JsonParser.parseString(Files.readString(path)).asJsonObject
            if (command.get("id").asString == lastId) return
            active = command
            lastId = command.get("id").asString
            require(lastId.matches(Regex("[a-zA-Z0-9_-]+")))
            ticks = 0
            started = System.nanoTime()
            try { begin(command) } catch (t: Throwable) { complete(command, null, t); return }
        }
        val command = active ?: return
        try {
            ticks++
            val result = observe(command)
            if (result != null) complete(command, result, null)
            else check((System.nanoTime() - started) / 1e9 < 150) { "Timed out waiting for ${command.get("action")}" }
        } catch (t: Throwable) { complete(command, null, t) }
    }

    private fun complete(command: JsonObject, observation: JsonObject?, error: Throwable?) {
        val result = JsonObject().apply {
            addProperty("id", lastId); addProperty("role", role)
            addProperty("action", command.get("action").asString)
            addProperty("status", if (error == null) "passed" else "failed")
            addProperty("detail", error?.stackTraceToString() ?: "")
            addProperty("pid", ProcessHandle.current().pid())
            addProperty("seconds", (System.nanoTime() - started) / 1e9)
            add("observation", observation ?: JsonObject())
        }
        try { capture(lastId) } catch (t: Throwable) { result.addProperty("screenshotError", t.toString()) }
        val destination = directory.resolve("$role-$lastId.json")
        val temporary = directory.resolve("$role-$lastId.tmp")
        Files.writeString(temporary, GsonBuilder().setPrettyPrinting().create().toJson(result))
        Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        if (error == null) Eln.logger.info("MULTIPLAYER PASS {} / {}", role, lastId)
        else Eln.logger.error("MULTIPLAYER FAIL {} / {}", role, lastId, error)
        active = null
        if (error == null) finish(command)
    }

    protected fun runtime(): JsonObject {
        check(FMLEnvironment.production) { "This suite must test the packaged mod, not development classes" }
        val jar = ModList.get().getModFileById("eln").file.filePath
        check(Files.isRegularFile(jar) && jar.fileName.toString().endsWith(".jar")) { "ELN not loaded from a JAR: $jar" }
        val digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)).joinToString("") { "%02x".format(it) }
        check(digest == System.getProperty("eln.multiplayerJarSha256")) { "Loaded ELN JAR does not match build artifact: $digest" }
        val create = ModList.get().isLoaded("create")
        check(create == (System.getProperty("eln.multiplayerProfile") == "create"))
        return JsonObject().apply { addProperty("jarSha256", digest); addProperty("create", create) }
    }

    protected fun action(c: JsonObject) = c.get("action").asString
    protected fun passed() = JsonObject()
}

object MultiplayerScene {
    val source = BlockPos(96, 65, 96)
    val monitor = BlockPos(96, 65, 100)
    val adapter = BlockPos(100, 65, 100)
}

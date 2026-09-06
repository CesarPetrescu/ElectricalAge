package mods.eln.client

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.InputEvent.Key
import mods.eln.Eln
import mods.eln.ServerKeyHandler
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Utils
import mods.eln.misc.UtilsClient.clientOpenGui
import mods.eln.misc.UtilsClient.sendPacketToServer
import mods.eln.wiki.Root
import net.minecraft.client.KeyMapping
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import mods.eln.client.Keyboard
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException

data class ElectricalAgeKey(var defaultKeybind: Int, val name: String, var lastState: Boolean = false, var binding: KeyMapping? = null)

class ClientKeyHandler {
    // Note: C is the default wrench key, but it can be changed with the GUI in-game. This is override with the value stored in options.txt
    private val keyboardKeys = listOf(
        ElectricalAgeKey(Keyboard.KEY_C, ServerKeyHandler.WRENCH),
        ElectricalAgeKey(org.lwjgl.glfw.GLFW.GLFW_KEY_P, ServerKeyHandler.WIKI)
    )

    init {
        keyboardKeys.forEach {
            it.binding = KeyMapping(it.name, it.defaultKeybind, "ElectricalAge")
        }
    }

    /** 1.21 registers key mappings through the mod-bus event (see ClientSetup). */
    fun register(event: RegisterKeyMappingsEvent) {
        keyboardKeys.forEach { key -> key.binding?.let { event.register(it) } }
    }

    @SubscribeEvent
    fun onKeyInput(@Suppress("UNUSED_PARAMETER") event: Key?) {
        keyboardKeys.forEach {
            setState(it.name, it.binding?.isDown ?: return@forEach)
        }
    }

    fun setState(name: String, state: Boolean) {
        val entry = keyboardKeys.firstOrNull { it.name == name }?: return
        if (entry.lastState != state) {
            entry.lastState = state // Be sure to set the state so that it calls again when key released

            if (entry.name == ServerKeyHandler.WIKI && state) {
                // Only trigger if state = true (ie, when pressed, not when released)
                // TODO: Add latch feature to allow closing of the UI by pressing again.
                clientOpenGui(Root(null))
            }

            Utils.println("Sending a client key event to server: ${entry.name} is $state")
            val bos = ByteArrayOutputStream(64)
            val stream = DataOutputStream(bos)
            try {
                stream.writeByte(Eln.packetPlayerKey.toInt())
                stream.writeUTF(entry.name)
                stream.writeBoolean(state)
            } catch (e: IOException) {
                e.printStackTrace()
            }
            sendPacketToServer(bos)
        }
    }
}

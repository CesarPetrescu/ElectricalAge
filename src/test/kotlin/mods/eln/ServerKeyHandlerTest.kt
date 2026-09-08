package mods.eln

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ServerKeyHandlerTest {
    private val alice = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val bob = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @After fun cleanup() = ServerKeyHandler.clear()

    @Test fun `holding or releasing a key cannot change another player's controls`() {
        ServerKeyHandler.set(alice, ServerKeyHandler.WRENCH, true)
        assertTrue(ServerKeyHandler.get(alice, ServerKeyHandler.WRENCH))
        assertFalse(ServerKeyHandler.get(bob, ServerKeyHandler.WRENCH))
        ServerKeyHandler.set(bob, ServerKeyHandler.WRENCH, true)
        ServerKeyHandler.set(alice, ServerKeyHandler.WRENCH, false)
        assertFalse(ServerKeyHandler.get(alice, ServerKeyHandler.WRENCH))
        assertTrue(ServerKeyHandler.get(bob, ServerKeyHandler.WRENCH))
    }

    @Test fun `disconnect and reconnect cannot inherit a held key`() {
        ServerKeyHandler.set(alice, ServerKeyHandler.WRENCH, true)
        ServerKeyHandler.set(bob, ServerKeyHandler.WRENCH, true)
        ServerKeyHandler.remove(alice)
        assertFalse(ServerKeyHandler.get(alice, ServerKeyHandler.WRENCH))
        assertTrue(ServerKeyHandler.get(bob, ServerKeyHandler.WRENCH))
    }

    @Test fun `server shutdown clears all session state`() {
        ServerKeyHandler.set(alice, ServerKeyHandler.WRENCH, true)
        ServerKeyHandler.set(bob, ServerKeyHandler.WRENCH, true)
        ServerKeyHandler.clear()
        assertFalse(ServerKeyHandler.get(alice, ServerKeyHandler.WRENCH))
        assertFalse(ServerKeyHandler.get(bob, ServerKeyHandler.WRENCH))
    }

    @Test fun `unknown keys and client-only wiki key are ignored`() {
        for (key in listOf("Unknown", ServerKeyHandler.WIKI, "", "wrench")) {
            ServerKeyHandler.set(alice, key, true)
            assertFalse(ServerKeyHandler.get(alice, key))
        }
        assertFalse(ServerKeyHandler.get(alice, ServerKeyHandler.WRENCH))
    }
}

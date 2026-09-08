package mods.eln

import java.util.UUID

object ServerKeyHandler {
    const val WRENCH = "Wrench"
    const val WIKI = "Wiki"
    // Only touched on the logical server thread, including packet and logout handlers.
    private val wrenchPlayers = HashSet<UUID>()

    fun get(playerId: UUID, name: String): Boolean = name == WRENCH && playerId in wrenchPlayers

    fun set(playerId: UUID, name: String, state: Boolean) {
        if (name != WRENCH) return // The wiki is client-only; do not store arbitrary packet keys.
        if (state) wrenchPlayers.add(playerId) else wrenchPlayers.remove(playerId)
    }

    fun remove(playerId: UUID) { wrenchPlayers.remove(playerId) }
    fun clear() { wrenchPlayers.clear() }
}

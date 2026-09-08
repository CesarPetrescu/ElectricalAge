package mods.eln.sixnode.electricalsource

/** Reads client render state only; no server-side objects are available in this JVM. */
object MultiplayerSourceClient {
    fun voltage(render: ElectricalSourceRender) = render.voltage
}

package mods.eln.sim.process.destruct

import mods.eln.sim.mna.state.VoltageState

class VoltageStateWatchDog @JvmOverloads constructor(var state: VoltageState, var reference: VoltageState? = null): ValueWatchdog() {
    override val watchdogType = WatchdogType.VOLTAGE

    override fun getValue(): Double {
        return state.voltage - (reference?.voltage ?: 0.0)
    }

    fun setNominalVoltage(nominalVoltage: Double): VoltageStateWatchDog {
        max = nominalVoltage * 1.3
        min = -nominalVoltage * 1.3
        timeoutReset = nominalVoltage * 0.25
        return this
    }
}

package mods.eln.sim.mna.component

import mods.eln.sim.mna.SubSystem

/** Disabled means an open branch (I=0), not the short imposed by a zero-volt source. */
class SwitchableVoltageSource(name: String) : VoltageSource(name) {
    var enabled = false
        set(value) {
            if (field != value) {
                field = value
                dirty()
            }
        }

    override fun applyToSubsystem(system: SubSystem) {
        if (enabled) super.applyToSubsystem(system)
        else system.addToA(currentState, currentState, 1.0)
    }

    override fun simProcessI(system: SubSystem) {
        if (enabled) super.simProcessI(system)
    }

    override fun getCurrent(): Double = if (enabled) super.getCurrent() else 0.0
    override fun getPower(): Double = if (enabled) super.getPower() else 0.0
}

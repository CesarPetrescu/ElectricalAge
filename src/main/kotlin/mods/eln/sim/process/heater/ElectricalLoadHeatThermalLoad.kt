package mods.eln.sim.process.heater

import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.ThermalLoad

class ElectricalLoadHeatThermalLoad(var resistor: ElectricalLoad, var load: ThermalLoad) : IProcess {
    /** Kept for old descriptors/addons. A stability setting must not destroy electrical energy. */
    @Deprecated("Heat is never clipped; use physical thermal properties and stable integration")
    fun limitTemperatureRate(maxDeltaTPerSecond: Double): ElectricalLoadHeatThermalLoad {
        return this
    }

    override fun process(time: Double) {
        if (resistor.isNotSimulated) return
        val current = resistor.current
        val power = current * current * resistor.serialResistance * 2
        load.movePowerTo(power)
    }
}

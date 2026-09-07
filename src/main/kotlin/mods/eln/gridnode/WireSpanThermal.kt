package mods.eln.gridnode

import mods.eln.Eln
import mods.eln.sim.IProcess
import mods.eln.sim.process.heater.ElectricalHeatAccumulator
import mods.eln.sixnode.electricalcable.*
import net.minecraft.nbt.CompoundTag

/** One active electrical core over the paid span length. Ambient is sampled at loaded endpoints.
 * Does not borrow unused multicore mass/cooling to make the single grid circuit harder to melt.
 */
internal class WireSpanThermal(val descriptor: UtilityCableDescriptor, val meters: Double) {
    val load = WireThermalLoad("span", WireThermalPhysics(descriptor.material, descriptor.conductorAreaMm2, meters))
    var insulationDamaged = descriptor.melted
    private var heater: ElectricalHeatAccumulator? = null
    private var refresh: IProcess? = null
    private var failure: IProcess? = null

    fun connect(connection: WireSpanConnection, ambient: () -> Double, burn: () -> Unit) {
        fun update() {
            val air = ambient()
            load.updateProperties(air, air, descriptor.insulated && !insulationDamaged)
            connection.spanOhms = descriptor.resistanceOhms(meters, load.absoluteCelsius)
            connection.notifyRsChange()
        }
        update()
        load.setAsSlow()
        val h = ElectricalHeatAccumulator({ connection.current * connection.current * connection.spanOhms }, load)
        heater = h
        refresh = IProcess { update() }
        failure = IProcess {
            if (descriptor.insulated && load.absoluteCelsius >= descriptor.meltTemperatureCelsius) insulationDamaged = true
            if (load.failed) burn()
        }
        Eln.simulator.addThermalLoad(load)
        Eln.simulator.addElectricalProcess(h.sample)
        Eln.simulator.addThermalSlowProcess(refresh)
        Eln.simulator.addThermalSlowProcess(h.deliver)
        Eln.simulator.addSlowProcess(failure)
    }

    fun disconnect() {
        heater?.let {
            // A disconnect can precede the next thermal tick. Keep already-paid heat.
            it.flushIntoLoad()
            Eln.simulator.removeElectricalProcess(it.sample)
            Eln.simulator.removeThermalSlowProcess(it.deliver)
        }
        Eln.simulator.removeThermalSlowProcess(refresh)
        Eln.simulator.removeSlowProcess(failure)
        Eln.simulator.removeThermalLoad(load)
        heater = null; refresh = null; failure = null
    }
    fun read(nbt: CompoundTag) { load.readFromNBT(nbt, ""); insulationDamaged = insulationDamaged || nbt.getBoolean("spanInsulationDamaged") }
    fun write(nbt: CompoundTag) { load.writeToNBT(nbt, ""); nbt.putBoolean("spanInsulationDamaged", insulationDamaged) }
}

package mods.eln.integration.opencomputers

import li.cil.oc.api.network.BlacklistedPeripheral
import mods.eln.integration.computercraft.ComputerProbePeripheral
import mods.eln.simplenode.computerprobe.ComputerProbeEntity

/** Loaded with BOTH mods only: keep CC available, but prevent OC wrapping it beside our native driver. */
class OcAwareComputerProbePeripheral(entity: ComputerProbeEntity) : ComputerProbePeripheral(entity), BlacklistedPeripheral {
    override fun isPeripheralBlacklisted() = true
}

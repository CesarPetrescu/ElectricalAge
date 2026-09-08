package mods.eln.transparentnode.heatfurnace

import mods.eln.Eln
import mods.eln.devtest.ContractReport
import mods.eln.misc.Coordinate
import mods.eln.node.transparent.TransparentNode
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/** Isolated production fuel process, opt-in survival campaign only. */
object HeatFurnaceFuelChecks {
    fun run(world: ServerLevel, report: ContractReport) {
        val path = "machines.heatFurnace.consumeFuel"
        val previous = Eln.config.getBooleanOrElse(path, true)
        try {
            for (consume in listOf(true, false)) for (fuel in listOf(false, true)) {
                report.test("eln:stone_heat_furnace", "consume-$consume-coal-$fuel") {
                    Eln.config.setRuntimeBoolean(path, consume)
                    val descriptor = Eln.transparentNodeItem.subItemList.values.filterIsInstance<HeatFurnaceDescriptor>().first()
                    val node = TransparentNode().apply { coordinate = Coordinate(208, 65, 160, world) }
                    val furnace = HeatFurnaceElement(node, descriptor)
                    node.element = furnace
                    descriptor.applyTo(furnace.thermalLoad)
                    descriptor.applyTo(furnace.furnaceProcess)
                    furnace.takeFuel = true
                    if (fuel) furnace.inventory.setItem(HeatFurnaceContainer.combustibleId, ItemStack(Items.COAL, 4))
                    furnace.inventoryProcess.process(.05)
                    val remaining = furnace.inventory.getItem(HeatFurnaceContainer.combustibleId).count
                    check(remaining == if (fuel) (if (consume) 3 else 4) else 0)
                    check((furnace.furnaceProcess.combustibleEnergy > 0) == (!consume || fuel))
                    furnace.furnaceProcess.process(.05)
                    check((furnace.thermalLoad.PcTemp > 0) == (!consume || fuel))
                }
            }
        } finally { Eln.config.setRuntimeBoolean(path, previous) }
    }
}

package mods.eln.misc

import mods.eln.fluid.ISidedFluidHandler
import mods.eln.node.transparent.TransparentNodeEntity
import mods.eln.node.transparent.TransparentNodeEntityWithFluid
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherEntity
import net.minecraft.core.Direction
import net.minecraft.world.WorldlyContainer
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.items.wrapper.InvWrapper
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper

/**
 * What other mods (hoppers, pipes, cables) can reach on the mod's blocks. 1.7.10 exposed this by
 * interface - ISidedInventory on the transparent node tile, IFluidHandler on the fluid one, the
 * energy exporter's RF handler; since 1.11 it is a capability looked up per block entity type and
 * side, registered here (mod bus, RegisterCapabilitiesEvent).
 */
object ElnCapabilities {
    @JvmStatic
    fun register(event: RegisterCapabilitiesEvent) {
        for (type in listOf(TransparentNodeEntity.TYPE.get(), TransparentNodeEntityWithFluid.TYPE.get())) {
            event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type) { entity, side ->
                if (side != null) SidedInvWrapper(entity as WorldlyContainer, side) else InvWrapper(entity)
            }
        }
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, TransparentNodeEntityWithFluid.TYPE.get()) { entity, side ->
            SidedFluidAdapter(entity, side)
        }
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, EnergyConverterElnToOtherEntity.TYPE.get()) { entity, _ ->
            entity.energyStorage
        }
    }
}

/** One side of the mod's side-aware fluid handler, in the shape the capability wants (no side on the calls). */
class SidedFluidAdapter(private val handler: ISidedFluidHandler, private val side: Direction?) : IFluidHandler {
    private fun action(action: IFluidHandler.FluidAction) = action.execute()

    override fun getTanks(): Int = handler.getTankInfo(side).size

    override fun getFluidInTank(tank: Int): FluidStack = handler.getTankInfo(side).getOrNull(tank)?.fluid ?: FluidStack.EMPTY

    override fun getTankCapacity(tank: Int): Int = handler.getTankInfo(side).getOrNull(tank)?.capacity ?: 0

    override fun isFluidValid(tank: Int, stack: FluidStack): Boolean = handler.canFill(side, stack.fluid)

    override fun fill(resource: FluidStack, action: IFluidHandler.FluidAction): Int = handler.fill(side, resource, action(action))

    override fun drain(resource: FluidStack, action: IFluidHandler.FluidAction): FluidStack =
        handler.drain(side, resource, action(action)) ?: FluidStack.EMPTY

    override fun drain(maxDrain: Int, action: IFluidHandler.FluidAction): FluidStack =
        handler.drain(side, maxDrain, action(action)) ?: FluidStack.EMPTY
}

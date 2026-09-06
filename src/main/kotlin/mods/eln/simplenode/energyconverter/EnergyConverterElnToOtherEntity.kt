package mods.eln.simplenode.energyconverter

import net.minecraftforge.fml.common.Optional
import net.minecraftforge.fml.common.Optional.InterfaceList
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import ic2.api.energy.tile.IEnergyAcceptor
import ic2.api.energy.tile.IEnergySource
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import mods.eln.Other
import mods.eln.misc.Direction
import mods.eln.node.simple.SimpleNodeEntity
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.util.ITickable
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.energy.CapabilityEnergy
import net.neoforged.neoforge.energy.IEnergyStorage
import java.io.DataInputStream
import java.io.IOException
import mods.eln.misc.writeToNBT

@InterfaceList(Optional.Interface(iface = "ic2.api.energy.tile.IEnergySource", modid = Other.modIdIc2), Optional.Interface(iface = "li.cil.oc.api.network.Environment", modid = Other.modIdOc))
class EnergyConverterElnToOtherEntity : SimpleNodeEntity("ElnToOther"), ITickable, IEnergySource, Environment {
    @JvmField
    var selectedResistance = 0.0
    @JvmField
    var hasChanges = false
    var ocEnergy: EnergyConverterElnToOtherFireWallOc? = null
    @JvmField
    var addedToEnet = false
    var ic2tier = 1

    @OnlyIn(Dist.CLIENT)
    override fun newGuiDraw(side: Direction, player: Player): Screen {
        return EnergyConverterElnToOtherGui(this)
    }

    override fun serverPublishUnserialize(stream: DataInputStream) {
        super.serverPublishUnserialize(stream)
        try {
            selectedResistance = stream.readDouble()
            ic2tier = stream.readInt()
            hasChanges = true
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    // ********************IC2********************
    @Optional.Method(modid = Other.modIdIc2)
    override fun emitsEnergyTo(receiver: IEnergyAcceptor, direction: EnumFacing): Boolean {
        if (world.isClientSide) return false
        node ?: return false
        return true
    }

    @Optional.Method(modid = Other.modIdIc2)
    override fun getOfferedEnergy(): Double {
        if (world.isClientSide) return 0.0
        if (node == null) return 0.0
        val node = node as EnergyConverterElnToOtherNode
        return node.availableEnergyInModUnitsWithLimit(IC2Tiers.values().first { it.tier == node.ic2tier }.euPerTick.toDouble(), Other.getWattsToEu())
    }

    @Optional.Method(modid = Other.modIdIc2)
    override fun drawEnergy(amount: Double) {
        if (world.isClientSide) return
        if (node == null) return
        val node = node as EnergyConverterElnToOtherNode
        node.drawEnergy(amount, Other.getWattsToEu())
    }

    @Optional.Method(modid = Other.modIdIc2)
    override fun getSourceTier(): Int {
        // val node = node as EnergyConverterElnToOtherNode
        return 5
    }

    // ***************** OC **********************
    @Optional.Method(modid = Other.modIdOc)
    fun getOc(): EnergyConverterElnToOtherFireWallOc {
        if (ocEnergy == null) ocEnergy = EnergyConverterElnToOtherFireWallOc(this)
        return ocEnergy!!
    }

    @Optional.Method(modid = Other.modIdOc)
    override fun node(): Node {
        return getOc().node!!
    }

    @Optional.Method(modid = Other.modIdOc)
    override fun onConnect(node: Node) {
    }

    @Optional.Method(modid = Other.modIdOc)
    override fun onDisconnect(node: Node) {
    }

    @Optional.Method(modid = Other.modIdOc)
    override fun onMessage(message: Message) {
    }

    /*
     * @Override
	 * 
	 * @Optional.Method(modid = Other.modIdOc) public Node
	 * sidedNode(EnumFacing side) { if(world.isClientSide){ if(front.back()
	 * == Direction.from(side)) return node(); return null; }else{
	 * if(getNode().getFront().back() == Direction.from(side)) return node();
	 * return null; } }
	 * 
	 * @Override
	 * 
	 * @OnlyIn(Dist.CLIENT)
	 * 
	 * @Optional.Method(modid = Other.modIdOc) public boolean
	 * canConnect(EnumFacing side) { if(front == null) return false;
	 * if(front.back() == Direction.from(side)) return true; return false; }
	 */
    // *************** Forge Energy (was cofh RF) **************
    private val energyStorage = object : IEnergyStorage {
        override fun receiveEnergy(maxReceive: Int, simulate: Boolean): Int = 0

        override fun extractEnergy(maxExtract: Int, simulate: Boolean): Int {
            if (world.isClientSide) return 0
            if (node == null) return 0
            val node = node as EnergyConverterElnToOtherNode
            val extract = Math.max(0, Math.min(maxExtract, node.availableEnergyInModUnits(Other.getWattsToRf()).toInt()))
            if (!simulate) node.drawEnergy(extract.toDouble(), Other.getWattsToRf())
            return extract
        }

        override fun getEnergyStored(): Int = 0

        override fun getMaxEnergyStored(): Int = 0

        override fun canExtract(): Boolean = true

        override fun canReceive(): Boolean = false
    }

    private fun canConnectEnergy(): Boolean {
        if (world?.isClientSide != false) return false
        if (node == null) return false
        return true
    }

    override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
        if (capability === CapabilityEnergy.ENERGY) return canConnectEnergy()
        return super.hasCapability(capability, facing)
    }

    override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
        if (capability === CapabilityEnergy.ENERGY) {
            return if (canConnectEnergy()) CapabilityEnergy.ENERGY.cast(energyStorage) else null
        }
        return super.getCapability(capability, facing)
    }

    // ***************** Bridges ****************
    // 1.12.2: only ITickable tile entities tick; there is no TileEntity.updateEntity() to chain to.
    override fun update() {
        if (Other.ic2Loaded) EnergyConverterElnToOtherFireWallIc2.updateEntity(this)
        if (Other.ocLoaded) getOc().updateEntity()
        if (Other.teLoaded) EnergyConverterElnToOtherFireWallRf.updateEntity(this)
    }

    fun onLoaded() {
        if (Other.ic2Loaded) EnergyConverterElnToOtherFireWallIc2.onLoaded(this)
    }

    override fun invalidate() {
        super.invalidate()
        if (Other.ic2Loaded) EnergyConverterElnToOtherFireWallIc2.invalidate(this)
        if (Other.ocLoaded) getOc().invalidate()
    }

    override fun onChunkUnload() {
        super.onChunkUnload()
        if (Other.ic2Loaded) EnergyConverterElnToOtherFireWallIc2.onChunkUnload(this)
        if (Other.ocLoaded) getOc().onChunkUnload()
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        if (Other.ocLoaded) getOc().readFromNBT(nbt)
    }

    override fun writeToNBT(nbt: CompoundTag): CompoundTag {
        super.writeToNBT(nbt)
        if (Other.ocLoaded) getOc().writeToNBT(nbt)
        return nbt
    }

    init {
        if (Other.ocLoaded) getOc().constructor()
    }
}

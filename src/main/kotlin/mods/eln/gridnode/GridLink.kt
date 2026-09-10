package mods.eln.gridnode

import mods.eln.misc.stackFromNbt

import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.INBTTReady
import mods.eln.misc.UserError
import mods.eln.node.NodeManager
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag

import java.util.HashSet
import java.util.Optional
import mods.eln.misc.writeToNBT

/**
 * Created by svein on 23/08/15.
 */
class GridLink : INBTTReady {

    internal var a = Coordinate()
    internal var b = Coordinate()

    internal var connected = false
    // Drop this if the link is broken.
    lateinit internal var cable: ItemStack
    lateinit private var `as`: Direction
    lateinit private var bs: Direction
    private var ae = Optional.empty<GridElement>()
    private var be = Optional.empty<GridElement>()
    private var ab: ElectricalConnection? = null
    private var rs = MnaConst.highImpedance
    internal var spanThermal: WireSpanThermal? = null

    constructor(a: Coordinate, b: Coordinate, `as`: Direction, bs: Direction, cable: ItemStack, rs: Double) {
        this.rs = rs
        this.a = a
        this.b = b
        this.`as` = `as`
        this.bs = bs
        this.cable = cable
    }

    constructor(nbt: CompoundTag, str: String) {
        readFromNBT(nbt, str)
    }

    fun elementA(): GridElement {
        if (!ae.isPresent) {
            ae = Optional.of(getElementFromCoordinate(a)!!)
        }
        return ae.get()
    }

    fun elementB(): GridElement {
        if (!be.isPresent) {
            be = Optional.of(getElementFromCoordinate(b)!!)
        }
        return be.get()
    }

    fun connect(): Boolean {
        val a = getElementFromCoordinate(this.a)
        val b = getElementFromCoordinate(this.b)

        if (a == null || b == null || connected) {
            return false
        }

        // Add link to simulator.
        val aLoad = a.getGridElectricalLoad(`as`)
        val bLoad = b.getGridElectricalLoad(bs)
        if (aLoad == null || bLoad == null) {
            throw UserError("Invalid connection side")
        }
        assert(ab == null)
        val utility = Eln.sixNodeItem.getDescriptor(cable) as? UtilityCableDescriptor
        ab = if (utility != null) WireSpanConnection(aLoad, bLoad, rs) else ElectricalConnection(aLoad, bLoad)
        Eln.simulator.addElectricalComponent(ab)
        if (utility == null) ab!!.resistance = rs
        if (utility != null) {
            val thermal = spanThermal ?: WireSpanThermal(utility, utility.getRemainingLengthMeters(cable)).also { spanThermal = it }
            thermal.connect(ab as WireSpanConnection,
                { (a.getAmbientTemperatureCelsius() + b.getAmbientTemperatureCelsius()) * .5 },
                { onBreakElement() }) // destroyed conductor: no intact spool refund
        }

        // Add link to link lists.
        if (!a.gridLinkList.contains(this)) a.gridLinkList.add(this)
        if (!b.gridLinkList.contains(this)) b.gridLinkList.add(this)
        updateElement(a)
        updateElement(b)

        connected = true
        return true
    }

    private fun updateElement(e: GridElement) {
        e.updateIdealRenderAngle()
        // Need to also publish everything connected to this.
        val s = HashSet<GridElement>()
        s.add(e)
        for (link in e.gridLinkList) {
            s.add(link.elementA())
            s.add(link.elementB())
        }
        for (element in s) {
            element.needPublish()
        }
    }

    fun disconnect() {
        if (!connected)
            return

        val a = getElementFromCoordinate(this.a)
        val b = getElementFromCoordinate(this.b)

        spanThermal?.disconnect()
        Eln.simulator.removeElectricalComponent(ab)
        ab?.breakConnection()
        ab = null

        a?.let { updateElement(it) }
        b?.let { updateElement(it) }

        connected = false
    }

    private fun links(a: GridElement, b: GridElement): Boolean {
        if (this.a == a.coordinate()) {
            return this.b == b.coordinate()
        }
        if (this.a == b.coordinate()) {
            return this.b == a.coordinate()
        }
        return false
    }

    override fun readFromNBT(nbt: CompoundTag, str: String) {
        a.readFromNBT(nbt, str + "a")
        b.readFromNBT(nbt, str + "b")
        `as` = Direction.readFromNBT(nbt, str + "as")!!
        bs = Direction.readFromNBT(nbt, str + "bs")!!
        rs = nbt.getDouble(str + "rs")
        cable = stackFromNbt(nbt)
        // Migrate the old saved, arbitrary resistance using the actual paid length in the cable stack.
        (Eln.sixNodeItem.getDescriptor(cable) as? UtilityCableDescriptor)?.let {
            rs = it.resistanceOhms(it.getRemainingLengthMeters(cable))
            spanThermal = WireSpanThermal(it, it.getRemainingLengthMeters(cable)).also { thermal -> thermal.read(nbt) }
        }
    }

    override fun writeToNBT(nbt: CompoundTag, str: String) {
        a.writeToNBT(nbt, str + "a")
        b.writeToNBT(nbt, str + "b")
        `as`.writeToNBT(nbt, str + "as")
        bs.writeToNBT(nbt, str + "bs")
        nbt.putDouble(str + "rs", rs)
        cable.writeToNBT(nbt)
        spanThermal?.write(nbt)
    }

    fun selfDestroy() {
        onBreakElement()
    }

    fun onBreakElement(): ItemStack {
        val a = getElementFromCoordinate(this.a)
        val b = getElementFromCoordinate(this.b)
        a?.gridLinkList?.remove(this)
        b?.gridLinkList?.remove(this)
        disconnect()
        val thermal = spanThermal
        if (thermal != null && thermal.insulationDamaged) {
            thermal.descriptor.meltedDescriptor?.let {
                cable = it.newItemStack().also { stack -> it.setRemainingLengthMeters(stack, thermal.meters) }
            }
        }
        return cable
    }

    fun getSide(gridElement: GridElement): Direction {
        if (gridElement === elementA()) {
            return `as`
        } else {
            return bs
        }
    }

    fun getOtherElement(gridElement: GridElement): GridElement {
        if (gridElement === elementA()) {
            return elementB()
        } else {
            return elementA()
        }
    }

    companion object {

        fun resistanceForCable(cable: ElectricalCableDescriptor, meters: Int): Double =
            if (cable is UtilityCableDescriptor) cable.resistanceOhms(meters.toDouble()) else cable.electricalRs * meters

        fun getElementFromCoordinate(coord: Coordinate?): GridElement? {
            if (coord == null) return null
            val element = NodeManager.instance!!.getTransparentNodeFromCoordinate(coord)
            if (element is GridElement) {
                return element
            } else {
                return null
            }
        }

        /** Shared by click-to-link and direct callers. Validate before changing either endpoint. */
        internal fun validateNewLink(a: GridElement, b: GridElement, fromSide: Direction, toSide: Direction,
                                     cable: ElectricalCableDescriptor, cableLength: Int) {
            if (!(a.transparentNodeDescriptor as GridDescriptor).acceptsGridCable(cable) ||
                !(b.transparentNodeDescriptor as GridDescriptor).acceptsGridCable(cable)) {
                throw UserError(tr("Grid links require an intact power cable rated at least %1$ V", GridCablePolicy.MINIMUM_VOLTAGE))
            }
            if (a === b || cableLength <= 0) throw UserError(tr("Invalid grid cable span"))
            if (a.getGridElectricalLoad(fromSide) == null || b.getGridElectricalLoad(toSide) == null) {
                throw UserError(tr("Select a grid terminal, not the device body"))
            }
        }

        internal fun linkStack(cable: ElectricalCableDescriptor, cableLength: Int, supplied: ItemStack?): ItemStack {
            if (cableLength <= 0) throw UserError(tr("Invalid grid cable span"))
            val stack = supplied?.copy() ?: when (cable) {
                is UtilityCableDescriptor -> cable.newItemStack(1).also { cable.setRemainingLengthMeters(it, cableLength.toDouble()) }
                else -> cable.newItemStack(cableLength)
            }
            if (stack.isEmpty || ElectricalCableDescriptor.getDescriptor(stack) !== cable) {
                throw UserError(tr("Grid cable item does not match the selected cable"))
            }
            if (cable is UtilityCableDescriptor) {
                val meters = cable.getRemainingLengthMeters(stack)
                if (stack.count != 1 || !meters.isFinite() || kotlin.math.abs(meters - cableLength) > UtilityCableDescriptor.LENGTH_METERS_EPSILON) {
                    throw UserError(tr("Grid cable item does not match the paid length"))
                }
            } else if (stack.count != cableLength) {
                throw UserError(tr("Grid cable item does not match the paid length"))
            }
            return stack
        }

        fun addLink(a: GridElement, b: GridElement, `as`: Direction, bs: Direction, cable: ElectricalCableDescriptor, cableLength: Int, cableStack: ItemStack? = null) {
            validateNewLink(a, b, `as`, bs, cable, cableLength)
            // Check if these two nodes are already linked.
            (a.gridLinkList + b.gridLinkList)
                .filter { it.links(a, b) }
                .forEach { throw UserError("Already Connected") }

            val linkStack = linkStack(cable, cableLength, cableStack)

            // Makin' a Link. Where'd Zelda go?
            val link = GridLink(
                    a.coordinate(), b.coordinate(), `as`, bs, linkStack,
                    resistanceForCable(cable, cableLength))
            if (!link.connect()) throw UserError(tr("Grid endpoint is no longer available"))
        }
    }
}

/** A span is additional wire, not merely the two pole contacts. Keep it across Rs notifications. */
internal class WireSpanConnection(private val from: ElectricalLoad, private val to: ElectricalLoad,
                                  var spanOhms: Double) : ElectricalConnection(from, to) {
    override fun notifyRsChange() {
        resistance = spanOhms + from.serialResistance + to.serialResistance
    }
}

package mods.eln.node.transparent

import mods.eln.Eln
import mods.eln.ghost.GhostObserver
import mods.eln.integration.waila.TransparentNodeWailaEntry
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.Direction.Companion.fromInt
import mods.eln.misc.INBTTReady
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.misc.Utils.readFromNBT
import mods.eln.misc.Utils.writeToNBT
import mods.eln.environment.BiomeClimateService
import mods.eln.node.INodeElement
import mods.eln.node.NodeConnectionEndpoint
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.SignalLoadSupport
import mods.eln.sim.ThermalConnection
import mods.eln.sim.ThermalLoad
import mods.eln.sim.mna.component.Component
import mods.eln.sim.mna.state.State
import mods.eln.sim.nbt.NbtThermalLoad
import mods.eln.sim.process.destruct.ThermalLoadWatchDog
import mods.eln.sound.IPlayer
import mods.eln.sound.SoundCommand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import mods.eln.fluid.ISidedFluidHandler
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.*
import mods.eln.misc.writeToNBT
import mods.eln.misc.tagCompound

abstract class TransparentNodeElement(@JvmField var node: TransparentNode?, @JvmField var transparentNodeDescriptor: TransparentNodeDescriptor) : GhostObserver, IPlayer, INodeElement {
    @JvmField
    var slowProcessList = ArrayList<IProcess>(4)
    var slowPreProcessList = ArrayList<IProcess>(4)
    var slowPostProcessList = ArrayList<IProcess>(4)
    @JvmField
    var electricalProcessList = ArrayList<IProcess>(4)
    @JvmField
    var electricalComponentList = ArrayList<Component>(4)
    private var signalReadComponentList = ArrayList<Component>(0)
    @JvmField
    var electricalLoadList = ArrayList<State>(4)
    @JvmField
    var thermalFastProcessList = ArrayList<IProcess>(4)
    var thermalConnectionList = ArrayList<ThermalConnection>(4)
    @JvmField
    var thermalLoadList = ArrayList<NbtThermalLoad>(4)
    open val descriptor: TransparentNodeDescriptor?
        get() = transparentNodeDescriptor

    @Throws(IOException::class)
    protected fun serialiseItemStack(stream: DataOutputStream?, stack: ItemStack?) {
        Utils.serialiseItemStack(stream!!, stack)
    }

    protected fun describeSimOwner(): String {
        val coord = node?.coordinate
        val coordStr = coord?.toString() ?: "unknown"
        return "${javaClass.simpleName}@$coordStr"
    }

    open fun connectJob() {
        // If we are about to destruct ourselves, do not add any elements to the simulation anymore.
        if (node != null && node!!.isDestructing) return
        val ownerTag = describeSimOwner()
        electricalComponentList.forEach { it.setOwner(ownerTag) }
        signalReadComponentList = SignalLoadSupport.createReadComponents(electricalLoadList)
        signalReadComponentList.forEach { it.setOwner(ownerTag) }
        electricalLoadList.forEach { it.setOwner(ownerTag) }
        val coord = node?.coordinate
        if (coord != null) {
            thermalLoadList.forEach { it.setSimCoordinate(coord.dimension, coord.x, coord.y, coord.z) }
        }
        Eln.simulator.addAllSlowProcess(slowProcessList)
        for (p in slowPreProcessList) Eln.simulator.addSlowPreProcess(p)
        for (p in slowPostProcessList) Eln.simulator.addSlowPostProcess(p)
        Eln.simulator.addAllElectricalComponent(electricalComponentList)
        Eln.simulator.addAllElectricalComponent(signalReadComponentList)
        for (load in electricalLoadList) Eln.simulator.addElectricalLoad(load)
        Eln.simulator.addAllElectricalProcess(electricalProcessList)
        Eln.simulator.addAllThermalConnection(thermalConnectionList)
        for (load in thermalLoadList) Eln.simulator.addThermalLoad(load)
        Eln.simulator.addAllThermalFastProcess(thermalFastProcessList)
    }

    open fun disconnectJob() {
        for (load in thermalLoadList) load.clearSimCoordinate()
        Eln.simulator.removeAllSlowProcess(slowProcessList)
        for (p in slowPreProcessList) Eln.simulator.removeSlowPreProcess(p)
        for (p in slowPostProcessList) Eln.simulator.removeSlowPostProcess(p)
        Eln.simulator.removeAllElectricalComponent(electricalComponentList)
        Eln.simulator.removeAllElectricalComponent(signalReadComponentList)
        signalReadComponentList.clear()
        for (load in electricalLoadList) Eln.simulator.removeElectricalLoad(load)
        Eln.simulator.removeAllElectricalProcess(electricalProcessList)
        Eln.simulator.removeAllThermalConnection(thermalConnectionList)
        for (load in thermalLoadList) Eln.simulator.removeThermalLoad(load)
        Eln.simulator.removeAllThermalFastProcess(thermalFastProcessList)
    }

    @JvmField
    var front: Direction = Direction.XN
    @JvmField
    var grounded = true
    open fun onGroundedChangedByClient() {
        needPublish()
    }

    fun networkUnserialize(stream: DataInputStream, @Suppress("UNUSED_PARAMETER") player: ServerPlayer?): Byte {
        return networkUnserialize(stream)
    }

    open fun networkUnserialize(stream: DataInputStream): Byte {
        var readed: Byte
        try {
            return when (stream.readByte().also { readed = it }) {
                unserializeGroundedId -> {
                    grounded = stream.readByte().toInt() != 0
                    onGroundedChangedByClient()
                    unserializeNulldId
                }
                else -> readed
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return unserializeNulldId
    }

    val lightValue: Int
        get() = 0

    open fun hasGui(): Boolean {
        return false
    }

    open val inventory: Container?
        get() = null

    fun preparePacketForClient(stream: DataOutputStream?) {
        node!!.preparePacketForClient(stream!!)
    }

    fun sendIdToAllClient(id: Byte) {
        val bos = ByteArrayOutputStream(64)
        val packet = DataOutputStream(bos)
        preparePacketForClient(packet)
        try {
            packet.writeByte(id.toInt())
        } catch (e: IOException) {
            e.printStackTrace()
        }
        sendPacketToAllClient(bos)
    }

    fun sendStringToAllClient(id: Byte, str: String) {
        val bos = ByteArrayOutputStream(64)
        val packet = DataOutputStream(bos)
        preparePacketForClient(packet)
        try {
            packet.writeByte(id.toInt())
            packet.writeUTF(str)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        sendPacketToAllClient(bos)
    }

    private fun sendPacketToAllClient(bos: ByteArrayOutputStream) {
        node!!.sendPacketToAllClient(bos)
    }

    open fun newContainer(side: Direction, player: Player): AbstractContainerMenu? {
        return null
    }

    open fun getFluidHandler(): ISidedFluidHandler? = null

    open fun onNeighborBlockChange() {
        checkCanStay(false)
    }

    fun checkCanStay(@Suppress("UNUSED_PARAMETER") onCreate: Boolean) {
        var needDestroy = false
        if (transparentNodeDescriptor.mustHaveFloor()) {
            if (!node!!.isBlockOpaque(Direction.YN)) needDestroy = true
        }
        if (transparentNodeDescriptor.mustHaveCeiling()) {
            if (!node!!.isBlockOpaque(Direction.YP)) needDestroy = true
        }
        if (transparentNodeDescriptor.mustHaveWallFrontInverse()) {
            if (!node!!.isBlockOpaque(front.inverse)) needDestroy = true
        }
        if (transparentNodeDescriptor.mustHaveWall()) {
            var wall = false
            if (node!!.isBlockOpaque(Direction.XN)) wall = true
            if (node!!.isBlockOpaque(Direction.XP)) wall = true
            if (node!!.isBlockOpaque(Direction.ZN)) wall = true
            if (node!!.isBlockOpaque(Direction.ZP)) wall = true
            if (!wall) needDestroy = true
        }
        if (needDestroy) {
            selfDestroy()
        }
    }

    open fun selfDestroy() {
        node!!.physicalSelfDestruction(0f)
    }

    override fun stop(uuid: Int) {
        val bos = ByteArrayOutputStream(8)
        val stream = DataOutputStream(bos)
        try {
            stream.writeByte(Eln.packetDestroyUuid.toInt())
            stream.writeInt(uuid)
            sendPacketToAllClient(bos)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    open fun onBreakElement() {
        if (useUuid()) stop(uuid)
        if (transparentNodeDescriptor.ghostGroup != null) {
            Eln.ghostManager.removeObserver(node!!.coordinate)
            Eln.ghostManager.removeGhostAndBlockWithObserver(node!!.coordinate)
        }
        node!!.dropInventory(inventory)
        node!!.dropElement(node!!.removedByPlayer)
    }

    val dropItemStack: ItemStack
        get() {
            val itemStack = Eln.transparentNodeItem.getDescriptor(node!!.elementId)?.newItemStack(1) ?: return ItemStack.EMPTY
            itemStack.tagCompound = getItemStackNBT()
            return itemStack
        }

    open fun getElectricalLoad(side: Direction, lrdu: LRDU): ElectricalLoad? = null
    open fun getElectricalLoad(side: Direction, lrdu: LRDU, remoteEndpoint: NodeConnectionEndpoint): ElectricalLoad? {
        return getElectricalLoad(side, lrdu)
    }
    open fun getThermalLoad(side: Direction, lrdu: LRDU): ThermalLoad? = null
    open fun getConnectionMask(side: Direction, lrdu: LRDU): Int = 0

    fun getAdjacentConnectionEndpoint(side: Direction, lrdu: LRDU): NodeConnectionEndpoint? {
        return node?.findAdjacentConnectionEndpoint(side, lrdu)
    }

    fun getAdjacentConnectionElement(side: Direction, lrdu: LRDU): Any? {
        return getAdjacentConnectionEndpoint(side, lrdu)?.element
    }

    open fun multiMeterString(side: Direction): String = ""
    open fun thermoMeterString(side: Direction): String = ""

    fun getAmbientTemperatureCelsius(): Double {
        val coord = node?.coordinate
            ?: throw IllegalStateException("Missing coordinate for ${javaClass.name} while sampling ambient temperature.")
        if (!coord.worldExist) {
            throw IllegalStateException("Level not loaded for coordinate $coord in ${javaClass.name} while sampling ambient temperature.")
        }
        val world = coord.world()
        return BiomeClimateService.sample(world, coord.x, coord.y, coord.z).temperatureCelsius
    }

    fun plotAmbientCelsius(header: String, thermalDeltaCelsius: Double): String {
        return Utils.plotCelsius(header, thermalDeltaCelsius + getAmbientTemperatureCelsius())
    }

    fun ambientAwareThermalWatchdog(watchdog: ThermalLoadWatchDog): ThermalLoadWatchDog {
        return watchdog.setAmbientTemperatureProvider { getAmbientTemperatureCelsius() }
    }

    open fun networkSerialize(stream: DataOutputStream) {
        try {
            stream.writeByte(front.int + if (grounded) 8 else 0)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun initializeFromThat(front: Direction, @Suppress("UNUSED_PARAMETER") entityLiving: LivingEntity?, itemStackNbt: CompoundTag?) {
        this.front = front
        readItemStackNBT(itemStackNbt)
        initialize()
    }

    abstract fun initialize()
    open fun readItemStackNBT(nbt: CompoundTag?) {}
    open fun getItemStackNBT(): CompoundTag? {return null}

    open fun onBlockActivated(player: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean = false

    open fun readFromNBT(nbt: CompoundTag) {
        val inv = inventory
        if (inv != null) {
            readFromNBT(nbt, "inv", inv)
        }
        for (electricalLoad in electricalLoadList) {
            if (electricalLoad is INBTTReady) (electricalLoad as INBTTReady).readFromNBT(nbt, "")
        }
        for (thermalLoad in thermalLoadList) {
            thermalLoad.readFromNBT(nbt, "")
        }
        for (c in electricalComponentList) if (c is INBTTReady) (c as INBTTReady).readFromNBT(nbt, "")
        for (process in slowProcessList) {
            if (process is INBTTReady) (process as INBTTReady).readFromNBT(nbt, "")
        }
        for (process in electricalProcessList) {
            if (process is INBTTReady) (process as INBTTReady).readFromNBT(nbt, "")
        }
        for (process in thermalFastProcessList) {
            if (process is INBTTReady) (process as INBTTReady).readFromNBT(nbt, "")
        }
        val b = nbt.getByte("others")
        front = fromInt(b.toInt() and 0x7)!!
        grounded = b.toInt() and 8 != 0
    }

    open fun writeToNBT(nbt: CompoundTag) {
        val inv = inventory
        if (inv != null) {
            writeToNBT(nbt, "inv", inv)
        }
        for (electricalLoad in electricalLoadList) {
            if (electricalLoad is INBTTReady) (electricalLoad as INBTTReady).writeToNBT(nbt, "")
        }
        for (thermalLoad in thermalLoadList) {
            thermalLoad.writeToNBT(nbt, "")
        }
        for (c in electricalComponentList) if (c is INBTTReady) (c as INBTTReady).writeToNBT(nbt, "")
        for (process in slowProcessList) {
            if (process is INBTTReady) (process as INBTTReady).writeToNBT(nbt, "")
        }
        for (process in electricalProcessList) {
            if (process is INBTTReady) (process as INBTTReady).writeToNBT(nbt, "")
        }
        for (process in thermalFastProcessList) {
            if (process is INBTTReady) (process as INBTTReady).writeToNBT(nbt, "")
        }
        nbt.putByte("others", (front.int + if (grounded) 8 else 0).toByte())
    }

    override fun reconnect() {
        node!!.reconnect()
    }

    override fun needPublish() {
        node!!.needPublish = true
    }

    fun connect() {
        node!!.connect()
    }

    fun disconnect() {
        node!!.disconnect()
    }

    override fun inventoryChange(inventory: Container?) {}

    open fun getLightOpacity(): Float = 0f

    override val ghostObserverCoordonate = node!!.coordinate

    override fun ghostDestroyed(UUID: Int) {
        if (UUID == transparentNodeDescriptor.ghostGroupUuid) {
            selfDestroy()
        }
    }

    override fun ghostBlockActivated(UUID: Int, entityPlayer: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean {
        return if (UUID == transparentNodeDescriptor.ghostGroupUuid) {
            node!!.onBlockActivated(entityPlayer, side, vx, vy, vz)
        } else false
    }

    fun world(): Level {
        return node!!.coordinate.world()
    }

    fun coordinate(): Coordinate {
        return node!!.coordinate
    }

    private var uuid = 0
    fun getUuid(): Int {
        if (uuid == 0) {
            uuid = Utils.uuid
        }
        return uuid
    }

    fun useUuid(): Boolean {
        return uuid != 0
    }

    override fun play(cmd: SoundCommand) {
        cmd.addUuid(getUuid())
        cmd.set(node!!.coordinate)
        cmd.play()
    }

    open fun unload() {}

    open fun getWaila(): Map<String, String> {
        val wailaList: MutableMap<String, String> = HashMap()
        wailaList["Info"] = multiMeterString(front)
        return wailaList
    }

    open fun getWailaEntries(): List<TransparentNodeWailaEntry> = getWaila().map { (label, value) ->
        TransparentNodeWailaEntry(label, value.split('\n'))
    }

    companion object {
        const val unserializeGroundedId: Byte = -127
        const val unserializeNulldId: Byte = -128
    }

    init {
        if (transparentNodeDescriptor.ghostGroup != null) Eln.ghostManager.addObserver(this)
    }
}

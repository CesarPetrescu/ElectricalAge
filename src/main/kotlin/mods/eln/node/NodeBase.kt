@file:Suppress("NAME_SHADOWING")
package mods.eln.node

import mods.eln.misc.Utils.println
import mods.eln.misc.Utils.sendMessage
import mods.eln.misc.Coordinate
import net.minecraft.server.level.ServerPlayer
import mods.eln.misc.LRDUCubeMask
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import mods.eln.Eln
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.player.Player
import net.minecraft.nbt.CompoundTag
import mods.eln.sound.SoundCommand
import mods.eln.GuiHandler
import mods.eln.misc.LRDU
import mods.eln.sim.ThermalLoad
import mods.eln.sim.ElectricalLoad
import mods.eln.node.six.SixNode
import mods.eln.sim.IProcess
import mods.eln.misc.INBTTReady
import java.io.IOException
import kotlin.jvm.JvmOverloads
import net.minecraft.server.MinecraftServer
import net.minecraftforge.fml.common.FMLCommonHandler
import mods.eln.ServerKeyHandler
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.Container
import net.minecraft.world.level.block.Blocks
import mods.eln.ghost.GhostBlock
import mods.eln.misc.Direction
import mods.eln.misc.Utils
import mods.eln.sim.ElectricalConnection
import mods.eln.sim.ThermalConnection
import net.minecraft.world.level.block.Block
import net.minecraft.world.entity.Entity
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.ArrayList
import kotlin.experimental.or
import mods.eln.misc.getBlock
import mods.eln.misc.setBlockToAir
import mods.eln.misc.isNothing
import mods.eln.misc.isReplaceable
import mods.eln.misc.rand
import mods.eln.misc.writeToNBT

abstract class NodeBase {
    var neighborOpaque: Byte = 0
    var neighborWrapable: Byte = 0
    @JvmField
    var coordinate: Coordinate
    @JvmField
    var nodeConnectionList = ArrayList<NodeConnection>(4)
    private var initialized = false
    private var isAdded = false
    var needPublish = false

    // public static boolean canBePlacedOn(ItemStack itemStack,Direction side)
    open fun mustBeSaved(): Boolean {
        return true
    }

    open val blockMetadata: Int
        get() = 0

    open fun networkUnserialize(stream: DataInputStream, player: ServerPlayer?) {}
    fun notifyNeighbor() {
        coordinate.world().updateNeighborsAt(coordinate.pos, coordinate.block, false)
    }

    //public abstract Block getBlock();
    abstract val nodeUuid: String?
    @JvmField
    var lrduCubeMask = LRDUCubeMask()
    fun neighborBlockRead() {
        val vector = IntArray(3)
        val world = coordinate.world()
        neighborOpaque = 0
        neighborWrapable = 0
        for (direction in Direction.values()) {
            vector[0] = coordinate.x
            vector[1] = coordinate.y
            vector[2] = coordinate.z
            direction.applyTo(vector, 1)
            val b = world.getBlock(vector[0], vector[1], vector[2])
            neighborOpaque = neighborOpaque or (1 shl direction.int).toByte()
            if (isBlockWrappable(b, world, coordinate.x, coordinate.y, coordinate.z)) neighborWrapable = neighborWrapable or (1 shl direction.int).toByte()
        }
    }

    open fun hasGui(side: Direction): Boolean {
        return false
    }

    open fun onNeighborBlockChange() {
        neighborBlockRead()
        if (isAdded) {
            reconnect()
        }
    }

    fun isBlockWrappable(direction: Direction): Boolean {
        return neighborWrapable.toInt() shr direction.int and 1 != 0
    }

    fun isBlockOpaque(direction: Direction): Boolean {
        return neighborOpaque.toInt() shr direction.int and 1 != 0
    }

    var isDestructing = false
    fun physicalSelfDestruction(explosionStrength: Float) {
        var explosionStrength = explosionStrength
        if (isDestructing) return
        isDestructing = true
        if (!Eln.config.getBooleanOrElse("gameplay.hazards.explosionsEnabled", false)) explosionStrength = 0f
        disconnect()
        coordinate.world().setBlockToAir(coordinate.x, coordinate.y, coordinate.z)
        NodeManager.instance!!.removeNode(this)
        if (explosionStrength != 0f) {
            coordinate.world().createExplosion(null as Entity?, coordinate.x.toDouble(), coordinate.y.toDouble(), coordinate.z.toDouble(), explosionStrength, true)
        }
    }

    fun onBlockPlacedBy(coordinate: Coordinate, front: Direction, entityLiving: LivingEntity?, itemStack: ItemStack?) {
        this.coordinate = coordinate
        neighborBlockRead()
        NodeManager.instance!!.addNode(this)
        initializeFromThat(front, entityLiving, itemStack)
        if (!itemStack.isNothing()) println("Node::constructor( meta = " + itemStack.itemDamage + ")")
    }

    abstract fun initializeFromThat(front: Direction, entityLiving: LivingEntity?, itemStack: ItemStack?)

    fun getNeighbor(direction: Direction): NodeBase? {
        val position = IntArray(3)
        position[0] = coordinate.x
        position[1] = coordinate.y
        position[2] = coordinate.z
        direction.applyTo(position, 1)
        val nodeCoordinate = Coordinate(position[0], position[1], position[2], coordinate.dimension)
        return NodeManager.instance!!.getNodeFromCoordonate(nodeCoordinate)
    }

    open fun onBreakBlock() {
        isDestructing = true
        disconnect()
        NodeManager.instance!!.removeNode(this)
        println("Node::onBreakBlock()")
    }

    open fun onBlockActivated(entityPlayer: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean {
        if (!entityPlayer.level.isClientSide && !entityPlayer.mainHandItem.isNothing()) {
            val equipped = entityPlayer.mainHandItem
            if (Eln.multiMeterElement.checkSameItemStack(equipped)) {
                val str = multiMeterString(side)
                addMeterChatMessages(entityPlayer, str)
                return true
            }
            if (Eln.thermometerElement.checkSameItemStack(equipped)) {
                val str = thermoMeterString(side)
                addMeterChatMessages(entityPlayer, str)
                return true
            }
            if (Eln.allMeterElement.checkSameItemStack(equipped)) {
                val str1 = multiMeterString(side)
                val str2 = thermoMeterString(side)
                val str = listOf(str1, str2).filter { it.isNotEmpty() }.joinToString("\n")
                if (str.isNotEmpty()) addMeterChatMessages(entityPlayer, str)
                return true
            }
            if (Eln.configCopyToolElement.checkSameItemStack(equipped)) {
                if (!equipped.hasTagCompound()) {
                    equipped.tagCompound /* TODO(components) */ = CompoundTag()
                }
                val act: String
                var snd = beepError
                if (entityPlayer.isShiftKeyDown) {
                    if (writeConfigTool(side, equipped.tagCompound /* TODO(components) */, entityPlayer)) snd = beepDownloaded
                    act = "write"
                } else {
                    if (readConfigTool(side, equipped.tagCompound /* TODO(components) */, entityPlayer)) {
                        needPublish()
                        snd = beepUploaded
                    }
                    act = "read"
                }
                snd.set(
                    entityPlayer.x,
                    entityPlayer.y,
                    entityPlayer.z,
                    entityPlayer.level
                ).play()
                println(String.format("NB.oBA: act %s data %s", act, equipped.tagCompound /* TODO(components) */.toString()))
                return true
            }
        }
        if (hasGui(side)) {
            GuiHandler.open(entityPlayer, GuiHandler.nodeBaseOpen + side.int, coordinate.world(), coordinate.x, coordinate.y, coordinate.z)
            return true
        }
        return false
    }

    private fun addMeterChatMessages(entityPlayer: Player, text: String) {
        text.split('\n')
            .map { it.trimEnd('\r') }
            .filter { it.isNotEmpty() }
            .forEach { sendMessage(entityPlayer, it) }
    }

    fun reconnect() {
        disconnect()
        connect()
    }

    abstract fun getSideConnectionMask(side: Direction, lrdu: LRDU): Int
    abstract fun getThermalLoad(side: Direction, lrdu: LRDU, mask: Int): ThermalLoad?
    abstract fun getElectricalLoad(side: Direction, lrdu: LRDU, mask: Int): ElectricalLoad?

    open fun getElectricalLoad(side: Direction, lrdu: LRDU, mask: Int, remoteEndpoint: NodeConnectionEndpoint): ElectricalLoad? {
        return getElectricalLoad(side, lrdu, mask)
    }

    open fun getConnectionEndpoint(side: Direction, lrdu: LRDU): NodeConnectionEndpoint {
        return NodeConnectionEndpoint(this, side, lrdu, this, side, lrdu)
    }

    fun findAdjacentConnectionEndpoint(side: Direction, lrdu: LRDU): NodeConnectionEndpoint? {
        findWrappedAdjacentConnectionEndpoint(side, lrdu)?.let { return it }
        return findDirectAdjacentConnectionEndpoint(side, lrdu)
    }

    private fun findWrappedAdjacentConnectionEndpoint(side: Direction, lrdu: LRDU): NodeConnectionEndpoint? {
        if (!isBlockWrappable(side)) return null
        val emptyBlockCoord = intArrayOf(coordinate.x, coordinate.y, coordinate.z)
        side.applyTo(emptyBlockCoord, 1)

        val elementSide = side.applyLRDU(lrdu)
        val otherBlockCoord = intArrayOf(emptyBlockCoord[0], emptyBlockCoord[1], emptyBlockCoord[2])
        elementSide.applyTo(otherBlockCoord, 1)

        val otherNode = NodeManager.instance!!.getNodeFromCoordonate(
            Coordinate(otherBlockCoord[0], otherBlockCoord[1], otherBlockCoord[2], coordinate.dimension)
        ) ?: return null
        val otherDirection = elementSide.inverse
        val otherLRDU = otherDirection.getLRDUGoingTo(side)?.inverse() ?: return null
        return otherNode.getConnectionEndpoint(otherDirection, otherLRDU)
    }

    private fun findDirectAdjacentConnectionEndpoint(side: Direction, lrdu: LRDU): NodeConnectionEndpoint? {
        val otherNode = getNeighbor(side) ?: return null
        if (!otherNode.isAdded) return null
        return otherNode.getConnectionEndpoint(side.inverse, lrdu.inverseIfLR())
    }

    open fun checkCanStay(onCreate: Boolean) {}
    open fun connectJob() {
        // EXTERNAL OTHERS SIXNODE
        run {
            val emptyBlockCoord = IntArray(3)
            val otherBlockCoord = IntArray(3)
            for (direction in Direction.values()) {
                if (isBlockWrappable(direction)) {
                    emptyBlockCoord[0] = coordinate.x
                    emptyBlockCoord[1] = coordinate.y
                    emptyBlockCoord[2] = coordinate.z
                    direction.applyTo(emptyBlockCoord, 1)
                    for (lrdu in LRDU.values()) {
                        val elementSide = direction.applyLRDU(lrdu)
                        otherBlockCoord[0] = emptyBlockCoord[0]
                        otherBlockCoord[1] = emptyBlockCoord[1]
                        otherBlockCoord[2] = emptyBlockCoord[2]
                        elementSide.applyTo(otherBlockCoord, 1)
                        val otherNode = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(otherBlockCoord[0], otherBlockCoord[1], otherBlockCoord[2], coordinate.dimension))
                            ?: continue
                        val otherDirection = elementSide.inverse
                        val otherLRDU = otherDirection.getLRDUGoingTo(direction)!!.inverse()
                        if (this is SixNode || otherNode is SixNode) {
                            tryConnectTwoNode(this, direction, lrdu, otherNode, otherDirection, otherLRDU)
                        }
                    }
                }
            }
        }
        run {
            for (dir in Direction.values()) {
                val otherNode = getNeighbor(dir)
                if (otherNode != null && otherNode.isAdded) {
                    for (lrdu in LRDU.values()) {
                        tryConnectTwoNode(this, dir, lrdu, otherNode, dir.inverse, lrdu.inverseIfLR())
                    }
                }
            }
        }
    }

    open fun disconnectJob() {
        for (c in nodeConnectionList) {
            if (c.N1 !== this) {
                c.N1.nodeConnectionList.remove(c)
                c.N1.needPublish = true
                c.N1.lrduCubeMask[c.dir1, c.lrdu1] = false
            }
            if (c.N2 !== this) {
                c.N2.nodeConnectionList.remove(c)
                c.N2.needPublish = true
                c.N2.lrduCubeMask[c.dir2, c.lrdu2] = false
            }
            c.destroy()
        }
        lrduCubeMask.clear()
        nodeConnectionList.clear()
    }

    open fun externalDisconnect(side: Direction?, lrdu: LRDU?) {}
    open fun newConnectionAt(connection: NodeConnection?, isA: Boolean) {}
    open fun connectInit() {
        lrduCubeMask.clear()
        nodeConnectionList.clear()
    }

    fun connect() {
        if (isAdded) {
            disconnect()
        }
        connectInit()
        connectJob()
        isAdded = true
        needPublish = true
    }

    fun disconnect() {
        if (!isAdded) {
            // println("Node destroy error already destroy")
            return
        }
        disconnectJob()
        isAdded = false
    }

    open fun nodeAutoSave(): Boolean {
        return true
    }

    open fun readFromNBT(nbt: CompoundTag) {
        coordinate.readFromNBT(nbt, "c")
        neighborOpaque = nbt.getByte("NBOpaque")
        neighborWrapable = nbt.getByte("NBWrap")
        initialized = true
    }

    open fun writeToNBT(nbt: CompoundTag) {
        coordinate.writeToNBT(nbt, "c")
        nbt.putByte("NBOpaque", neighborOpaque)
        nbt.putByte("NBWrap", neighborWrapable)
    }

    open fun multiMeterString(side: Direction): String {
        return ""
    }

    open fun thermoMeterString(side: Direction): String {
        return ""
    }

    open fun readConfigTool(side: Direction?, tag: CompoundTag?, invoker: Player?): Boolean {
        return false
    }

    open fun writeConfigTool(side: Direction?, tag: CompoundTag?, invoker: Player?): Boolean {
        return false
    }

    private fun isINodeProcess(process: IProcess): Boolean {
        for (c in process.javaClass.interfaces) {
            if (c == INBTTReady::class.java) return true
        }
        return false
    }

    @JvmField
    var needNotify = false
    open fun publishSerialize(stream: DataOutputStream) {}
    fun preparePacketForClient(stream: DataOutputStream) {
        try {
            stream.writeByte(Eln.packetForClientNode.toInt())
            stream.writeInt(coordinate.x)
            stream.writeInt(coordinate.y)
            stream.writeInt(coordinate.z)
            stream.writeByte(coordinate.dimension)
            stream.writeUTF(nodeUuid!!)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun sendPacketToClient(bos: ByteArrayOutputStream?, player: ServerPlayer?) {
        Utils.sendPacketToClient(bos!!, player!!)
    }

    /**
     * Sends to every player who has this node's chunk loaded client-side - the same set that
     * holds its tile entity - optionally narrowed by [range]. The chunk-watch test is the
     * point: a player at the edge of a large render distance must still get node updates, and
     * a plain radius broadcast (what Re-Wired switched to) silently stops updating their lamps
     * and meters past 64 blocks.
     */
    @JvmOverloads
    fun sendPacketToAllClient(bos: ByteArrayOutputStream?, range: Double = 100000.0) {
        val bytes = bos ?: return
        forEachWatchingPlayer { player ->
            if (coordinate.distanceTo(player) <= range) Utils.sendPacketToClient(bytes, player)
        }
    }

    private inline fun forEachWatchingPlayer(action: (ServerPlayer) -> Unit) {
        val server = FMLCommonHandler.instance().minecraftServerInstance ?: return
        val worldServer = server.getWorld(coordinate.dimension) ?: return
        val chunkMap = worldServer.playerChunkMap
        val chunkX = coordinate.x shr 4
        val chunkZ = coordinate.z shr 4
        for (player in server.playerList.players) {
            if (player.dimension != coordinate.dimension) continue
            if (!chunkMap.isPlayerWatchingChunk(player, chunkX, chunkZ)) continue
            action(player)
        }
    }

    val publishPacket: ByteArrayOutputStream?
        get() {
            val bos = ByteArrayOutputStream(64)
            val stream = DataOutputStream(bos)
            try {
                stream.writeByte(Eln.packetNodeSingleSerialized.toInt())
                stream.writeInt(coordinate.x)
                stream.writeInt(coordinate.y)
                stream.writeInt(coordinate.z)
                stream.writeByte(coordinate.dimension)
                stream.writeUTF(nodeUuid!!)
                publishSerialize(stream)
                return bos
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }

    fun publishToAllPlayer() {
        val packet = publishPacket
        if (packet != null) {
            forEachWatchingPlayer { player -> Utils.sendPacketToClient(packet, player) }
        }
        if (needNotify) {
            needNotify = false
            notifyNeighbor()
        }
        needPublish = false
    }

    fun publishToPlayer(player: ServerPlayer?) {
        Utils.sendPacketToClient(publishPacket!!, player!!)
    }

    fun dropItem(itemStack: ItemStack?) {
        if (itemStack.isNothing()) return
        if (coordinate.world().gameRules.getBoolean("doTileDrops")) {
            val var6 = 0.7f
            val var7 = (coordinate.world().rand.nextFloat() * var6).toDouble() + (1.0f - var6).toDouble() * 0.5
            val var9 = (coordinate.world().rand.nextFloat() * var6).toDouble() + (1.0f - var6).toDouble() * 0.5
            val var11 = (coordinate.world().rand.nextFloat() * var6).toDouble() + (1.0f - var6).toDouble() * 0.5
            val var13 = ItemEntity(coordinate.world(), coordinate.x.toDouble() + var7, coordinate.y.toDouble() + var9, coordinate.z.toDouble() + var11, itemStack)
            var13.setPickupDelay(10)
            coordinate.world().addFreshEntity(var13)
        }
    }

    fun dropInventory(inventory: Container?) {
        if (inventory == null) return
        for (idx in 0 until inventory.containerSize) {
            dropItem(inventory.getItem(idx))
        }
    }

    abstract fun initializeFromNBT()
    open fun globalBoot() {}
    fun needPublish() {
        needPublish = true
    }

    open fun unload() {
        disconnect()
    }

    companion object {
        const val maskElectricalPower = 1 shl 0
        const val maskThermal = 1 shl 1
        const val maskElectricalGate = 1 shl 2
        const val maskElectricalAll = maskElectricalPower or maskElectricalGate
        const val maskElectricalInputGate = maskElectricalAll
        const val maskElectricalOutputGate = maskElectricalAll
        const val maskWire = 0
        const val maskElectricalWire = 1 shl 3
        const val maskThermalWire = maskWire + maskThermal
        const val maskSignal = 1 shl 9
        const val maskRs485 = 1 shl 10
        const val maskSignalBus = 1 shl 11
        const val maskConduit = 1 shl 12
        const val maskColorData = 0xF shl 16
        const val maskColorShift = 16
        const val maskColorCareShift = 20
        const val maskColorCareData = 1 shl 20
        const val networkSerializeUFactor = 10.0
        const val networkSerializeIFactor = 100.0
        const val networkSerializeTFactor = 10.0
        var teststatic = 0
        @JvmStatic
        fun isBlockWrappable(block: Block, w: Level?, x: Int, y: Int, z: Int): Boolean {
            if (w != null && block.isReplaceable(w, BlockPos(x, y, z))) return true
            if (block === Blocks.AIR) return true
            if (block === Eln.sixNodeBlock) return true
            if (block is GhostBlock) return true
            if (block === Blocks.TORCH) return true
            if (block === Blocks.REDSTONE_TORCH) return true
            if (block === Blocks.UNLIT_REDSTONE_TORCH) return true
            return block === Blocks.REDSTONE_WIRE
        }

        var beepUploaded = SoundCommand("eln:beep_accept_2").smallRange()
        var beepDownloaded = SoundCommand("eln:beep_accept").smallRange()
        var beepError = SoundCommand("eln:beep_error").smallRange()

        fun tryConnectTwoNode(nodeA: NodeBase, directionA: Direction, lrduA: LRDU, nodeB: NodeBase, directionB: Direction, lrduB: LRDU) {
            val mskA = nodeA.getSideConnectionMask(directionA, lrduA)
            val mskB = nodeB.getSideConnectionMask(directionB, lrduB)
            if (compareConnectionMask(mskA, mskB)) {
                val eCon: ElectricalConnection?
                val tCon: ThermalConnection?
                val nodeConnection = NodeConnection(nodeA, directionA, lrduA, nodeB, directionB, lrduB)
                nodeA.nodeConnectionList.add(nodeConnection)
                nodeB.nodeConnectionList.add(nodeConnection)
                nodeA.needPublish = true
                nodeB.needPublish = true
                nodeA.lrduCubeMask[directionA, lrduA] = true
                nodeB.lrduCubeMask[directionB, lrduB] = true
                nodeA.newConnectionAt(nodeConnection, true)
                nodeB.newConnectionAt(nodeConnection, false)
                var eLoad: ElectricalLoad?
                if (nodeA.getElectricalLoad(directionA, lrduA, mskB, nodeConnection.endpoint(false)).also { eLoad = it } != null) {
                    val otherELoad = nodeB.getElectricalLoad(directionB, lrduB, mskA, nodeConnection.endpoint(true))
                    if (otherELoad != null) {
                        eCon = ElectricalConnection(eLoad, otherELoad)
                        Eln.simulator.addElectricalComponent(eCon)
                        nodeConnection.addConnection(eCon)
                    }
                }
                var tLoad: ThermalLoad?
                if (nodeA.getThermalLoad(directionA, lrduA, mskB).also { tLoad = it } != null) {
                    val otherTLoad = nodeB.getThermalLoad(directionB, lrduB, mskA)
                    if (otherTLoad != null) {
                        tCon = ThermalConnection(tLoad, otherTLoad)
                        Eln.simulator.addThermalConnection(tCon)
                        nodeConnection.addConnection(tCon)
                    }
                }
            }
        }

        @JvmStatic
        fun compareConnectionMask(mask1: Int, mask2: Int): Boolean {
            if (mask1 and 0xFFFF and (mask2 and 0xFFFF) == 0) return false
            if (mask1 and maskColorCareData and (mask2 and maskColorCareData) == 0) return true
            return mask1 and maskColorData == mask2 and maskColorData
        }
    }

    init {
        coordinate = Coordinate()
    }
}

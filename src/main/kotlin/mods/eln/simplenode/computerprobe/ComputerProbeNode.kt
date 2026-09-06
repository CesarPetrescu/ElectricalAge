package mods.eln.simplenode.computerprobe

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.node.NodeBase
import mods.eln.node.simple.SimpleNode
import mods.eln.sim.ElectricalLoad
import mods.eln.sim.IProcess
import mods.eln.sim.ThermalLoad
import mods.eln.sim.nbt.NbtElectricalGateInputOutput
import mods.eln.sim.nbt.NbtElectricalGateOutputProcess
import mods.eln.sixnode.wirelesssignal.IWirelessSignalSpot
import mods.eln.sixnode.wirelesssignal.IWirelessSignalTx
import mods.eln.sixnode.wirelesssignal.WirelessUtils
import mods.eln.sixnode.wirelesssignal.aggregator.BiggerAggregator
import mods.eln.sixnode.wirelesssignal.aggregator.IWirelessSignalAggregator
import mods.eln.sixnode.wirelesssignal.aggregator.SmallerAggregator
import mods.eln.sixnode.wirelesssignal.tx.WirelessSignalTxElement
import net.minecraft.nbt.CompoundTag
import mods.eln.misc.rand

/**
 * The node behind the computer probe. It knows nothing of any computer mod: the operations a
 * computer may call are plain methods here, and `mods.eln.integration.computercraft` exposes
 * them as a peripheral when CC: Tweaked is present. (1.7.10 had the node implement
 * ComputerCraft's and OpenComputers' interfaces directly; OpenComputers has no 1.21 release.)
 */
class ComputerProbeNode : SimpleNode() {
    @JvmField
    val ioGate = arrayOfNulls<NbtElectricalGateInputOutput>(6)

    @JvmField
    val ioGateProcess = arrayOfNulls<NbtElectricalGateOutputProcess>(6)

    private var spotTimeout = 0.0
    private var spot: IWirelessSignalSpot? = null
    private val txSet = HashMap<String, HashSet<IWirelessSignalTx>>()
    private val txStrength = HashMap<IWirelessSignalTx, Double>()
    private val wirelessTxMap = HashMap<String, WirelessTx>()

    override fun initialize() {
        slowProcessList.add(SlowProcess())

        for (idx in 0 until 6) {
            val gate = NbtElectricalGateInputOutput("ioGate$idx")
            val process = NbtElectricalGateOutputProcess("ioGateProcess$idx", gate)
            ioGate[idx] = gate
            ioGateProcess[idx] = process

            electricalLoadList.add(gate)
            electricalComponentList.add(process)

            process.isHighImpedance = true
        }
        connect()
    }

    private inner class SlowProcess : IProcess {
        override fun process(time: Double) {
            if (spot != null) {
                spotTimeout -= time
                if (spotTimeout < 0) {
                    spot = null
                    txSet.clear()
                    txStrength.clear()
                }
            }
        }
    }

    private fun wirelessRead(channel: String, aggregator: IWirelessSignalAggregator): Double? {
        if (spot == null) {
            spot = WirelessUtils.buildSpot(coordinate, null, 0)
            txSet.clear()
            txStrength.clear()
            WirelessUtils.getTx(spot, txSet, txStrength)
            spotTimeout = Utils.rand(1.0, 2.0)
        }

        val txs = txSet[channel] ?: return null
        return aggregator.aggregate(txs)
    }

    fun aggregatorFor(name: String): IWirelessSignalAggregator? {
        return when (name.lowercase()) {
            "bigger" -> BiggerAggregator()
            "smaller" -> SmallerAggregator()
            else -> null
        }
    }

    fun directionFor(name: String): Direction? {
        return Direction.values().firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    override fun onBreakBlock() {
        super.onBreakBlock()
        unregister()
    }

    override fun unload() {
        super.unload()
        unregister()
    }

    private fun unregister() {
        for (tx in wirelessTxMap.values) {
            WirelessSignalTxElement.channelRemove(tx)
        }
    }

    override fun getSideConnectionMask(side: Direction, lrdu: LRDU): Int {
        return NodeBase.maskElectricalAll
    }

    override fun getThermalLoad(side: Direction, lrdu: LRDU, mask: Int): ThermalLoad? {
        return null
    }

    override fun getElectricalLoad(side: Direction, lrdu: LRDU, mask: Int): ElectricalLoad {
        return ioGate[side.int]!!
    }

    override val nodeUuid: String
        get() = getNodeUuidStatic()

    fun signalSetDir(side: Direction, highImpedance: Boolean) {
        ioGateProcess[side.int]!!.isHighImpedance = highImpedance
    }

    /** "in" (high impedance, reading) or "out" (driving). */
    fun signalGetDir(side: Direction): String = if (ioGateProcess[side.int]!!.isHighImpedance) "in" else "out"

    fun signalSetOut(side: Direction, value: Double) {
        ioGateProcess[side.int]!!.outputNormalized = value
    }

    fun signalGetOut(side: Direction): Double = ioGateProcess[side.int]!!.outputNormalized

    fun signalGetIn(side: Direction): Double = ioGate[side.int]!!.inputNormalized

    fun wirelessSet(channel: String, value: Double) {
        var tx = wirelessTxMap[channel]
        if (tx == null) {
            tx = WirelessTx()
            tx.channelName = channel
            WirelessSignalTxElement.channelRegister(tx)
            wirelessTxMap[channel] = tx
        }
        tx.signalValue = value
    }

    fun wirelessRemove(channel: String) {
        val tx = wirelessTxMap.remove(channel) ?: return
        WirelessSignalTxElement.channelRemove(tx)
    }

    fun wirelessRemoveAll() {
        for (tx in wirelessTxMap.values) {
            WirelessSignalTxElement.channelRemove(tx)
        }
        wirelessTxMap.clear()
    }

    /** The channel's value as the aggregator sees the transmitters in range, or null when none is. */
    fun wirelessGet(channel: String, aggregator: IWirelessSignalAggregator): Double? = wirelessRead(channel, aggregator)

    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putInt("wirelessTxCount", wirelessTxMap.size)
        var idx = 0
        for (tx in wirelessTxMap.values) {
            nbt.putString("wirelessTx" + idx + "channel", tx.channelName)
            nbt.putDouble("wirelessTx" + idx + "value", tx.signalValue)
            idx++
        }
    }

    override fun readFromNBT(nbt: CompoundTag) {
        super.readFromNBT(nbt)
        val wirelessTxCount = nbt.getInt("wirelessTxCount")
        for (idx in 0 until wirelessTxCount) {
            val tx = WirelessTx()
            tx.channelName = nbt.getString("wirelessTx" + idx + "channel")
            tx.signalValue = nbt.getDouble("wirelessTx" + idx + "value")
            WirelessSignalTxElement.channelRegister(tx)
            wirelessTxMap[tx.channelName] = tx
        }
    }

    private inner class WirelessTx : IWirelessSignalTx {
        lateinit var channelName: String
        var signalValue = 0.0

        override fun getCoordinate(): Coordinate {
            return coordinate
        }

        override fun getRange(): Int {
            return Eln.config.getIntOrElse("wireless.transmitter.maxRangeBlocks", 32)
        }

        override fun getChannel(): String {
            return channelName
        }

        override fun getValue(): Double {
            return signalValue
        }
    }

    companion object {
        @JvmStatic
        fun getNodeUuidStatic(): String {
            return "ElnComputerProbe"
        }
    }
}

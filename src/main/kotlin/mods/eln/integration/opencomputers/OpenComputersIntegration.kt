package mods.eln.integration.opencomputers

import li.cil.oc.api.API
import li.cil.oc.api.Driver
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import mods.eln.Eln
import mods.eln.misc.Version
import mods.eln.simplenode.computerprobe.ComputerProbeEntity
import mods.eln.simplenode.computerprobe.ComputerProbeNode
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level

/** Optional OC: Rebooted integration, loaded only when the opencomputers mod is present. */
object OpenComputersIntegration {
    const val COMPONENT = "eln_probe"
    @JvmStatic fun register() {
        checkNotNull(API.driver) { "OpenComputers driver API has not initialized" }
        Driver.add(ProbeDriver())
        Eln.LOGGER.info("OpenComputers found: Computer Probe available as {} through an Adapter", COMPONENT)
    }
    class ProbeDriver : DriverBlock {
        override fun worksWith(world: Level, pos: BlockPos, side: Direction) = world.getBlockEntity(pos) is ComputerProbeEntity
        override fun createEnvironment(world: Level, pos: BlockPos, side: Direction): ManagedEnvironment? =
            (world.getBlockEntity(pos) as? ComputerProbeEntity)?.let { ProbeEnvironment(it) }
    }
}

/** Settings belong to ELN's node; the OC Adapter owns this environment's lifecycle. */
class ProbeEnvironment(private val entity: ComputerProbeEntity) : AbstractManagedEnvironment(), NamedBlock {
    init { setNode(Network.newNode(this, Visibility.Network).withComponent(OpenComputersIntegration.COMPONENT).create()) }
    override fun preferredName() = OpenComputersIntegration.COMPONENT
    override fun priority() = 100
    private fun probe(): ComputerProbeNode {
        val world = checkNotNull(entity.level) { "Probe is not in a world" }
        check(!world.isClientSide && world.server?.isSameThread == true) { "Probe callbacks must run on the server thread" }
        check(!entity.isRemoved && world.getBlockEntity(entity.blockPos) === entity) { "Probe was removed" }
        return entity.node as? ComputerProbeNode ?: error("Probe is not ready")
    }
    private fun side(args: Arguments) = probe().directionFor(args.checkString(0))
        ?: throw IllegalArgumentException("Unknown side; expected XN, XP, YN, YP, ZN or ZP")
    private fun signal(args: Arguments) = args.checkDouble(1).also {
        require(it.isFinite() && it in 0.0..1.0) { "Signal must be finite and between 0 and 1" }
    }
    @Callback(direct = false, doc = "function(side:string, direction:string) -- Set 'in' or 'out'.")
    fun signalSetDir(context: Context?, args: Arguments): Array<Any?> {
        val direction = args.checkString(1)
        require(direction == "in" || direction == "out") { "Expected direction 'in' or 'out'" }
        probe().signalSetDir(side(args), direction == "in")
        return emptyArray()
    }
    @Callback(direct = false, doc = "function(side:string):string -- Read the input/output mode.")
    fun signalGetDir(context: Context?, args: Arguments): Array<Any?> = arrayOf(probe().signalGetDir(side(args)))
    @Callback(direct = false, doc = "function(side:string, value:number) -- Set a normalized 0..1 output.")
    fun signalSetOut(context: Context?, args: Arguments): Array<Any?> {
        probe().signalSetOut(side(args), signal(args)); return emptyArray()
    }
    @Callback(direct = false, doc = "function(side:string):number -- Read the configured output.")
    fun signalGetOut(context: Context?, args: Arguments): Array<Any?> = arrayOf(probe().signalGetOut(side(args)))
    @Callback(direct = false, doc = "function(side:string):number -- Read the measured input.")
    fun signalGetIn(context: Context?, args: Arguments): Array<Any?> = arrayOf(probe().signalGetIn(side(args)))
    @Callback(direct = false, doc = "function(channel:string, value:number) -- Transmit a normalized 0..1 signal.")
    fun wirelessSet(context: Context?, args: Arguments): Array<Any?> {
        probe().wirelessSet(args.checkString(0), signal(args)); return emptyArray()
    }
    @Callback(direct = false, doc = "function(channel:string, aggregation:string='bigger'):number -- Read a channel, or nil/reason.")
    fun wirelessGet(context: Context?, args: Arguments): Array<Any?> {
        val probe = probe()
        val aggregation = probe.aggregatorFor(args.optString(1, "bigger")) ?: return arrayOf(null, "Expected aggregation 'bigger' or 'smaller'")
        return probe.wirelessGet(args.checkString(0), aggregation)?.let { arrayOf(it) } ?: arrayOf(null, "Channel not available")
    }
    @Callback(direct = false, doc = "function(channel:string) -- Stop transmitting a channel.")
    fun wirelessRemove(context: Context?, args: Arguments): Array<Any?> {
        probe().wirelessRemove(args.checkString(0)); return emptyArray()
    }
    @Callback(direct = false, doc = "function() -- Stop all transmissions from this probe.")
    fun wirelessRemoveAll(context: Context?, args: Arguments): Array<Any?> {
        probe().wirelessRemoveAll(); return emptyArray()
    }
    @Callback(direct = false, doc = "function():string -- Electrical Age version.")
    fun version(context: Context?, args: Arguments): Array<Any?> = arrayOf(Version.simpleVersionName)
}

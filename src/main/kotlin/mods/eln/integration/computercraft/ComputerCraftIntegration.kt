package mods.eln.integration.computercraft

import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.lua.LuaFunction
import dan200.computercraft.api.lua.MethodResult
import dan200.computercraft.api.peripheral.IPeripheral
import dan200.computercraft.api.peripheral.PeripheralCapability
import mods.eln.Eln
import mods.eln.misc.Direction
import mods.eln.misc.Version
import mods.eln.simplenode.computerprobe.ComputerProbeEntity
import mods.eln.simplenode.computerprobe.ComputerProbeNode
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import java.util.Optional

/**
 * CC: Tweaked, optional at run time: nothing outside this file names a `dan200` class, and the
 * registration is only reached when the mod is present. The peripheral capability replaced
 * 1.7.10's peripheral provider; it is served from the probe's block entity.
 */
object ComputerCraftIntegration {
    const val MOD_ID = "computercraft"

    @JvmStatic
    fun register(event: RegisterCapabilitiesEvent) {
        event.registerBlockEntity(PeripheralCapability.get(), ComputerProbeEntity.TYPE.get()) { entity, _ -> ComputerProbePeripheral(entity) }
        Eln.LOGGER.info("CC: Tweaked found: the computer probe is a peripheral")
    }

    /** The peripheral type a computer would see at the position, or null: what the smoke test checks. */
    @JvmStatic
    fun peripheralTypeAt(level: Level, pos: BlockPos): String? =
        level.getCapability(PeripheralCapability.get(), pos, null)?.type

    /**
     * For the smoke test: the Lua-visible method names CC: Tweaked's generator accepts on the
     * peripheral at the position (a `@LuaFunction` with a signature it cannot bind is dropped with
     * a log line, not an error), and the result of calling `version` through the generated
     * binding. The generator is internal to CC (`core.asm`), hence reflection.
     */
    @JvmStatic
    fun describePeripheralAt(level: Level, pos: BlockPos): String {
        val peripheral = level.getCapability(PeripheralCapability.get(), pos, null) ?: return "no peripheral"
        val supplier = Class.forName("dan200.computercraft.core.asm.PeripheralMethodSupplier")
            .getMethod("create", List::class.java).invoke(null, emptyList<Any>())
        @Suppress("UNCHECKED_CAST")
        val methods = Class.forName("dan200.computercraft.core.methods.MethodSupplier")
            .getMethod("getSelfMethods", Any::class.java).invoke(supplier, peripheral) as Map<String, Any>
        val version = methods["version"]?.let { method ->
            val apply = Class.forName("dan200.computercraft.core.methods.PeripheralMethod").methods.first { it.name == "apply" }
            (apply.invoke(method, peripheral, null, null, dan200.computercraft.api.lua.ObjectArguments()) as MethodResult).result?.firstOrNull()
        }
        return "type=${peripheral.type} methods=${methods.keys.sorted()} version=$version"
    }
}

/**
 * The probe as a computer sees it: `peripheral.wrap(side)` on an adjacent computer gives an
 * object with the 1.7.10 method names. Sides are the mod's own (XN, XP, YN, YP, ZN, ZP, any
 * case). A signal is normalised to 0..1. A failed call raises a Lua error rather than returning
 * nothing, the 1.7.10 behaviour, except `wirelessGet`, which keeps its `nil, reason` return.
 *
 * Every method runs on the server thread ([LuaFunction.mainThread]): the node's state belongs
 * to the simulation, which the computer thread must not touch.
 */
class ComputerProbePeripheral(private val entity: ComputerProbeEntity) : IPeripheral {
    override fun getType(): String = "ElnProbe"

    override fun getTarget(): Any = entity

    override fun equals(other: IPeripheral?): Boolean = other is ComputerProbePeripheral && other.entity === entity

    private fun node(): ComputerProbeNode = entity.node as? ComputerProbeNode ?: throw LuaException("the probe is not ready")

    private fun side(name: String): Direction = node().directionFor(name) ?: throw LuaException("unknown side: $name")

    @LuaFunction(mainThread = true)
    fun signalSetDir(side: String, direction: String) {
        if (direction != "in" && direction != "out") throw LuaException("unknown direction '$direction'; expected in or out")
        node().signalSetDir(side(side), direction == "in")
    }

    @LuaFunction(mainThread = true)
    fun signalGetDir(side: String): String = node().signalGetDir(side(side))

    @LuaFunction(mainThread = true)
    fun signalSetOut(side: String, value: Double) = node().signalSetOut(side(side), value)

    @LuaFunction(mainThread = true)
    fun signalGetOut(side: String): Double = node().signalGetOut(side(side))

    @LuaFunction(mainThread = true)
    fun signalGetIn(side: String): Double = node().signalGetIn(side(side))

    @LuaFunction(mainThread = true)
    fun wirelessSet(channel: String, value: Double) = node().wirelessSet(channel, value)

    @LuaFunction(mainThread = true)
    fun wirelessRemove(channel: String) = node().wirelessRemove(channel)

    @LuaFunction(mainThread = true)
    fun wirelessRemoveAll() = node().wirelessRemoveAll()

    @LuaFunction(mainThread = true)
    fun wirelessGet(channel: String, aggregation: Optional<String>): MethodResult {
        val node = node()
        val name = aggregation.orElse("bigger")
        val aggregator = node.aggregatorFor(name)
            ?: return MethodResult.of(null, "unknown aggregation '$name'; expected bigger or smaller")
        val value = node.wirelessGet(channel, aggregator)
            ?: return MethodResult.of(null, "channel not available: $channel")
        return MethodResult.of(value)
    }

    @LuaFunction
    fun version(): String = Version.simpleVersionName
}

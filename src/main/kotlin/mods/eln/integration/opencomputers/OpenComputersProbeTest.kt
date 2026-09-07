package mods.eln.integration.opencomputers

import li.cil.oc.api.Driver
import li.cil.oc.api.Network
import li.cil.oc.api.internal.Adapter
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Visibility
import mods.eln.misc.Version
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Blocks

/** Invokes OC's actual Component dispatcher, not our Kotlin methods or a CC compatibility bridge. */
object OpenComputersProbeTest {
    private val methods = setOf("signalSetDir", "signalGetDir", "signalSetOut", "signalGetOut", "signalGetIn", "wirelessSet", "wirelessGet", "wirelessRemove", "wirelessRemoveAll", "version")
    fun prepare(world: ServerLevel, p: BlockPos) {
        val adapterPos = p.north()
        world.setBlockAndUpdate(adapterPos.below(), Blocks.STONE.defaultBlockState())
        val block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse("opencomputers:adapter")).orElseThrow()
        world.setBlockAndUpdate(adapterPos, block.defaultBlockState())
        check(world.getBlockEntity(adapterPos) is Adapter) { "No real OpenComputers Adapter" }
    }

    fun verify(world: ServerLevel, p: BlockPos, restart: Boolean) {
        val driver = checkNotNull(Driver.driverFor(world, p, Direction.NORTH)) { "OC did not register our driver" }
        check(driver.worksWith(world, p, Direction.NORTH))
        val adapter = world.getBlockEntity(p.north()) as? Adapter ?: error("Adapter did not survive restart")
        val network = checkNotNull(adapter.node().network()) { "Adapter did not join a network" }
        val components = network.nodes().filterIsInstance<Component>().filter {
            it.name() == OpenComputersIntegration.COMPONENT && it.visibility() == Visibility.Network
        }
        check(components.size == 1) { "Expected one native probe, got ${components.size}; missing or duplicate CC bridge" }
        val component = components.single()
        check(component.methods().toSet() == methods) { "Missing/extra Lua callbacks: ${component.methods()}" }
        check(methods.all { !component.annotation(it).direct }) { "ELN callbacks must stay on the server thread" }
        fun call(name: String, vararg args: Any?): Array<Any?> = component.invoke(name, null, *args)
        check(call("version").single() == Version.simpleVersionName)
        if (restart) {
            check(call("signalGetDir", "XP").single() == "out") { "Signal mode was lost on restart" }
            check(call("signalGetOut", "XP").single() == .625) { "Output setting was lost on restart" }
            check(call("wirelessGet", "eln-ci-saved").first() == .375) { "Wireless transmitter was lost on restart" }
        }
        for (side in listOf("XN", "XP", "YN", "YP", "ZN", "ZP")) {
            call("signalSetDir", side, "out")
            call("signalSetOut", side, .625)
            check(call("signalGetDir", side).single() == "out")
            check(call("signalGetOut", side).single() == .625)
            check((call("signalGetIn", side).single() as Number).toDouble().isFinite())
            if (side != "XP") call("signalSetDir", side, "in")
        }
        for (bad in listOf(Double.NaN, Double.POSITIVE_INFINITY, -.1, 1.1)) {
            check(runCatching { call("signalSetOut", "XP", bad) }.isFailure) { "Accepted unsafe signal $bad" }
            check(call("signalGetOut", "XP").single() == .625) { "Rejected call changed the output" }
        }
        check(runCatching { call("signalSetDir", "BAD", "out") }.isFailure)
        check(runCatching { call("signalSetDir", "XP", "invalid") }.isFailure)
        check(call("wirelessGet", "not-present").first() == null)
        check(call("wirelessGet", "not-present", "invalid").first() == null)
        call("wirelessSet", "eln-ci-remove", .5)
        call("wirelessRemove", "eln-ci-remove")
        call("wirelessRemoveAll")
        call("wirelessSet", "eln-ci-saved", .375)
        // Check a second-side driver environment and clean up its network explicitly.
        // The actual Adapter fixture is retained for the separate-JVM restart test.
        val extra = checkNotNull(driver.createEnvironment(world, p, Direction.SOUTH))
        Network.joinNewNetwork(extra.node())
        check((extra.node() as Component).invoke("version", null).single() == Version.simpleVersionName)
        extra.node().remove()
    }
}

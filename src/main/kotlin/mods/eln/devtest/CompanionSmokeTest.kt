package mods.eln.devtest

import mods.eln.Eln
import mods.eln.fluid.FuelRegistry
import mods.eln.item.TurbineBladeLists
import mods.eln.mechanical.RadialMotorElement
import mods.eln.mechanical.SimpleShaftElement
import mods.eln.mechanical.TurbineElement
import mods.eln.misc.Coordinate
import mods.eln.node.NodeManager
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.simplenode.computerprobe.ComputerProbeEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.storage.LevelResource
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModList
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Files

/** Real optional mods, real capabilities and a Pipez-driven engine. Never enabled in normal play. */
class CompanionSmokeTest(private val profile: String, private val restart: Boolean) {
    private val report = ContractReport("companions-$profile-${if (restart) "restart" else "place"}")
    private var ticks = 0
    private var nextCell = 0
    private val enginePos = BlockPos(512, 100, 1024)
    private val sourcePos = enginePos.north(2)
    private val pipePos = enginePos.north()
    private val probePos = enginePos.east(16)
    private var initialSupply = 0
    private var generated = 0.0
    private var baselineEnergy = 0.0
    private var transferReady = false
    private val fluids get() = profile != "opencomputers"
    private val computers get() = profile != "fluids"
    private val world: ServerLevel get() = ServerLifecycleHooks.getCurrentServer()!!.overworld()

    companion object {
        @JvmStatic fun register(mode: String) {
            val parts = mode.split('-')
            require(parts.size == 3 && parts[1] in setOf("fluids", "opencomputers", "combined") && parts[2] in setOf("place", "restart"))
            NeoForge.EVENT_BUS.register(CompanionSmokeTest(parts[1], parts[2] == "restart"))
        }
    }

    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        ticks++
        if (ticks == 20) {
            report.write(false)
            val ready = report.test(profile, "setup") { setup() }
            if (!ready) { finish(); return }
        }
        if (ticks in 21..119 && transferReady) {
            // A dynamometer absorbs produced energy each tick, preventing an unloaded turbine
            // from overspeeding. Starting energy is seeded once, never counted as output.
            val engine = element(enginePos) as? TurbineElement
            if (engine != null) {
                val excess = engine.shaft.energy - baselineEnergy
                if (excess > 0) { generated += excess; engine.shaft.energy = baselineEnergy }
            }
        }
        if (ticks == 120) {
            if (fluids) verifyTransfer()
            if (computers) report.test("opencomputers", "native-component") {
                mods.eln.integration.opencomputers.OpenComputersProbeTest.verify(world, probePos, restart)
            }
            if (profile == "combined") report.test("computercraft", "probe-alongside-opencomputers") {
                val description = mods.eln.integration.computercraft.ComputerCraftIntegration.describePeripheralAt(world, probePos)
                check(description.contains("type=ElnProbe") && description.contains("signalSetOut") && description.contains("version=")) { description }
            }
            if (fluids && !restart) fuelContracts()
            finish()
        }
    }

    private fun setup() {
        val marker = world.server.getWorldPath(LevelResource.ROOT).resolve("eln-companions-$profile.txt")
        if (restart) check(Files.readString(marker) == profile) { "No matching saved compatibility world" }
        else {
            check(!Files.exists(marker)) { "Refusing to overwrite a previous compatibility world; use a fresh run directory" }
            Files.writeString(marker, profile)
        }
        val expected = mutableListOf("eln", "jade")
        if (fluids) expected.addAll(listOf("pneumaticcraft", "railcraft", "immersiveengineering", "pipez"))
        if (computers) expected.add("opencomputers")
        if (profile == "combined") expected.addAll(listOf("computercraft", "create"))
        for (id in expected) report.test(id, "loaded") {
            check(ModList.get().isLoaded(id)) { "Required test mod did not load" }
            Eln.logger.info("COMPANION VERSION {} {}", id, ModList.get().getModContainerById(id).get().modInfo.version)
        }
        if (profile == "opencomputers") report.test("computercraft", "absent-native-isolation") {
            check(!ModList.get().isLoaded("computercraft")) { "Native OC test must not use the CC bridge" }
        }
        check(report.failures == 0) { "Incorrect mod profile" }
        for (x in 31..37) for (z in 63..66) world.setChunkForced(x, z, true)
        if (fluids) {
            if (!restart) setupTransfer()
            report.test("pipez", if (restart) "persisted-tank-and-extraction" else "configured-source") {
                val supply = handler(sourcePos)
                check(supply.getFluidInTank(0).fluid == fluid("pneumaticcraft:gasoline"))
                check(supply.getFluidInTank(0).amount in 900..1000) { "Tank fuel lost or duplicated" }
                val pipe = checkNotNull(world.getBlockEntity(pipePos))
                check(pipe.javaClass.getMethod("isExtracting", Direction::class.java).invoke(pipe, Direction.NORTH) == true)
                check(element(enginePos) is TurbineElement)
            }
            val engine = element(enginePos) as TurbineElement
            engine.shaft.rads = engine.desc.optimalRads
            baselineEnergy = engine.shaft.energy
            initialSupply = handler(sourcePos).getFluidInTank(0).amount + handler(enginePos).getFluidInTank(0).amount
            transferReady = true
        }
        if (computers && !restart) {
            world.setBlockAndUpdate(probePos.below(), Blocks.STONE.defaultBlockState())
            placeItem("eln:elnprobe", probePos)
            check(world.getBlockEntity(probePos) is ComputerProbeEntity)
            mods.eln.integration.opencomputers.OpenComputersProbeTest.prepare(world, probePos)
        }
    }

    private fun setupTransfer() {
        val engine = placeMachine("Gas Turbine", enginePos) as TurbineElement
        engine.inventory.setItem(0, TurbineBladeLists.registeredBlades.first().newItemStack(1))
        placeItem("pneumaticcraft:small_tank", sourcePos)
        check(handler(sourcePos).fill(FluidStack(fluid("pneumaticcraft:gasoline"), 1000), FluidAction.EXECUTE) == 1000)
        var state = block("pipez:fluid_pipe").defaultBlockState()
        for (name in listOf("has_data", "north", "south")) state = state.setValue(state.properties.first { it.name == name } as BooleanProperty, true)
        world.setBlockAndUpdate(pipePos, state)
        val pipe = checkNotNull(world.getBlockEntity(pipePos))
        pipe.javaClass.getMethod("setExtracting", Direction::class.java, Boolean::class.javaPrimitiveType).invoke(pipe, Direction.NORTH, true)
    }

    private fun verifyTransfer() {
        report.test("pipez", "pneumaticcraft-tank-to-eln-engine") {
            check(transferReady)
            val remaining = handler(sourcePos).getFluidInTank(0).amount + handler(enginePos).getFluidInTank(0).amount
            val consumed = initialSupply - remaining
            check(consumed > 0) { "Pipe or engine never consumed fuel: $initialSupply -> $remaining" }
            check(consumed <= 30) { "Unexpected fuel loss: $consumed mB" }
            check(generated.isFinite() && generated > 0) { "No mechanical work produced: $generated J" }
            val maximum = (consumed + 2) * FuelRegistry.heatEnergyPerMilliBucket(fluid("pneumaticcraft:gasoline"))
            check(generated <= maximum) { "Created energy: $generated J exceeds fuel budget $maximum J" }
            Eln.logger.info("COMPANION Pipez transfer consumed={}mB shaft-work={}J", consumed, generated)
        }
    }

    private fun fuelContracts() {
        val light = listOf("pneumaticcraft:gasoline", "pneumaticcraft:kerosene", "pneumaticcraft:lpg", "pneumaticcraft:ethanol", "immersiveengineering:ethanol")
        for (name in listOf("Gas Turbine", "Radial Motor")) for (fuel in light) engineContract(name, fuel)
        for (name in listOf("Steam Turbine", "Large Steam Turbine")) engineContract(name, "railcraft:steam")
        engineContract("Large Gas Turbine", "pneumaticcraft:gasoline")
        for (fuel in listOf("immersiveengineering:biodiesel", "pneumaticcraft:biodiesel", "pneumaticcraft:diesel", "railcraft:creosote")) {
            report.test("Fuel Heat Furnace/$fuel", "accept-and-persist-fuel") {
                val p = cell()
                val machine = placeMachine("Fuel Heat Furnace", p)
                val tank = handler(p)
                check(tank.fill(FluidStack(fluid(fuel), 20), FluidAction.EXECUTE) == 20)
                val nbt = net.minecraft.nbt.CompoundTag()
                machine.writeToNBT(nbt)
                tank.drain(20, FluidAction.EXECUTE)
                machine.readFromNBT(nbt)
                check(tank.getFluidInTank(0).amount == 20 && tank.getFluidInTank(0).fluid == fluid(fuel))
                world.removeBlock(p, false)
            }
        }
    }

    private fun engineContract(name: String, fuel: String) {
        report.test("$name/$fuel", "capability-and-mechanical-work") {
            val p = cell()
            val engine = placeMachine(name, p) as SimpleShaftElement
            try {
                val tank = handler(p)
                check(tank.fill(FluidStack(Fluids.WATER, 1), FluidAction.EXECUTE) == 0) { "Accepted water as fuel" }
                val capacity = tank.getTankCapacity(0)
                val stack = FluidStack(fluid(fuel), capacity)
                val beforeSimulation = net.minecraft.nbt.CompoundTag().also { engine.writeToNBT(it) }
                check(tank.fill(stack, FluidAction.SIMULATE) == capacity && tank.getFluidInTank(0).isEmpty) { "SIMULATE mutated/rejected tank" }
                val afterSimulation = net.minecraft.nbt.CompoundTag().also { engine.writeToNBT(it) }
                check(beforeSimulation == afterSimulation) { "SIMULATE mutated persisted machine state" }
                check(tank.fill(stack, FluidAction.EXECUTE) == capacity) { "Rejected $fuel" }
                if (engine is TurbineElement) {
                    engine.shaft.rads = engine.desc.optimalRads
                    val before = engine.shaft.energy
                    engine.turbineSlowProcess.process(.05)
                    check(engine.shaft.energy == before && tank.getFluidInTank(0).amount == capacity) { "Turbine ran without a blade" }
                    engine.inventory.setItem(0, TurbineBladeLists.registeredBlades.first().newItemStack(1))
                } else engine.shaft.rads = 40.0
                val before = engine.shaft.energy
                repeat(10) {
                    tank.fill(stack, FluidAction.EXECUTE)
                    when (engine) {
                        is TurbineElement -> engine.turbineSlowProcess.process(.05)
                        is RadialMotorElement -> engine.radialMotorSlowProcess.process(.05)
                    }
                }
                check(engine.shaft.energy.isFinite() && engine.shaft.energy > before) {
                    "No shaft work from $fuel: $before -> ${engine.shaft.energy} J, mass=${engine.shaft.mass}, speed=${engine.shaft.rads}"
                }
                // Fractional gas consumption can leave integer tank amount unchanged on the last step.
                val rate = when (engine) { is TurbineElement -> engine.fluidRate; is RadialMotorElement -> engine.fluidRate; else -> 0f }
                check(rate > 0) { "Engine reported no fuel consumption" }
            } finally { world.removeBlock(p, false) }
        }
    }

    private fun cell(): BlockPos = enginePos.offset(32 + (nextCell % 4) * 12, 0, (nextCell++ / 4) * 12)
    private fun element(p: BlockPos): TransparentNodeElement? =
        (NodeManager.instance?.getNodeFromCoordonate(Coordinate(p.x, p.y, p.z, world)) as? TransparentNode)?.element
    private fun fluid(id: String) = BuiltInRegistries.FLUID.getOptional(ResourceLocation.parse(id)).orElseThrow { IllegalStateException("Missing fluid $id") }
    private fun block(id: String) = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow { IllegalStateException("Missing block $id") }
    private fun handler(p: BlockPos): IFluidHandler = checkNotNull(world.getCapability(Capabilities.FluidHandler.BLOCK, p, Direction.UP)) { "Missing fluid port at $p" }
    private fun placeMachine(name: String, p: BlockPos): TransparentNodeElement {
        // Support the controller only: the radial motor also occupies cells below its shaft.
        world.setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState())
        val player = FakePlayerFactory.getMinecraft(world)
        player.yRot = 0f; player.yHeadRot = 0f; player.xRot = 0f
        val stack = checkNotNull(Eln.findItemStack(name, 1))
        check(!stack.isEmpty) { "Missing machine $name" }
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)
        check(Eln.transparentNodeItem.placeBlockAt(stack, player, world, p, Direction.UP)) { "Cannot place $name" }
        return checkNotNull(element(p)) { "No node for $name" }
    }
    private fun placeItem(id: String, p: BlockPos) {
        world.setBlockAndUpdate(p.below(), Blocks.STONE.defaultBlockState())
        val player = FakePlayerFactory.getMinecraft(world)
        val stack = ItemStack(block(id))
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)
        val hit = BlockHitResult(Vec3(p.x + .5, p.y.toDouble(), p.z + .5), Direction.UP, p.below(), false)
        stack.useOn(UseOnContext(player, InteractionHand.MAIN_HAND, hit))
        check(world.getBlockState(p).block == block(id)) { "Failed to place $id" }
    }
    private fun finish() {
        report.write(true)
        val server = world.server
        if (report.failures > 0) Thread({ server.runningThread.join(); kotlin.system.exitProcess(1) }, "companions-failed-exit").start()
        server.halt(false)
    }
}

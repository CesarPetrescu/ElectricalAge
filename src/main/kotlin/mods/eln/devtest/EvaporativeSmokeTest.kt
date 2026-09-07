package mods.eln.devtest

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction as ElnDirection
import mods.eln.misc.LRDU
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import mods.eln.transparentnode.evaporative.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.storage.LevelResource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.io.*
import java.nio.file.Files
import kotlin.math.abs

/** Dedicated, opt-in disposable-world checks, including real MNA power and separate-JVM restart. */
class EvaporativeSmokeTest(private val restart: Boolean) {
    companion object {
        @JvmField val ACTIVE = BlockPos(32, 80, 32)
        @JvmField val DRY = BlockPos(32, 80, 38)
        @JvmField val SAVED = BlockPos(40, 80, 32)
        const val NAME = "240V Evaporative Heat Sink"
        @JvmStatic fun register(mode: String) {
            require(mode == "evaporative-place" || mode == "evaporative-restart")
            NeoForge.EVENT_BUS.register(EvaporativeSmokeTest(mode.endsWith("restart")))
        }
        @JvmStatic fun element(w: ServerLevel, p: BlockPos): EvaporativeCoolerElement =
            (NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x, p.y, p.z, w)) as TransparentNode).element as EvaporativeCoolerElement
        @JvmStatic fun prepareClient(w: ServerLevel) {
            val e = element(w, ACTIVE)
            e.controls.restore(3, 40, 100, 0)
            e.water.fill(null, FluidStack(Fluids.WATER, 4000), true)
            e.thermal.temperatureCelsius = 70 - e.getAmbientTemperatureCelsius()
            e.sampleEnvironment(); e.updateDemand(); e.needPublish()
        }
    }
    private val report = ContractReport("evaporative-${if (restart) "restart" else "place"}")
    private var ticks = 0
    private var finished = false
    private val w: ServerLevel get() = ServerLifecycleHooks.getCurrentServer()!!.overworld()
    private fun test(name: String, body: () -> Unit) { report.test(NAME, name, body); report.write(false) }
    private fun near(a: Double, b: Double, tolerance: Double = 1e-7) { check(abs(a-b) <= tolerance) { "$a != $b" } }
    private fun source(p: BlockPos) = (NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x, p.y, p.z, w)) as SixNode).getElement(ElnDirection.YN) as ElectricalSourceElement
    private fun voltage(v: Double) { source(ACTIVE.west(2)).readConfigTool(CompoundTag().apply { putDouble("voltage", v) }, FakePlayerFactory.getMinecraft(w)) }
    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        if (finished) return
        ticks++
        try {
            if (ticks == 20) {
                report.write(false)
                if (!report.test(NAME, "world-setup") { setup() }) { finish(); return }
                if (restart) test("saved-water-film-settings-and-temperature") {
                    val e = element(w, SAVED)
                    near(e.water.availableMb, 1233.625)
                    check(e.controls.mode() == 2 && e.controls.targetCelsius() == 55 && e.controls.fanPercent() == 70 && e.controls.redstoneMode() == 1)
                } else contracts()
            }
            if (restart) {
                if (ticks == 80) {
                    test("electrical-network-and-wet-cooling-resume") { verifyWet() }
                    finish()
                }
                return
            }
            when (ticks) {
                80 -> {
                    test("real-source-cable-fan-and-evaporation") { verifyWet() }
                    test("wet-beats-dry-with-equal-initial-temperature") {
                        val a=element(w, ACTIVE); val d=element(w, DRY)
                        check(a.surfaceCelsius < d.surfaceCelsius - .3) { "wet=${a.surfaceCelsius}, dry=${d.surfaceCelsius}" }
                        check(d.water.availableMb == 0.0 && d.evaporationWatts == 0.0)
                    }
                    voltage(0.0)
                }
                100 -> {
                    test("power-loss-removes-forced-and-wet-cooling") {
                        val e=element(w, ACTIVE)
                        check(e.status == EvaporativeStatus.NO_POWER && e.evaporationWatts == 0.0 && e.fanSpeed < .001)
                    }
                    voltage(120.0)
                }
                120 -> {
                    test("brownout-inhibits-pump") {
                        val e=element(w, ACTIVE)
                        check(e.status == EvaporativeStatus.LOW_VOLTAGE && e.evaporationWatts == 0.0)
                        check(e.fanSpeed in .1.. .9)
                    }
                    voltage(240.0)
                }
                140 -> w.setBlockAndUpdate(ACTIVE.east(), Blocks.STONE.defaultBlockState())
                160 -> {
                    test("covered-air-aperture-disables-fan") {
                        val e=element(w, ACTIVE)
                        check(!e.airflowClear && e.status == EvaporativeStatus.BLOCKED && e.evaporationWatts == 0.0)
                    }
                    w.removeBlock(ACTIVE.east(), false)
                    for (dx in -8..8) w.setBlockAndUpdate(ACTIVE.offset(dx, 2, 0), Blocks.STONE.defaultBlockState())
                }
                180 -> {
                    test("unvented-intake-is-dry-only") {
                        val e=element(w, ACTIVE)
                        check(e.airflowClear && !e.outdoor && e.status == EvaporativeStatus.INDOORS && e.evaporationWatts == 0.0)
                    }
                    for (dx in -8..8) w.removeBlock(ACTIVE.offset(dx, 2, 0), false)
                    val e=element(w, ACTIVE)
                    e.water.drain(null, 4000, true); e.water.evaporate(e.water.availableMb)
                }
                200 -> {
                    test("empty-water-falls-back-to-dry") {
                        val e=element(w, ACTIVE)
                        check(e.status == EvaporativeStatus.EMPTY && e.evaporationWatts == 0.0 && e.fanSpeed > .95)
                    }
                    prepareClient(w)
                }
                240 -> {
                    test("refill-restores-wet-cooling") { verifyWet() }
                    Files.writeString(w.server.getWorldPath(LevelResource.ROOT).resolve("eln-evaporative-fixture.txt"), "v1")
                    finish()
                }
            }
        } catch (t: Throwable) {
            test("uncaught-tick-$ticks") { throw t }; finish()
        }
    }
    private fun verifyWet() {
        val e=element(w, ACTIVE)
        check(e.supply.voltage in 235.0..241.0) { "Supply ${e.supply.voltage} V" }
        check(e.electricalWatts in 125.0..136.0) { "Motor ${e.electricalWatts} W" }
        check(e.airflowClear && e.outdoor) { "Its power cable blocked its own airflow" }
        check(e.status == EvaporativeStatus.WET && e.evaporationWatts > 500) { "Status=${e.status}, evaporation=${e.evaporationWatts}" }
        check(e.water.availableMb in 0.0..3999.999)
        check(e.surfaceCelsius.isFinite())
    }
    private fun setup() {
        w.setDayTime(6000)
        w.setWeatherParameters(100000, 0, false, false)
        for (cx in 1..3) for (cz in 1..3) w.setChunkForced(cx, cz, true)
        if (restart) {
            check(Files.readString(w.server.getWorldPath(LevelResource.ROOT).resolve("eln-evaporative-fixture.txt")) == "v1")
            return
        }
        for (p in listOf(ACTIVE, DRY, SAVED)) {
            for (dx in -3..3) for (dz in -2..2) {
                w.setBlockAndUpdate(p.offset(dx,-1,dz), Blocks.STONE.defaultBlockState())
                for (dy in 0..3) w.removeBlock(p.offset(dx,dy,dz), false)
            }
            val player=FakePlayerFactory.getMinecraft(w)
            player.yRot=0f; player.yHeadRot=0f
            val stack=checkNotNull(Eln.findItemStack(NAME,1))
            check(Eln.transparentNodeItem.placeBlockAt(stack,player,w,p,Direction.UP))
            val e=element(w,p)
            e.front=ElnDirection.XN; e.reconnect()
            e.controls.restore(if (p==DRY) 1 else 3,40,100,0)
            e.thermal.temperatureCelsius=70-e.getAmbientTemperatureCelsius()
            if (p != SAVED) {
                six("Electrical Source",p.west(2)); six("Medium Voltage Cable",p.west())
                source(p.west(2)).readConfigTool(CompoundTag().apply { putDouble("voltage", 240.0) }, player)
            }
            e.sampleEnvironment(); e.updateDemand()
        }
        // Both comparators have the same thermal cable load, and native heat connections.
        six("Copper Thermal Cable", ACTIVE.north()); six("Copper Thermal Cable", DRY.north())
        element(w,ACTIVE).water.fill(null,FluidStack(Fluids.WATER,2000),true)
    }
    private fun six(name: String,p: BlockPos) {
        val player=FakePlayerFactory.getMinecraft(w)
        val stack=checkNotNull(Eln.findItemStack(name,1))
        player.setItemInHand(InteractionHand.MAIN_HAND,stack)
        Eln.sixNodeItem.onItemUse(stack,player,w,p,InteractionHand.MAIN_HAND,Direction.UP,.5f,1f,.5f)
        check(w.getBlockEntity(p) != null) { "Failed to place $name" }
    }
    private fun contracts() {
        val e=element(w,SAVED)
        val player=FakePlayerFactory.getMinecraft(w)
        player.setGameMode(GameType.SURVIVAL)
        player.setPos(SAVED.x+.5,SAVED.y.toDouble(),SAVED.z+2.0)
        test("native-fluid-capability") {
            val h=checkNotNull(w.getCapability(Capabilities.FluidHandler.BLOCK,SAVED,Direction.UP))
            check(h.fill(FluidStack(Fluids.WATER,1000),FluidAction.SIMULATE)==1000)
            near(e.water.availableMb,0.0)
            check(h.fill(FluidStack(Fluids.LAVA,1000),FluidAction.EXECUTE)==0)
            check(h.fill(FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)==1000)
            check(h.drain(1000,FluidAction.SIMULATE).amount==1000)
            near(e.water.availableMb,1000.0)
            check(h.drain(1000,FluidAction.EXECUTE).amount==1000)
        }
        test("horizontal-fluid-ports-reject-water") {
            for (side in listOf(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST)) {
                val h=w.getCapability(Capabilities.FluidHandler.BLOCK,SAVED,side)
                check(h == null || h.fill(FluidStack(Fluids.WATER,1000),FluidAction.EXECUTE)==0)
            }
        }
        test("survival-water-bucket-and-empty-bucket-conservation") {
            player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(Items.WATER_BUCKET))
            check(e.onBlockActivated(player,ElnDirection.YP,.5f,.5f,.5f))
            near(e.water.availableMb,1000.0); check(player.mainHandItem.`is`(Items.BUCKET))
            check(e.onBlockActivated(player,ElnDirection.YP,.5f,.5f,.5f))
            near(e.water.availableMb,0.0); check(player.mainHandItem.`is`(Items.WATER_BUCKET))
        }
        test("tank-capacity-and-partial-bucket-no-loss") {
            check(e.water.fill(null,FluidStack(Fluids.WATER,3900),true)==3900)
            e.onBlockActivated(player,ElnDirection.YP,.5f,.5f,.5f)
            near(e.water.availableMb,3900.0); check(player.mainHandItem.`is`(Items.WATER_BUCKET))
            check(e.water.fill(null,FluidStack(Fluids.WATER,1000),true)==100)
            near(e.water.availableMb,4000.0); e.water.drain(null,4000,true)
        }
        test("menu-range-spectator-and-command-validation") {
            val m=EvaporativeCoolerMenu(player,e)
            check(m.stillValid(player)); check(m.clickMenuButton(player,1)); check(!m.clickMenuButton(player,9999))
            player.setPos(1000.0,80.0,1000.0); check(!m.clickMenuButton(player,3))
            player.setPos(SAVED.x+.5,SAVED.y.toDouble(),SAVED.z+2.0)
            player.setGameMode(GameType.SPECTATOR); check(!m.clickMenuButton(player,3))
            player.setGameMode(GameType.SURVIVAL)
        }
        test("thermal-and-electrical-ports-are-disjoint") {
            for (side in ElnDirection.values()) {
                val t=e.getThermalLoad(side,LRDU.Down); val p=e.getElectricalLoad(side,LRDU.Down)
                check(t==null || p==null)
                if (!side.isY) check(t!=null || p!=null)
            }
            check(element(w,ACTIVE).node!!.nodeConnectionList.size >= 2)
        }
        e.water.fill(null,FluidStack(Fluids.WATER,1234),true)
        near(e.water.evaporate(.375),.375)
        e.controls.restore(2,55,70,1)
        test("full-nbt-and-drop-nbt-preserve-prepaid-film") {
            val full=CompoundTag(); e.writeToNBT(full)
            val copy=EvaporativeCoolerElement(TransparentNode().apply { coordinate=Coordinate(SAVED.x,SAVED.y,SAVED.z,w) },e.transparentNodeDescriptor)
            copy.readFromNBT(full); near(copy.water.availableMb,1233.625)
            check(copy.controls.targetCelsius()==55 && copy.controls.redstoneMode()==1)
            val dropped=EvaporativeCoolerElement(TransparentNode().apply { coordinate=Coordinate(SAVED.x,SAVED.y,SAVED.z,w) },e.transparentNodeDescriptor)
            dropped.readItemStackNBT(e.getItemStackNBT()); near(dropped.water.availableMb,1233.625)
            check(dropped.controls.fanPercent()==70)
        }
        test("renderer-packet-is-finite-and-complete") {
            val bytes=ByteArrayOutputStream(); e.networkSerialize(DataOutputStream(bytes))
            val input=DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
            input.readByte(); check(input.readFloat().isFinite()); check(input.readFloat() in 0f..1f)
            input.readBoolean(); input.readInt(); check(input.available()==0)
        }
        e.updateDemand()
    }
    private fun finish() {
        finished=true; report.write(true)
        val server=w.server
        if (report.failures>0) Thread({ server.runningThread.join(); kotlin.system.exitProcess(1) },"evaporative-failed-exit").start()
        server.halt(false)
    }
}

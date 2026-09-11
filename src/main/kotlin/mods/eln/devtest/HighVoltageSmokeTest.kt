package mods.eln.devtest

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction as Side
import mods.eln.misc.LRDU
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeElement
import mods.eln.node.transparent.TransparentNode
import mods.eln.node.transparent.TransparentNodeElement
import mods.eln.sim.mna.component.SwitchableVoltageSource
import mods.eln.sixnode.CreativePowerResistorElement
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import mods.eln.sixnode.electricalcable.*
import mods.eln.transparentnode.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

/** Real registered converters, cables and loads in the running server simulator.
 * The restart mode NEVER replaces the converter/wire/load fixtures. Creative sources/resistors
 * isolate the electrical contract from survival acquisition, which is covered by WireBehaviorChecks.
 */
class HighVoltageSmokeTest(private val restart: Boolean) {
    data class Bench(val name: String, val input: Double, val output: Double, val rating: Double,
        val primary: Double = 1.0, val secondary: Double = 2.0, val facing: Side = Side.ZP)
    companion object {
        private val benches = listOf(
            Bench("DC-DC Converter", 50.0, 800.0, 1000.0, 1.0, 16.0),
            Bench("One-way Boost vDC/DC Converter", 50.0, 3200.0, 5000.0),
            Bench("One-way Buck vDC/DC Converter", 800.0, 50.0, 1000.0),
            Bench("One-way Boost/Buck vDC/DC Converter", 50.0, 800.0, 1000.0),
            Bench("Variable DC-DC Converter", 50.0, 12800.0, 20000.0),
            Bench("One-way DC-DC Converter", 50.0, 800.0, 1000.0, 1.0, 16.0),
            Bench("Isolation Transformer", 50.0, 50.0, 1000.0, 1.0, 1.0),
            Bench("One-way Boost vDC/DC Converter", 50.0, 800.0, 1000.0, facing=Side.ZN),
            Bench("One-way Boost vDC/DC Converter", 50.0, 800.0, 1000.0, facing=Side.XN),
            Bench("One-way Boost vDC/DC Converter", 50.0, 800.0, 1000.0, facing=Side.XP)
        )
        private fun pos(i: Int) = BlockPos(64, 80, 128 + i * 8)
        private val CHAIN = BlockPos(88,80,128)
        private val FAULT = BlockPos(88,80,144)
        @JvmStatic fun register(mode: String) {
            require(mode == "hv-converters-place" || mode == "hv-converters-restart")
            NeoForge.EVENT_BUS.register(HighVoltageSmokeTest(mode.endsWith("restart")))
        }
    }
    private val suite = "hv-world-${if (restart) "restart" else "place"}"
    private val report = ContractReport(suite)
    private val world: ServerLevel get() = ServerLifecycleHooks.getCurrentServer()!!.overworld()
    private val player get() = FakePlayerFactory.getMinecraft(world)
    private var ticks = 0
    private var finished = false
    private var faultStopped = false
    private val trace = StringBuilder("tick,fixture,input_V,output_V,input_W,output_W,primary_delta_C,secondary_delta_C\n")
    private fun node(p: BlockPos) = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x,p.y,p.z,world))
    private fun machine(p: BlockPos) = (node(p) as TransparentNode).element!!
    private fun six(p: BlockPos) = (node(p) as SixNode).getElement(Side.YN)!!
    private fun offset(p: BlockPos, side: Side, n: Int): BlockPos {
        val coordinates = intArrayOf(p.x,p.y,p.z); side.applyTo(coordinates,n)
        return BlockPos(coordinates[0],coordinates[1],coordinates[2])
    }
    private fun sourcePos(i: Int) = offset(pos(i), benches[i].facing.left(),2)
    private fun loadPos(i: Int) = offset(pos(i), benches[i].facing.right(),2)
    private fun checkCase(name: String, body: () -> Unit) { report.test("eln:hv",name,body); report.write(false) }
    private fun near(actual: Double, expected: Double, tolerance: Double) {
        check(actual.isFinite() && abs(actual-expected)<=tolerance) { "$actual != $expected (tolerance $tolerance)" }
    }
    private fun sources(e: TransparentNodeElement): Pair<SwitchableVoltageSource,SwitchableVoltageSource> = when(e) {
        is DcDcElement -> e.primaryVoltageSource to e.secondaryVoltageSource
        is VariableDcDcElement -> e.primaryVoltageSource to e.secondaryVoltageSource
        is OneWayDcDcElement -> e.inputSink to e.outputSource
        else -> error("Not a converter: ${e.javaClass.name}")
    }
    private fun volts(e: TransparentNodeElement): Pair<Double,Double> = when(e) {
        is DcDcElement -> e.primaryLoad.voltage to e.secondaryLoad.voltage
        is VariableDcDcElement -> e.primaryLoad.voltage to e.secondaryLoad.voltage
        is OneWayDcDcElement -> (e.primaryLoad.voltage - if(e.isolated) e.primaryReferenceLoad.voltage else 0.0) to
            (e.secondaryLoad.voltage - if(e.isolated) e.secondaryReferenceLoad.voltage else 0.0)
        else -> error("Not a converter")
    }
    private fun winding(i: Int, slot: Int): UtilityCableDescriptor =
        dcDcWinding(machine(pos(i)).inventory!!.getItem(slot))!!.descriptor as UtilityCableDescriptor
    private fun wire(rating: Double, meters: Double = 1.0): ItemStack {
        val d = UtilityCableDescriptor.allDescriptors().single {
            it.material == UtilityCableMaterial.COPPER && it.sizeLabel == "2 AWG" &&
                it.insulated && !it.melted && it.insulationVoltageRating == rating
        }
        return d.newItemStack().also { d.setRemainingLengthMeters(it,meters) }
    }
    private fun placeSix(stack: ItemStack, p: BlockPos): SixNodeElement {
        check(!stack.isEmpty); player.setItemInHand(InteractionHand.MAIN_HAND,stack)
        Eln.sixNodeItem.onItemUse(stack,player,world,p,InteractionHand.MAIN_HAND,Direction.UP,.5f,1f,.5f)
        return six(p)
    }
    private fun placeSource(p: BlockPos, voltage: Double) {
        val e = placeSix(Eln.findItemStack("Electrical Source",1),p) as ElectricalSourceElement
        e.readConfigTool(CompoundTag().apply { putDouble("voltage",voltage) },player)
    }
    private fun placeLoad(p: BlockPos, axis: Side, resistance: Double) {
        val e = placeSix(Eln.findItemStack("Creative Power Resistor",1),p) as CreativePowerResistorElement
        e.front = LRDU.values().first { Side.YN.applyLRDU(it.right()) == axis }
        e.reconnect()
        e.readConfigTool(CompoundTag().apply { putDouble("resistance",resistance) },player)
    }
    private fun populate(p: BlockPos, b: Bench): TransparentNodeElement {
        check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack(b.name,1),player,world,p,Direction.UP))
        val e = machine(p); e.front = b.facing
        e.inventory!!.setItem(2,Eln.findItemStack("Optimal Ferromagnetic Core",1))
        e.inventory!!.setItem(0,wire(b.rating,b.primary))
        e.inventory!!.setItem(1,wire(b.rating,b.secondary))
        val settings = when(e) { is OneWayDcDcElement -> e.settings; is VariableDcDcElement -> e.settings; else -> null }
        settings?.apply { version=2; mode="VOLTAGE"; value=b.output; enabled=true }
        e.inventory!!.setChanged(); e.reconnect(); e.needPublish()
        return e
    }
    private fun setup() {
        world.setDayTime(6000); world.setWeatherParameters(100000,0,false,false)
        for(x in 3..6) for(z in 7..14) world.setChunkForced(x,z,true)
        if(restart)return
        for(p in benches.indices.map(::pos) + listOf(CHAIN,CHAIN.east(8),FAULT)) {
            for(dx in -4..4) for(dz in -4..4) {
                world.setBlockAndUpdate(p.offset(dx,-1,dz),Blocks.STONE.defaultBlockState())
                for(dy in 0..2) world.removeBlock(p.offset(dx,dy,dz),false)
            }
        }
        benches.forEachIndexed { i,b ->
            val p=pos(i); val e=populate(p,b)
            placeSix(wire(b.rating),offset(p,b.facing.left(),1))
            placeSix(wire(b.rating),offset(p,b.facing.right(),1))
            placeSource(sourcePos(i),b.input)
            placeLoad(loadPos(i),b.facing.right(),b.output*b.output/100.0)
            placeSix(Eln.findItemStack("Ground Cable",1),offset(p,b.facing.right(),3))
            if(e is OneWayDcDcElement && e.isolated) {
                placeSix(Eln.findItemStack("Ground Cable",1),offset(p,b.facing,1))
                placeSix(Eln.findItemStack("Ground Cable",1),offset(p,b.facing.back(),1))
            }
        }
        populate(CHAIN,Bench("DC-DC Converter",50.0,3200.0,5000.0,1.0,64.0))
        populate(CHAIN.east(8),Bench("DC-DC Converter",3200.0,200.0,5000.0,16.0,1.0))
        placeSource(CHAIN.west(2),50.0); placeSix(wire(5000.0),CHAIN.west())
        for(dx in 1..7)placeSix(wire(5000.0),CHAIN.east(dx))
        placeSix(wire(5000.0),CHAIN.east(9))
        placeLoad(CHAIN.east(10),Side.XP,400.0)
        placeSix(Eln.findItemStack("Ground Cable",1),CHAIN.east(11))
        // Voltage-driven insulation fault, then remove power as soon as it latches.
        val faultWire=UtilityCableDescriptor.allDescriptors().single {
            it.material==UtilityCableMaterial.COPPER && it.sizeLabel=="2 AWG" && it.insulated &&
                !it.melted && it.insulationVoltageRating==600.0
        }
        placeSix(faultWire.newCreativeTabStack(),FAULT)
        placeSource(FAULT.west(),800.0)
    }
    private fun verifyBench(i: Int) {
        val e=machine(pos(i)); val b=benches[i]; val (vin,vout)=volts(e); val (a,z)=sources(e)
        near(vin,b.input,b.input*.01+.01); near(vout,b.output,b.output*.015+.1)
        check(a.enabled && z.enabled) { "${b.name}: ${e.getWaila()}" }
        check(-a.power>90.0 && z.power>90.0) { "${b.name}: Pin=${-a.power}, Pout=${z.power}" }
        check(-a.power + .001 >= z.power) { "Generated internal power in ${b.name}" }
        check(e.thermalLoadList.all { it.temperatureCelsius.isFinite() })
        check(!(six(offset(pos(i),b.facing.right(),1)) as UtilityCableElement).insulationFailed)
    }
    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        if(finished)return
        ticks++
        try {
            if(ticks==20) {
                if(!report.test("eln:hv","setup-or-load-saved-fixtures") { setup() }) { finish();return }
                if(restart) {
                    benches.indices.forEach { i -> checkCase("saved-identity-windings-settings-$i") {
                        val e=machine(pos(i)); val b=benches[i]
                        near(dcDcWinding(e.inventory!!.getItem(0))!!.amount,b.primary,1e-6)
                        near(dcDcWinding(e.inventory!!.getItem(1))!!.amount,b.secondary,1e-6)
                        near(winding(i,1).insulationVoltageRating,b.rating,0.0)
                        val settings=when(e) { is OneWayDcDcElement -> e.settings; is VariableDcDcElement -> e.settings; else -> null }
                        settings?.let { check(it.version==2 && it.mode=="VOLTAGE" && it.enabled); near(it.value,b.output,0.0) }
                    } }
                    checkCase("insulation-fault-remains-latched-after-new-jvm") { check((six(FAULT) as UtilityCableElement).insulationFailed) }
                }
            }
            if(ticks<=20)return
            if(!restart && !faultStopped && (six(FAULT) as UtilityCableElement).insulationFailed) {
                (six(FAULT.west()) as ElectricalSourceElement).readConfigTool(CompoundTag().apply { putDouble("voltage",0.0) },player)
                faultStopped=true
            }
            if(ticks%10==0) benches.indices.forEach { i ->
                val e=machine(pos(i)); val (vi,vo)=volts(e); val (a,b)=sources(e)
                trace.append("$ticks,$i,$vi,$vo,${-a.power},${b.power},${e.thermalLoadList[0].temperatureCelsius},${e.thermalLoadList[1].temperatureCelsius}\n")
            }
            if(restart) {
                if(ticks==100) {
                    benches.indices.forEach { i -> checkCase("saved-network-resumes-$i") { verifyBench(i) } }
                    checkCase("saved-transmission-chain-resumes") { near(volts(machine(CHAIN.east(8))).second,200.0,5.0) }
                    finish()
                }; return
            }
            when(ticks) {
                100 -> {
                    checkCase("grid-cables-all-power-cables-all-devices-both-click-orders") { GridCableSmokeChecks.run(world) }
                    benches.indices.forEach { i -> checkCase("loaded-conversion-and-orientation-$i") { verifyBench(i) } }
                    checkCase("step-up-line-step-down-power-network") { near(volts(machine(CHAIN.east(8))).second,200.0,5.0) }
                    checkCase("overvoltage-latches-fault-without-inventing-all-core-short") { check(faultStopped && (six(FAULT) as UtilityCableElement).insulationFailed) }
                    world.removeBlock(sourcePos(1),false)
                }
                130 -> {
                    checkCase("input-block-removal-disconnects-converter") {
                        val e=machine(pos(1)); val (a,b)=sources(e)
                        check(!a.enabled && !b.enabled); near(b.power,0.0,1e-8); near(volts(e).second,0.0,1e-4)
                    }
                    placeSource(sourcePos(1),50.0)
                }
                150 -> {
                    checkCase("supply-reconnection-recovers") { verifyBench(1) }
                    (six(loadPos(3)) as CreativePowerResistorElement).readConfigTool(CompoundTag().apply { putDouble("resistance",8.0) },player)
                }
                170 -> {
                    checkCase("overload-respects-actual-input-current") {
                        val e=machine(pos(3)) as OneWayDcDcElement
                        check(-e.inputSink.current>1.0 && -e.inputSink.current<=e.primaryMeltCurrent+1e-4)
                        check(e.secondaryLoad.voltage < 800.0)
                    }
                    (six(loadPos(3)) as CreativePowerResistorElement).readConfigTool(CompoundTag().apply { putDouble("resistance",6400.0) },player)
                }
                220 -> {
                    checkCase("unloading-recovers-regulation") { verifyBench(3) }
                    world.removeBlock(loadPos(5),false); placeSource(loadPos(5),840.0)
                }
                240 -> {
                    checkCase("one-way-fixed-blocks-precharged-output") {
                        val e=machine(pos(5)) as OneWayDcDcElement
                        check(!e.inputSink.enabled && !e.outputSource.enabled)
                        near(e.secondaryLoad.voltage,840.0,1.0)
                    }
                    world.removeBlock(loadPos(5),false); placeLoad(loadPos(5),benches[5].facing.right(),6400.0)
                }
                300 -> {
                    benches.indices.forEach { i -> checkCase("final-state-ready-to-save-$i") { verifyBench(i) } }
                    checkCase("cable-identities-unique-and-new-ids-explicit") {
                        val descriptors=UtilityCableDescriptor.allDescriptors()
                        check(descriptors.map { it.parentItemDamage }.distinct().size==descriptors.size)
                        val hv=descriptors.filter { it.parentItemDamage in (38 shl 6)..((38 shl 6)+19) }
                        check(hv.size==20)
                        check(hv.filter { !it.melted }.map { it.insulationVoltageRating }.toSet()==setOf(1000.0,5000.0,20000.0,40000.0,150000.0))
                    }
                    finish()
                }
            }
        } catch(t: Throwable) { checkCase("unexpected-runtime-error") { throw t }; finish() }
    }
    private fun finish() {
        if(finished)return
        finished=true; report.write(true)
        val path=Path.of("../../build/smoke-artifacts/$suite.csv")
        Files.createDirectories(path.parent); Files.writeString(path,trace)
        val server=world.server
        if(report.failures>0) Thread({ server.runningThread.join(); kotlin.system.exitProcess(1) },"hv-test-failed").start()
        server.halt(false)
    }
}

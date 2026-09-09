package mods.eln.devtest

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction as Side
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.transparent.TransparentNode
import mods.eln.sim.IProcess
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import mods.eln.sixnode.electricalcable.*
import mods.eln.transparentnode.OneWayDcDcElement
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.math.abs

/** Opt-in native integration fixture. It uses the unmodified, pinned AutoPropulsion jar,
 * four placed converters and real utility cable, the actual charger and actual car battery.
 * No external ground/dummy load/creative charger supply; no fixture replacement on restart.
 */
class ConverterChargerSmokeTest(private val restart: Boolean) {
    companion object {
        @JvmStatic fun register(mode: String) {
            require(mode == "converter-charger-place" || mode == "converter-charger-restart")
            NeoForge.EVENT_BUS.register(ConverterChargerSmokeTest(mode.endsWith("restart")))
        }
        private val P = BlockPos(1024,80,1024)
        private val CHARGER = P.east(4)
        private val DIR = Path.of("../../build/smoke-artifacts")
        private val MANIFEST = DIR.resolve("converter-charger-world.json")
    }
    private val suite = "converter-charger-${if(restart) "restart" else "place"}"
    private val report = ContractReport(suite)
    private val world: ServerLevel get() = ServerLifecycleHooks.getCurrentServer()!!.overworld()
    private val player get() = FakePlayerFactory.getMinecraft(world)
    private var ticks = 0
    private var phase = 0
    private var phaseTick = 0
    private var finished = false
    private lateinit var car: Entity
    private lateinit var charger: Any
    private lateinit var carId: UUID
    private var beforeEnergy = 0.0
    private var beforeDelivered = 0.0
    private var beforeLoss = 0.0
    private var beforeOutput = 0.0
    private var outputJ = 0.0
    private var inputJ = 0.0
    private var peakSourceW = 0.0
    private var sampler: IProcess? = null
    private val trace = StringBuilder("tick,phase,busV,sourceW,outputW,inputJ,outputJ,chargerV,chargerW,deliveredJ,batteryJ,lossJ,status\n")
    private fun call(target: Any, name: String, vararg args: Any?): Any? {
        val methods = target.javaClass.methods.filter { it.name == name && it.parameterCount == args.size }
        check(methods.size == 1) { "Ambiguous/missing API ${target.javaClass.name}.$name/${args.size}" }
        return methods.single().invoke(target,*args)
    }
    private fun number(target: Any, name: String) = (call(target,name) as Number).toDouble()
    private fun batteryJ() = number(call(car,"tractionBattery")!!,"energyJ")
    private fun converter(i: Int) = (NodeManager.instance!!.getNodeFromCoordonate(Coordinate(P.x,P.y,P.z+4*i,world)) as TransparentNode).element as OneWayDcDcElement
    private fun source(i: Int) = (NodeManager.instance!!.getNodeFromCoordonate(Coordinate(P.x-2,P.y,P.z+4*i,world)) as SixNode).getElement(Side.YN) as ElectricalSourceElement
    private fun assertCase(name: String, body: () -> Unit) {
        check(report.test("eln:sparkmotors",name,body)) { "Integration assertion failed: $name" }
        report.write(false)
    }
    private fun force(value: Boolean) { for(x in 63..65)for(z in 63..65) world.setChunkForced(x,z,value) }
    private fun wire(meters: Double = 1.0) = UtilityCableDescriptor.allDescriptors().single {
        it.material == UtilityCableMaterial.COPPER && it.sizeLabel == "2 AWG" &&
            it.insulated && !it.melted && it.insulationVoltageRating == 40000.0
    }.let { d -> d.newItemStack().also { d.setRemainingLengthMeters(it,meters) } }
    private fun placeWire(p: BlockPos) {
        val stack = wire(); player.setItemInHand(InteractionHand.MAIN_HAND,stack)
        Eln.sixNodeItem.onItemUse(stack,player,world,p,InteractionHand.MAIN_HAND,Direction.UP,.5f,1f,.5f)
        check(world.getBlockEntity(p) != null) { "Native wire missing: $p" }
    }
    private fun createWorld() {
        for(x in -4..14)for(z in -4..17) {
            world.setBlockAndUpdate(P.offset(x,-1,z),Blocks.STONE.defaultBlockState())
            for(y in 0..3)world.removeBlock(P.offset(x,y,z),false)
        }
        for(i in 0..3) {
            val p=P.south(4*i)
            check(Eln.transparentNodeItem.placeBlockAt(Eln.findItemStack("One-way Boost/Buck vDC/DC Converter",1),player,world,p,Direction.UP))
            val e=converter(i);e.front=Side.ZP
            e.inventory!!.setItem(2,Eln.findItemStack("Optimal Ferromagnetic Core",1))
            e.inventory!!.setItem(0,wire(124.0));e.inventory!!.setItem(1,wire(124.0))
            e.settings.apply { version=2;mode="VOLTAGE";value=3200.0;enabled=true }
            e.inventory!!.setChanged();e.reconnect();e.needPublish()
            placeWire(p.west())
            val stack=Eln.findItemStack("Electrical Source",1);player.setItemInHand(InteractionHand.MAIN_HAND,stack)
            Eln.sixNodeItem.onItemUse(stack,player,world,p.west(2),InteractionHand.MAIN_HAND,Direction.UP,.5f,1f,.5f)
            source(i).readConfigTool(CompoundTag().apply { putDouble("voltage",300.0) },player)
            placeWire(p.east());placeWire(p.east(2))
        }
        for(z in 0..12)placeWire(P.offset(3,0,z))
        val block=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("sparkmotors","ultra_charger"))
        check(block != Blocks.AIR) { "Pinned production charger is not loaded" }
        world.setBlockAndUpdate(CHARGER,block.defaultBlockState())
        charger=world.getBlockEntity(CHARGER)!!
        car=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("sparkmotors","sedan")).create(world)!!
        car.moveTo(P.x+8.5,P.y.toDouble(),P.z+2.5,0f,0f)
        car.setNoGravity(true)
        val type=Class.forName("com.photonspark.sparkmotors.sim.electric.Powertrain").enumConstants.first { it.toString()=="ELECTRIC_400" }
        call(car,"initializePowertrain",type,.25)
        call(car,"setOwner",player.uuid)
        check(world.addFreshEntity(car))
        carId=car.uuid
    }
    private fun startSampler() {
        sampler=IProcess { dt ->
            if(!finished && phase !in 4..5) {
                val elements=(0..3).map { runCatching { converter(it) }.getOrNull() }
                if(elements.all { it!=null }) {
                    val out=elements.sumOf { it!!.outputSource.power }.coerceAtLeast(0.0)
                    val input=elements.sumOf { -it!!.inputSink.power }.coerceAtLeast(0.0)
                    outputJ+=out*dt;inputJ+=input*dt;peakSourceW=maxOf(peakSourceW,input)
                }
            }
        }.also { Eln.simulator.addElectricalProcess(it) }
    }
    private fun setup() {
        Files.createDirectories(DIR);world.setDayTime(6000);world.setWeatherParameters(100000,0,false,false)
        force(true)
        if(!restart) createWorld()
        else {
            val data=JsonParser.parseString(Files.readString(MANIFEST)).asJsonObject
            carId=UUID.fromString(data["car"].asString)
        }
        assertCase("actual-companion-mod-loaded") { check(net.neoforged.fml.ModList.get().isLoaded("sparkmotors")) }
        startSampler()
    }
    private fun recoverWorld(): Boolean {
        val loaded=world.getEntity(carId) ?: return false
        val entity=world.getBlockEntity(CHARGER) ?: return false
        car=loaded;charger=entity
        return true
    }
    private fun pair() {
        player.moveTo(car.position())
        check(call(charger,"connect",player,car) == true) { "Native charger refused vehicle pairing" }
        check(call(charger,"creativePower") == false) { "Creative power is forbidden in this fixture" }
        beforeEnergy=batteryJ();beforeDelivered=number(charger,"deliveredJ")
        beforeLoss=number(charger,"lossJ");beforeOutput=outputJ
    }
    private fun verifyCharge(prefix: String) {
        assertCase("$prefix-native-charger-draws-power") { check(number(charger,"inputKw")>1.0) { "${call(charger,"status")} ${number(charger,"inputVoltage")} V" } }
        assertCase("$prefix-real-battery-gains-energy") { check(batteryJ()>beforeEnergy+1000) }
        assertCase("$prefix-energy-conserved-through-charger-and-battery") {
            val stored=batteryJ()-beforeEnergy; val drawn=number(charger,"deliveredJ")-beforeDelivered
            val losses=number(charger,"lossJ")-beforeLoss
            check(stored<=drawn+1e-6 && losses>=0) { "stored=$stored drawn=$drawn losses=$losses" }
            // The car also powers its real auxiliary loads. They may decrease stored energy.
            check(stored+losses<=drawn+1.0) { "stored+loss=$stored+$losses > $drawn" }
            // Charger buffer contains at most 0.1 s of its input rating. This explicit
            // boundary allowance is recorded; it is not a converter energy tolerance.
            check(drawn<=outputJ-beforeOutput+32000.0) { "delivered=$drawn converterOutput=${outputJ-beforeOutput}" }
        }
        assertCase("$prefix-four-live-converters-no-latched-fault") {
            for(i in 0..3) { val e=converter(i); check(e.outputSource.enabled && e.primaryLoad.voltage>200) { e.getWaila().toString() } }
            check(peakSourceW>1000 && converter(0).secondaryLoad.voltage in 3190.0..3200.1)
        }
    }
    private fun verifyIdle(prefix: String) {
        assertCase("$prefix-no-load-output-stays-regulated") {
            for(i in 0..3)check(converter(i).outputSource.enabled && converter(i).secondaryLoad.voltage in 3190.0..3200.1)
        }
        assertCase("$prefix-no-phantom-charging-or-large-bleeder") {
            check(number(charger,"inputKw")==0.0 && batteryJ()<=beforeEnergy+1e-6)
            check((0..3).sumOf { abs(converter(it).outputSource.power) }<1.0)
        }
    }
    private fun record() {
        if(phase in 4..5 || !::car.isInitialized)return
        trace.append("$ticks,$phase,${converter(0).secondaryLoad.voltage},${(0..3).sumOf { -converter(it).inputSink.power }},${(0..3).sumOf { converter(it).outputSource.power }},$inputJ,$outputJ,${number(charger,"inputVoltage")},${number(charger,"inputKw")*1000},${number(charger,"deliveredJ")},${batteryJ()},${number(charger,"lossJ")},${call(charger,"status")}\n")
    }
    private fun next(p: Int) { phase=p;phaseTick=ticks }
    private fun finish() {
        finished=true;sampler?.let { Eln.simulator.removeElectricalProcess(it) }
        report.write(true);Files.writeString(DIR.resolve("$suite.csv"),trace)
        ServerLifecycleHooks.getCurrentServer()!!.saveEverything(false,true,true)
        ServerLifecycleHooks.getCurrentServer()!!.halt(false)
    }
    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        if(finished)return
        ticks++
        try {
            if(ticks==10) { setup();next(0) }
            if(ticks<10)return
            val elapsed=ticks-phaseTick
            if(phase !in 4..5 && ::car.isInitialized)record()
            when(phase) {
                0 -> if(elapsed>=90 && (!restart || recoverWorld())) {
                    if(restart) {
                        val data=JsonParser.parseString(Files.readString(MANIFEST)).asJsonObject
                        assertCase("separate-jvm-saved-car-energy-preserved") { check(abs(batteryJ()-data["batteryJ"].asDouble)<10.0) }
                        assertCase("separate-jvm-connection-lease-expired") { check(call(charger,"connected")==false && number(charger,"inputKw")==0.0) }
                        assertCase("separate-jvm-converter-settings-and-windings-preserved") { for(i in 0..3) { val e=converter(i);check(e.settings.mode=="VOLTAGE" && e.settings.value==3200.0 && e.settings.version==2); check(e.inventory!!.getItem(0).isEmpty.not()) } }
                    }
                    assertCase("no-external-ground-or-dummy-load") {
                        check(call(charger,"creativePower")==false)
                        for(x in -3..5)for(z in 0..12) {
                            val n=NodeManager.instance!!.getNodeFromCoordonate(Coordinate(P.x+x,P.y,P.z+z,world))
                            if(n is SixNode) {
                                val e=n.getElement(Side.YN)
                                check(e !is mods.eln.sixnode.CreativePowerResistorElement && e?.javaClass?.simpleName?.contains("Ground",true)!=true)
                            }
                        }
                    }
                    beforeEnergy=batteryJ();verifyIdle("initial");pair();next(1)
                }
                1 -> if(elapsed>=160) { verifyCharge("initial");call(charger,"disconnect");beforeEnergy=batteryJ();next(2) }
                2 -> if(elapsed>=50) { verifyIdle("unplugged");pair();next(3) }
                3 -> if(elapsed>=120) {
                    verifyCharge("replugged");call(charger,"disconnect");beforeEnergy=batteryJ()
                    if(restart) { next(7) } else { next(4);force(false) }
                }
                4 -> {
                    if(world.getChunkSource().getChunkNow(P.x shr 4,P.z shr 4)==null && world.getEntity(carId)==null) {
                        assertCase("actual-chunk-unload-closes-native-charger-port") {
                            val field=charger.javaClass.getDeclaredField("port");field.isAccessible=true;check(field.get(charger)==null)
                        }
                        force(true);next(5)
                    } else check(elapsed<900) { "Test chunk did not actually unload; no simulated lifecycle pass" }
                }
                5 -> if(elapsed>=60 && recoverWorld()) {
                    assertCase("real-chunk-reload-preserves-car-and-expires-lease") { check(call(charger,"connected")==false && batteryJ()<=beforeEnergy+1.0) }
                    pair();next(6)
                } else check(elapsed<500) { "Saved charger/car did not reload" }
                6 -> if(elapsed>=160) { verifyCharge("chunk-reloaded");call(charger,"disconnect");beforeEnergy=batteryJ();next(7) }
                7 -> if(elapsed>=50) {
                    verifyIdle("final")
                    if(!restart)Files.writeString(MANIFEST,GsonBuilder().setPrettyPrinting().create().toJson(mapOf("car" to carId.toString(),"batteryJ" to batteryJ(),"inputJ" to inputJ,"outputJ" to outputJ,"chargerDeliveredJ" to number(charger,"deliveredJ"))))
                    finish()
                }
            }
            check(ticks<2200) { "Integration timed out in phase $phase" }
        } catch(t: Throwable) {
            report.test("eln:sparkmotors","uncaught-runtime-failure") { throw t };finish()
        }
    }
}

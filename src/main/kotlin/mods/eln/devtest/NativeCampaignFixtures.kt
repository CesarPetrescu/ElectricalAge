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
import mods.eln.sixnode.CreativePowerResistorElement
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import mods.eln.sixnode.electricalcable.*
import mods.eln.sixnode.logicgate.*
import mods.eln.transparentnode.*
import mods.eln.transparentnode.battery.*
import mods.eln.mechanical.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import kotlin.math.abs
import kotlin.random.Random

/** Server-thread fixtures; normal production simulation advances every physical state.
 * Creative supplies/resistors are declared boundary conditions, not internal energy injection.
 */
internal class NativeCampaignFixtures(val world: ServerLevel, val player: ServerPlayer) {
    data class Step(val id: String, val title: String, val target: BlockPos,
        val components: List<String>, val waitTicks: Long = 20,
        val begin: () -> Unit = {}, val sample: () -> Map<String, Any> = { emptyMap() },
        val verify: () -> Map<String, Any>, val view: String = "world", val end: () -> Unit = {})
    val steps = mutableListOf<Step>()
    val retained = linkedSetOf<BlockPos>()
    private var nextCell = 0
    fun node(p: BlockPos) = NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x,p.y,p.z,world))
    fun machine(p: BlockPos) = (node(p) as TransparentNode).element!!
    fun six(p: BlockPos) = (node(p) as SixNode).getElement(Side.YN)!!
    fun id(e: TransparentNodeElement) = BuiltInRegistries.ITEM.getKey(e.descriptor!!.parentItem).toString()
    fun id(e: SixNodeElement) = BuiltInRegistries.ITEM.getKey(e.sixNodeElementDescriptor.parentItem).toString()
    fun cell(): BlockPos {
        val p = BlockPos(1280 + (nextCell % 6)*20,80,1280+(nextCell/6)*24);nextCell++
        for(x in -5..9) for(z in -5..17) {
            world.setChunkForced((p.x+x) shr 4,(p.z+z) shr 4,true)
            world.setBlockAndUpdate(p.offset(x,-1,z),Blocks.STONE.defaultBlockState())
            for(y in 0..3)world.removeBlock(p.offset(x,y,z),false)
        }
        return p
    }
    fun placeSix(stack: ItemStack,p: BlockPos): SixNodeElement {
        check(!stack.isEmpty);player.setItemInHand(InteractionHand.MAIN_HAND,stack)
        Eln.sixNodeItem.onItemUse(stack,player,world,p,InteractionHand.MAIN_HAND,Direction.UP,.5f,1f,.5f)
        return six(p)
    }
    fun placeMachine(stack: ItemStack,p: BlockPos): TransparentNodeElement {
        player.yRot=0f;player.xRot=0f
        check(Eln.transparentNodeItem.placeBlockAt(stack,player,world,p,Direction.UP))
        retained.add(p)
        return machine(p)
    }
    fun wire(p: BlockPos,signal: Boolean = false) {
        placeSix(if(signal)Eln.findItemStack("Signal Cable",1) else spool(),p)
    }
    fun spool(meters: Double = 1.0): ItemStack = UtilityCableDescriptor.allDescriptors().single {
        it.material==UtilityCableMaterial.COPPER && it.sizeLabel=="2 AWG" && it.insulated && !it.melted && it.insulationVoltageRating==40000.0
    }.let { d -> d.newItemStack().also { d.setRemainingLengthMeters(it,meters) } }
    fun source(p: BlockPos,volts: Double,signal: Boolean = false) {
        val e=placeSix(Eln.findItemStack(if(signal)"Signal Source" else "Electrical Source",1),p) as ElectricalSourceElement
        e.readConfigTool(CompoundTag().apply { putDouble("voltage",volts) },player)
    }
    fun voltage(p: BlockPos,volts: Double) = (six(p) as ElectricalSourceElement)
        .readConfigTool(CompoundTag().apply { putDouble("voltage",volts) },player)
    fun load(p: BlockPos,resistance: Double,axis: Side = Side.XP) {
        val e=if(node(p)==null)placeSix(Eln.findItemStack("Creative Power Resistor",1),p) as CreativePowerResistorElement else six(p) as CreativePowerResistorElement
        e.front=LRDU.values().first { Side.YN.applyLRDU(it.right())==axis };e.reconnect()
        e.readConfigTool(CompoundTag().apply { putDouble("resistance",resistance) },player)
    }
    fun ground(p: BlockPos) { placeSix(Eln.findItemStack("Ground Cable",1),p) }
    fun near(a: Double,b: Double,t: Double) { check(a.isFinite() && abs(a-b)<=t) { "$a != $b (tolerance $t)" } }
    fun setupConverter(p: BlockPos,name: String,vin: Double,vout: Double,primary: Double=1.0,secondary: Double=1.0): TransparentNodeElement {
        val e=placeMachine(Eln.findItemStack(name,1),p);e.front=Side.ZP
        e.inventory!!.setItem(2,Eln.findItemStack("Optimal Ferromagnetic Core",1))
        e.inventory!!.setItem(0,spool(primary));e.inventory!!.setItem(1,spool(secondary))
        when(e) {
            is OneWayDcDcElement -> e.settings.apply { version=2;mode="VOLTAGE";value=vout;enabled=true }
            is VariableDcDcElement -> e.settings.apply { version=2;mode="RATIO";value=vout/vin;enabled=true }
        }
        e.inventory!!.setChanged();e.reconnect();e.needPublish()
        wire(p.west());wire(p.east());source(p.west(2),vin)
        if(e is OneWayDcDcElement && e.isolated) { ground(p.north());ground(p.south()) }
        return e
    }
    fun converterValues(e: TransparentNodeElement): Map<String,Any> {
        val data=when(e) {
            is OneWayDcDcElement -> listOf(e.primaryLoad.voltage-(if(e.isolated)e.primaryReferenceLoad.voltage else 0.0),e.secondaryLoad.voltage-(if(e.isolated)e.secondaryReferenceLoad.voltage else 0.0),-e.inputSink.power,e.outputSource.power,-e.inputSink.current,e.outputSource.current)
            is DcDcElement -> listOf(e.primaryLoad.voltage,e.secondaryLoad.voltage,-e.primaryVoltageSource.power,e.secondaryVoltageSource.power,-e.primaryVoltageSource.current,e.secondaryVoltageSource.current)
            is VariableDcDcElement -> listOf(e.primaryLoad.voltage,e.secondaryLoad.voltage,-e.primaryVoltageSource.power,e.secondaryVoltageSource.power,-e.primaryVoltageSource.current,e.secondaryVoltageSource.current)
            else -> error("Unsupported converter ${e.javaClass}")
        }
        check(data.all { it.isFinite() }) { "Nonfinite native converter telemetry: $data" }
        if(e is OneWayDcDcElement) {
            check(e.transferStatus !in setOf("NON_CONVERGENT","INVALID_NETWORK")) { e.getWaila().toString() }
            check(data[4]>=-1e-6 && data[4]<=e.primaryMeltCurrent*1.000001+1e-6)
            check(data[5]>=-1e-6 && data[5]<=e.secondaryMeltCurrent*1.000001+1e-6)
        }
        check(data[3]<=data[2]+.05) { "Converter creates power: input=${data[2]} output=${data[3]}" }
        return listOf("inputV","outputV","inputW","outputW","inputA","outputA").zip(data).toMap() + mapOf("status" to if(e is OneWayDcDcElement)e.transferStatus else "ratio-coupled")
    }
    private fun power() {
        val cases=listOf(
            Triple("DC-DC Converter",50.0,800.0), Triple("One-way DC-DC Converter",50.0,800.0),
            Triple("Variable DC-DC Converter",50.0,12800.0), Triple("One-way Boost vDC/DC Converter",50.0,3200.0),
            Triple("One-way Buck vDC/DC Converter",800.0,50.0), Triple("One-way Boost/Buck vDC/DC Converter",300.0,3200.0),
            Triple("Isolation Transformer",50.0,50.0))
        cases.forEachIndexed { n,(name,vin,vout) ->
            val p=cell();val fixed=name in setOf("DC-DC Converter","One-way DC-DC Converter")
            val e=setupConverter(p,name,vin,vout,1.0,if(fixed)vout/vin else 1.0)
            val component=listOf(id(e));val r=vout*vout/100.0
            steps+=Step("converter-$n-loaded","$name: $vin V to $vout V at 100 W",p,component,30,
                begin={load(p.east(2),r);ground(p.east(3))},sample={converterValues(e)},verify={
                    val v=converterValues(e);near(v["outputV"] as Double,vout,vout*.02+.1);check(v["outputW"] as Double>90);v
                })
            steps+=Step("converter-$n-open","$name: genuinely open output, no dummy load",p,component,10,
                begin={world.removeBlock(p.east(2),false);world.removeBlock(p.east(3),false)},sample={converterValues(e)},verify={
                    val v=converterValues(e);near(v["outputV"] as Double,vout,vout*.02+.1);check(abs(v["outputW"] as Double)<.01);v
                })
            if(name=="One-way Boost/Buck vDC/DC Converter") {
                steps+=Step("converter-native-voltage-entry","Type 1600 V in the native converter GUI and observe live output",p,component,15,
                    sample={converterValues(e)},verify={val v=converterValues(e);check((e as OneWayDcDcElement).settings.value==1600.0);near(v["outputV"] as Double,1600.0,2.0);v},view="converter:1600")
            }
        }
        val ui=cell();source(ui,50.0);wire(ui.east());load(ui.east(2),100.0);ground(ui.east(3))
        steps+=Step("source-native-voltage-entry","Type 25 V in the actual source GUI; server and live cable must change",ui,listOf("eln:electrical_source"),15,
            verify={val v=six(ui).getElectricalLoad(LRDU.Down,0)!!.voltage;near(v,25.0,.001);mapOf("observedServerV" to v,"typedText" to "25")},view="source:25")
        val p=cell()
        val group=(0..3).map { i -> setupConverter(p.south(i*4),"One-way Boost/Buck vDC/DC Converter",400.0,3200.0,124.0,124.0) as OneWayDcDcElement }
        for(i in 0..3)wire(p.east(2).south(i*4))
        for(z in 0..12)wire(p.east(3).south(z))
        ground(p.east(5));load(p.east(4),1000.0)
        val components=listOf(id(group.first()))
        fun measurements(): Map<String,Any> {
            val all=group.map(::converterValues)
            return mapOf("busV" to group.first().secondaryLoad.voltage,"inputW" to all.sumOf { it["inputW"] as Double },"outputW" to all.sumOf { it["outputW"] as Double },"converters" to all)
        }
        steps+=Step("parallel-four-rated","Four converters, shared 3.2 kV bus",p.east(1).south(6),components,30,sample=::measurements,verify={ measurements().also { near(it["busV"] as Double,3200.0,10.0) } })
        steps+=Step("parallel-four-overload","10 ohm overload: current limiting, no numerical trip",p.east(1).south(6),components,6,
            begin={load(p.east(4),10.0)},sample=::measurements,verify={ measurements().also { check((it["busV"] as Double) in 1.0..3100.0);check(group.any { -it.inputSink.current>it.primaryMeltCurrent*.98 }) } },end={load(p.east(4),1000.0)})
        steps+=Step("parallel-four-recovery","Restore light load without resetting converters",p.east(1).south(6),components,10,
            begin={load(p.east(4),1000.0)},sample=::measurements,verify={ measurements().also { near(it["busV"] as Double,3200.0,10.0) } })
        steps+=Step("parallel-four-missing-input","Disconnect one supply while other sources remain",p.east(1).south(6),components,10,
            begin={world.removeBlock(p.west(2),false)},sample=::measurements,verify={ measurements().also { near(it["busV"] as Double,3200.0,10.0);check(abs(group[0].outputSource.power)<.001) } })
        steps+=Step("parallel-four-unequal-input","Reconnect unequal 241/300/360/400 V supplies",p.east(1).south(6),components,15,
            begin={source(p.west(2),241.0);voltage(p.south(4).west(2),300.0);voltage(p.south(8).west(2),360.0)},sample=::measurements,verify={ measurements().also { near(it["busV"] as Double,3200.0,10.0) } })
        val random=Random(20260909)
        repeat(8) { n -> val resistance=500.0+random.nextDouble()*5000.0
            steps+=Step("parallel-seed-20260909-$n","Seeded live shared-bus load change $n",p.east(1).south(6),components,8,
                begin={load(p.east(4),resistance)},sample=::measurements,verify={measurements().also { near(it["busV"] as Double,3200.0,10.0) }+mapOf("loadOhms" to resistance,"seed" to 20260909)})
        }
    }
    private fun batteries() {
        val descriptors=Eln.transparentNodeItem.subItemList.values.filterIsInstance<BatteryDescriptor>().sortedBy { it.name }
        check(descriptors.size>=7)
        descriptors.forEach { d ->
            val p=cell();val e=placeMachine(d.newItemStack(1),p) as BatteryElement;e.front=Side.ZP;e.reconnect()
            wire(p.west());wire(p.east());ground(p.east(2));val resistance=d.electricalU*d.electricalU/(d.electricalStdP*.1)
            ground(p.west(3));var before=0.0
            fun measure()=mapOf<String,Any>("charge" to e.batteryProcess.charge,"energyJ" to e.batteryProcess.energy,"voltageV" to (e.positiveLoad.voltage-e.negativeLoad.voltage),"currentA" to e.batteryProcess.dischargeCurrent,"temperatureC" to e.thermalLoad.temperatureCelsius)
            val key=BuiltInRegistries.ITEM.getKey(d.parentItem).path
            steps+=Step("battery-$key-discharge","${d.name}: finite stored energy into a real resistor",p,listOf(id(e)),40,
                begin={before=e.batteryProcess.energy;load(p.west(2),resistance)},sample=::measure,verify={
                    check(e.batteryProcess.energy in 0.0..<before);check(e.batteryProcess.dischargeCurrent>0);measure()+mapOf("beforeJ" to before)
                })
            steps+=Step("battery-$key-open","${d.name}: disconnect load; no phantom discharge current",p,listOf(id(e)),10,
                begin={world.removeBlock(p.west(2),false)},sample=::measure,verify={check(abs(e.batteryProcess.dischargeCurrent)<.1);measure()})
        }
    }
    private fun logic() {
        val descriptors=Eln.sixNodeItem.subItemList.values.filterIsInstance<LogicGateDescriptor>().sortedBy { it.name }
        check(descriptors.size>=12)
        for(d in descriptors) {
            val p=cell();val gate=placeSix(d.newItemStack(1),p) as LogicGateElement;retained.add(p)
            gate.front=Side.YN.getLRDUGoingTo(Side.XP)!!;gate.reconnect();wire(p.east(),true)
            val inputDirs=listOf(gate.front.inverse(),gate.front.left(),gate.front.right()).take(d.function.inputCount)
            fun offset(side: Side,n: Int): BlockPos { val v=intArrayOf(p.x,p.y,p.z);side.applyTo(v,n);return BlockPos(v[0],v[1],v[2]) }
            inputDirs.forEach { dir -> val side=Side.YN.applyLRDU(dir);wire(offset(side,1),true);source(offset(side,2),0.0,true) }
            val component=listOf(id(gate));val kind=d.function.javaClass.simpleName
            fun values()=mapOf<String,Any>("inputsV" to inputDirs.map { gate.getElectricalLoad(it,0)!!.voltage },"outputV" to gate.getElectricalLoad(gate.front,0)!!.voltage,"function" to kind)
            fun add(suffix:String,input:List<Double>,expected:Boolean) {
                steps+=Step("logic-${kind.lowercase()}-$suffix","${d.name}: $input -> ${if(expected)1 else 0}",p,component,8,
                    begin={input.forEachIndexed { i,v -> voltage(offset(Side.YN.applyLRDU(inputDirs[i]),2),v) }},sample=::values,verify={
                        val output=gate.getElectricalLoad(gate.front,0)!!.voltage;check(if(expected)output>40 else abs(output)<10) { "Expected $expected, observed $output V; ${values()}" };values()+mapOf("expectedHigh" to expected)
                    })
            }
            when(kind) {
                "Not","And","Nand","Or","Nor","Xor","XNor","Pal" -> repeat(1 shl d.function.inputCount) { bits ->
                    val input=(0 until d.function.inputCount).map { bits and (1 shl it)!=0 }
                    val expected=when(kind) { "Not" -> !input[0];"And"->input.all { it };"Nand"->!input.all { it };"Or"->input.any { it };"Nor"->!input.any { it };"Xor"->input.count { it }%2==1;"XNor"->input.count { it }%2==0;else->false }
                    add("truth-$bits",input.map { if(it)50.0 else 0.0 },expected)
                }
                "SchmittTrigger" -> { add("low",listOf(0.0),false);add("high",listOf(40.0),true);add("hold",listOf(20.0),true);add("reset",listOf(0.0),false) }
                "DFlipFlop" -> { add("clear",listOf(0.0,0.0),false);add("data",listOf(50.0,0.0),false);add("edge",listOf(50.0,50.0),true);add("hold",listOf(0.0,50.0),true);add("fall",listOf(0.0,0.0),true);add("clear-edge",listOf(0.0,50.0),false) }
                "JKFlipFlop" -> { add("idle",listOf(0.0,50.0,0.0),false);add("set",listOf(50.0,50.0,0.0),true);add("fall",listOf(0.0,50.0,50.0),true);add("toggle",listOf(50.0,50.0,50.0),false) }
                "Oscillator" -> {
                    var transitions=0;var previous=false
                    steps+=Step("logic-oscillator-pulses","${d.name}: observe real transitions over four seconds",p,component,80,
                        begin={voltage(offset(Side.YN.applyLRDU(inputDirs[0]),2),50.0);previous=gate.getElectricalLoad(gate.front,0)!!.voltage>25},
                        sample={val high=gate.getElectricalLoad(gate.front,0)!!.voltage>25;if(high!=previous)transitions++;previous=high;values()},
                        verify={check(transitions>=1) { "No native oscillator transition" };values()+mapOf("transitions" to transitions)})
                }
                else -> error("New logic function requires an independent native oracle: $kind")
            }
        }
        val base=cell()
        for(x in -3..25){world.setChunkForced((base.x+x) shr 4,base.z shr 4,true);world.setBlockAndUpdate(base.offset(x,-1,0),Blocks.STONE.defaultBlockState())}
        val descriptor=descriptors.single { it.function is Not }
        val chain=(0..7).map { i -> val p=base.east(i*3)
            val e=placeSix(descriptor.newItemStack(1),p) as LogicGateElement;retained.add(p);e.front=Side.YN.getLRDUGoingTo(Side.XP)!!;e.reconnect()
            if(i<7){wire(p.east(),true);wire(p.east(2),true)};e
        }
        wire(base.west(),true);source(base.west(2),0.0,true)
        for(high in listOf(false,true,false)) {
            val suffix=steps.count { it.id.startsWith("logic-chain") }
            steps+=Step("logic-chain-$suffix","Eight physical NOT gates: propagate ${if(high)50 else 0} V",base.east(10),listOf("eln:not_chip","eln:signal_cable"),20,
                begin={voltage(base.west(2),if(high)50.0 else 0.0)},verify={
                    val outputs=chain.map { it.getElectricalLoad(it.front,0)!!.voltage }
                    outputs.forEachIndexed { i,v -> val expected=if(i%2==0)!high else high;check(if(expected)v>40 else abs(v)<10) { "Gate $i did not propagate: $outputs" } }
                    mapOf("inputHigh" to high,"outputVolts" to outputs)
                })
        }
    }
    private fun mechanical() {
        val p=BlockPos(512,65,526)
        fun motor()=machine(p) as MotorElement
        fun gen()=machine(p.east(4)) as GeneratorElement
        fun measurements()=mapOf<String,Any>("speedRadS" to motor().shaft.rads,"shaftJ" to motor().shaft.energy,"generatorW" to gen().electricalPowerSource.power,"generatorV" to gen().inputLoad.voltage,"sharedNetwork" to (motor().shaft===gen().shaft))
        val component=(0..4).map { retained.add(p.east(it));id(machine(p.east(it))) }
        steps+=Step("shaft-loaded","Motor, joint, tachometer, flywheel and generator under load",p.east(2),component,80,sample=::measurements,verify={
            check(motor().shaft===gen().shaft);check(motor().shaft.rads>motor().desc.nominalRads*.7);check(gen().electricalPowerSource.power>1);measurements()
        })
        var energy=0.0
        steps+=Step("shaft-coasting","Remove electrical supply: flywheel pays for remaining output",p.east(2),component,30,
            begin={energy=motor().shaft.energy;world.removeBlock(p.north().west(),false)},sample=::measurements,verify={check(motor().shaft.energy in 0.0..<energy);measurements()+mapOf("initialShaftJ" to energy)})
        steps+=Step("shaft-split","Break the flywheel; no invisible mechanical connection",p.east(2),component,20,
            begin={world.destroyBlock(p.east(3),false)},sample=::measurements,verify={check(node(p.east(3))==null);check(motor().shaft!==gen().shaft);measurements()})
        steps+=Step("shaft-reconnect","Replace flywheel and supply; recover loaded generation",p.east(2),component,160,
            begin={placeMachine(Eln.findItemStack("Flywheel",1),p.east(3));source(p.north().west(),480.0)},sample=::measurements,verify={check(motor().shaft===gen().shaft);check(gen().electricalPowerSource.power>1);measurements()})
        val large=BlockPos(512,65,533)
        steps+=Step("large-shaft-loaded","Large motor and generator with elevated shaft connector",large.east(2),listOf(id(machine(large)),id(machine(large.east(4)))),40,verify={
            val m=machine(large) as MotorElement;val g=machine(large.east(4)) as GeneratorElement
            check(m.shaft===g.shaft && g.electricalPowerSource.power>1);mapOf("speedRadS" to m.shaft.rads,"outputW" to g.electricalPowerSource.power)
        })
        steps+=Step("large-shaft-remove","Remove multiblock motor and require ghost teardown",large.east(2),listOf("eln:large_shaft_motor"),15,
            begin={world.destroyBlock(large,false)},verify={check(node(large)==null);check(BlockPos.betweenClosed(large.offset(-1,0,-1),large.offset(1,2,1)).none { world.getBlockState(it).block==Eln.ghostBlock });mapOf("ghostsRemaining" to 0)})
    }
    private fun thermal() {
        val p=BlockPos(512,65,547)
        fun furnace()=machine(p) as mods.eln.transparentnode.heatfurnace.HeatFurnaceElement
        val turbine=p.west().south();retained.add(p);retained.add(turbine)
        steps+=Step("fuel-thermal-electric","Coal -> furnace -> thermal cable -> turbine -> electrical resistor",p.south(2),listOf(id(furnace()),id(machine(turbine))),100,verify={
            val t=machine(turbine) as mods.eln.transparentnode.turbine.TurbineElement
            val cable=six(turbine.south()) as mods.eln.sixnode.electricalcable.ElectricalCableElement
            check(furnace().thermalLoad.temperature>80);check(cable.electricalLoad.voltage>20 && cable.electricalLoad.current>.1)
            mapOf("furnaceDeltaC" to furnace().thermalLoad.temperature,"outputV" to cable.electricalLoad.voltage,"outputA" to cable.electricalLoad.current,"turbineMeter" to t.multiMeterString(Side.ZN))
        })
        steps+=Step("thermal-break","Break thermal link; the simulator must remove the connection",p.south(2),listOf("eln:copper_thermal_cable"),10,
            begin={world.destroyBlock(p.south(),false)},verify={check(node(p.south())==null);mapOf("linkRemoved" to true,"furnaceDeltaC" to furnace().thermalLoad.temperature)})
        steps+=Step("thermal-reconnect","Replace thermal cable through its real placement path",p.south(2),listOf("eln:copper_thermal_cable"),60,
            begin={placeSix(Eln.findItemStack("Copper Thermal Cable",1),p.south())},verify={check(node(p.south())!=null);val c=six(turbine.south()) as mods.eln.sixnode.electricalcable.ElectricalCableElement;check(c.electricalLoad.voltage>0);mapOf("outputV" to c.electricalLoad.voltage,"outputA" to c.electricalLoad.current)})
    }
    fun prepare(suite: String): List<Step> {
        when(suite) { "power" -> power();"logic" -> logic();"mechanical" -> mechanical();"storage-thermal" -> {batteries();thermal()};else->error("Unknown native suite $suite") }
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY)
        check(steps.isNotEmpty() && steps.map { it.id }.distinct().size==steps.size)
        return steps
    }
}

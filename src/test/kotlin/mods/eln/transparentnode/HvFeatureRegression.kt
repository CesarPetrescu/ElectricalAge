package mods.eln.transparentnode

import mods.eln.sim.mna.RootSystem
import mods.eln.sim.mna.component.*
import mods.eln.sim.mna.state.VoltageState
import mods.eln.sim.power.*
import mods.eln.sim.process.heater.ElectricalHeatAccumulator
import mods.eln.sixnode.electricalcable.*
import net.minecraft.nbt.CompoundTag
import java.io.*
import kotlin.math.abs

/** Shared by the offline executable and the real FML/JUnit suite. */
object HvFeatureRegression {
    private var assertions = 0
    private fun verify(ok: Boolean, message: String) { assertions++; check(ok) { message } }
    private fun near(a: Double, b: Double, message: String, eps: Double = 1e-8) =
        verify(a.isFinite() && b.isFinite() && abs(a-b)<=eps, "$message: $a vs $b")

    @JvmStatic fun runAll(): Int {
        assertions = 0
        controls()
        windings()
        insulation()
        catalogue()
        println("HV feature regression: $assertions assertions passed")
        return assertions
    }

    private fun controls() {
        near(1.0/256, ControlMapping.ratio(ConverterKind.VARIABLE,0.0,MappingVersion.LOGARITHMIC_V2), "new min")
        near(1.0, ControlMapping.ratio(ConverterKind.VARIABLE,0.5,MappingVersion.LOGARITHMIC_V2), "unity midpoint")
        near(256.0, ControlMapping.ratio(ConverterKind.VARIABLE,1.0,MappingVersion.LOGARITHMIC_V2), "new max")
        near(128.001953125, ControlMapping.ratio(ConverterKind.VARIABLE,.5,MappingVersion.LEGACY_LINEAR_V1), "old formula unchanged")
        val legacy = ConverterControlSettings().apply { readNbt(CompoundTag(),true) }
        verify(legacy.mapping == MappingVersion.LEGACY_LINEAR_V1 && legacy.mode == ConverterInputMode.EXTERNAL_SIGNAL, "old saves retain external legacy control")
        near(50.0,legacy.ratio(ConverterKind.BOOST,1.0),"old boost remains 50x")
        val new = ConverterControlSettings()
        near(1.0,new.ratio(ConverterKind.VARIABLE,0.0),"new machine starts manual unity")
        verify(!new.setValue(Double.NaN) && !new.setValue(Double.POSITIVE_INFINITY), "reject invalid manual requests")
        verify(!new.setValue(0.0) && !new.setValue(257.0),"ratio bounds")
        verify(!new.setMode(20,true) && !new.setMode(2,false),"invalid modes rejected")
        verify(new.setValue(64.0),"manual 64x accepted")
        near(64.0,new.ratio(ConverterKind.BOOST,0.0),"manual high conversion")
        verify(new.ratio(ConverterKind.BUCK,0.0).isNaN(),"buck does not boost")
        verify(new.setMode(2,true) && new.setValue(3200.0),"manual voltage")
        near(3200.0,new.voltageTarget()!!,"voltage target independent of signal")
        verify(!new.setValue(120001.0),"voltage hardware ceiling")
        val tag = CompoundTag();new.writeNbt(tag)
        val loaded = ConverterControlSettings().apply { readNbt(tag,true) }
        near(3200.0,loaded.voltageTarget()!!,"save target round trip")
        val bytes = ByteArrayOutputStream();new.writeWire(DataOutputStream(bytes))
        val client = ConverterControlSettings().apply { readWire(DataInputStream(ByteArrayInputStream(bytes.toByteArray())),true) }
        near(3200.0,client.voltageTarget()!!,"actual telemetry wire format")
        tag.putString("converterMapping","FUTURE_UNKNOWN")
        loaded.readNbt(tag,true);verify(!loaded.valid,"unknown save version fails closed")
        val again = CompoundTag();loaded.writeNbt(again)
        val reopened = ConverterControlSettings().apply { readNbt(again,true) }
        verify(!reopened.valid,"unknown control fault survives another save")
        val malformed = CompoundTag().apply { putDouble("converterManualRatio",Double.NaN) }
        loaded.readNbt(malformed,true);verify(!loaded.valid,"nonfinite NBT fails closed")
    }

    private fun windings() {
        val copper=UtilityCableMaterial.COPPER
        val r=WirePhysics.resistance(copper,3.309,16.0,20.0)
        near(r*2,WirePhysics.resistance(copper,3.309,32.0,20.0),"length doubles resistance")
        verify(WirePhysics.resistance(copper,3.309,16.0,100.0)>r,"hot winding higher resistance")
        val load=WindingThermalLoad("coil")
        load.configure(copper,3.309,16.0,true,20.0)
        val start=load.energyJoules
        var watts=0.0
        val heating=ElectricalHeatAccumulator({watts},load)
        repeat(5) {watts=if(it==1) 1000.0 else 0.0; heating.sample.process(.01)}
        heating.deliver.process(.05)
        near(10.0,heating.deliveredJoules,"short current pulse retained")
        load.integrateEnergy(load.PcTemp*.05);load.PcTemp=0.0
        near(start+10.0,load.energyJoules,"heat reaches actual winding state")
        val oldTemperature=load.absoluteCelsius
        load.configure(copper,3.309,32.0,true,20.0)
        near(start+10,load.energyJoules,"reconfiguration does not discard assembly energy")
        verify(load.absoluteCelsius<oldTemperature,"larger mass changes temperature consistently")
        val energy=load.energyJoules;val t=load.absoluteCelsius
        load.updateAmbient(-10.0)
        near(energy,load.energyJoules,"ambient shift conserves energy")
        near(t,load.absoluteCelsius,"ambient shift conserves absolute temperature")
        val tag=CompoundTag();load.writeToNBT(tag,"")
        val restored=WindingThermalLoad("coil");restored.readFromNBT(tag,"")
        restored.configure(copper,3.309,32.0,true,35.0)
        near(energy,restored.energyJoules,"canonical winding energy survives restart")
        val legacy=WindingThermalLoad("coil");legacy.temperatureCelsius=80.0
        legacy.integrateEnergy(0.0);legacy.configure(copper,3.309,16.0,true,20.0)
        near(100.0,legacy.absoluteCelsius,"first empty flush preserves legacy temperature")
        val phase=WireThermalPhysics(copper,3.309,16.0)
        val hot=WindingThermalLoad("hot");hot.configure(copper,3.309,16.0,false,20.0)
        hot.integrateEnergy(phase.meltingEnthalpy+phase.fusionJoules*.5)
        verify(!hot.failed,"bare winding needs latent heat before melting")
        hot.integrateEnergy(phase.fusionJoules*.6)
        verify(hot.failed,"complete winding fusion fails")
        near(phase.failureEnthalpy+phase.fusionJoules*.1,hot.energyJoules,"fusion overshoot not clipped",1e-7)
    }

    private fun insulation() {
        val state=CableInsulationState()
        verify(state.commit(doubleArrayOf(800.0,-800.0,42.0),1000.0,.05),"1600 V pair fails 1 kV insulation")
        verify(state.fault?.fromCore==0 && state.fault?.toCore==1,"only failing core pair selected")
        val root=RootSystem(.05,1)
        val cores=Array(3){VoltageState()};cores.forEach(root::addState)
        val sources=listOf(800.0,-800.0,42.0).mapIndexed { i,v -> VoltageSource("c$i",cores[i],null).setVoltage(v).also(root::addComponent) }
        val branch=state.connection(cores)!!;root.addComponent(branch);root.step()
        near(160.0,branch.current,"finite fault impedance determines real current")
        near(0.0,sources[2].current,"third core not silently shorted")
        near(branch.power,sources.sumOf{it.power},"fault heat drawn from network",1e-6)
        repeat(50){verify(state.connection(cores)===branch,"reconnect reuses one fault component")}
        val tag=CompoundTag();state.writeNbt(tag)
        val restored=CableInsulationState();restored.readNbt(tag,3,1000.0)
        verify(restored.fault==state.fault,"latched endpoint survives restart")
        verify(!restored.commit(doubleArrayOf(0.0,0.0,0.0),1000.0,1.0) && restored.fault!=null,"fault does not auto-heal")
        val duration=CableInsulationState()
        repeat(19) {verify(!duration.commit(doubleArrayOf(1250.0),1000.0,.05),"damage uses simulated duration")}
        verify(duration.commit(doubleArrayOf(1250.0),1000.0,.05),"one second sustained 25% overstress")
        val safe=CableInsulationState()
        repeat(1000){verify(!safe.commit(doubleArrayOf(800.0),1000.0,.05),"800 V valid on new 1 kV cable")}
        near(0.0,safe.exposure,"rated operation does not accumulate insulation damage")
    }

    private fun catalogue() {
        val entries=HvCableSpecifications.entries
        verify(entries.size==15,"explicit 15-variant catalogue")
        verify(entries.map{it.descriptorId}.distinct().size==15,"new identities unique")
        verify(entries.all{it.descriptorId>2327},"legacy utility allocation untouched")
        for(gauge in listOf("12 AWG","8 AWG","2 AWG")) {
            val specs=entries.filter{it.gauge==gauge}
            verify(specs.map{it.volts}==listOf(1000.0,5000.0,20000.0,40000.0,150000.0),"voltage classes for $gauge")
            verify(specs.map{it.areaMm2}.distinct().size==1 && specs.map{it.amps}.distinct().size==1,"insulation does not change copper or ampacity")
            verify(specs.map{it.rubberMultiplier}.zipWithNext().all{it.first<it.second},"HV insulation consumes a larger material budget")
        }
    }
}

fun main() { HvFeatureRegression.runAll() }

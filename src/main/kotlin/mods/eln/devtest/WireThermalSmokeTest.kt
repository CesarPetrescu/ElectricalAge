package mods.eln.devtest

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction as Side
import mods.eln.node.NodeManager
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeElement
import mods.eln.sixnode.CurrentSourceElement
import mods.eln.sixnode.electricalsource.ElectricalSourceElement
import mods.eln.sixnode.electricalcable.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.server.ServerLifecycleHooks
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

/** Dedicated actual-world overloads; no direct temperature assignment in the fault fixtures.
 * Restart reuses the saved nodes, with no re-placement. Enabled only by explicit smoke property.
 */
class WireThermalSmokeTest(private val restart: Boolean) {
    companion object {
        private val FAULT=BlockPos(32,80,32)
        private val CURRENT=BlockPos(32,80,38)
        private val NORMAL=BlockPos(32,80,44)
        private val COOL=BlockPos(32,80,50)
        private val SAVED=BlockPos(32,80,56)
        @JvmStatic fun register(mode: String) {
            require(mode=="wire-thermal-place" || mode=="wire-thermal-restart")
            NeoForge.EVENT_BUS.register(WireThermalSmokeTest(mode.endsWith("restart")))
        }
    }
    private val report=ContractReport(if(restart) "wire-world-restart" else "wire-world-place")
    private val w: ServerLevel get()=ServerLifecycleHooks.getCurrentServer()!!.overworld()
    private var ticks=0
    private var finished=false
    private var sawDamaged=false
    private var sawFifty=false
    private var coolPeak=0.0
    private val trace=StringBuilder("tick,fixture,type,temperature_C,current_A,heating_W\n")
    private fun element(p: BlockPos): SixNodeElement? =
        (NodeManager.instance!!.getNodeFromCoordonate(Coordinate(p.x,p.y,p.z,w)) as? SixNode)?.getElement(Side.YN)
    private fun cable(p: BlockPos)=element(p) as UtilityCableElement
    private fun test(name: String, block: ()->Unit) { report.test("eln:wire-network",name,block);report.write(false) }
    private fun voltage(p: BlockPos,value: Double) {
        (element(p.west()) as ElectricalSourceElement).readConfigTool(CompoundTag().apply { putDouble("voltage",value) },FakePlayerFactory.getMinecraft(w))
    }
    private fun current(value: Double) { (element(CURRENT.west()) as CurrentSourceElement).currentSource.current=value }

    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        if(finished)return
        ticks++
        try {
            if(ticks==20) {
                report.write(false)
                if(!report.test("eln:wire-network","setup-or-load-saved-fixtures"){setup()}) { finish();return }
                if(restart) {
                    test("broken-conductors-stay-open-after-real-restart") {
                        check(element(FAULT) is MoltenMetalPileElement)
                        check(element(CURRENT) is MoltenMetalPileElement)
                        check((element(FAULT.west()) as ElectricalSourceElement).electricalLoadList.all{abs(it.current)<1e-6})
                    }
                    test("damaged-insulation-and-hot-state-survive-restart") {
                        check(cable(SAVED).descriptor.melted)
                        check(cable(SAVED).thermalLoad.absoluteCelsius>100)
                    }
                }
            }
            if(ticks<=20)return
            if(restart) {
                if(ticks==100) {
                    test("normal-circuit-resumes-without-ghost-connections") {
                        check(cable(NORMAL).heating.lastWatts>0 && !cable(NORMAL).descriptor.melted)
                        check(element(FAULT) is MoltenMetalPileElement)
                    };finish()
                };return
            }
            for(p in listOf(FAULT,CURRENT,NORMAL,COOL)) {
                val e=element(p)
                if(e is UtilityCableElement) {
                    if(p==CURRENT) {
                        val amps=e.electricalLoadList.maxOf{abs(it.current)}
                        if(abs(amps-50)<.01) sawFifty=true
                        if(e.descriptor.melted)sawDamaged=true
                    }
                    if(ticks%2==0)trace.append("$ticks,${p.z},${if(e.descriptor.melted) "damaged" else "intact"},${e.thermalLoad.absoluteCelsius},${e.electricalLoadList.maxOf{abs(it.current)}},${e.heating.lastWatts}\n")
                } else if(p==CURRENT && e is MoltenMetalPileElement) current(0.0)
            }
            when(ticks) {
                24 -> { coolPeak=cable(COOL).thermalLoad.absoluteCelsius;voltage(COOL,0.0) }
                180 -> {
                    test("sustained-50A-damages-insulation-and-breaks-conductor") {
                        check(sawFifty && sawDamaged)
                        check(element(CURRENT) is MoltenMetalPileElement) { "50 A fixture survived" }
                    }
                    test("voltage-fed-short-interrupts-real-current") {
                        check(element(FAULT) is MoltenMetalPileElement)
                        check((element(FAULT.west()) as ElectricalSourceElement).electricalLoadList.all{abs(it.current)<1e-6})
                    }
                    test("power-removal-cools-without-repairing") {
                        val e=cable(COOL)
                        check(e.thermalLoad.absoluteCelsius<coolPeak)
                        check(e.heating.lastWatts<1e-8)
                    }
                }
                240 -> {
                    test("normal-load-survives-and-keeps-heating") {
                        val e=cable(NORMAL)
                        check(!e.descriptor.melted && e.thermalLoad.absoluteCelsius<80 && e.heating.lastWatts>0)
                    }
                    // A dedicated saved-state fixture: damage is genuine descriptor identity, not a repaired cable.
                    cable(SAVED).thermalLoad.temperatureCelsius=200-cable(SAVED).getAmbientTemperatureCelsius()
                    cable(SAVED).needPublish()
                    finish()
                }
            }
        } catch(t: Throwable) { test("unexpected-runtime-error"){throw t};finish() }
    }
    private fun setup() {
        w.setDayTime(6000);w.setWeatherParameters(100000,0,false,false)
        for(cx in 1..3)for(cz in 1..4)w.setChunkForced(cx,cz,true)
        if(restart)return
        val d=UtilityCableDescriptor.allDescriptors().first { it.material==UtilityCableMaterial.COPPER && it.sizeLabel=="26 AWG" && it.insulated && !it.melted }
        for(p in listOf(FAULT,CURRENT,NORMAL,COOL,SAVED)) {
            for(dx in -2..2) {
                w.setBlockAndUpdate(p.offset(dx,-1,0),Blocks.STONE.defaultBlockState())
                w.removeBlock(p.offset(dx,0,0),false)
            }
            place(if(p==SAVED) checkNotNull(d.meltedDescriptor).newItemStack() else d.newCreativeTabStack(),p)
            if(p==SAVED)continue
            place(Eln.findItemStack(if(p==CURRENT) "Current Source" else "Electrical Source",1),p.west())
            place(Eln.findItemStack("Ground Cable",1),p.east())
            if(p==CURRENT)current(50.0)
            else voltage(p,if(p==NORMAL) .134 else if(p==COOL) 4.0 else 24.0)
        }
    }
    private fun place(stack: net.minecraft.world.item.ItemStack,p: BlockPos) {
        check(!stack.isEmpty)
        val player=FakePlayerFactory.getMinecraft(w)
        player.setItemInHand(InteractionHand.MAIN_HAND,stack)
        Eln.sixNodeItem.onItemUse(stack,player,w,p,InteractionHand.MAIN_HAND,Direction.UP,.5f,1f,.5f)
        check(element(p)!=null) { "Failed to place ${stack.hoverName.string} at $p" }
    }
    private fun finish() {
        if(finished)return
        finished=true;report.write(true)
        val path=Path.of("../../build/smoke-artifacts/wire-world-${if(restart) "restart" else "place"}.csv")
        Files.createDirectories(path.parent);Files.writeString(path,trace)
        val server=w.server
        if(report.failures>0)Thread({server.runningThread.join();kotlin.system.exitProcess(1)},"wire-thermal-failed").start()
        server.halt(false)
    }
}

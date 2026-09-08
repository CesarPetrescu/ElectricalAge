package mods.eln.devtest

import com.google.gson.GsonBuilder
import mods.eln.misc.Coordinate
import mods.eln.node.NodeManager
import mods.eln.node.transparent.TransparentNode
import mods.eln.transparentnode.WireMachineElement
import mods.eln.transparentnode.heatfurnace.HeatFurnaceContainer
import mods.eln.transparentnode.heatfurnace.HeatFurnaceElement
import mods.eln.transparentnode.turbine.TurbineElement
import mods.eln.sixnode.electricalcable.UtilityCableDescriptor
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.levelgen.Heightmap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs

/** Extension of the no-inventory-grants starter. Every block and fuel item is removed from its
 * actual acquired inventory. Layout is built in air on a platform paid for with gathered blocks.
 * Container insertion/configuration is automated; electrical and thermal simulation runs normally. */
object NaturalPowerInstallation {
    private lateinit var world: ServerLevel
    private lateinit var take: (String, Int) -> ItemStack
    private lateinit var receive: (ItemStack) -> Unit
    private lateinit var furnace: HeatFurnaceElement
    private lateinit var turbine: TurbineElement
    private lateinit var roller: WireMachineElement
    private lateinit var insulator: WireMachineElement
    private lateinit var base: BlockPos
    private var stage = 0
    private var seconds = 0
    private var peakVoltage = 0.0
    private var peakPower = 0.0
    private var bareId = ""
    private val snapshots = mutableListOf<Map<String, Any>>()
    private val file = Path.of("../../build/smoke-artifacts/contracts/natural-powered-production.json")
    private fun element(pos: BlockPos) = (NodeManager.instance!!.getNodeFromCoordonate(Coordinate(pos.x,pos.y,pos.z,world)) as TransparentNode).element!!
    private fun packet(element: HeatFurnaceElement, command: Byte, number: Float? = null) {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.writeByte(command.toInt()); if(number!=null)it.writeFloat(number) }
        element.networkUnserialize(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
    }
    private fun write(complete: Boolean, finalWire: ItemStack? = null) {
        Files.createDirectories(file.parent)
        Files.writeString(file,GsonBuilder().setPrettyPrinting().create().toJson(mapOf(
            "complete" to complete, "elapsedSimulatedSeconds" to seconds,
            "peakTurbineVoltage" to peakVoltage, "peakTurbineOutputWatts" to peakPower,
            "fuelRemaining" to checkNotNull(furnace.inventory).getItem(HeatFurnaceContainer.combustibleId).count,
            "bareWireRegistryId" to bareId,
            "insulatedWire" to (finalWire?.toString() ?: "not yet produced"),
            "platform" to listOf(base.x,base.y,base.z),
            "snapshots" to snapshots,
            "scope" to "Naturally acquired resources, actual survival placement and normal fuel/thermal/electrical processing; automated container insertion and configuration, no seeded power source or battery attached."
        )))
    }
    fun start(level: ServerLevel, player: ServerPlayer, table: BlockPos,
              removeItem: (String, Int) -> ItemStack,
              placeItem: (String, BlockPos, Direction) -> BlockPos,
              collectItem: (ItemStack) -> Unit) {
        world=level;take=removeItem;receive=collectItem
        val highest=(-3..3).flatMap { dx -> (0..4).map { dz -> world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,table.x+dx,table.z+dz) } }.max()
        var pillar=table
        val floorY=maxOf(highest+4,table.y+4)
        check(floorY-table.y < 60) { "Natural campsite exceeds acquired platform budget" }
        while(pillar.y<floorY-1) pillar=placeItem("minecraft:dark_oak_planks",pillar,Direction.UP)
        val floorCenter=placeItem("minecraft:cobblestone",pillar,Direction.UP)
        val built=mutableSetOf(floorCenter)
        val pending=(-3..3).flatMap { dx -> (0..4).map { dz -> floorCenter.offset(dx,0,dz) } }.filter { it!=floorCenter }.sortedBy { abs(it.x-floorCenter.x)+abs(it.z-floorCenter.z) }.toMutableList()
        while(pending.isNotEmpty()) {
            val target=pending.removeAt(0)
            val towardSupport=Direction.Plane.HORIZONTAL.firstOrNull { target.relative(it) in built } ?: error("Disconnected paid platform construction")
            check(world.getBlockState(target).canBeReplaced()) { "Platform would overwrite natural obstruction" }
            val placed=placeItem("minecraft:cobblestone",target.relative(towardSupport),towardSupport.opposite)
            check(placed==target);built.add(target)
        }
        base=floorCenter.above()
        fun place(name:String,dx:Int,dz:Int):BlockPos {
            player.yRot=0f;player.xRot=0f
            return placeItem(name,base.offset(dx,-1,dz),Direction.UP)
        }
        furnace=element(place("eln:stone_heat_furnace",0,0)) as HeatFurnaceElement
        place("eln:copper_thermal_cable",0,1)
        turbine=element(place("eln:48v_turbine",-1,1)) as TurbineElement
        place("eln:low_voltage_cable",-1,2)
        roller=element(place("eln:wire_roller",-1,3)) as WireMachineElement
        place("eln:low_voltage_cable",0,2)
        insulator=element(place("eln:wire_insulator",0,3)) as WireMachineElement
        roller.targetLengthMeters=2
        roller.inventory.setItem(0,take("minecraft:copper_ingot",1))
        roller.inventory.setItem(1,take("eln:iron_roller_wheel",1))
        roller.inventory.setItem(2,take("eln:iron_roller_wheel",1))
        val fuelInventory=checkNotNull(furnace.inventory)
        fuelInventory.setItem(HeatFurnaceContainer.combustibleId,take("minecraft:coal",4))
        furnace.inventoryChange(fuelInventory)
        packet(furnace,HeatFurnaceElement.unserializeToogleTakeFuelId)
        packet(furnace,HeatFurnaceElement.unserializeGain,.3f)
        check(!player.abilities.instabuild)
        stage=1;write(false)
    }
    /** Called once per 20 normal server ticks, not an accelerated simulation loop. */
    fun step():Boolean {
        check(++seconds <= 300) { "No fuel-paid insulated wire within five minutes" }
        val voltage=turbine.positiveLoad.voltage
        val power=abs(turbine.positiveLoad.voltage*turbine.positiveLoad.current)
        check(voltage.isFinite() && power.isFinite())
        peakVoltage=maxOf(peakVoltage,voltage);peakPower=maxOf(peakPower,power)
        snapshots.add(mapOf("second" to seconds,"turbineVoltage" to voltage,"turbineOutputWatts" to power,
            "furnaceThermalRiseC" to furnace.thermalLoad.temperatureCelsius,"rollerProgressMeters" to roller.progressMeters,"insulatorProgressMeters" to insulator.progressMeters))
        if(stage==1 && !roller.inventory.getItem(3).isEmpty) {
            val bare=roller.inventory.removeItem(3,1)
            val descriptor=UtilityCableDescriptor.allDescriptors().single { it.checkSameItemStack(bare) }
            check(!descriptor.insulated && !descriptor.melted && abs(descriptor.getRemainingLengthMeters(bare)-2.0)<1e-8)
            bareId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(bare.item).toString()
            insulator.inventory.setItem(0,bare)
            insulator.inventory.setItem(1,take("eln:rubber",1))
            stage=2
        }
        if(stage==2 && !insulator.inventory.getItem(2).isEmpty) {
            val wire=insulator.inventory.removeItem(2,1)
            val descriptor=UtilityCableDescriptor.allDescriptors().single { it.checkSameItemStack(wire) }
            check(descriptor.insulated && !descriptor.melted && abs(descriptor.getRemainingLengthMeters(wire)-2.0)<1e-8)
            check(peakVoltage>1.0 && peakPower>.01 && checkNotNull(furnace.inventory).getItem(HeatFurnaceContainer.combustibleId).count<4) { "No observed thermal generation and paid fuel consumption" }
            check(insulator.inventory.getItem(0).isEmpty)
            write(true,wire)
            receive(wire)
            packet(furnace,HeatFurnaceElement.unserializeToogleTakeFuelId)
            return true
        }
        write(false)
        return false
    }
}

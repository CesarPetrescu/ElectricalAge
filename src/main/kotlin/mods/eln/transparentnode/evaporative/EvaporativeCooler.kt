package mods.eln.transparentnode.evaporative

import mods.eln.Eln
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.environment.BiomeClimateService
import mods.eln.environment.RoomThermalManager
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.Utils
import mods.eln.misc.VoltageLevelColor
import mods.eln.node.NodeBase
import mods.eln.node.NodePeriodicPublishProcess
import mods.eln.node.transparent.*
import mods.eln.sim.IProcess
import mods.eln.sim.mna.component.Resistor
import mods.eln.sim.nbt.NbtElectricalLoad
import mods.eln.sim.nbt.NbtThermalLoad
import mods.eln.sim.process.destruct.ThermalLoadWatchDog
import mods.eln.sim.process.destruct.VoltageStateWatchDog
import mods.eln.sim.process.destruct.WorldExplosion
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUtils
import net.minecraft.world.item.Items
import net.minecraft.world.level.material.Fluids
import net.neoforged.neoforge.fluids.FluidStack
import java.io.DataOutputStream
import kotlin.math.*

class EvaporativeCoolerDescriptor(name: String) : TransparentNodeDescriptor(
    name, EvaporativeCoolerElement::class.java, EvaporativeCoolerRender::class.java, EntityMetaTag.Fluid
) {
    init {
        voltageLevelColor = VoltageLevelColor.fromCable(Eln.instance.meduimVoltageCableDescriptor)
        mods.eln.wiki.Data.addThermal { newItemStack() }
    }
    override fun handleRenderType(item: ItemStack, type: IItemRenderer.ItemRenderType) = true
    override fun shouldUseRenderHelper(type: IItemRenderer.ItemRenderType, item: ItemStack, helper: IItemRenderer.ItemRendererHelper) =
        type != IItemRenderer.ItemRenderType.INVENTORY
    override fun renderItem(type: IItemRenderer.ItemRenderType, item: ItemStack, vararg data: Any) {
        if (type == IItemRenderer.ItemRenderType.INVENTORY) super.renderItem(type, item, *data)
        else {
            val obj = Eln.obj.getObj("evaporativecooler")
            for (part in arrayOf("main", "pad_dry", "rotor")) obj.getPart(part)?.draw()
        }
    }
    override fun mustHaveFloor() = false
    override fun addInformation(itemStack: ItemStack?, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.add(tr("Water-assisted thermal-network cooling; 240 V."))
        list.add(tr("Fan: 120 W. Pump: 15 W. Water: 4 buckets."))
        list.add(tr("Up to 8 kW evaporation; capacity depends on temperature and humidity."))
        list.add(tr("Wet operation requires clear outdoor intake and exhaust."))
        list.add(tr("Copper sides: heat. Front/rear: power. Top/bottom: water."))
        list.add(tr("Right-click for controls; use water or empty buckets to transfer water."))
    }
}

/** Explicit status codes are persisted in menus as integers, never translated on the server wire. */
object EvaporativeStatus {
    const val OFF = 0; const val IDLE = 1; const val DRY = 2; const val WET = 3
    const val EMPTY = 4; const val NO_POWER = 5; const val LOW_VOLTAGE = 6
    const val BLOCKED = 7; const val INDOORS = 8; const val FROZEN = 9
    const val TOO_HOT = 10; const val CLIMATE = 11; const val REDSTONE = 12
    fun text(status: Int): String = when (status) {
        OFF -> tr("Disabled - passive cooling")
        IDLE -> tr("Target reached - passive cooling")
        DRY -> tr("Dry fan cooling")
        WET -> tr("Evaporative cooling")
        EMPTY -> tr("Water empty - dry cooling")
        NO_POWER -> tr("No power - passive cooling")
        LOW_VOLTAGE -> tr("Low voltage - pump inhibited")
        BLOCKED -> tr("Airflow blocked - passive cooling")
        INDOORS -> tr("Outdoor exhaust required - dry cooling")
        FROZEN -> tr("Freeze protection - dry cooling")
        TOO_HOT -> tr("Surface too hot - dry cooling")
        CLIMATE -> tr("Unsupported climate - dry cooling")
        REDSTONE -> tr("Redstone interlock - passive cooling")
        else -> tr("Waiting for server")
    }
}

class EvaporativeCoolerElement(node: TransparentNode, descriptor: TransparentNodeDescriptor) :
    TransparentNodeElement(node, descriptor) {
    companion object {
        const val VOLTAGE = 240.0
        const val FAN_WATTS = 120.0
        const val PUMP_WATTS = 15.0
        const val CAPACITY_J_K = 18000.0
        const val PASSIVE_G = 6.0
        const val FORCED_G = 40.0
        const val MAX_EVAPORATION_WATTS = 8000.0
    }
    val thermal = NbtThermalLoad("thermalLoad")
    val supply = NbtElectricalLoad("positiveLoad")
    val motor = Resistor(supply, null)
    val controls = EvaporativeControls()
    val water = EvaporativeWaterTank { needPublish() }
    var status = EvaporativeStatus.IDLE; private set
    var ambientCelsius = 20.0; private set
    var airCelsius = 20.0; private set
    var humidityPercent = 50.0; private set
    var wetBulbCelsius = 0.0; private set
    var fanSpeed = 0.0; private set
    var electricalWatts = 0.0; private set
    var evaporationWatts = 0.0; private set
    var sensibleWatts = 0.0; private set
    var waterMbPerSecond = 0.0; private set
    var outdoor = false; private set
    var airflowClear = false; private set
    var wetActive = false; private set
    val surfaceCelsius get() = ambientCelsius + thermal.temperatureCelsius
    val netCoolingWatts get() = sensibleWatts + evaporationWatts - electricalWatts
    private var environment = EvaporationModel.environment(20.0, 50.0)
    private var climateValid = false
    private var requestedFanWatts = 0.0
    private var requestedPumpWatts = 0.0
    private var wetRequested = false
    private var commandedSpeed = 0.0
    private var sampleCountdown = 0.0
    private var redstonePowered = false
    private var dimensionAllowsWater = false

    val controlProcess = IProcess { dt ->
        sampleCountdown -= dt
        if (sampleCountdown <= 0) { sampleEnvironment(); sampleCountdown = .5 }
        updateDemand()
    }
    val coolingProcess = IProcess { dt -> cool(dt) }

    init {
        thermal.set(.001, 1.0 / PASSIVE_G, CAPACITY_J_K)
        electricalLoadList.add(supply)
        electricalComponentList.add(motor)
        thermalLoadList.add(thermal)
        slowPreProcessList.add(controlProcess)
        thermalFastProcessList.add(coolingProcess)
        slowProcessList.add(NodePeriodicPublishProcess(node, .5, .2))
        slowProcessList.add(ambientAwareThermalWatchdog(ThermalLoadWatchDog(thermal))
            .setMaximumTemperature(160.0).setDestroys(WorldExplosion(this).machineExplosion()))
        slowProcessList.add(VoltageStateWatchDog(supply).setNominalVoltage(VOLTAGE)
            .setDestroys(WorldExplosion(this).machineExplosion()))
        motor.resistance = 1e12
    }
    override fun initialize() {
        Eln.instance.meduimVoltageCableDescriptor.applyTo(supply)
        sampleEnvironment()
        updateDemand()
        connect()
    }
    override fun getElectricalLoad(side: Direction, lrdu: LRDU) =
        if (!side.isY && lrdu == LRDU.Down && (side == front || side == front.inverse)) supply else null
    override fun getThermalLoad(side: Direction, lrdu: LRDU) =
        if (!side.isY && lrdu == LRDU.Down && side != front && side != front.inverse) thermal else null
    override fun getConnectionMask(side: Direction, lrdu: LRDU) = when {
        side.isY || lrdu != LRDU.Down -> 0
        side == front || side == front.inverse -> NodeBase.maskElectricalPower
        else -> NodeBase.maskThermal
    }
    override fun getFluidHandler() = water
    override fun hasGui() = true
    override fun newContainer(side: Direction, player: Player) = EvaporativeCoolerMenu(player, this)

    /** Nearby loaded blocks only. Does not force-load chunks or assume all air cells are outdoors. */
    fun sampleEnvironment() {
        climateValid = false; outdoor = false; airflowClear = false
        val c = coordinate()
        if (!c.worldExist) return
        val level = world()
        val origin = BlockPos(c.x, c.y, c.z)
        val climate = BiomeClimateService.sample(level, c.x, c.y, c.z)
        ambientCelsius = climate.temperatureCelsius
        airCelsius = ambientCelsius + (RoomThermalManager.getRoomAt(level, c.x, c.y, c.z)?.temperatureCelsius ?: 0.0)
        humidityPercent = climate.relativeHumidityPercent
        dimensionAllowsWater = !level.dimensionType().ultraWarm()
        redstonePowered = level.hasNeighborSignal(origin)
        fun clear(p: BlockPos): Boolean {
            if (!level.hasChunkAt(p) || !level.getFluidState(p).isEmpty) return false
            val shape = level.getBlockState(p).getCollisionShape(level, p)
            // Power cables occupy the floor, below the fan aperture. They must not block their own cooler.
            return shape.isEmpty || shape.max(net.minecraft.core.Direction.Axis.Y) <= .30
        }
        fun reachesOutside(direction: net.minecraft.core.Direction): Boolean {
            for (distance in 1..8) {
                val p = origin.relative(direction, distance)
                if (!clear(p)) return false
                if (level.canSeeSky(p)) return true
            }
            return false
        }
        val out = front.toFacing()
        airflowClear = clear(origin.relative(out)) && clear(origin.relative(out.opposite))
        outdoor = airflowClear && reachesOutside(out) && reachesOutside(out.opposite)
        // Room lookup is a second, conservative guard against exhausting moisture into a sealed room.
        if (RoomThermalManager.getRoomAt(level, c.x, c.y, c.z) != null) outdoor = false
        if (airCelsius.isFinite() && humidityPercent.isFinite() && airCelsius in -80.0..70.0 && humidityPercent in 0.0..100.0) {
            environment = EvaporationModel.environment(airCelsius, humidityPercent)
            wetBulbCelsius = environment.wetBulbCelsius()
            climateValid = true
        }
    }

    fun updateDemand() {
        val demand = controls.demand(surfaceCelsius, redstonePowered)
        commandedSpeed = if (airflowClear) demand.speed() else 0.0
        wetRequested = demand.wet()
        requestedFanWatts = FAN_WATTS * commandedSpeed.pow(3)
        requestedPumpWatts = if (commandedSpeed > 0 && canWet()) PUMP_WATTS else 0.0
        val requested = requestedFanWatts + requestedPumpWatts
        motor.resistance = if (requested > 0) VOLTAGE * VOLTAGE / requested else 1e12
    }
    private fun canWet() = wetRequested && water.availableMb > 1e-12 && outdoor && climateValid &&
        dimensionAllowsWater && airCelsius > 0 && surfaceCelsius > 1 && surfaceCelsius < 95

    /** Called once per thermal solver step; the native Rp path supplies all sensible exchange. */
    fun cool(dt: Double) {
        if (!dt.isFinite() || dt <= 0 || dt > 1) return
        val oldStatus = status
        electricalWatts = motor.power.let { if (it.isFinite()) it.coerceAtLeast(0.0) else 0.0 }
        val requested = requestedFanWatts + requestedPumpWatts
        val factor = if (requested > 0) (electricalWatts / requested).coerceIn(0.0, 1.0) else 0.0
        fanSpeed = commandedSpeed * Math.cbrt(factor)
        val conductance = PASSIVE_G + FORCED_G * fanSpeed.pow(.8)
        thermal.setRp(1.0 / conductance)
        wetActive = canWet() && requestedPumpWatts > 0 && fanSpeed > .01 && abs(supply.voltage) >= VOLTAGE * .75
        evaporationWatts = 0.0; waterMbPerSecond = 0.0
        sensibleWatts = conductance * (surfaceCelsius - airCelsius)
        if (climateValid && surfaceCelsius.isFinite()) {
            val result = EvaporationModel.step(surfaceCelsius, environment, conductance,
                MAX_EVAPORATION_WATTS * factor, water.availableMb, electricalWatts, thermal.heatCapacity, dt, wetActive)
            val consumed = water.evaporate(result.waterMb())
            waterMbPerSecond = consumed / dt
            evaporationWatts = consumed * EvaporationModel.KG_PER_MB * EvaporationModel.latentHeat(surfaceCelsius) / dt
        }
        // Electrical energy is not free: all fan/pump losses are conservatively deposited in this load.
        thermal.movePowerTo(electricalWatts - evaporationWatts)
        status = when {
            controls.mode() == EvaporativeControls.OFF -> EvaporativeStatus.OFF
            (controls.redstoneMode() == 1 && !redstonePowered) || (controls.redstoneMode() == 2 && redstonePowered) -> EvaporativeStatus.REDSTONE
            !airflowClear -> EvaporativeStatus.BLOCKED
            requestedFanWatts <= 0 -> EvaporativeStatus.IDLE
            electricalWatts < .01 -> EvaporativeStatus.NO_POWER
            !wetRequested -> EvaporativeStatus.DRY
            water.availableMb <= 1e-12 -> EvaporativeStatus.EMPTY
            !climateValid || !dimensionAllowsWater -> EvaporativeStatus.CLIMATE
            !outdoor -> EvaporativeStatus.INDOORS
            airCelsius <= 0 || surfaceCelsius <= 1 -> EvaporativeStatus.FROZEN
            surfaceCelsius >= 95 -> EvaporativeStatus.TOO_HOT
            abs(supply.voltage) < VOLTAGE * .75 -> EvaporativeStatus.LOW_VOLTAGE
            evaporationWatts > .01 -> EvaporativeStatus.WET
            else -> EvaporativeStatus.DRY
        }
        if (status != oldStatus) needPublish()
    }
    fun command(id: Int): Boolean {
        if (!controls.command(id)) return false
        updateDemand(); needPublish()
        return true
    }
    override fun onNeighborBlockChange() { super.onNeighborBlockChange(); sampleCountdown = 0.0 }
    override fun onBlockActivated(player: Player, side: Direction, vx: Float, vy: Float, vz: Float): Boolean {
        // The legacy element callback has no hand argument. Intentionally handle main-hand buckets only.
        val held = player.mainHandItem
        if (!held.`is`(Items.WATER_BUCKET) && !held.`is`(Items.BUCKET)) return false
        if (player.isSpectator) return true
        if (world().isClientSide) return true
        if (held.`is`(Items.WATER_BUCKET)) {
            val stack = FluidStack(Fluids.WATER, 1000)
            if (water.fill(null, stack, false) == 1000) {
                water.fill(null, stack, true)
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemUtils.createFilledResult(held, player, ItemStack(Items.BUCKET)))
            }
        } else if (water.drain(null, 1000, false).amount == 1000) {
            water.drain(null, 1000, true)
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemUtils.createFilledResult(held, player, ItemStack(Items.WATER_BUCKET)))
        }
        updateDemand()
        return true
    }
    private fun saveSettings(tag: CompoundTag) {
        tag.putInt("mode", controls.mode()); tag.putInt("target", controls.targetCelsius())
        tag.putInt("fan", controls.fanPercent()); tag.putInt("redstone", controls.redstoneMode())
        water.save(tag)
    }
    private fun loadSettings(tag: CompoundTag) {
        controls.restore(if (tag.contains("mode")) tag.getInt("mode") else 2,
            if (tag.contains("target")) tag.getInt("target") else 40,
            if (tag.contains("fan")) tag.getInt("fan") else 100, tag.getInt("redstone"))
        water.load(tag)
    }
    override fun writeToNBT(nbt: CompoundTag) { super.writeToNBT(nbt); nbt.put("evaporative", CompoundTag().also(::saveSettings)) }
    override fun readFromNBT(nbt: CompoundTag) { super.readFromNBT(nbt); loadSettings(nbt.getCompound("evaporative")); sampleCountdown = 0.0 }
    override fun getItemStackNBT() = CompoundTag().also { it.put("evaporative", CompoundTag().also(::saveSettings)) }
    override fun readItemStackNBT(nbt: CompoundTag?) { if (nbt?.contains("evaporative") == true) loadSettings(nbt.getCompound("evaporative")) }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        stream.writeFloat(fanSpeed.toFloat()); stream.writeFloat((water.availableMb / EvaporativeWaterTank.CAPACITY).toFloat())
        stream.writeBoolean(wetActive && evaporationWatts > .01); stream.writeInt(status)
    }
    override fun multiMeterString(side: Direction) = Utils.plotVolt("U: ", supply.voltage) + Utils.plotPower(" P: ", electricalWatts)
    override fun thermoMeterString(side: Direction) = plotAmbientCelsius("T: ", thermal.temperatureCelsius) + Utils.plotPower(" Q: ", netCoolingWatts)
    override fun getWaila() = linkedMapOf(
        tr("Status") to EvaporativeStatus.text(status),
        tr("Temperature") to Utils.plotCelsius("", surfaceCelsius),
        tr("Net heat rejection") to Utils.plotPower("", netCoolingWatts),
        tr("Electrical consumption") to Utils.plotPower("", electricalWatts),
        tr("Water") to tr("%1$ / 4000 mB", water.availableMb.roundToInt()),
        tr("Relative humidity") to tr("%1$ percent", humidityPercent.roundToInt())
    )
}

package mods.eln.integration.create

import com.simibubi.create.content.kinetics.base.KineticBlockEntity
import mods.eln.Eln
import mods.eln.i18n.I18N.tr
import mods.eln.mechanical.*
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction as ShaftDirection
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.*

class CreateAdapterEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState, val industrial: Boolean) :
    KineticBlockEntity(type, pos, state), ShaftElement, MenuProvider {
    var ratio = 8; private set
    var engaged = true; private set
    var autoRetry = false; private set
    var fault = 0; private set // 1: stress, 2: speed
    var deliveredPower = 0.0; private set
    var requestedImpact = 0.0; private set
    var outputAngle = 0.0; private set
    private var shaft: ShaftNetwork? = null
    private var retryTicks = 0
    private var redstone = false
    private var disconnecting = false
    private var age = 0
    private var drive: AdapterDrive? = null
    private var savedRads = 0.0
    override val shaftMass = if (industrial) 4.0 else 1.0
    override val shaftConnectivity get() = arrayOf(ShaftDirection.fromFacing(blockState.getValue(CreateAdapterBlock.FACING)))
    override fun coordonate() = Coordinate(this)
    override fun getShaft(dir: ShaftDirection) = if (dir in shaftConnectivity) shaft else null
    override fun setShaft(dir: ShaftDirection, net: ShaftNetwork?) { if (dir in shaftConnectivity) shaft = net }
    override fun isShaftElementDestructing() = disconnecting || isRemoved
    override fun needPublish() { if (level?.isClientSide == false) { setChanged(); sendData() } }
    val outputSpeed get() = shaft?.rads ?: savedRads

    private fun mechanics(): AdapterDrive = drive ?: run {
        fun config(key: String, default: Double, minimum: Double, maximum: Double): Double {
            val v = Eln.config.getDoubleOrElse("integrations.create.$key", default)
            return if (v.isFinite()) v.coerceIn(minimum, maximum) else default
        }
        AdapterDrive(config(if (industrial) "industrialPower" else "basicPower", if (industrial) 16000.0 else 4000.0, 1.0, 1e6),
            config(if (industrial) "industrialTorque" else "basicTorque", if (industrial) 160.0 else 40.0, 0.1, 1e6),
            config("efficiency", 0.9, 0.01, 1.0), config("wattsPerStressUnit", 1.0, 0.001, 1e6)).also { drive = it }
    }

    override fun initialize() {
        super<KineticBlockEntity>.initialize()
        if (level?.isClientSide == false) {
            shaft = ShaftNetwork(this, shaftConnectivity.iterator()).also { it.rads = savedRads }
            shaft?.connectShaft(this, shaftConnectivity[0])
            reserve(0.0)
        }
    }

    override fun tick() {
        super.tick()
        val world = level ?: return
        if (world.isClientSide) { outputAngle = (outputAngle + outputSpeed * 0.05) % (2 * PI); return }
        age++
        if (shaft == null) return
        if (age % 20 == 0) shaft?.connectShaft(this, shaftConnectivity[0])
        val powered = world.hasNeighborSignal(worldPosition)
        if (powered && !redstone) resetFault()
        redstone = powered
        if (fault != 0 && autoRetry && ++retryTicks >= 100) resetFault()
        deliveredPower = 0.0
        val rpm = theoreticalSpeed.toDouble()
        val model = mechanics()
        if (engaged && fault == 0 && model.target(rpm, ratio) > AdapterDrive.MAX_SPEED) trip(2)
        if (engaged && fault == 0 && isOverStressed && abs(rpm) > 0) trip(1)
        if (!engaged || fault != 0 || !hasNetwork() || abs(speed) < 1e-6) { reserve(0.0); publishPeriodically(); return }
        val net = shaft ?: return
        val inertia = net.mass * Eln.config.getDoubleOrElse("balance.mechanics.shaftEnergyFactor", 0.05)
        val power = model.requestedPower(rpm, ratio, net.rads, inertia, 0.05)
        val wanted = model.stressImpact(power, rpm)
        // Reserve on Create's real network before releasing any energy into ELN.
        reserve(if (wanted >= requestedImpact) wanted else max(wanted, requestedImpact * 0.9))
        if (isOverStressed || abs(speed) < 1e-6) { trip(1); publishPeriodically(); return }
        val joules = model.permittedEnergy(power, requestedImpact, speed.toDouble(), 0.05)
        if (joules > 0) net.energy += joules
        deliveredPower = joules / 0.05
        publishPeriodically()
    }

    private fun publishPeriodically() { if (age % 5 == 0) needPublish() }
    private fun reserve(value: Double) {
        val safe = if (value.isFinite()) value.coerceAtLeast(0.0) else 0.0
        if (safe == requestedImpact) return
        requestedImpact = safe
        lastStressApplied = safe.toFloat()
        if (hasNetwork()) orCreateNetwork.updateStressFor(this, lastStressApplied)
    }
    override fun calculateStressApplied(): Float { lastStressApplied = requestedImpact.toFloat(); return lastStressApplied }
    fun trip(reason: Int) { fault = reason; retryTicks = 0; reserve(0.0); needPublish() }
    fun resetFault() { fault = 0; retryTicks = 0; reserve(0.0); needPublish() }
    fun command(id: Int): Boolean {
        when (id) {
            0 -> { engaged = !engaged; if (!engaged) reserve(0.0) }
            1 -> { if (engaged) return false; ratio = AdapterDrive.RATIOS[(AdapterDrive.RATIOS.indexOf(ratio) + 1) % 4] }
            2 -> resetFault()
            3 -> autoRetry = !autoRetry
            else -> return false
        }
        needPublish(); return true
    }
    override fun remove() {
        detachOutput()
        super.remove()
    }
    override fun onChunkUnloaded() {
        detachOutput()
        super.onChunkUnloaded()
    }
    private fun detachOutput() {
        if (level?.isClientSide == false && !disconnecting) {
            savedRads = outputSpeed
            disconnecting = true; reserve(0.0); shaft?.disconnectShaft(this); shaft = null
        }
    }
    override fun write(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.write(tag, registries, clientPacket)
        tag.putInt("Ratio", ratio); tag.putBoolean("Engaged", engaged); tag.putBoolean("AutoRetry", autoRetry); tag.putInt("Fault", fault)
        tag.putDouble("OutputSpeed", outputSpeed)
        if (clientPacket) { tag.putDouble("OutputPower", deliveredPower); tag.putDouble("Impact", requestedImpact) }
    }
    override fun read(tag: CompoundTag, registries: HolderLookup.Provider, clientPacket: Boolean) {
        super.read(tag, registries, clientPacket)
        ratio = tag.getInt("Ratio").takeIf { it in AdapterDrive.RATIOS } ?: 8
        engaged = !tag.contains("Engaged") || tag.getBoolean("Engaged")
        autoRetry = tag.getBoolean("AutoRetry"); fault = tag.getInt("Fault").coerceIn(0, 2)
        savedRads = tag.getDouble("OutputSpeed").takeIf { it.isFinite() && it >= 0 } ?: 0.0
        requestedImpact = if (clientPacket) tag.getDouble("Impact") else 0.0
        deliveredPower = if (clientPacket) tag.getDouble("OutputPower") else 0.0
    }
    override fun getDisplayName(): Component = Component.literal(if (industrial) tr("Industrial Create Shaft Adapter") else tr("Create Shaft Adapter"))
    override fun createMenu(id: Int, inventory: Inventory, player: Player): AbstractContainerMenu = CreateAdapterMenu(id, inventory, this)
}

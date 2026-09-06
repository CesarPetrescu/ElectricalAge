package mods.eln.railroad

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.node.NodeManager
import mods.eln.sim.mna.misc.MnaConst
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.vehicle.AbstractMinecart
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.sign

class EntityElectricMinecart(type: EntityType<out EntityElectricMinecart>, world: Level): AbstractMinecart(type, world) {

    constructor(world: Level, x: Double, y: Double, z: Double): this(TYPE.get(), world) {
        setPos(x, y, z)
        xo = x
        yo = y
        zo = z
    }

    companion object {
        /** Registered by EntityRegistration through ElnRegistry. */
        @JvmField
        var TYPE: Supplier<EntityType<EntityElectricMinecart>> = Supplier { throw IllegalStateException("EntityElectricMinecart type not registered") }
    }

    private var lastPowerElement: RailroadPowerInterface? = null
    private val locomotiveMaximumResistance = 200.0
    var energyBufferTargetJoules = 10_000.0
    var energyBufferJoules = 0.0

    override fun tick() {
        super.tick()
        val cartCoordinate = Coordinate(x.toInt(), y.toInt(), z.toInt(), level())
        val overheadWires = getOverheadWires(cartCoordinate)
        val underTrackWires = getUnderTrackWires(cartCoordinate)

        when (val oldPowerElement = lastPowerElement) {
            overheadWires, underTrackWires, null -> {
                // references existing overhead wires or under track wires, or nothing
            }
            else -> {
                // references old overhead wires or under track wires that are not the current ones
                energyBufferJoules += PoweredMinecartSimulationSingleton.cartCollectEnergy(this)
                Eln.logger.info("Deregister cart from $oldPowerElement")
                oldPowerElement.deregisterCart(this)
            }
        }

        val currentElement: RailroadPowerInterface? = underTrackWires ?: overheadWires

        if (currentElement != null) {
            if (currentElement != lastPowerElement) {
                Eln.logger.info("Register cart to $currentElement")
                currentElement.registerCart(this)
            }

            if (energyBufferJoules < energyBufferTargetJoules) {
                val chargeRateInv = energyBufferTargetJoules / (abs(energyBufferTargetJoules - energyBufferJoules) * 2)
                PoweredMinecartSimulationSingleton.powerCart(this, chargeRateInv * locomotiveMaximumResistance, 0.1)
            } else {
                PoweredMinecartSimulationSingleton.powerCart(this, MnaConst.highImpedance, 0.1)
            }
            energyBufferJoules += PoweredMinecartSimulationSingleton.cartCollectEnergy(this)
        }

        lastPowerElement = currentElement
    }

    var pushX: Double = 0.0
    var pushZ: Double = 0.0

    /**
     * 1.7.10 passed the speed cap into func_145821_a and this cart handed it "+1"; on 1.12.2 the cap is
     * read through getMaxSpeed() inside moveAlongTrack, so the same bump lives there.
     */
    override fun getMaxSpeed(): Double = super.getMaxSpeed() + 1

    override fun moveAlongTrack(pos: BlockPos, state: BlockState) {
        super.moveAlongTrack(pos, state)
        if (energyBufferJoules > 0) {
            val maxEnergy = 40.0
            var energyAvailable = maxEnergy
            if (energyBufferJoules < maxEnergy) {
                energyAvailable = energyBufferJoules
            }

            val startingThreshold = 0.0005
            val motion = deltaMovement

            if (abs(motion.x) >= startingThreshold || abs(motion.z) >= startingThreshold) {
                if (abs(motion.x) < 0.5 && abs(motion.z) < 0.5) {
                    pushX = motion.x.sign * 0.05 * (energyAvailable / maxEnergy)
                    pushZ = motion.z.sign * 0.05 * (energyAvailable / maxEnergy)
                    energyBufferJoules -= energyAvailable
                }
            }
        }

        //Eln.logger.info("Push: ($pushX, $pushZ)")

        deltaMovement = deltaMovement.add(pushX, 0.0, pushZ)

        //Eln.logger.info("Speed: ($motionX, $motionZ)")

        pushX = 0.0
        pushZ = 0.0
    }

    private fun getOverheadWires(coordinate: Coordinate): OverheadLinesElement? {
        // Pass coordinate of tracks and check vertically the next 3 blocks (4 up looks visually weird)
        val originalY = coordinate.y
        while (coordinate.y <= (originalY + 3)) {
            coordinate.y
            val node = NodeManager.instance!!.getTransparentNodeFromCoordinate(coordinate)
            if (node is OverheadLinesElement) {
                return node
            }
            coordinate.y++
        }
        return null
    }

    private fun getUnderTrackWires(coordinate: Coordinate): UnderTrackPowerElement? {
        coordinate.y -= 1 // check the block below the cart
        val node = NodeManager.instance!!.getTransparentNodeFromCoordinate(coordinate)
        if (node is UnderTrackPowerElement) {
            return node
        }
        return null
    }

    override fun interact(player: Player, hand: InteractionHand): InteractionResult {
        val ret = super.interact(player, hand)
        if (ret.consumesAction()) return ret
        if (player.isSecondaryUseActive) return InteractionResult.PASS
        if (isVehicle) return InteractionResult.PASS
        if (!level().isClientSide) {
            return if (player.startRiding(this)) InteractionResult.CONSUME else InteractionResult.PASS
        }
        return InteractionResult.SUCCESS
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.putDouble("EnergyBufferJ", energyBufferJoules)
        tag.putDouble("EnergyBufferTargetJ", energyBufferTargetJoules)
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        if (tag.contains("EnergyBufferJ")) {
            energyBufferJoules = tag.getDouble("EnergyBufferJ")
        }
        if (tag.contains("EnergyBufferTargetJ")) {
            energyBufferTargetJoules = tag.getDouble("EnergyBufferTargetJ")
        }
    }

    override fun getMinecartType(): AbstractMinecart.Type = AbstractMinecart.Type.RIDEABLE

    /** What breaking the cart drops: the electric minecart item when it exists, else a plain minecart. */
    override fun getDropItem(): Item = Eln.findItemStack("Electric Minecart", 1)?.item ?: Items.MINECART

    override fun getDefaultDisplayBlockState(): BlockState = Blocks.IRON_BLOCK.defaultBlockState()
}

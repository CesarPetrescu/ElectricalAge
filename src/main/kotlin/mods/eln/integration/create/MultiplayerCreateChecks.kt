package mods.eln.integration.create

import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity
import mods.eln.Eln
import mods.eln.devtest.MultiplayerScene
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.neoforged.neoforge.common.util.FakePlayerFactory
import kotlin.math.abs
import kotlin.math.PI

/** Only reached in the Create profile. No production state is patched by the observations. */
object MultiplayerCreateChecks {
    fun place(world: ServerLevel) {
        val p = MultiplayerScene.adapter
        val motor = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"))
        world.setBlockAndUpdate(p.west(), motor.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST))
        world.setBlockAndUpdate(p, CreateIntegration.basic.get().defaultBlockState().setValue(CreateAdapterBlock.FACING, Direction.EAST))
        val player = FakePlayerFactory.getMinecraft(world)
        val stack = Eln.findItemStack("Joint", 1)
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)
        check(Eln.transparentNodeItem.placeBlockAt(stack, player, world, p.east(), Direction.UP))
    }
    fun start(world: ServerLevel) {
        val motor = world.getBlockEntity(MultiplayerScene.adapter.west()) as CreativeMotorBlockEntity
        motor.generatedSpeed.setValue(64)
        motor.updateGeneratedRotation()
    }
    fun matches(world: ServerLevel, ratio: Int, engaged: Boolean): Boolean {
        val a = world.getBlockEntity(MultiplayerScene.adapter) as? CreateAdapterEntity ?: return false
        return a.ratio == ratio && a.engaged == engaged && a.fault == 0 && abs(a.theoreticalSpeed - 64f) < .01 &&
            (!engaged || abs(a.outputSpeed - 64.0 * PI / 30 * ratio) < 1)
    }
}

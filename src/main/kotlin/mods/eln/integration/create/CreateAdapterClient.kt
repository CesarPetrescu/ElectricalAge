package mods.eln.integration.create

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import mods.eln.i18n.I18N.tr
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.client.event.EntityRenderersEvent
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import kotlin.math.PI
import kotlin.math.abs

object CreateAdapterClient {
    @JvmStatic fun screens(event: RegisterMenuScreensEvent) { event.register(CreateIntegration.menu.get(), ::AdapterScreen) }
    @JvmStatic fun renderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(CreateIntegration.basicType.get()) { AdapterRenderer() }
        event.registerBlockEntityRenderer(CreateIntegration.industrialType.get()) { AdapterRenderer() }
    }
}

private class AdapterScreen(menu: CreateAdapterMenu, inventory: Inventory, title: Component) : AbstractContainerScreen<CreateAdapterMenu>(menu, inventory, title) {
    init { imageWidth = 260; imageHeight = 200 }
    override fun init() {
        super.init()
        fun button(x: Int, y: Int, id: Int, text: String) {
            addRenderableWidget(Button.builder(Component.literal(text)) {
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, id)
            }.bounds(leftPos + x, topPos + y, 116, 20).build())
        }
        button(10, 147, 0, tr("Engage / disengage"))
        button(134, 147, 1, tr("Change gear"))
        button(10, 173, 2, tr("Reset fault"))
        button(134, 173, 3, tr("Toggle automatic retry"))
    }
    override fun renderBg(graphics: GuiGraphics, partial: Float, mouseX: Int, mouseY: Int) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF242D35.toInt())
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + 26, 0xFF515D68.toInt())
    }
    override fun renderLabels(g: GuiGraphics, x: Int, y: Int) {
        g.drawString(font, title, 10, 11, 0xFFFFFF, false)
        val d = menu.values
        val rpm = d.get(4); val omega = d.get(5) / 10.0
        val state = when { d.get(3) == 1 -> tr("Tripped: Create overstressed"); d.get(3) == 2 -> tr("Tripped: target exceeds 240 rad/s"); d.get(1) == 0 -> tr("Disengaged"); rpm == 0 -> tr("Waiting for Create rotation"); else -> tr("Engaged") }
        val lines = listOf(state,
            tr("Input: %1$ RPM | Gear: %2$:1", rpm, d.get(0)),
            tr("Target: %1$ rad/s", String.format(java.util.Locale.ROOT, "%.1f", abs(rpm) * PI / 30 * d.get(0))),
            tr("Output: %1$ rad/s (%2$ RPM)", omega, (omega * 30 / PI).toInt()),
            tr("Power: %1$ W | Stress: %2$ SU", d.get(6), d.get(7)),
            if (d.get(2) == 1) tr("Automatic retry: on (5 seconds)") else tr("Automatic retry: off"),
            tr("Disengage before changing gear."))
        lines.forEachIndexed { i, text -> g.drawString(font, text, 10, 35 + i * 15, 0xEEEEEE, false) }
    }
}

private class AdapterRenderer : BlockEntityRenderer<CreateAdapterEntity> {
    override fun render(be: CreateAdapterEntity, partial: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int) {
        val facing = be.blockState.getValue(CreateAdapterBlock.FACING)
        fun shaft(input: Boolean) {
            pose.pushPose()
            pose.translate(0.5, 0.5, 0.5)
            pose.mulPose(facing.rotation)
            val angle = if (input) ((be.level?.gameTime ?: 0L) % 1200 + partial) * be.speed * 0.3
                else (be.outputAngle + be.outputSpeed * partial * 0.05) * 180 / PI
            pose.mulPose(Axis.YP.rotationDegrees(angle.toFloat()))
            pose.translate(-0.125, if (input) -0.5 else 0.25, -0.125)
            pose.scale(0.25f, 0.25f, 0.25f)
            Minecraft.getInstance().blockRenderer.renderSingleBlock((if (input) Blocks.IRON_BLOCK else Blocks.COPPER_BLOCK).defaultBlockState(), pose, buffers, light, overlay)
            pose.popPose()
        }
        shaft(true); shaft(false)
    }
}

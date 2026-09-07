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
        button(134, 173, 3, tr("Automatic retry"))
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
            if (input) {
                // Real Create shaft mesh/texture. No stationary duplicate in the block model.
                pose.translate(-0.5, -0.5, -0.5)
                pose.scale(1f, 0.25f, 1f)
                val state = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "shaft")).defaultBlockState()
                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Y)
                Minecraft.getInstance().blockRenderer.renderSingleBlock(state, pose, buffers, light, overlay)
            } else {
                // ELN's steel texture on an octagonal output spindle, separate from its teal bearing.
                val sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                    .apply(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("eln", "block/create_adapter_steel"))
                val consumer = buffers.getBuffer(net.minecraft.client.renderer.RenderType.solid())
                val radius = 0.125
                fun vertex(x: Double, y: Double, z: Double, u: Float, v: Float, nx: Float, ny: Float, nz: Float) {
                    consumer.addVertex(pose.last(), x.toFloat(), y.toFloat(), z.toFloat()).setColor(255, 255, 255, 255)
                        .setUv(sprite.getU(u), sprite.getV(v)).setOverlay(overlay).setLight(light).setNormal(pose.last(), nx, ny, nz)
                }
                for (i in 0..7) {
                    val a = i * PI / 4; val b = (i + 1) * PI / 4
                    val x0 = kotlin.math.cos(a) * radius; val z0 = kotlin.math.sin(a) * radius
                    val x1 = kotlin.math.cos(b) * radius; val z1 = kotlin.math.sin(b) * radius
                    val nx = kotlin.math.cos((a+b)/2).toFloat(); val nz = kotlin.math.sin((a+b)/2).toFloat()
                    vertex(x0, .34, z0, 0f, 0f, nx, 0f, nz); vertex(x0, .5, z0, 0f, 1f, nx, 0f, nz)
                    vertex(x1, .5, z1, 1f, 1f, nx, 0f, nz); vertex(x1, .34, z1, 1f, 0f, nx, 0f, nz)
                    vertex(0.0, .5, 0.0, .5f, .5f, 0f, 1f, 0f); vertex(x1, .5, z1, 1f, 1f, 0f, 1f, 0f)
                    vertex(x0, .5, z0, 0f, 1f, 0f, 1f, 0f); vertex(0.0, .5, 0.0, .5f, .5f, 0f, 1f, 0f)
                }
            }
            pose.popPose()
        }
        shaft(true); shaft(false)
    }
}

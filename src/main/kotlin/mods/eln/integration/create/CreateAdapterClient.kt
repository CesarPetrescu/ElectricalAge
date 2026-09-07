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
    private val ratios = intArrayOf(1, 2, 4, 8)
    private val gearButtons = mutableListOf<Button>()
    private lateinit var clutchButton: Button
    private lateinit var retryButton: Button
    init { imageWidth = 290; imageHeight = 224 }
    override fun init() {
        super.init()
        gearButtons.clear()
        fun button(x: Int, y: Int, width: Int, id: Int, text: String): Button =
            addRenderableWidget(Button.builder(Component.literal(text)) {
                minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId, id)
            }.bounds(leftPos + x, topPos + y, width, 18).build())
        ratios.forEachIndexed { i, ratio -> gearButtons.add(button(10 + i * 69, 130, 63, 4 + i, tr("%1$:1", ratio))) }
        clutchButton = button(10, 178, 132, 0, tr("Disengage"))
        button(148, 178, 132, 2, tr("Reset fault"))
        retryButton = button(10, 202, 270, 3, tr("Automatic retry: off"))
        updateControls()
    }
    override fun containerTick() {
        super.containerTick()
        updateControls()
    }
    private fun updateControls() {
        val engaged = menu.values.get(1) != 0
        clutchButton.message = Component.literal(if (engaged) tr("Disengage") else tr("Engage"))
        retryButton.message = Component.literal(if (menu.values.get(2) != 0) tr("Automatic retry: on (5 seconds)") else tr("Automatic retry: off"))
        gearButtons.forEachIndexed { i, button ->
            val selected = menu.values.get(0) == ratios[i]
            button.active = !engaged && !selected
            button.tooltip = net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                if (engaged) tr("Disengage to change gear.") else if (selected) tr("Selected gear") else tr("Select %1$:1 gear", ratios[i])))
        }
    }
    override fun renderBg(graphics: GuiGraphics, partial: Float, mouseX: Int, mouseY: Int) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF242D35.toInt())
        graphics.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + 26, 0xFF515D68.toInt())
        val selected = ratios.indexOf(menu.values.get(0))
        if (selected >= 0) {
            val x = leftPos + 10 + selected * 69
            graphics.fill(x - 1, topPos + 129, x + 64, topPos + 149, 0xFF73C9AE.toInt())
        }
    }
    override fun renderLabels(g: GuiGraphics, x: Int, y: Int) {
        g.drawString(font, title, 10, 11, 0xFFFFFF, false)
        val d = menu.values
        val rpm = d.get(4); val omega = d.get(5) / 10.0
        val state = when { d.get(3) == 1 -> tr("Tripped: Create overstressed"); d.get(3) == 2 -> tr("Tripped: target exceeds 240 rad/s"); d.get(1) == 0 -> tr("Disengaged - coasting"); d.get(9) > 0 -> tr("Braking to selected gear"); rpm == 0 -> tr("Waiting for Create rotation"); else -> tr("Engaged") }
        val lines = listOf(state,
            tr("Input: %1$ RPM | Gear: %2$:1", rpm, d.get(0)),
            tr("Target: %1$ rad/s", String.format(java.util.Locale.ROOT, "%.1f", abs(rpm) * PI / 30 * d.get(0))),
            tr("Output: %1$ rad/s (%2$ RPM)", omega, (omega * 30 / PI).toInt()),
            if (d.get(9) > 0) tr("Braking: %1$ W | Stress: %2$ SU", d.get(9), d.get(7))
            else tr("Power: %1$ W | Stress: %2$ SU", d.get(6), d.get(7)))
        lines.forEachIndexed { i, text -> g.drawString(font, text, 10, 35 + i * 15, 0xEEEEEE, false) }
        g.drawString(font, tr("Selected gear: %1$:1", d.get(0)), 10, 114, 0x73C9AE, false)
        g.drawString(font, if (d.get(1) != 0) tr("Disengage to change gear.") else tr("Select a ratio, then engage."),
            10, 157, if (d.get(1) != 0) 0xE7BE75 else 0xEEEEEE, false)
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
            run {
                // Draw explicit geometry: Create's runtime baked shaft model can be empty.
                // Input matches Create's 4-pixel square shaft; output is a 2-pixel ELN spindle.
                val sideSprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                    .apply(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                        if (input) "create" else "eln", if (input) "block/axis" else "block/create_adapter_steel"))
                val endSprite = if (input) Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                    .apply(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "block/axis_top")) else sideSprite
                var sprite = sideSprite
                val consumer = buffers.getBuffer(net.minecraft.client.renderer.RenderType.solid())
                val radius = if (input) kotlin.math.sqrt(2.0) * .125 else .0625
                val sides = if (input) 4 else 8
                val start = if (input) PI / 4 else 0.0
                val bottom = if (input) -.5 else .34
                val top = if (input) -.25 else .5
                fun vertex(x: Double, y: Double, z: Double, u: Float, v: Float, nx: Float, ny: Float, nz: Float) {
                    consumer.addVertex(pose.last(), x.toFloat(), y.toFloat(), z.toFloat()).setColor(255, 255, 255, 255)
                        .setUv(sprite.getU(u), sprite.getV(v)).setOverlay(overlay).setLight(light).setNormal(pose.last(), nx, ny, nz)
                }
                for (i in 0 until sides) {
                    sprite = sideSprite
                    val a = start + i * 2 * PI / sides; val b = start + (i + 1) * 2 * PI / sides
                    val x0 = kotlin.math.cos(a) * radius; val z0 = kotlin.math.sin(a) * radius
                    val x1 = kotlin.math.cos(b) * radius; val z1 = kotlin.math.sin(b) * radius
                    val nx = kotlin.math.cos((a+b)/2).toFloat(); val nz = kotlin.math.sin((a+b)/2).toFloat()
                    val u0 = if (input) .375f else 0f; val u1 = if (input) .625f else 1f
                    vertex(x0, bottom, z0, u0, 0f, nx, 0f, nz); vertex(x0, top, z0, u0, 1f, nx, 0f, nz)
                    vertex(x1, top, z1, u1, 1f, nx, 0f, nz); vertex(x1, bottom, z1, u1, 0f, nx, 0f, nz)
                    val end = if (input) bottom else top
                    sprite = endSprite
                    val sign = if (input) -1f else 1f
                    vertex(0.0, end, 0.0, .5f, .5f, 0f, sign, 0f)
                    vertex(if (input) x0 else x1, end, if (input) z0 else z1, u1, 1f, 0f, sign, 0f)
                    vertex(if (input) x1 else x0, end, if (input) z1 else z0, u0, 1f, 0f, sign, 0f)
                    vertex(0.0, end, 0.0, .5f, .5f, 0f, sign, 0f)
                }
            }
            pose.popPose()
        }
        shaft(true); shaft(false)
    }
}

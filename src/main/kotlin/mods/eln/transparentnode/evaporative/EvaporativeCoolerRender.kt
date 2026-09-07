package mods.eln.transparentnode.evaporative

import mods.eln.Eln
import mods.eln.client.gl.GL11
import mods.eln.misc.Direction
import mods.eln.node.transparent.TransparentNodeDescriptor
import mods.eln.node.transparent.TransparentNodeElementRender
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.player.Player
import java.io.DataInputStream
import kotlin.math.exp

class EvaporativeCoolerRender(entity: TransparentNodeEntity, descriptor: TransparentNodeDescriptor) :
    TransparentNodeElementRender(entity, descriptor) {
    private val obj = Eln.obj.getObj("evaporativecooler")
    private var targetSpeed = 0f
    private var speed = 0f
    private var angle = 0f
    private var waterLevel = 0f
    private var wet = false
    private var particleTimer = 0f
    override fun draw() {
        front?.glRotateXnRef()
        obj.getPart("main")?.draw()
        obj.getPart(if (wet) "pad_wet" else "pad_dry")?.draw()
        obj.getPart("rotor")?.draw(angle, 1f, 0f, 0f)
        if (waterLevel > .001) {
            GL11.glPushMatrix()
            // Gauge bottom is at Y=-.4375 in model coordinates; scale level upwards from that point.
            GL11.glTranslated(0.0, -.4375, 0.0)
            GL11.glScalef(1f, waterLevel, 1f)
            GL11.glTranslated(0.0, .4375, 0.0)
            obj.getPart("water")?.draw()
            GL11.glPopMatrix()
        }
    }
    override fun refresh(deltaT: Float) {
        val dt = deltaT.coerceIn(0f, .25f)
        speed += (targetSpeed - speed) * (1 - exp(-dt * 3))
        angle = (angle + speed * 900f * dt) % 360f
        particleTimer += dt
        val world = tileEntity.level ?: return
        if (wet && speed > .1 && particleTimer >= .25f) {
            particleTimer = 0f
            val facing = front?.toFacing() ?: return
            val p = tileEntity.blockPos
            // Cosmetic moisture plume only; all energy and water accounting stays on the server.
            world.addParticle(ParticleTypes.CLOUD, p.x + .5 + facing.stepX * .55, p.y + .68,
                p.z + .5 + facing.stepZ * .55, facing.stepX * .03, .012, facing.stepZ * .03)
        }
    }
    override fun networkUnserialize(stream: DataInputStream) {
        super.networkUnserialize(stream)
        targetSpeed = stream.readFloat().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
        waterLevel = stream.readFloat().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
        wet = stream.readBoolean(); stream.readInt() // Status remains in the wire protocol for compatibility.
    }
    override fun newGuiDraw(side: Direction, player: Player) = EvaporativeCoolerScreen(EvaporativeCoolerMenu(player), player.inventory)
}

package mods.eln.sixnode.electricalcable

import mods.eln.cable.CableRender
import mods.eln.cable.CableRenderDescriptor
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.misc.UtilsClient.bindTexture
import mods.eln.misc.UtilsClient.disableBlend
import mods.eln.misc.UtilsClient.disableLight
import mods.eln.misc.UtilsClient.enableBlend
import mods.eln.misc.UtilsClient.enableLight
import mods.eln.node.six.SixNode
import mods.eln.node.six.SixNodeDescriptor
import mods.eln.node.six.SixNodeElement
import mods.eln.node.six.SixNodeElementRender
import mods.eln.node.six.SixNodeEntity
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import mods.eln.client.gl.GL11
import mods.eln.sim.IProcess
import mods.eln.misc.Utils
import net.minecraft.nbt.CompoundTag
import java.io.DataInputStream
import java.io.DataOutputStream

class MoltenMetalPileDescriptor(
    name: String,
    @JvmField val material: UtilityCableMaterial,
    @JvmField val render: CableRenderDescriptor
) : SixNodeDescriptor(name, MoltenMetalPileElement::class.java, MoltenMetalPileRender::class.java) {

    override fun addInformation(itemStack: ItemStack, entityPlayer: Player?, list: MutableList<String>, par4: Boolean) {
        super.addInformation(itemStack, entityPlayer, list, par4)
        list.add(tr("A puddle of molten %1$ from an overheated cable.", material.label.lowercase()))
    }
}

class MoltenMetalPileElement(
    sixNode: SixNode?,
    side: Direction?,
    descriptor: SixNodeDescriptor
) : SixNodeElement(sixNode!!, side!!, descriptor) {

    private val material = (descriptor as MoltenMetalPileDescriptor).material
    private var thermal = WireThermalLoad("scrap", WireThermalPhysics(material, 1.0))
    private var publishCountdown=0.0
    private var publishedTemperature=Double.NaN
    init {
        thermal.setAsSlow()
        thermalLoadList.add(thermal)
        thermalSlowProcessList.add(IProcess {
            val air = getAmbientTemperatureCelsius()
            thermal.updateProperties(air, air, false)
        })
        slowProcessList.add(IProcess { dt ->
            publishCountdown-=dt
            if(publishCountdown<=0) {
                publishCountdown=.5
                val t=thermal.absoluteCelsius
                if(publishedTemperature.isNaN() || kotlin.math.abs(t-publishedTemperature)>2 || (t>550)!=(publishedTemperature>550)) {
                    publishedTemperature=t;needPublish()
                }
            }
        })
    }
    private fun geometry(area: Double) {
        thermalLoadList.remove(thermal)
        thermal = WireThermalLoad("scrap", WireThermalPhysics(material, area))
        thermal.setAsSlow()
        thermalLoadList.add(thermal)
    }
    fun inheritHeat(from: WireThermalLoad) {
        geometry(from.physics.totalAreaMm2)
        thermal.inheritHeat(from)
        thermal.updateProperties(from.ambientCelsius, from.ambientCelsius, false)
    }
    override fun initialize() {
        val air = getAmbientTemperatureCelsius()
        thermal.updateProperties(air, air, false)
    }
    override fun readFromNBT(nbt: CompoundTag) {
        geometry(nbt.getDouble("scrapAreaMm2").takeIf { it.isFinite() && it > 0 } ?: 1.0)
        super.readFromNBT(nbt)
    }
    override fun writeToNBT(nbt: CompoundTag) {
        super.writeToNBT(nbt)
        nbt.putDouble("scrapAreaMm2", thermal.physics.totalAreaMm2)
    }
    override fun networkSerialize(stream: DataOutputStream) {
        super.networkSerialize(stream)
        stream.writeFloat(thermal.absoluteCelsius.toFloat())
    }

    override fun getElectricalLoad(lrdu: LRDU, mask: Int) = null

    override fun getThermalLoad(lrdu: LRDU, mask: Int) = null

    override fun getConnectionMask(lrdu: LRDU) = 0

    override fun multiMeterString() = ""

    override fun thermoMeterString() = Utils.plotCelsius("T: ", thermal.absoluteCelsius)

    override fun getWaila() = mapOf(tr("State") to tr("Broken conductor"), tr("Temperature") to Utils.plotCelsius("", thermal.absoluteCelsius))
}

class MoltenMetalPileRender(
    tileEntity: SixNodeEntity?,
    side: Direction?,
    descriptor: SixNodeDescriptor
) : SixNodeElementRender(tileEntity!!, side!!, descriptor) {

    private val descriptor = descriptor as MoltenMetalPileDescriptor
    private var temperature = 20f
    override fun publishUnserialize(stream: DataInputStream) {
        super.publishUnserialize(stream)
        temperature = stream.readFloat()
    }

    override fun drawCableAuto() = false

    override fun glListEnable() = true

    override fun glListDraw() {
        CableRender.drawNode(descriptor.render, connectedSide, CableRender.connectionType(this, side))
    }

    override fun getCableRender(lrdu: LRDU) = descriptor.render

    override fun draw() {
        Minecraft.getInstance().profiler.push("MoltenMetalPile")
        when (descriptor.material) {
            UtilityCableMaterial.COPPER -> GL11.glColor3f(0.92f, 0.38f, 0.10f)
            UtilityCableMaterial.ALUMINUM -> GL11.glColor3f(0.83f, 0.85f, 0.88f)
        }
        bindTexture(descriptor.render.cableTexture)
        glListCall()
        if (temperature > 550f) drawHotGlow()
        GL11.glColor3f(1f, 1f, 1f)
        Minecraft.getInstance().profiler.pop()
    }

    private fun drawHotGlow() {
        disableLight()
        enableBlend()
        GL11.glColor4f(1.0f, 0.55f, 0.12f, 0.8f)
        bindTexture(descriptor.render.cableTexture)
        glListCall()
        disableBlend()
        enableLight()
    }
}

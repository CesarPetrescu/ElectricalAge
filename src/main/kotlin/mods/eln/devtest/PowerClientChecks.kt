package mods.eln.devtest

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import mods.eln.Eln
import mods.eln.client.gl.FixedFunction
import mods.eln.client.gl.GL11
import mods.eln.mechanical.GeneratorDescriptor
import mods.eln.mechanical.MotorDescriptor
import mods.eln.misc.Obj3D
import mods.eln.misc.UtilsClient
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.registries.BuiltInRegistries
import java.lang.reflect.Proxy

/** Capture the real emitted vertices of the real OBJ LED meshes on the client runner. */
object PowerClientChecks {
    @JvmStatic fun run(): Int {
        val report = ContractReport("power-client-leds")
        val colors = mutableListOf<List<Double>>()
        val lights = mutableListOf<Int>()
        val consumer = Proxy.newProxyInstance(VertexConsumer::class.java.classLoader, arrayOf(VertexConsumer::class.java)) { proxy, method, args ->
            when (method.name) {
                "setColor" -> if (args?.size == 4) colors.add(args.map { (it as Number).toDouble() / if (it is Int) 255.0 else 1.0 })
                "setLight" -> lights.add(args!![0] as Int)
            }
            if (method.returnType == VertexConsumer::class.java) proxy else null
        } as VertexConsumer
        val buffers = MultiBufferSource { consumer }
        for (d in Eln.transparentNodeItem.subItemList.values.filterNotNull()) {
            val parts: Array<Obj3D.Obj3DPart> = when (d) {
                is GeneratorDescriptor -> d.powerLights
                is MotorDescriptor -> d.leds
                else -> continue
            }
            val key = BuiltInRegistries.ITEM.getKey(d.parentItem).toString()
            for ((index, part) in parts.withIndex()) report.test(key, "led-$index-live-colors") {
                // First render black, then reuse the SAME cached mesh in multiple colors and frames.
                for (color in listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 1f, 0f),
                    floatArrayOf(1f, 1f, 0f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 0f))) {
                    colors.clear(); lights.clear()
                    FixedFunction.begin(PoseStack(), buffers, 0, OverlayTexture.NO_OVERLAY)
                    try {
                        UtilsClient.disableLight()
                        GL11.glColor3f(color[0], color[1], color[2])
                        part.draw()
                    } finally { FixedFunction.finish() }
                    check(colors.isNotEmpty()) { "LED emitted no geometry" }
                    check(colors.all { got -> (0..2).all { kotlin.math.abs(got[it] - color[it]) < .005 } }) {
                        "Cached LED color differs from current color: expected ${color.toList()}, got ${colors.first()}"
                    }
                    check(lights.isNotEmpty() && lights.all { it == LightTexture.pack(15, 15) }) { "LED not emissive in darkness" }
                }
            }
        }
        report.write(true)
        return report.failures
    }
}

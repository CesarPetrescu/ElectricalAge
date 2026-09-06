package mods.eln.cable

import mods.eln.misc.UtilsClient.bindTexture
import net.minecraft.resources.ResourceLocation

class CableRenderDescriptor(modName: String?, cableTexture: String?, var widthPixel: Float, var heightPixel: Float) {
    var width: Float = widthPixel / 16
    var height: Float = heightPixel / 16
    var widthDiv2: Float = width / 2
    // The asset tree is lowercase (1.13+ resource locations must be); the registrations still name
    // textures the 1.7.10 way ("sprites/cableVHV.png").
    @JvmField
    var cableTexture = ResourceLocation.fromNamespaceAndPath(modName, cableTexture!!.lowercase(java.util.Locale.ROOT))

    fun bindCableTexture() {
        bindTexture(cableTexture)
    }
}

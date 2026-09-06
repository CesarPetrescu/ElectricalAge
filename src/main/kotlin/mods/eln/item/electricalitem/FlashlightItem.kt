package mods.eln.item.electricalitem

import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.lightblock.LightBlockEntity
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.util.Mth
import net.minecraft.world.level.Level
import mods.eln.misc.isAirBlock
import mods.eln.misc.isEmptyBlock

abstract class FlashlightItem(name: String?) : GenericItemUsingDamageDescriptor(name!!) {
    abstract fun getLightState(stack: ItemStack): Int
    abstract fun getRange(stack: ItemStack): Int
    abstract fun getLight(stack: ItemStack): Int
    override fun onUpdate(stack: ItemStack, world: Level, entity: Entity, par4: Int, par5: Boolean) {
        if (world.isClientSide) return
        if (getLightState(stack) == 0) return
        val light = getLight(stack)
        if (light == 0) return
        for (yOffset in 0..1) {
            var x = entity.x
            var y = entity.y + 1.62 - yOffset
            var z = entity.z
            val v = entity.lookVec.scale(0.25) // Vec3d is immutable on 1.12.2
            val range = getRange(stack) + 1
            var rCount = 0
            for (idx in 0 until range) {
                x += v.x
                y += v.y
                z += v.z
                val fx = Mth.floor(x)
                val fy = Mth.floor(y)
                val fz = Mth.floor(z)
                if (!world.isEmptyBlock(fx, fy, fz)) {
                    x -= v.x
                    y -= v.y
                    z -= v.z
                    break
                }
                rCount++
            }
            while (rCount > 0) {
                var stride = 1
                val fx = Mth.floor(x)
                val fy = Mth.floor(y)
                val fz = Mth.floor(z)
                if (world.isEmptyBlock(fx, fy, fz)) {
                    LightBlockEntity.addLight(world, fx, fy, fz, light, 5)
                    stride = 3
                }
                x -= v.x * stride
                y -= v.y * stride
                z -= v.z * stride
                rCount -= stride
            }
        }
    }
}

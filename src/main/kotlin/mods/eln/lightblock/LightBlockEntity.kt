package mods.eln.lightblock

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.INBTTReady
import mods.eln.misc.Utils
import net.minecraft.world.level.block.Blocks
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import java.util.function.Supplier
import net.minecraft.world.level.Level
import mods.eln.misc.getBlock
import mods.eln.misc.getBlockMetadata
import mods.eln.misc.getBlockEntity
import mods.eln.misc.setBlock
import mods.eln.misc.setBlockToAir
import mods.eln.misc.xCoord
import mods.eln.misc.yCoord
import mods.eln.misc.zCoord
import mods.eln.misc.getBlockState
import mods.eln.misc.writeToNBT

class LightBlockEntity(pos: BlockPos, state: BlockState) : BlockEntity(TYPE.get(), pos, state) {

    /** 1.7.10's `worldObj`. */
    val world: Level
        get() = level!!

    companion object {
        /** Registered by Eln through ElnRegistry.registerBlockEntity. */
        @JvmField
        var TYPE: Supplier<BlockEntityType<LightBlockEntity>> = Supplier { throw IllegalStateException("LightBlockEntity type not registered") }

        @JvmField
        val observers: MutableList<LightBlockObserver> = mutableListOf()

        @JvmStatic
        fun addLight(w: Level, x: Int, y: Int, z: Int, light: Int, timeout: Int) {
            val block = w.getBlock(x, y, z)

            if (block !== Eln.lightBlock) {
                if (block !== Blocks.AIR) return
                w.setBlock(x, y, z, Eln.lightBlock, light, 2)
            }

            val t = w.getBlockEntity(x, y, z)

            if (t is LightBlockEntity) t.addLight(light, timeout)
            else Utils.println("Error in setting light at %d %d %d", x, y, z)
        }

        @JvmStatic
        fun addLight(coord: Coordinate, light: Int, timeout: Int) {
            addLight(coord.world(), coord.x, coord.y, coord.z, light, timeout)
        }
    }

    interface LightBlockObserver {

        fun lightBlockDestructor(coord: Coordinate)

    }

    private val lightList: MutableList<LightHandle> = mutableListOf()

    private fun addLight(light: Int, timeout: Int) {
        // block light is 0..15 (a state property now; 1.7.10's metadata nibble silently wrapped 25 to 9)
        lightList.add(LightHandle(light.coerceIn(0, 15), timeout))
    }

    internal class LightHandle(var value: Int = 0, var timeout: Int = 0) : INBTTReady {

        override fun readFromNBT(nbt: CompoundTag, str: String) {
            value = nbt.getInt(str + "value")
            timeout = nbt.getInt(str + "timeout")
        }

        override fun writeToNBT(nbt: CompoundTag, str: String) {
            nbt.putInt(str + "value", value)
            nbt.putInt(str + "timeout", timeout)
        }

    }

    /** Ticked by the block's BlockEntityTicker (server only). */
    fun update() {
        if (world.isClientSide) return

        if (lightList.isEmpty()) {
            world.setBlockToAir(xCoord, yCoord, zCoord)
            Utils.println("Destroy light at %d %d %d", xCoord, yCoord, zCoord)
            return
        }

        var light = 0
        val iterator: MutableIterator<LightHandle> = lightList.iterator()

        while (iterator.hasNext()) {
            val l = iterator.next()
            if (light < l.value) light = l.value
            l.timeout--
            if (l.timeout <= 0) iterator.remove()
        }

        val state = world.getBlockState(blockPos)
        light = light.coerceIn(0, 15)
        if (state.block === Eln.lightBlock && light != state.getValue(LightBlock.LIGHT)) {
            // The light level is a blockstate property, not a metadata nibble; the light engine follows the state change.
            world.setBlock(blockPos, state.setValue(LightBlock.LIGHT, light), 2)
        }
    }

}
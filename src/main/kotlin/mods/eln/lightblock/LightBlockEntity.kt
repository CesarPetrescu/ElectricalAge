package mods.eln.lightblock

import mods.eln.Eln
import mods.eln.misc.Coordinate
import mods.eln.misc.INBTTReady
import mods.eln.misc.Utils
import net.minecraft.world.level.block.Blocks
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.util.ITickable
import net.minecraft.world.level.LightLayer
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

class LightBlockEntity : BlockEntity(), ITickable {

    companion object {
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
        lightList.add(LightHandle(light, timeout))
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

    override fun update() {
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

        val state = world.getBlockState(pos)
        if (state.block === Eln.lightBlock && light != state.getValue(LightBlock.LIGHT)) {
            // The light level is a blockstate property on 1.12, not a metadata nibble.
            world.setBlockState(pos, state.withProperty(LightBlock.LIGHT, light), 2)
            world.checkLightFor(LightLayer.BLOCK, pos)
        }
    }

}
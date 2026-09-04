package mods.eln.server

import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent
import mods.eln.Eln
import mods.eln.environment.RoomThermalManager
import mods.eln.item.electricalitem.TreeCapitation.process
import mods.eln.misc.Coordinate
import mods.eln.misc.Utils
import mods.eln.mqtt.MqttManager
import mods.eln.node.NodeManager
import mods.eln.server.ElnWorldStorage.Companion.forWorld
import net.minecraft.entity.effect.EntityLightningBolt
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.World
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.EntityEvent.EntityConstructing
import net.minecraftforge.event.world.WorldEvent
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*

class ServerEventListener {
    private var lightningListNext = LinkedList<EntityLightningBolt>()
    private var lightningList = LinkedList<EntityLightningBolt>()
    @SubscribeEvent
    fun tick(event: ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        lightningList = lightningListNext
        lightningListNext = LinkedList()
        process(0.05)
    }

    @SubscribeEvent
    fun onNewEntity(event: EntityConstructing) {
        if (event.entity is EntityLightningBolt) {
            lightningListNext.add(event.entity as EntityLightningBolt)
        }
    }

    fun clear() {
        lightningList.clear()
    }

    fun getLightningClosestTo(c: Coordinate): Double {
        var best = 10000000.0
        for (l in lightningList) {
            if (c.world() !== l.world) continue
            val d = l.getDistance(c.x.toDouble(), c.y.toDouble(), c.z.toDouble())
            if (d < best) best = d
        }
        return best
    }

    private val loadedWorlds = HashSet<Int>()
    @SubscribeEvent
    fun onWorldLoad(e: WorldEvent.Load) {
        if (e.world.isRemote) return
        loadedWorlds.add(e.world.provider.dimension)
        val fileNames = FileNames(e)
        val dimension = e.world.provider.dimension
        try {
            readSave(fileNames.worldSave, dimension)
        } catch (ex: Exception) {
            try {
                ex.printStackTrace()
                println("Using BACKUP Electrical Age save: " + fileNames.backupSave)
                readSave(fileNames.backupSave, dimension)
            } catch (ex2: Exception) {
                ex2.printStackTrace()
                println("Failed to read backup save!")
                forWorld(e.world)
            }
        }
    }

    @Throws(IOException::class)
    private fun readSave(worldSave: Path, dimension: Int) {
        val inputStream = ByteArrayInputStream(Files.readAllBytes(worldSave))
        val nbt = CompressedStreamTools.readCompressed(inputStream)
        readFromEaWorldNBT(nbt, dimension)
    }

    @SubscribeEvent
    fun onWorldUnload(e: WorldEvent.Unload) {
        if (e.world.isRemote) return
        loadedWorlds.remove(e.world.provider.dimension)
        try {
            NodeManager.instance!!.unload(e.world.provider.dimension)
            Eln.ghostManager.unload(e.world.provider.dimension)
            RoomThermalManager.unloadDimension(e.world.provider.dimension)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @SubscribeEvent
    fun onWorldSave(e: WorldEvent.Save) {
        if (e.world.isRemote) return
        if (!loadedWorlds.contains(e.world.provider.dimension)) {
            //System.out.println("I hate you minecraft");
            return
        }
        try {
            val nbt = NBTTagCompound()
            writeToEaWorldNBT(nbt, e.world.provider.dimension)
            val fileNames = FileNames(e)

            // Write a new save to a temporary file.
            val bytes = ByteArrayOutputStream(512 * 1024)
            CompressedStreamTools.writeCompressed(nbt, bytes)
            Files.write(fileNames.tempSave, bytes.toByteArray())

            // Replace backup save with old save, and old save with new one.
            if (Files.exists(fileNames.worldSave)) replaceFile(fileNames.worldSave, fileNames.backupSave)
            replaceFile(fileNames.tempSave, fileNames.worldSave)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @Throws(IOException::class)
    private fun replaceFile(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private inner class FileNames internal constructor(e: WorldEvent) {
        val worldSave: Path
        val tempSave: Path
        val backupSave: Path
        private fun getEaWorldSaveName(w: World): String {
            return Utils.mapFolder + "data/electricalAgeWorld" + w.provider.dimension + ".dat"
        }

        init {
            val saveName = getEaWorldSaveName(e.world)
            worldSave = FileSystems.getDefault().getPath(saveName)
            tempSave = FileSystems.getDefault().getPath("$saveName.tmp")
            backupSave = FileSystems.getDefault().getPath("$saveName.bak")
        }
    }

    companion object {
        fun readFromEaWorldNBT(nbt: NBTTagCompound, dim: Int) {
            try {
                NodeManager.instance!!.loadFromNbt(nbt.getCompoundTag("nodes"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                Eln.ghostManager.loadFromNBT(nbt.getCompoundTag("ghost"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                MqttManager.readWorldData(nbt.getCompoundTag("mqtt"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                RoomThermalManager.readFromNbt(nbt, dim)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun writeToEaWorldNBT(nbt: NBTTagCompound?, dim: Int) {
            try {
                NodeManager.instance!!.saveToNbt(Utils.newNbtTagCompund(nbt, "nodes"), dim)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                Eln.ghostManager.saveToNBT(Utils.newNbtTagCompund(nbt, "ghost"), dim)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                val tag = Utils.newNbtTagCompund(nbt, "mqtt")
                MqttManager.writeWorldData(tag)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                RoomThermalManager.writeToNbt(nbt!!, dim)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        MinecraftForge.EVENT_BUS.register(this)
        MinecraftForge.EVENT_BUS.register(this)
    }
}

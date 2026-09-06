package mods.eln.server

import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import mods.eln.Eln
import mods.eln.environment.RoomThermalManager
import mods.eln.item.electricalitem.TreeCapitation.process
import mods.eln.misc.Coordinate
import mods.eln.misc.Utils
import mods.eln.mqtt.MqttManager
import mods.eln.node.NodeManager
import mods.eln.server.ElnWorldStorage.Companion.forWorld
import net.minecraft.world.entity.LightningBolt
import net.minecraft.nbt.NbtIo
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.minecraft.nbt.NbtAccounter
import mods.eln.misc.DimensionIds
import net.neoforged.neoforge.event.level.LevelEvent
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
    private var lightningListNext = LinkedList<LightningBolt>()
    private var lightningList = LinkedList<LightningBolt>()
    @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        lightningList = lightningListNext
        lightningListNext = LinkedList()
        process(0.05)
    }

    /** Electrical armor absorbs damage with energy (1.7.10's ISpecialArmor, see ElectricalArmor.absorb). */
    @SubscribeEvent
    fun onLivingDamage(event: net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre) {
        mods.eln.item.electricalitem.ElectricalArmor.absorb(event)
    }

    @SubscribeEvent
    fun onNewEntity(event: EntityJoinLevelEvent) {
        if (event.entity is LightningBolt) {
            lightningListNext.add(event.entity as LightningBolt)
        }
    }

    fun clear() {
        lightningList.clear()
    }

    fun getLightningClosestTo(c: Coordinate): Double {
        var best = 10000000.0
        for (l in lightningList) {
            if (c.world() !== l.level()) continue
            val d = Math.sqrt(l.distanceToSqr(c.x.toDouble(), c.y.toDouble(), c.z.toDouble()))
            if (d < best) best = d
        }
        return best
    }

    private val loadedWorlds = HashSet<Int>()
    @SubscribeEvent
    fun onWorldLoad(e: LevelEvent.Load) {
        val level = e.level as? Level ?: return
        if (level.isClientSide) return
        loadedWorlds.add(DimensionIds.id(level))
        val fileNames = FileNames(level)
        val dimension = DimensionIds.id(level)
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
                forWorld(level)
            }
        }
    }

    @Throws(IOException::class)
    private fun readSave(worldSave: Path, dimension: Int) {
        val inputStream = ByteArrayInputStream(Files.readAllBytes(worldSave))
        val nbt = NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap())
        readFromEaWorldNBT(nbt, dimension)
    }

    @SubscribeEvent
    fun onWorldUnload(e: LevelEvent.Unload) {
        val level = e.level as? Level ?: return
        if (level.isClientSide) return
        val dimension = DimensionIds.id(level)
        loadedWorlds.remove(dimension)
        try {
            NodeManager.instance!!.unload(dimension)
            Eln.ghostManager.unload(dimension)
            RoomThermalManager.unloadDimension(dimension)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    @SubscribeEvent
    fun onWorldSave(e: LevelEvent.Save) {
        val level = e.level as? Level ?: return
        if (level.isClientSide) return
        val dimension = DimensionIds.id(level)
        if (!loadedWorlds.contains(dimension)) {
            //System.out.println("I hate you minecraft");
            return
        }
        try {
            val nbt = CompoundTag()
            writeToEaWorldNBT(nbt, dimension)
            val fileNames = FileNames(level)

            // Write a new save to a temporary file.
            val bytes = ByteArrayOutputStream(512 * 1024)
            NbtIo.writeCompressed(nbt, bytes)
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

    private inner class FileNames internal constructor(level: Level) {
        val worldSave: Path
        val tempSave: Path
        val backupSave: Path
        /** `<world>/data/electricalAgeWorld<dim>.dat`, resolved through the server's level path (1.7.10 built the string). */
        private fun getEaWorldSaveName(w: Level): String {
            return Utils.getMapFile("data/electricalAgeWorld" + DimensionIds.id(w) + ".dat").path
        }

        init {
            val saveName = getEaWorldSaveName(level)
            worldSave = FileSystems.getDefault().getPath(saveName)
            tempSave = FileSystems.getDefault().getPath("$saveName.tmp")
            backupSave = FileSystems.getDefault().getPath("$saveName.bak")
            Files.createDirectories(worldSave.parent)
        }
    }

    companion object {
        fun readFromEaWorldNBT(nbt: CompoundTag, dim: Int) {
            try {
                NodeManager.instance!!.loadFromNbt(nbt.getCompound("nodes"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                Eln.ghostManager.loadFromNBT(nbt.getCompound("ghost"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                MqttManager.readWorldData(nbt.getCompound("mqtt"))
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                RoomThermalManager.readFromNbt(nbt, dim)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun writeToEaWorldNBT(nbt: CompoundTag?, dim: Int) {
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
        NeoForge.EVENT_BUS.register(this)
    }
}

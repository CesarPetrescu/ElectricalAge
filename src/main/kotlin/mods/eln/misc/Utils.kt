@file:Suppress("NAME_SHADOWING")
package mods.eln.misc

import mods.eln.Eln
import net.minecraft.world.entity.LivingEntity
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.FurnaceBlockEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.Container
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerPlayer
import mods.eln.client.gl.GL11
import mods.eln.network.ElnNetwork
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.GameRules
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.server.ServerLifecycleHooks
import net.minecraft.core.NonNullList
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.neoforged.api.distmarker.Dist
import mods.eln.ServerKeyHandler
import net.minecraft.world.entity.item.ItemEntity
import java.io.IOException
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.world.level.LightLayer
import mods.eln.node.ITileEntitySpawnClient
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import mods.eln.generic.GenericItemUsingDamage
import mods.eln.generic.GenericItemBlockUsingDamage
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.phys.Vec3
import net.minecraft.client.player.RemotePlayer
import net.minecraft.world.level.block.Blocks
import java.lang.SecurityException
import java.lang.IllegalAccessException
import java.lang.NoSuchFieldException
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.ShapelessRecipe
import net.minecraft.network.chat.Component
import net.minecraft.world.level.BlockGetter
import kotlin.jvm.JvmOverloads
import java.io.FileInputStream
import mods.eln.misc.Obj3D.Obj3DPart
import mods.eln.sim.mna.SubSystem
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.Item
import net.minecraft.world.level.chunk.LevelChunk
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.lang.ClassNotFoundException
import java.lang.Exception
import java.lang.IllegalArgumentException
import java.nio.charset.Charset
import java.text.DecimalFormat
import java.util.*
import kotlin.experimental.and
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

object Utils {
    val d = arrayOfNulls<Any>(5)
    const val minecraftDay = (60 * 24).toDouble()
    val random = Random()
    const val burnTimeToEnergyFactor = 1.0
    const val voltageMageFactor = 0.1

    @JvmStatic
    var uuid = 1
        get() {
            if (field < 1) field = 1
            return field++
        }
        private set

    @JvmStatic
    fun rand(min: Double, max: Double): Double {
        return random.nextDouble() * (max - min) + min
    }

    @JvmStatic
    fun println(str: String?) {
        if (Eln.config.getBooleanOrElse("debug.logging.enabled", false)) Eln.logger.info(str)
    }

    @JvmStatic
    fun println(str: Any?) {
        if (str != null) println(str.toString())
    }

    @JvmStatic
    fun println(format: String?, vararg data: Any?) {
        println(String.format(format!!, *data))
    }

    @JvmStatic
    fun floatToStr(f: Double, high: Int, low: Int): String {
        var temp = ""
        for (idx in 0 until high) temp += "0"
        temp = "$temp."
        for (idx in 0 until low) temp += "0"
        val str = DecimalFormat(temp).format(f)
        var idx = 0
        val ch = str.toCharArray()
        while (true) {
            if (str.length == idx) break
            if (ch[idx] == '.') {
                ch[idx - 1] = '0'
                break
            }
            if (ch[idx] != '0' && ch[idx] != ' ') break
            ch[idx] = '_'
            idx++
        }
        return String(ch)
    }

    @JvmStatic
    fun isTheClass(o: Any, c: Class<*>): Boolean {
        if (o.javaClass == c) return true
        var classIterator: Class<*>? = o.javaClass.superclass
        while (classIterator != null) {
            if (classIterator == c) {
                return true
            }
            classIterator = classIterator.superclass
        }
        return false
    }

    @JvmStatic
    fun entityLivingViewDirection(entityLiving: LivingEntity): Direction {
        if (entityLiving.xRot > 45) return Direction.YN
        if (entityLiving.xRot < -45) return Direction.YP
        val dirx = Mth.floor((entityLiving.yRot * 4.0f / 360.0f).toDouble() + 0.5) and 3
        if (dirx == 3) return Direction.XP
        if (dirx == 0) return Direction.ZP
        return if (dirx == 1) Direction.XN else Direction.ZN
    }

    @JvmStatic
    fun entityLivingHorizontalViewDirection(entityLiving: LivingEntity): Direction {
        val dirx = Mth.floor((entityLiving.yRot * 4.0f / 360.0f).toDouble() + 0.5) and 3
        if (dirx == 3) return Direction.XP
        if (dirx == 0) return Direction.ZP
        return if (dirx == 1) Direction.XN else Direction.ZN
    }

    @JvmStatic
    fun getItemEnergie(par0ItemStack: ItemStack?): Double {
        return burnTimeToEnergyFactor * 80000.0 / 1600 * (par0ItemStack?.getBurnTime(null) ?: 0)
    }

    @JvmStatic
    val coalEnergyReference: Double
        get() = burnTimeToEnergyFactor * 80000.0

    @JvmStatic
    fun plotValue(value: Double): String {
        val valueAbs = abs(value)
        return when {
            valueAbs < 0.0001 ->
                "0"
            valueAbs < 0.000999 ->
                String.format("%1.2fµ", value * 10_000)
            valueAbs < 0.00999 ->
                String.format("%1.2fm", value * 1_000)
            valueAbs < 0.0999 ->
                String.format("%2.1fm", value * 1_000)
            valueAbs < 0.999 ->
                String.format("%3.0fm", value * 1_000)
            valueAbs < 9.99 ->
                String.format("%1.2f", value)
            valueAbs < 99.9 ->
                String.format("%2.1f", value)
            valueAbs < 999 ->
                String.format("%3.0f", value)
            valueAbs < 9999 ->
                String.format("%1.2fk", value / 1_000.0)
            valueAbs < 99999 ->
                String.format("%2.1fk", value / 1_000.0)
            valueAbs < 999999 ->
                String.format("%3.0fK", value / 1_000.0)
            valueAbs < 9999999 ->
                String.format("%1.2fM", value / 1_000_000.0)
            valueAbs < 99999999 ->
                String.format("%2.1fM", value / 1_000_000.0)
            valueAbs < 999999999 ->
                String.format("%3.0fM", value / 1_000_000.0)
            valueAbs < 9999999999 ->
                String.format("%1.2fG", value / 1_000_000_000.0)
            valueAbs < 99999999999 ->
                String.format("%2.1fG", value / 1_000_000_000.0)
            valueAbs < 999999999999 ->
                String.format("%3.0fG", value / 1_000_000_000.0)
            valueAbs < 9999999999999 ->
                String.format("%1.2fT", value / 1_000_000_000_000.0)
            valueAbs < 99999999999999 ->
                String.format("%2.1fT", value / 1_000_000_000_000.0)
            valueAbs < 999999999999999 ->
                String.format("%3.0fT", value / 1_000_000_000_000.0)
            valueAbs < 9999999999999999 ->
                String.format("%1.2fP", value / 1_000_000_000_000_000.0)
            valueAbs < 99999999999999999 ->
                String.format("%2.1fP", value / 1_000_000_000_000_000.0)
            valueAbs < 999999999999999999 ->
                String.format("%3.0fP", value / 1_000_000_000_000_000.0)
            valueAbs < 9999999999999999999.0 ->
                String.format("%1.2fE", value / 1_000_000_000_000_000_000.0)
            valueAbs < 99999999999999999999.0 ->
                String.format("%2.1fE", value / 1_000_000_000_000_000_000.0)
            else ->
                String.format("%3.0fE", value / 1_000_000_000_000_000_000.0)
        }
    }

    @JvmStatic
    fun plotValue(value: Double, unit: String): String {
        return plotValue(value) + unit
    }

    @JvmStatic
    fun plotVolt(value: Double): String {
        return plotValue(value, "V  ")
    }

    @JvmStatic
    fun plotVolt(header: String, value: Double): String {
        var header = header
        if (header != "") header += " "
        return header + plotVolt(value)
    }

    @JvmStatic
    fun plotAmpere(value: Double): String {
        return plotValue(value, "A  ")
    }

    @JvmStatic
    fun plotAmpere(header: String, value: Double): String {
        var header = header
        if (header != "") header += " "
        return header + plotAmpere(value)
    }

    @JvmStatic
    fun plotCelsius(header: String, value: Double): String {
        var header = header
        if (header != "") header += " "
        return header + plotValue(value, "\u00B0C ")
    }

    @JvmStatic
    fun plotPercent(header: String, value: Double): String {
        var header = header
        if (header != "") header += " "
        return if (value >= 1.0) header + String.format("%3.0f", value * 100.0) + "%   " else header + String.format("%3.1f", value * 100.0) + "%   "
    }

    @JvmStatic
    fun plotEnergy(value: Double): String {
        return plotValue(value, "J  ")
    }

    @JvmStatic
    fun plotEnergy(header: String, value: Double): String {
        var header = header
        if (header != "") header += " "
        return header + plotEnergy(value)
    }

    @JvmStatic
    fun plotRads(header: String, value: Double): String {
        var header = header
        if (header != "") header += " "
        return header + plotValue(value, "rad/s ")
    }

    @JvmStatic
    fun plotER(E: Double, R: Double): String {
        return plotEnergy("E", E) + plotRads("R", R)
    }

    @JvmStatic
    fun plotPower(value: Double): String {
        return plotValue(value, "W  ")
    }

    @JvmStatic
    fun plotPower(header: String, value: Double): String {
        var header = header
        if (header != "") header += " "
        return header + plotPower(value)
    }

    @JvmStatic
    fun plotOhm(value: Double): String {
        return plotValue(value, "\u2126 ")
    }

    @JvmStatic
    fun plotOhm(header: String, value: Double): String {
        var header = header
        if (header != "") header += " "
        return header + plotOhm(value)
    }

    @JvmStatic
    fun plotUIP(U: Double, I: Double): String {
        return plotVolt("U", U) + plotAmpere("I", I) + plotPower("P", abs(U * I))
    }

    @JvmStatic
    fun plotUIP(U: Double, I: Double, R: Double): String {
        return plotVolt("U", U) + plotAmpere("I", I) + plotPower("P", I * I * R)
    }

    @JvmStatic
    fun plotTime(value: Double): String {
        var value = value
        var str = ""
        if (value == 0.0) return str + "0''"
        val h: Int = (value / 3600).toInt()
        value %= 3600
        val mn: Int = (value / 60).toInt()
        value %= 60
        val s: Int = (value / 1).toInt()
        if (h != 0) str += h.toString() + "h"
        if (mn != 0) str += "$mn'"
        if (s != 0) str += "$s''"
        return str
    }

    @JvmStatic
    fun plotTime(header: String, value: Double): String {
        var header = header
        if (header != "") header += " "
        return header + plotTime(value)
    }

    @JvmStatic
    fun plotBuckets(header: String, buckets: Double): String {
        var header = header
        if (header != "") header += " "
        return header + plotValue(buckets, "B ")
    }

    @JvmStatic
    fun readFromNBT(nbt: CompoundTag, str: String?, inventory: Container) {
        val var2 = nbt.getList(str, 10)
        for (var3 in 0 until var2.size) {
            val var4 = var2.getCompound(var3)
            val var5: Int = (var4.getByte("Slot") and (255).toByte()).toInt()
            if (var5 >= 0 && var5 < inventory.containerSize) {
                inventory.setItem(var5, stackFromNbt(var4))
            }
        }
    }

    @JvmStatic
    fun writeToNBT(nbt: CompoundTag, str: String?, inventory: Container) {
        val var2 = ListTag()
        for (var3 in 0 until inventory.containerSize) {
            if (!inventory.getItem(var3).isNothing()) {
                val var4 = CompoundTag()
                var4.putByte("Slot", var3.toByte())
                inventory.getItem(var3).writeToNBT(var4)
                var2.add(var4)
            }
        }
        nbt.put(str, var2)
    }

    @JvmStatic
    fun sendPacketToClient(bos: ByteArrayOutputStream, player: ServerPlayer) {
        ElnNetwork.sendTo(bos, player)
    }

    @JvmStatic
    fun setGlColorFromDye(damage: Int) {
        setGlColorFromDye(damage, 1.0f)
    }

    @JvmStatic
    fun setGlColorFromDye(damage: Int, gain: Float) {
        setGlColorFromDye(damage, gain, 0f)
    }

    @JvmStatic
    fun setGlColorFromDye(damage: Int, gain: Float, bias: Float) {
        when (damage) {
            0 -> GL11.glColor4f(0.2f * gain + bias, 0.2f * gain + bias, 0.2f * gain + bias, 1.0f)
            1 -> GL11.glColor4f(1.0f * gain + bias, 0.05f * gain + bias, 0.05f * gain + bias, 1.0f)
            2 -> GL11.glColor4f(0.2f * gain + bias, 0.5f * gain + bias, 0.1f * gain + bias, 1.0f)
            3 -> GL11.glColor4f(0.3f * gain + bias, 0.2f * gain + bias, 0.1f * gain + bias, 1.0f)
            4 -> GL11.glColor4f(0.2f * gain + bias, 0.2f * gain + bias, 1.0f * gain + bias, 1.0f)
            5 -> GL11.glColor4f(0.7f * gain + bias, 0.05f * gain + bias, 1.0f * gain + bias, 1.0f)
            6 -> GL11.glColor4f(0.2f * gain + bias, 0.7f * gain + bias, 0.9f * gain + bias, 1.0f)
            7 -> GL11.glColor4f(0.7f * gain + bias, 0.7f * gain + bias, 0.7f * gain + bias, 1.0f)
            8 -> GL11.glColor4f(0.4f * gain + bias, 0.4f * gain + bias, 0.4f * gain + bias, 1.0f)
            9 -> GL11.glColor4f(1.0f * gain + bias, 0.5f * gain + bias, 0.5f * gain + bias, 1.0f)
            10 -> GL11.glColor4f(0.05f * gain + bias, 1.0f * gain + bias, 0.05f * gain + bias, 1.0f)
            11 -> GL11.glColor4f(0.9f * gain + bias, 0.8f * gain + bias, 0.1f * gain + bias, 1.0f)
            12 -> GL11.glColor4f(0.4f * gain + bias, 0.5f * gain + bias, 1.0f * gain + bias, 1.0f)
            13 -> GL11.glColor4f(0.9f * gain + bias, 0.3f * gain + bias, 0.9f * gain + bias, 1.0f)
            14 -> GL11.glColor4f(1.0f * gain + bias, 0.6f * gain + bias, 0.3f * gain + bias, 1.0f)
            15 -> GL11.glColor4f(1.0f * gain + bias, 1.0f * gain + bias, 1.0f * gain + bias, 1.0f)
            else -> GL11.glColor4f(0.05f * gain + bias, 0.05f * gain + bias, 0.05f * gain + bias, 1.0f)
        }
    }

    @JvmStatic
    fun setGlColorFromLamp(colorIdx: Int) {
        when (colorIdx) {
            15 -> GL11.glColor3f(1.0f, 1.0f, 1.0f)
            0 -> GL11.glColor3f(0.25f, 0.25f, 0.25f)
            1 -> GL11.glColor3f(1.0f, 0.5f, 0.5f)
            2 -> GL11.glColor3f(0.5f, 1.0f, 0.5f)
            3 -> GL11.glColor3f(0.5647f, 0.36f, 0.36f)
            4 -> GL11.glColor3f(0.5f, 0.5f, 1.0f)
            5 -> GL11.glColor3f(0.78125f, 0.46666f, 1.0f)
            6 -> GL11.glColor3f(0.5f, 1.0f, 1.0f)
            7 -> GL11.glColor3f(0.75f, 0.75f, 0.75f)
            8 -> GL11.glColor3f(0.5f, 0.5f, 0.5f)
            9 -> GL11.glColor3f(1.0f, 0.5f, 0.65882f)
            10 -> GL11.glColor3f(0.75f, 1.0f, 0.5f)
            11 -> GL11.glColor3f(1.0f, 1.0f, 0.5f)
            12 -> GL11.glColor3f(0.5f, 0.75f, 1.0f)
            13 -> GL11.glColor3f(1.0f, 0.5f, 1.0f)
            14 -> GL11.glColor3f(1.0f, 0.80f, 0.5f)
            else -> GL11.glColor3f(1.0f, 1.0f, 1.0f)
        }
    }

    // Into utilsClient To
    @JvmStatic
    fun getWeatherNoLoad(dim: Int): Double {
        if (!getWorldExist(dim)) return 0.0
        val world = getWorld(dim)
        if (world.isThundering) return 1.0
        return if (world.isRaining) 0.5 else 0.0
    }

    @JvmStatic
    fun getWorld(dim: Int): Level {
        return DimensionIds.serverLevel(dim) ?: throw IllegalStateException("no world for dimension $dim")
    }

    @JvmStatic
    fun getWorldExist(dim: Int): Boolean {
        return DimensionIds.serverLevel(dim) != null
    }

    @JvmStatic
    fun getWind(worldId: Int, y: Int): Double {
        return if (!getWorldExist(worldId)) {
            0.0.coerceAtLeast(Eln.wind.getWind(y))
        } else {
            val world = getWorld(worldId)
            val factor = 1f + world.getRainLevel(0f) * 0.2f + world.getThunderLevel(0f) * 0.2f
            0.0.coerceAtLeast(
                Eln.wind.getWind(y) * factor + world.getRainLevel(0f) * 1f + world.getThunderLevel(0f) * 2f
            )
        }
    }

    @JvmStatic
    fun dropItem(itemStack: ItemStack?, x: Int, y: Int, z: Int, world: Level) {
        if (itemStack.isNothing()) return
        if (world.gameRules.getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            val var6 = 0.7f
            val var7 = (world.rand.nextFloat() * var6).toDouble() + (1.0f - var6).toDouble() * 0.5
            val var9 = (world.rand.nextFloat() * var6).toDouble() + (1.0f - var6).toDouble() * 0.5
            val var11 = (world.rand.nextFloat() * var6).toDouble() + (1.0f - var6).toDouble() * 0.5
            val var13 = ItemEntity(world, x.toDouble() + var7, y.toDouble() + var9, z.toDouble() + var11, itemStack)
            var13.setPickUpDelay(10)
            world.addFreshEntity(var13)
        }
    }

    @JvmStatic
    fun dropItem(itemStack: ItemStack?, coordinate: Coordinate) {
        dropItem(itemStack, coordinate.x, coordinate.y, coordinate.z, coordinate.world())
    }

    @JvmStatic
    fun tryPutStackInInventory(stack: ItemStack, inventory: Container?): Boolean {
        if (inventory == null) return false
        val limit = inventory.maxStackSize
        var changed = false

        // First, make a list of possible target slots.
        val slots = ArrayList<Int>(4)
        var need = stack.count
        run {
            var i = 0
            while (i < inventory.containerSize && need > 0) {
                val slot = inventory.getItem(i)
                if (slot != null && slot.count < limit && slot.isItemEqual(stack)) {
                    slots.add(i)
                    need -= limit - slot.count
                }
                i++
            }
        }
        var i = 0
        while (i < inventory.containerSize && need > 0) {
            if (inventory.getItem(i).isNothing()) {
                slots.add(i)
                need -= limit
            }
            i++
        }

        // Is there space enough?
        if (need > 0) {
            return false
        }

        // Yes. Proceed.
        var toPut = stack.count
        for (slot in slots) {
            val target = inventory.getItem(slot)
            if (target == null) {
                val amount = toPut.coerceAtMost(limit)
                inventory.setItem(slot, stack.copyWithCount(amount))
                toPut -= amount
                changed = true
            } else {
                val space = limit - target.count
                val amount = toPut.coerceAtMost(space)
                target.count += amount
                toPut -= amount
                if (amount > 0) changed = true
            }
            if (toPut <= 0) break
        }
        if (changed) {
            inventory.setChanged()
        }
        return true
    }

    // Can attest, this seems pretty broken.
    @JvmStatic
    fun canPutStackInInventory(stackList: Array<ItemStack>, inventory: Container, slotsIdList: IntArray): Boolean {
        val limit = inventory.maxStackSize
        val outputStack = arrayOfNulls<ItemStack>(slotsIdList.size)
        val inputStack = arrayOfNulls<ItemStack>(stackList.size)
        for (idx in outputStack.indices) {
            if (!inventory.getItem(slotsIdList[idx]).isNothing()) outputStack[idx] = inventory.getItem(slotsIdList[idx]).copy()
        }
        for (idx in stackList.indices) {
            inputStack[idx] = stackList[idx].copy()
        }
        var oneStackDone: Boolean
        for (stack in inputStack) {
            // if(stack.isNothing()) continue;
            oneStackDone = false
            for (idx in slotsIdList.indices) {
                val targetStack = outputStack[idx]
                if (targetStack.isNothing()) {
                    outputStack[idx] = stack
                    oneStackDone = true
                    break
                } else if (targetStack.isItemEqual(stack)) {
                    // inventory.removeItem(idx, -stack.count);
                    val transferMax = limit - targetStack.count
                    if (transferMax > 0) {
                        var transfer = stack!!.count
                        if (transfer > transferMax) transfer = transferMax
                        outputStack[idx]!!.count += transfer
                        stack.count -= transfer
                    }
                    if (stack!!.count == 0) {
                        oneStackDone = true
                        break
                    }
                }
            }
            if (!oneStackDone) return false
        }
        return true
    }

    @JvmStatic
    fun tryPutStackInInventory(stackList: Array<ItemStack>, inventory: Container, slotsIdList: IntArray): Boolean {
        val limit = inventory.maxStackSize
        var changed = false
        for (stack in stackList) {
            for (idx in slotsIdList.indices) {
                val targetStack = inventory.getItem(slotsIdList[idx])
                if (targetStack.isNothing()) {
                    inventory.setItem(slotsIdList[idx], stack.copy())
                    stack.count = 0
                    changed = true
                    break
                } else if (targetStack.isItemEqual(stack)) {
                    // inventory.removeItem(idx, -stack.count);
                    val transferMax = limit - targetStack.count
                    if (transferMax > 0) {
                        var transfer = stack.count
                        if (transfer > transferMax) transfer = transferMax
                        inventory.removeItem(slotsIdList[idx], -transfer)
                        stack.count -= transfer
                        if (transfer > 0) changed = true
                    }
                    if (stack.count == 0) {
                        break
                    }
                }
            }
        }
        if (changed) {
            inventory.setChanged()
        }
        return true
    }

    fun voltageMargeFactorSub(value: Double): Double {
        if (value > 1 + voltageMageFactor) {
            return value - voltageMageFactor
        } else if (value > 1) {
            return 1.0
        }
        return value
    }

    @JvmStatic
    @Throws(IOException::class)
    fun serialiseItemStack(stream: DataOutputStream, stack: ItemStack?) {
        if (stack.isNothing()) {
            stream.writeShort(-1)
            stream.writeShort(-1)
        } else {
            // 1.21: registry ids can exceed a short in large packs, and there is no damage sub-id.
            stream.writeShort(itemId(stack.item))
            stream.writeShort(0)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun unserialiseItemStack(stream: DataInputStream): ItemStack? {
        val id: Short = stream.readShort()
        val damage: Short = stream.readShort()
        return if (id.toInt() == -1) null else newItemStack(id.toInt(), 1, damage.toInt())
    }

    @JvmStatic
    @Throws(IOException::class)
    fun unserializeItemStackToEntityItem(stream: DataInputStream, old: ItemEntity?, tileEntity: BlockEntity): ItemEntity? {
        var itemId: Short
        val ItemDamage: Short
        return if (stream.readShort().also { itemId = it }.toInt() == -1) {
            stream.readShort()
            null
        } else {
            ItemDamage = stream.readShort()
            if (old == null || itemId(old.item.item) != itemId.toInt()) ItemEntity(tileEntity.level!!, tileEntity.xCoord + 0.5, tileEntity.yCoord + 0.5, tileEntity.zCoord + 1.2, newItemStack(itemId.toInt(), 1, ItemDamage.toInt())) else old
        }
    }

    @JvmStatic
    val isGameInPause: Boolean
        get() = Minecraft.getInstance().isPaused

    @JvmStatic
    fun getLight(w: Level, e: LightLayer?, x: Int, y: Int, z: Int): Int {
        return w.getBrightness(e ?: LightLayer.BLOCK, BlockPos(x, y, z))
    }

    @JvmStatic
    fun notifyNeighbor(t: BlockEntity) {
        val x = t.xCoord
        val y = t.yCoord
        val z = t.zCoord
        val w = t.level ?: return
        var o: BlockEntity? = w.getBlockEntity(x + 1, y, z)
        if (o != null && o is ITileEntitySpawnClient) (o as ITileEntitySpawnClient).tileEntityNeighborSpawn()
        o = w.getBlockEntity(x - 1, y, z)
        if (o != null && o is ITileEntitySpawnClient) (o as ITileEntitySpawnClient).tileEntityNeighborSpawn()
        o = w.getBlockEntity(x, y + 1, z)
        if (o != null && o is ITileEntitySpawnClient) (o as ITileEntitySpawnClient).tileEntityNeighborSpawn()
        o = w.getBlockEntity(x, y - 1, z)
        if (o != null && o is ITileEntitySpawnClient) (o as ITileEntitySpawnClient).tileEntityNeighborSpawn()
        o = w.getBlockEntity(x, y, z + 1)
        if (o != null && o is ITileEntitySpawnClient) (o as ITileEntitySpawnClient).tileEntityNeighborSpawn()
        o = w.getBlockEntity(x, y, z - 1)
        if (o != null && o is ITileEntitySpawnClient) (o as ITileEntitySpawnClient).tileEntityNeighborSpawn()
    }

    @JvmStatic
    fun playerHasMeter(entityPlayer: Player): Boolean {
        val cur = entityPlayer.mainHandItem
        return (Eln.multiMeterElement.checkSameItemStack(cur)
            || Eln.thermometerElement.checkSameItemStack(cur)
            || Eln.allMeterElement.checkSameItemStack(cur)
            || Eln.configCopyToolElement.checkSameItemStack(cur))
    }

    @JvmStatic
    fun getRedstoneLevelAround(coord: Coordinate, side: Direction): Int {
        var side = side
        // 1.21: getDirectSignal takes the side it is asked from; the strongest of the six is the old "level here".
        var level = net.minecraft.core.Direction.values().maxOf { coord.world().getDirectSignal(BlockPos(coord.x, coord.y, coord.z), it) }
        if (level >= 15) return 15
        side = side.inverse
        when (side) {
            Direction.YN, Direction.YP -> {
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x + 1, coord.y, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x - 1, coord.y, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y, coord.z + 1, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y, coord.z - 1, side.toSideValue()))
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y + 1, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y - 1, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y, coord.z + 1, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y, coord.z - 1, side.toSideValue()))
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x + 1, coord.y, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x - 1, coord.y, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y + 1, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y - 1, coord.z, side.toSideValue()))
            }
            Direction.XN, Direction.XP -> {
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y + 1, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y - 1, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y, coord.z + 1, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y, coord.z - 1, side.toSideValue()))
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x + 1, coord.y, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x - 1, coord.y, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y + 1, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y - 1, coord.z, side.toSideValue()))
            }
            Direction.ZN, Direction.ZP -> {
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x + 1, coord.y, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x - 1, coord.y, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y + 1, coord.z, side.toSideValue()))
                if (level >= 15) return 15
                level = level.coerceAtLeast(coord.world().getIndirectPowerLevelTo(coord.x, coord.y - 1, coord.z, side.toSideValue()))
            }
        }
        return level
    }

    @JvmStatic
    fun isPlayerAround(world: Level, axisAlignedBB: AABB?): Boolean {
        return world.getEntitiesOfClass(Player::class.java, axisAlignedBB).isNotEmpty()
    }

    @JvmStatic
    fun getItemObject(stack: ItemStack?): Any? {
        if (stack.isNothing()) return null
        val i = stack.item
        // The Flattening: the family is reachable from the per-descriptor item.
        val family = (i as? mods.eln.generic.IDescriptorItem)?.descriptorFamily()
        if (family is GenericItemUsingDamage<*>) {
            return family.getDescriptor(stack)
        }
        return if (family is GenericItemBlockUsingDamage<*>) {
            family.getDescriptor(stack)
        } else i
    }

    /*
	 * public static void drawIcon(Icon icon) { Utils.bindTextureByName(icon.getIconName()); Utils.disableCulling(); GL11.glBegin(GL11.GL_QUADS); GL11.glTexCoord2f(0f, 0f); GL11.glVertex3f(0.5f,-0.5f,0f); GL11.glTexCoord2f(0f, 0f);GL11.glVertex3f(-0.5f,-0.5f,0f); GL11.glTexCoord2f(0f, 1f);GL11.glVertex3f(-0.5f,0.5f,0f); GL11.glTexCoord2f(1f, 1f);GL11.glVertex3f(0.5f,0.5f,0f); GL11.glEnd(); Utils.enableCulling(); }
	 *
	 * public static void drawEnergyBare(float e) { float x = 14f/16f,y = 15f/16f-e*14f/16f; GL11.glColor3f(e, e, 0f); GL11.glDisable(GL11.GL_TEXTURE_2D); GL11.glBegin(GL11.GL_QUADS); GL11.glVertex3f(x+1f/16f,y,0.01f); GL11.glVertex3f(x,y,0f); GL11.glVertex3f(x,15f/16f,0f); GL11.glVertex3f(x+1f/16f,15f/16f,0.01f); GL11.glEnd(); GL11.glEnable(GL11.GL_TEXTURE_2D); GL11.glColor3f(1f, 1f, 1f); }
	 */
    @JvmStatic
    fun getItemStack(name: String, list: MutableList<ItemStack>) {
        val s = name.lowercase()
        for (item in BuiltInRegistries.ITEM) {
            val itemstack = ItemStack(item)
            if (itemstack.hoverName.string.lowercase().contains(s)) {
                list.add(itemstack)
            }
        }
    }

    /** 1.7.10's "effective side": which logical side the current thread belongs to. */
    val side: Dist
        get() = if (Thread.currentThread().name.startsWith("Render") || (FMLEnvironment.dist.isClient && ServerLifecycleHooks.getCurrentServer() == null)) Dist.CLIENT else Dist.DEDICATED_SERVER
    val isServer: Boolean
        get() = side == Dist.DEDICATED_SERVER

    fun printSide(string: String?) {
        println(string)
    }

    @JvmStatic
    fun modbusToShort(outputNormalized: Double, i: Int): Short {
        val bit = java.lang.Float.floatToRawIntBits(outputNormalized.toFloat())
        return if (i == 1) bit.toShort() else (bit ushr 16).toShort()
    }

    @JvmStatic
    fun modbusToFloat(first: Short, second: Short): Float {
        val bit = (first.toInt() and 0xFFFF shl 16) + (second.toInt() and 0xFFFF)
        return java.lang.Float.intBitsToFloat(bit)
    }

    @JvmStatic
    fun areSame(stack: ItemStack, output: ItemStack): Boolean {
        try {
            if (stack.item === output.item) return true
            // Ore dictionary -> item tags: two stacks are "the same" when they share a tag.
            val stackTags = stack.tags.toList()
            for (tag in output.tags) {
                if (stackTags.contains(tag)) return true
            }
        } catch (_: Exception) {
        }
        return false
    }

    @JvmStatic
    fun getVec05(c: Coordinate): Vec3 {
        return Vec3(c.x + (if (c.x < 0) -1 else 1) * 0.5, c.y + (if (c.y < 0) -1 else 1) * 0.5, c.z + (if (c.z < 0) -1 else 1) * 0.5)
    }

    fun getHeadPosY(e: Entity): Double {
        return if (e is RemotePlayer) e.y + e.getEyeHeight() else e.y
    }

    /*
	 * public static boolean isPlayerInteractRiseWith(ServerPlayer entity, ItemStack stack) {
	 *
	 * return entity.inventory.getCurrentItem() == stack && Eln.playerManager.get(entity).getInteractRise(); }
	 */
    @JvmStatic
    fun isCreative(entityPlayer: ServerPlayer): Boolean {
        return entityPlayer.gameMode.isCreative
        /*
		 * Minecraft m = Minecraft.getInstance(); return m.getSingleplayerServer().getGameType().isCreative();
		 */
    }

    @JvmStatic
    fun mustDropItem(entityPlayer: ServerPlayer?): Boolean {
        return if (entityPlayer == null) true else !isCreative(entityPlayer)
    }

    @JvmStatic
    fun serverTeleport(e: Entity, x: Double, y: Double, z: Double) {
        if (e is ServerPlayer) e.teleportTo(x, y, z) else e.setPos(x, y, z)
    }

    /** 1.12.2: returns block states, since opacity (what every caller asks) is a state property now. */
    @JvmStatic
    fun traceRay(world: Level, x: Double, y: Double,
                 z: Double, tx: Double, ty: Double, tz: Double): ArrayList<BlockState> {
        var x = x
        var y = y
        var z = z
        val blockList = ArrayList<BlockState>()
        var dx: Double = tx - x
        var dy: Double = ty - y
        var dz: Double = tz - z
        val norm = sqrt(dx * dx + dy * dy + dz * dz)
        val normInv = 1 / (norm + 0.000000001)
        dx *= normInv
        dy *= normInv
        dz *= normInv
        var d = 0.0
        while (d < norm) {
            if (isBlockLoaded(world, x, y, z)) {
                blockList.add(getBlockState(world, x, y, z))
            }
            x += dx
            y += dy
            z += dz
            d += 1.0
        }
        return blockList
    }

    @JvmStatic
    fun traceRay(w: Level, posX: Double, posY: Double, posZ: Double, targetX: Double, targetY: Double, targetZ: Double, weight: TraceRayWeight): Float {
        val posXint = posX.roundToInt()
        val posYint = posY.roundToInt()
        val posZint = posZ.roundToInt()
        var x = (posX - posXint).toFloat()
        var y = (posY - posYint).toFloat()
        var z = (posZ - posZint).toFloat()
        var vx = (targetX - posX).toFloat()
        var vy = (targetY - posY).toFloat()
        var vz = (targetZ - posZ).toFloat()
        val rangeMax = sqrt((vx * vx + vy * vy + vz * vz).toDouble()).toFloat()
        val normInv = 1f / rangeMax
        vx *= normInv
        vy *= normInv
        vz *= normInv
        if (vx == 0f) vx += 0.0001f
        if (vy == 0f) vy += 0.0001f
        if (vz == 0f) vz += 0.0001f
        val vxInv = 1f / vx
        val vyInv = 1f / vy
        val vzInv = 1f / vz
        var stackRed = 0f
        var d = 0f
        while (d < rangeMax) {
            val xFloor = Mth.floor(x).toFloat()
            val yFloor = Mth.floor(y).toFloat()
            val zFloor = Mth.floor(z).toFloat()
            var dx = x - xFloor
            var dy = y - yFloor
            var dz = z - zFloor
            dx = if (vx > 0) (1 - dx) * vxInv else -dx * vxInv
            dy = if (vy > 0) (1 - dy) * vyInv else -dy * vyInv
            dz = if (vz > 0) (1 - dz) * vzInv else -dz * vzInv
            val dBest = dx.coerceAtMost(dy).coerceAtMost(dz) + 0.01f
            val xInt = xFloor.toInt()
            val yInt = yFloor.toInt()
            val zInt = zFloor.toInt()
            var block = Blocks.AIR
            if (w.isBlockLoaded(xInt + posXint, yInt + posYint, zInt + posZint)) block = w.getBlock(xInt + posXint, yInt + posYint, zInt + posZint)
            var dToStack: Float = if (d + dBest < rangeMax) dBest else {
                rangeMax - d
            }
            stackRed += weight.getWeight(block) * dToStack
            x += vx * dBest
            y += vy * dBest
            z += vz * dBest
            d += dBest
        }
        return stackRed
    }

    fun isBlockLoaded(world: Level, x: Double, y: Double, z: Double): Boolean {
        return world.isBlockLoaded(Mth.floor(x), Mth.floor(y), Mth.floor(z))
    }

    fun getBlock(world: Level, x: Double, y: Double, z: Double): Block {
        return world.getBlock(Mth.floor(x), Mth.floor(y), Mth.floor(z))
    }

    fun getBlockState(world: Level, x: Double, y: Double, z: Double): BlockState {
        return world.getBlockState(Mth.floor(x), Mth.floor(y), Mth.floor(z))
    }

    @JvmStatic
    fun getLength(x: Double, y: Double, z: Double, tx: Double, ty: Double, tz: Double): Double {
        val dx: Double = tx - x
        val dy: Double = ty - y
        val dz: Double = tz - z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun <T> readPrivateInt(o: Any, fieldName: String?): Int {
        try {
            val f = fieldName?.let { o.javaClass.getDeclaredField(it) }
            if (f != null) {
                f.isAccessible = true
                return f.getInt(o)
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
        }
        return 0
    }

    fun <T> readPrivateDouble(o: Any, fieldName: String?): Double {
        try {
            val f = fieldName?.let { o.javaClass.getDeclaredField(it) }
            if (f != null) {
                f.isAccessible = true
                return f.getDouble(o)
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: NoSuchFieldException) {
            e.printStackTrace()
        }
        return 0.0
    }

    /**
     * The 3x3 grid of a crafting recipe, for the in-game wiki.
     *
     * 1.12 replaced the per-slot ItemStack/oredict-list soup with [Ingredient]: every recipe
     * type now answers [Recipe.getIngredients] uniformly, and an ingredient reports the stacks
     * it accepts. Only the shaped types carry a width, so the shapeless ones still fill the grid
     * in reading order, which is what the wiki drew before.
     */
    @JvmStatic
    fun getItemStackGrid(r: Recipe<*>?): Array<Array<ItemStack?>>? {
        if (r == null) return null
        val stacks = Array(3) { arrayOfNulls<ItemStack>(3) }
        return try {
            val ingredients = r.ingredients
            val width = when (r) {
                is ShapedRecipe -> r.width
                else -> 0
            }
            if (width in 1..3) {
                val height = (ingredients.size + width - 1) / width
                for (row in 0 until minOf(height, 3)) {
                    for (col in 0 until width) {
                        val idx = col + row * width
                        if (idx < ingredients.size) stacks[row][col] = firstStackOf(ingredients[idx])
                    }
                }
            } else {
                for ((idx, ingredient) in ingredients.withIndex()) {
                    if (idx >= 9) break
                    stacks[idx / 3][idx % 3] = firstStackOf(ingredient)
                }
            }
            stacks
        } catch (e: Exception) {
            null
        }
    }

    /** The stack the wiki shows for an ingredient: the first item it accepts, if any. */
    private fun firstStackOf(ingredient: Ingredient?): ItemStack? =
        ingredient?.items?.firstOrNull { !it.isEmpty }

    @JvmStatic
    fun getRecipeInputs(r: Recipe<*>?): ArrayList<ItemStack?> {
        return try {
            val stacks = ArrayList<ItemStack?>()
            r?.ingredients?.forEach { ingredient ->
                ingredient.items.filterTo(stacks) { !it.isEmpty }
            }
            stacks
        } catch (e: Exception) {
            ArrayList()
        }
    }

    @JvmStatic
    fun getWorldTime(world: Level): Double {
        return world.dayTime / 23999.0
    }

    @JvmStatic
    fun isWater(waterCoord: Coordinate): Boolean {
        val block = waterCoord.block
        return block === Blocks.WATER
    }

    @JvmStatic
    fun sendMessage(entityPlayer: Player, string: String?) {
        entityPlayer.sendSystemMessage(Component.literal(string ?: ""))
    }

    @JvmStatic
    fun newItemStack(i: Int, size: Int, @Suppress("UNUSED_PARAMETER") damage: Int): ItemStack {
        return ItemStack(itemById(i), size)
    }

    @JvmStatic
    fun newItemStack(i: Item?, size: Int, @Suppress("UNUSED_PARAMETER") damage: Int): ItemStack {
        return if (i == null) ItemStack.EMPTY else ItemStack(i, size)
    }

    @JvmStatic
    fun getTags(nbt: CompoundTag): List<CompoundTag> {
        val set: Array<Any> = nbt.allKeys.filterNotNull().toTypedArray()
        val tags = ArrayList<CompoundTag>()
        for (idx in set.indices) {
            tags.add(nbt.getCompound(set[idx] as String))
        }
        return tags
    }

    @JvmStatic
    fun isRemote(world: BlockGetter): Boolean {
        if (world !is Level) {
            fatal()
        }
        return (world as Level).isClientSide
    }

    @JvmStatic
    fun nullCheck(o: Any?): Boolean {
        return o == null
    }

    fun nullFatal(o: Any?) {
        if (o == null) fatal()
    }

    @JvmStatic
    fun fatal() {
        try {
            throw Exception()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getBlock(blockId: Int): Block {
        return blockById(blockId)
    }

    @JvmStatic
    fun updateSkylight(@Suppress("UNUSED_PARAMETER") chunk: LevelChunk) {
        // 1.14+: the light engine keeps itself current; nothing to poke.
    }

    @JvmStatic
    fun updateAllLightTypes(world: Level, xCoord: Int, yCoord: Int, zCoord: Int) {
        world.lightEngine.checkBlock(BlockPos(xCoord, yCoord, zCoord))
    }

    @JvmStatic
    fun getItemId(stack: ItemStack): Int {
        return itemId(stack.item)
    }

    @JvmStatic
    fun getItemId(block: Block?): Int {
        return if (block == null) 0 else itemId(block.asItem())
    }

    /**
     * Smelting recipes are data since 1.13 (data/eln/recipe/<name>.json, generated from these calls by
     * the recipe data generator); at run time this only records the pair.
     */
    @JvmStatic
    @JvmOverloads
    fun addSmelting(parentItem: Item?, @Suppress("UNUSED_PARAMETER") parentItemDamage: Int, findItemStack: ItemStack?, f: Float = 0.3f) {
        if (parentItem == null || findItemStack == null) return
        smeltingRecipes.add(Triple(ItemStack(parentItem), findItemStack, f))
    }

    @JvmStatic
    @JvmOverloads
    fun addSmelting(parentBlock: Block?, parentItemDamage: Int, findItemStack: ItemStack?, f: Float = 0.3f) {
        addSmelting(parentBlock?.asItem(), parentItemDamage, findItemStack, f)
    }

    /** (input, output, experience) of every smelting recipe the mod declared. */
    @JvmStatic
    val smeltingRecipes = ArrayList<Triple<ItemStack, ItemStack, Float>>()

    @JvmStatic
    fun newNbtTagCompund(nbt: CompoundTag?, string: String): CompoundTag {
        val cmp = CompoundTag()
        nbt?.put(string, cmp)
        return cmp
    }

    /** 1.7.10's `FurnaceRecipes.smelting().getSmeltingResult(stack)`: matched by ingredient in the recipe manager (either side). */
    @JvmStatic
    fun getSmeltingResult(input: ItemStack?): ItemStack {
        if (input == null || input.isEmpty) return ItemStack.EMPTY
        val manager = McRecipes.manager() ?: return ItemStack.EMPTY
        return manager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.SMELTING)
            .firstOrNull { it.value().ingredients.firstOrNull()?.test(input) == true }
            ?.value()?.getResultItem(McRegistries.access())?.copy() ?: ItemStack.EMPTY
    }

    fun getMapFile(name: String): File {
        val server = ServerLifecycleHooks.getCurrentServer() ?: throw IllegalStateException("no server")
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource(name)).toFile()
    }

    @JvmStatic
    fun mapFileExists(name: String): Boolean {
        return getMapFile(name).exists()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readMapFile(name: String): String {
        val file = getMapFile(name)
        val fis = FileInputStream(file)
        val data = ByteArray(file.length().toInt())
        fis.read(data)
        fis.close()
        return String(data, Charset.defaultCharset())
    }

    @JvmStatic
    fun generateHeightMap(@Suppress("UNUSED_PARAMETER") chunk: LevelChunk?) {}

    @JvmStatic
    fun getSixNodePinDistance(obj: Obj3DPart): FloatArray {
        return floatArrayOf(abs(obj.zMin * 16), abs(obj.zMax * 16), abs(obj.yMin * 16), abs(obj.yMax * 16))
    }

    fun isWrench(stack: ItemStack): Boolean {
        return areSame(stack, Eln.wrenchItemStack) || stack.hoverName.string.lowercase().contains("wrench")
    }

    @JvmStatic
    fun isPlayerUsingWrench(player: Player?): Boolean {
        if (player == null) return false
        if (ServerKeyHandler.get(ServerKeyHandler.WRENCH)) return true
        val stack = player.inventory.getSelected() ?: return false
        return isWrench(stack)
    }

    fun isClassLoaded(@Suppress("UNUSED_PARAMETER") name: String?): Boolean {
        try {
            // val cc = Class.forName(name)
            return true
        } catch (_: ClassNotFoundException) {
        }
        return false
    }

    @JvmStatic
    fun plotSignal(u: Double): String {
        return plotVolt("U", u) + plotPercent("Value", u / Eln.SVU)
    }

    @JvmStatic
    fun limit(value: Float, min: Float, max: Float): Float {
        return value.coerceAtMost(max).coerceAtLeast(min)
    }

    @JvmStatic
    fun limit(value: Double, min: Double, max: Double): Double {
        return value.coerceAtMost(max).coerceAtLeast(min)
    }

    @JvmStatic
    fun printFunction(func: FunctionTable, start: Double, end: Double, step: Double) {
        println("********")
        var x: Double
        var idx = 0
        while (start + step * idx.also { x = it.toDouble() } < end + 0.00001) {
            println(func.getValue(x))
            idx++
        }
        println("********")
    }

    interface TraceRayWeight {
        fun getWeight(block: Block?): Float
    }

    class TraceRayWeightOpaque : TraceRayWeight {
        override fun getWeight(block: Block?): Float {
            if (block == null) return 0f
            return if (block.defaultBlockState().canOcclude()) 1f else 0f
        }
    }

    @JvmStatic
    fun renderSubSystemWaila(subSystem: SubSystem?): String {
        return if (subSystem != null) {
            val subSystemSize = subSystem.component.size
            val textColor: String = if (subSystemSize <= 8) {
                "§a"
            } else if (subSystemSize <= 15) {
                "§6"
            } else {
                "§c"
            }
            textColor + subSystemSize
        } else {
            "§cnull SubSystem"
        }
    }

    @JvmStatic
    fun renderDoubleSubsystemWaila(subSystemA: SubSystem?, subSystemB: SubSystem?): String {
        val leftSubSystemSize = subSystemA?.component?.size?: -1
        val rightSubSystemSize = subSystemB?.component?.size?: -1
        val textColorLeft = when {
            leftSubSystemSize <= 0 -> "null"
            leftSubSystemSize <= 8 -> "§a"
            leftSubSystemSize <= 15 -> "§6"
            else -> "§c"
        }
        val textColorRight = when {
            rightSubSystemSize <= 0 -> "null"
            rightSubSystemSize <= 8 -> "§a"
            rightSubSystemSize <= 15 -> "§6"
            else -> "§c"
        }
        return "$textColorLeft$leftSubSystemSize §r| $textColorRight$rightSubSystemSize"
    }
}

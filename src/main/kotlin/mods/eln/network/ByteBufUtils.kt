package mods.eln.network

import io.netty.buffer.ByteBuf
import mods.eln.misc.McRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.item.ItemStack

/** The Forge `ByteBufUtils` helpers the typed packets use, on the 1.21 buffer types. */
object ByteBufUtils {
    private fun friendly(buf: ByteBuf?): FriendlyByteBuf = buf as? FriendlyByteBuf ?: FriendlyByteBuf(buf)
    private fun registry(buf: ByteBuf?): RegistryFriendlyByteBuf =
        buf as? RegistryFriendlyByteBuf ?: RegistryFriendlyByteBuf(buf, McRegistries.access())

    @JvmStatic
    fun writeUTF8String(buf: ByteBuf?, s: String?) {
        friendly(buf).writeUtf(s ?: "")
    }

    @JvmStatic
    fun readUTF8String(buf: ByteBuf?): String = friendly(buf).readUtf()

    @JvmStatic
    fun writeItemStack(buf: ByteBuf?, stack: ItemStack?) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(registry(buf), stack ?: ItemStack.EMPTY)
    }

    @JvmStatic
    fun readItemStack(buf: ByteBuf?): ItemStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(registry(buf))

    @JvmStatic
    fun writeTag(buf: ByteBuf?, tag: CompoundTag?) {
        friendly(buf).writeNbt(tag)
    }

    @JvmStatic
    fun readTag(buf: ByteBuf?): CompoundTag? = friendly(buf).readNbt()
}

package mods.eln.network

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import mods.eln.Eln
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.io.ByteArrayOutputStream
import java.util.function.Supplier

/**
 * Networking. Electrical Age has two protocols and both survive unchanged:
 *
 * - the byte protocol (`DataOutputStream` payloads dispatched on a leading packet id by
 *   [mods.eln.PacketHandler]) travels in one [RawPayload]; the dispatch code never sees the
 *   transport;
 * - the six SimpleImpl-style typed packets keep their [IMessage]/[IMessageHandler] shape; each
 *   message class becomes its own [CustomPacketPayload] type through [register], serialised with
 *   the same `toBytes`/`fromBytes` as before.
 *
 * NeoForge runs payload handlers on the main thread, so no hop is needed.
 */
@EventBusSubscriber(modid = Eln.MODID, bus = EventBusSubscriber.Bus.MOD)
object ElnNetwork {

    /** The byte protocol. */
    class RawPayload(val data: ByteArray) : CustomPacketPayload {
        override fun type() = TYPE

        companion object {
            val TYPE = CustomPacketPayload.Type<RawPayload>(ResourceLocation.fromNamespaceAndPath(Eln.MODID, "raw"))
            val CODEC: StreamCodec<FriendlyByteBuf, RawPayload> = StreamCodec.of(
                { buf, payload -> buf.writeByteArray(payload.data) },
                { buf -> RawPayload(buf.readByteArray()) })
        }
    }

    /** One typed message wrapped as a payload. */
    class MessagePayload<T : IMessage>(val registration: Registration<T>, val message: T) : CustomPacketPayload {
        override fun type() = registration.type
    }

    class Registration<T : IMessage>(
        val type: CustomPacketPayload.Type<MessagePayload<T>>,
        val factory: Supplier<T>,
        val handler: IMessageHandler<T, *>,
        val toServer: Boolean,
    ) {
        val codec: StreamCodec<RegistryFriendlyByteBuf, MessagePayload<T>> = StreamCodec.of(
            { buf, payload -> payload.message.toBytes(buf) },
            { buf -> MessagePayload(this, factory.get().also { it.fromBytes(buf) }) })
    }

    private val registrations = LinkedHashMap<Class<out IMessage>, Registration<*>>()
    private var rawHandler: ((ByteArray, IPayloadContext) -> Unit)? = null

    /** Replaces SimpleNetworkWrapper.registerMessage; call from the mod constructor. */
    @JvmStatic
    fun <T : IMessage> register(id: String, messageClass: Class<T>, factory: Supplier<T>, handler: IMessageHandler<T, *>, toServer: Boolean) {
        val type = CustomPacketPayload.Type<MessagePayload<T>>(ResourceLocation.fromNamespaceAndPath(Eln.MODID, id))
        registrations[messageClass] = Registration(type, factory, handler, toServer)
    }

    @JvmStatic
    fun setRawHandler(handler: (ByteArray, IPayloadContext) -> Unit) {
        rawHandler = handler
    }

    @SubscribeEvent
    fun onRegisterPayloads(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(Eln.MODID)
        registrar.playBidirectional(RawPayload.TYPE, RawPayload.CODEC) { payload, context ->
            rawHandler?.invoke(payload.data, context)
        }
        registrations.values.forEach { registerTyped(registrar, it) }
    }

    private fun <T : IMessage> registerTyped(registrar: net.neoforged.neoforge.network.registration.PayloadRegistrar, reg: Registration<T>) {
        val handle = { payload: MessagePayload<T>, context: IPayloadContext ->
            val reply = reg.handler.onMessage(payload.message, MessageContext(context))
            if (reply != null) {
                val player = context.player()
                if (player is ServerPlayer) sendTo(reply, player) else sendToServer(reply)
            }
        }
        if (reg.toServer) registrar.playToServer(reg.type, reg.codec, handle) else registrar.playToClient(reg.type, reg.codec, handle)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : IMessage> wrap(message: T): MessagePayload<T> {
        val reg = registrations[message.javaClass] as Registration<T>? ?: throw IllegalStateException("unregistered message ${message.javaClass}")
        return MessagePayload(reg, message)
    }

    // --- typed messages
    @JvmStatic
    fun sendToServer(message: IMessage) = PacketDistributor.sendToServer(wrap(message))

    @JvmStatic
    fun sendTo(message: IMessage, player: ServerPlayer) = PacketDistributor.sendToPlayer(player, wrap(message))

    // --- byte protocol
    @JvmStatic
    fun sendToServer(bos: ByteArrayOutputStream) = PacketDistributor.sendToServer(RawPayload(bos.toByteArray()))

    @JvmStatic
    fun sendTo(bos: ByteArrayOutputStream, player: ServerPlayer) = PacketDistributor.sendToPlayer(player, RawPayload(bos.toByteArray()))

    @JvmStatic
    fun sendToAll(bos: ByteArrayOutputStream) = PacketDistributor.sendToAllPlayers(RawPayload(bos.toByteArray()))

    /** 1.7.10 semantics: every player watching the chunk, i.e. render distance decides. */
    @JvmStatic
    fun sendToTracking(bos: ByteArrayOutputStream, level: Level, chunk: ChunkPos) {
        val server = level as? net.minecraft.server.level.ServerLevel ?: return
        PacketDistributor.sendToPlayersTrackingChunk(server, chunk, RawPayload(bos.toByteArray()))
    }

    @JvmStatic
    fun sendToTracking(bos: ByteArrayOutputStream, level: Level, x: Int, z: Int) = sendToTracking(bos, level, ChunkPos(x shr 4, z shr 4))
}

/** The SimpleImpl message shape, kept so the six typed packets only change an import. */
interface IMessage {
    fun fromBytes(buf: ByteBuf)
    fun toBytes(buf: ByteBuf)
}

interface IMessageHandler<REQ : IMessage?, REPLY : IMessage?> {
    fun onMessage(message: REQ, ctx: MessageContext?): REPLY?
}

/** What the handlers read from Forge's MessageContext: the player, and which side runs the handler. */
class MessageContext(val context: IPayloadContext) {
    val player: Player get() = context.player()
    val isServer: Boolean get() = player is ServerPlayer
    val serverHandler: ServerHandler get() = ServerHandler(player as ServerPlayer)

    class ServerHandler(@JvmField val player: ServerPlayer)
}

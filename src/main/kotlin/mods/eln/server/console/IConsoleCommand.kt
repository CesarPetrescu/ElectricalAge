package mods.eln.server.console

import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/**
 * Who ran `/eln`: 1.7.10's `ICommandSender`, over the Brigadier source. [player] is null for the
 * console and command blocks; the position and level are the source's (a player's own).
 */
class ICommandSender(val source: CommandSourceStack) {
    val name: String get() = source.textName
    val player: ServerPlayer? get() = source.entity as? ServerPlayer
    val level: ServerLevel get() = source.level
    val positionVector: Vec3 get() = source.position
    val position: BlockPos get() = BlockPos.containing(source.position)

    fun sendMessage(message: Component) = source.sendSystemMessage(message)
}

interface IConsoleCommand {
    val name: String

    fun runCommand(ics: ICommandSender, args: List<String>)
    fun getManPage(ics: ICommandSender, args: List<String>) {}
    fun getTabCompletion(args: List<String>): List<String> = listOf()
    fun isIndexOfUsername(args: List<String>, index: Int): Boolean = false
    fun requiredPermission(): List<UserPermission> = listOf(UserPermission.IS_OPERATOR)
}

package mods.eln.server.console

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import mods.eln.misc.FC
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.neoforged.neoforge.event.RegisterCommandsEvent

val ElnConsoleCommandList = mutableListOf<IConsoleCommand>()

internal fun findConsoleCommand(
    name: String,
    commands: Iterable<IConsoleCommand> = ElnConsoleCommandList
): IConsoleCommand? {
    return commands.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

/**
 * `/eln <command> [args]`: the 1.7.10 command tree kept as-is behind one Brigadier literal with a
 * greedy argument (the sub-commands parse their own words, as they always did). Registered on
 * [RegisterCommandsEvent]; permission is decided per sub-command, so the literal itself is open.
 */
class ElnConsoleCommands {

    init {
        ElnConsoleCommandList.addAll(listOf(
            ElnLsCommand(),
            ElnConfigCommand(),
            ElnAboutCommand(),
            ElnVersionCommand(),
            ElnNewWindDirectionCommand(),
            ElnMatrixCommand(),
            ElnManCommand(),
            ElnWailaEasyModeCommand(),
            ElnDebugCommand(),
            ElnSimSnapshotCommand(),
            ElnWatchdogCommand(),
            ElnIconsCommand(),
            ElnPoleMapCommand(),
            ElnStopShaftCommand(),
            ElnResetAmbientTempsCommand(),
            ElnResetLampLifeCommand(),
            ElnZoneDumpCommand(),
            ElnZoneCleanCommand(),
            ElnZoneRemoveCommand(),
            ElnZoneDestroyCommand()
        ))
    }

    companion object {
        fun cprint(ics: ICommandSender, text: String, indent: Int = 0) {
            printIndented(text, indent).forEach {
                ics.sendMessage(Component.literal(it))
            }
        }

        fun cprint(ics: ICommandSender, text: String, url: String) {
            val msg = Component.literal(FC.BRIGHT_GREY + text)
            msg.style = msg.style.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, url))
            ics.sendMessage(msg)
        }

        fun printIndented(text: String, indent: Int): List<String> {
            val lineLength = 60 - indent * 2
            val list = mutableListOf<String>()
            val finalLine = text
                .split(' ')
                .fold("", { acc, next ->
                    if (acc.length + next.length > lineLength) {
                        list.add(acc)
                        next
                    } else {
                        "$acc $next"
                    }
                })
            list.add(finalLine)
            var mostRecentColor = '7' // default color is light gray
            val list2 = list.map  {
                line ->
                val lastLine = "§$mostRecentColor$line"
                if ("§" in line) {
                    mostRecentColor = line[line.lastIndexOf("§") + 1]
                }
                lastLine
            }
            val whitespace = (0 until indent * 2).joinToString("") { " " }
            return list2.map{"$whitespace$it"}
        }

        fun getArgBool(ics: ICommandSender, arg: String): Boolean? {
            val lowerArg = arg.lowercase()
            return if (lowerArg.isEmpty()) {
                cprint(ics, "Error: Empty argument.", indent = 1)
                null
            }else if (lowerArg == "0" || lowerArg == "false" || lowerArg == "no" || lowerArg == "disabled" || lowerArg == "disable") {
                false
            } else if (lowerArg == "1" || lowerArg == "true" || lowerArg == "yes" || lowerArg == "enabled" || lowerArg == "enable") {
                true
            } else {
                cprint(ics, "Error: Expected (true/false), got $arg",  indent = 1)
                null
            }
        }

        fun boolToStr(value: Boolean): String {
            return if (value) "Enabled" else "Disabled"
        }
    }


    fun register(event: RegisterCommandsEvent) {
        val suggestions = SuggestionProvider<CommandSourceStack> { context, builder ->
            val typed = try { StringArgumentType.getString(context, "args") } catch (e: IllegalArgumentException) { "" }
            val words = typed.split(' ')
            val completions = if (words.size <= 1) {
                ElnConsoleCommandList.map { it.name }.filter { it.startsWith(words.getOrElse(0) { "" }, ignoreCase = true) }
            } else {
                val command = findConsoleCommand(words[0])
                command?.getTabCompletion(words.drop(1))?.map { (words.dropLast(1) + it).joinToString(" ") } ?: emptyList()
            }
            // the suggestion replaces the whole greedy argument
            val offset = builder.createOffset(builder.start)
            SharedSuggestionProvider.suggest(completions, offset)
        }
        event.dispatcher.register(
            Commands.literal("eln")
                .executes { context -> execute(context, emptyList()); 1 }
                .then(Commands.argument("args", StringArgumentType.greedyString())
                    .suggests(suggestions)
                    .executes { context -> execute(context, StringArgumentType.getString(context, "args").split(' ').filter { it.isNotEmpty() }); 1 })
        )
    }

    private fun execute(context: CommandContext<CommandSourceStack>, args: List<String>) {
        val ics = ICommandSender(context.source)
        if (args.isEmpty()) {
            cprint(ics, "${FC.DARK_CYAN}Electrical Age Console, run /eln ls for commands${FC.BRIGHT_GREY }")
            return
        }
        val permissions = determinePermissionsList(context.source.server, ics)
        val command = findConsoleCommand(args[0])
        if (command == null) {
            cprint(ics, "${FC.DARK_CYAN}Command not found, run /eln ls for commands${FC.BRIGHT_GREY }")
            return
        }
        cprint(ics, "${FC.DARK_CYAN}${ics.name} $${FC.DARK_YELLOW} /eln ${args.joinToString(" ")}")
        val canRun = permissions.any { command.requiredPermission().contains(it) }
        if (canRun) {
            command.runCommand(ics, args.drop(1))
        } else {
            cprint(ics, "${FC.DARK_CYAN}You do not have permission to run that command. " +
                "You need to have one of the following: ${command.requiredPermission()}${FC.BRIGHT_GREY }")
        }
    }

    fun determinePermissionsList(server: MinecraftServer, ics: ICommandSender): List<UserPermission> {
        var creative = false
        var singlePlayer = false
        var isOperator = false
        val player = ics.player
        val console = player == null
        if (player != null) {
            creative = player.isCreative
            singlePlayer = server.isSingleplayer
            isOperator = server.playerList.isOp(player.gameProfile)
        }
        val playerPerms = mutableListOf<UserPermission>()
        if (creative)
            playerPerms.add(UserPermission.IS_CREATIVE)
        if (console) {
            playerPerms.add(UserPermission.IS_CONSOLE)
            playerPerms.add(UserPermission.IS_OPERATOR)
        }
        if (isOperator)
            playerPerms.add(UserPermission.IS_OPERATOR)
        if (singlePlayer)
            playerPerms.add(UserPermission.IS_OPERATOR)
        return playerPerms.toList()
    }
}

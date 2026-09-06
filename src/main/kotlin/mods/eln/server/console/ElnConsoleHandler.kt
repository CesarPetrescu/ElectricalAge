package mods.eln.server.console

import mods.eln.misc.FC
import net.minecraft.command.ICommand
import net.minecraft.command.ICommandSender
import net.minecraft.network.chat.ClickEvent
import net.minecraft.server.MinecraftServer
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

val ElnConsoleCommandList = mutableListOf<IConsoleCommand>()

internal fun findConsoleCommand(
    name: String,
    commands: Iterable<IConsoleCommand> = ElnConsoleCommandList
): IConsoleCommand? {
    return commands.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

class ElnConsoleCommands: ICommand {

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
            msg.style.setClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, url))
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

    // ICommand is Comparable<ICommand> on 1.12, not Comparable<Object>: commands sort by name.
    override fun compareTo(other: ICommand): Int = name.compareTo(other.name)

    override fun getName() = "eln"

    override fun getUsage(sender: ICommandSender) =
        "${FC.DARK_CYAN}Electrical Age Console, run /eln ls for commands${FC.BRIGHT_GREY }"

    override fun getAliases() = mutableListOf<String>()

    override fun execute(server: MinecraftServer, ics: ICommandSender, args: Array<out String>) {
        if (args.isEmpty()) {
            cprint(ics,"${FC.DARK_CYAN}Electrical Age Console, run /eln ls for commands${FC.BRIGHT_GREY }")
            return
        }
        val permissions = determinePermissionsList(server, ics)
        val command = findConsoleCommand(args[0])
        if (command == null) {
            cprint(ics,"${FC.DARK_CYAN}Command not found, run /eln ls for commands${FC.BRIGHT_GREY }")
            return
        }
        cprint(ics, "${FC.DARK_CYAN}${ics.name} $${FC.DARK_YELLOW} /eln ${args.joinToString(" ")}")
        val canRun = permissions.any { command.requiredPermission().contains(it) }
        if (canRun) {
            command.runCommand(ics, args.toList().drop(1))
        } else {
            cprint(ics, "${FC.DARK_CYAN}You do not have permission to run that command. " +
                "You need to have one of the following: ${command.requiredPermission()}${FC.BRIGHT_GREY }")
        }
    }

    fun determinePermissionsList(server: MinecraftServer, ics: ICommandSender): List<UserPermission> {
        var creative = false
        var singlePlayer = false
        var isOperator = false
        val player = ics.entityWorld.getPlayerEntityByName(ics.name)
        val console = player == null
        if (!console) {
            creative = player.isCreative()
            singlePlayer = server.isSinglePlayer
                isOperator = server.playerList.oppedPlayers.getEntry(player.gameProfile) != null
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

    // We don't actually use this because we do it on command execution for more control
    override fun checkPermission(server: MinecraftServer, ics: ICommandSender) = true

    override fun getTabCompletions(
        server: MinecraftServer, ics: ICommandSender, args: Array<out String>, targetPos: BlockPos?
    ): MutableList<String> {
        if (args.toList().isEmpty() || args[0] == "") {
            return ElnConsoleCommandList.map {it.name}.toMutableList()
        }
        val command = findConsoleCommand(args[0])
        if (command == null) {
            return ElnConsoleCommandList.filter {it.name.startsWith(args[0], ignoreCase = true)}.map{it.name}.toMutableList()
        }
        return command.getTabCompletion(args.drop(1)).toMutableList()
    }

    override fun isUsernameIndex(args: Array<out String>, index: Int): Boolean {
        return false
    }
}

package mods.eln.sixnode.mqttsignal

import mods.eln.misc.BasicContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.Container

class MqttSignalControllerContainer(player: Player, inventory: Container) :
    BasicContainer(player, inventory, emptyArray())

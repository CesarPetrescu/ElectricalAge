package mods.eln.sixnode.thermometersensor

import mods.eln.misc.BasicContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.Container
import net.minecraft.world.inventory.Slot

class ThermometerSensorContainer(player: Player, inventory: Container) :
    BasicContainer(player, inventory, arrayOf<Slot>())

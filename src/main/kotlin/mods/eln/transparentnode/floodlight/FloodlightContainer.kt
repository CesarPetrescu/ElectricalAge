package mods.eln.transparentnode.floodlight

import mods.eln.item.lampitem.LampItemSlot
import mods.eln.misc.BasicContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.Container

class FloodlightContainer(player: Player, inventory: Container, descriptor: FloodlightDescriptor) :
    BasicContainer(player, inventory, arrayOf(
        LampItemSlot(inventory, LAMP_SLOT_1_ID, 70+1, 61+1, 1, descriptor.acceptedLampTypes),
        LampItemSlot(inventory, LAMP_SLOT_2_ID, 88+1, 61+1, 1, descriptor.acceptedLampTypes)
    )) {

    companion object {
        const val LAMP_SLOT_1_ID: Int = 0
        const val LAMP_SLOT_2_ID: Int = 1
    }

}
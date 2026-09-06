package mods.eln.sim.process.destruct

import mods.eln.Eln
import mods.eln.node.six.SixNodeElement
import net.minecraft.world.level.block.Blocks
import mods.eln.misc.setBlock

class CableMelt(private val element: SixNodeElement) : IDestructible {
    override fun destructImpl() {
        val coordinate = element.coordinate ?: return
        element.sixNode?.dropItem(Eln.instance.wireScrapDescriptor?.newItemStack())
        coordinate.world().setBlock(coordinate.x, coordinate.y, coordinate.z, Blocks.AIR)
    }

    override fun describe(): String {
        val coordinate = element.coordinate
        return "${element.javaClass.simpleName} (${coordinate?.toString() ?: "unknown"})"
    }
}

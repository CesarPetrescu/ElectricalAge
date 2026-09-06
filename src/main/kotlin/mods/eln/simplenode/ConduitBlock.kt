package mods.eln.simplenode

import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.simple.SimpleNode
import mods.eln.node.simple.SimpleNodeBlock
import mods.eln.node.simple.SimpleNodeEntity
import mods.eln.sim.ElectricalLoad
import net.minecraft.block.material.Material
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.Direction as EnumFacing
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level

class ConduitBlock(): SimpleNodeBlock(Material.ROCK) {

    override fun createNewTileEntity(worldIn: Level?, meta: Int): BlockEntity {
        return ConduitEntity()
    }

    override fun newNode(): SimpleNode {
        return ConduitNode()
    }


    override fun isSideSolid(state: BlockState, world: BlockGetter, pos: BlockPos, side: EnumFacing): Boolean {
        return true
    }
}

class ConduitNode: SimpleNode() {

    override fun initialize() {
        connect()
    }

    override val nodeUuid: String
        get() = getNodeUuidStatic()

    override fun getSideConnectionMask(side: Direction, lrdu: LRDU): Int {
        return maskConduit
    }

    override fun getElectricalLoad(side: Direction, lrdu: LRDU, mask: Int): ElectricalLoad? {
        return null
    }

    override fun getThermalLoad(side: Direction, lrdu: LRDU, mask: Int) = null


    companion object {
        fun getNodeUuidStatic(): String {
            return "ElnConduit"
        }
    }
}

class ConduitEntity(): SimpleNodeEntity(ConduitNode.getNodeUuidStatic()) {

}
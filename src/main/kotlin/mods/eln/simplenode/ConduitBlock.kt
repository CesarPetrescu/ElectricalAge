package mods.eln.simplenode

import mods.eln.misc.Direction
import mods.eln.misc.LRDU
import mods.eln.node.simple.SimpleNode
import mods.eln.node.simple.SimpleNodeBlock
import mods.eln.node.simple.SimpleNodeEntity
import mods.eln.sim.ElectricalLoad
import mods.eln.node.NodeBlock
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.function.Supplier
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.core.BlockPos

class ConduitBlock(): SimpleNodeBlock(NodeBlock.nodeProperties()) {

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = ConduitEntity(pos, state)

    override fun newNode(): SimpleNode {
        return ConduitNode()
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

class ConduitEntity(pos: BlockPos, state: BlockState): SimpleNodeEntity(TYPE.get(), pos, state, ConduitNode.getNodeUuidStatic()) {
    companion object {
        @JvmField
        var TYPE: Supplier<BlockEntityType<ConduitEntity>> = Supplier { throw IllegalStateException("ConduitEntity type not registered") }
    }
}
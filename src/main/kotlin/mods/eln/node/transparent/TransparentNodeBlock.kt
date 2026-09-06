package mods.eln.node.transparent

import mods.eln.Eln
import mods.eln.node.NodeBase
import mods.eln.node.NodeBlock
import mods.eln.node.NodeBlockEntity
import net.minecraft.block.material.Material
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.RenderShape
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.world.phys.AABB
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import java.lang.RuntimeException
import java.lang.reflect.InvocationTargetException
import java.util.*
import mods.eln.misc.getBlockMetadata
import mods.eln.misc.getTileEntity

class TransparentNodeBlock(material: Material?, tileEntityClass: Class<*>?) : NodeBlock(material, tileEntityClass!!, 0) {

    override fun getSubBlocks(tab: CreativeModeTab, subItems: NonNullList<ItemStack>) {
        Eln.transparentNodeItem.getSubItems(tab, subItems)
    }

    override fun isOpaqueCube(state: BlockState): Boolean {
        return false
    }

    override fun isFullCube(state: BlockState): Boolean {
        return false
    }

    override fun getRenderType(state: BlockState): RenderShape {
        return RenderShape.INVISIBLE
    }

    override fun removedByPlayer(state: BlockState, world: Level, pos: BlockPos, entityPlayer: Player, willHarvest: Boolean): Boolean {
        if (!world.isClientSide) {
            val entity = world.getBlockEntity(pos) as? NodeBlockEntity
            if (entity != null) {
                val nodeBase: NodeBase? = entity.node
                if (nodeBase is TransparentNode) {
                    nodeBase.removedByPlayer = entityPlayer as ServerPlayer
                }
            }
        }
        return super.removedByPlayer(state, world, pos, entityPlayer, willHarvest)
    }

    /** See the note on SixNodeBlock.getDamageValue: no longer an override on 1.12.2. */
    fun getDamageValue(world: Level, pos: BlockPos): Int {
        val tile = world.getBlockEntity(pos)
        return if (tile is TransparentNodeEntity) tile.getDamageValue(world, pos.x, pos.y, pos.z) else 0
    }

    override fun getLightOpacity(state: BlockState, world: BlockGetter, pos: BlockPos): Int {
        return getMetaFromState(state) and 3 shl 6
    }

    override fun getItemDropped(state: BlockState, random: Random, fortune: Int): Item? {
        return null
    }

    override fun quantityDropped(par1Random: Random): Int {
        return 0
    }

    override fun canPlaceBlockOnSide(world: Level, pos: BlockPos, side: Direction): Boolean {
        return true
    }

    override fun addCollisionBoxToList(
        state: BlockState, world: Level, pos: BlockPos, entityBox: AABB,
        collidingBoxes: MutableList<AABB>, entity: Entity?, isActualState: Boolean
    ) {
        val tileEntity = world.getBlockEntity(pos)
        if (tileEntity !is TransparentNodeEntity) {
            super.addCollisionBoxToList(state, world, pos, entityBox, collidingBoxes, entity, isActualState)
        } else {
            @Suppress("UNCHECKED_CAST")
            tileEntity.addCollisionBoxesToList(entityBox, collidingBoxes as MutableList<AABB?>, null)
        }
    }

    override fun createTileEntity(world: Level, state: BlockState): BlockEntity {
        val meta = getMetaFromState(state)
        try {
            for (tag in EntityMetaTag.values()) {
                if (tag.meta == meta) {
                    return tag.cls.getConstructor().newInstance() as BlockEntity
                }
            }
            // Sadly, this will happen a lot with pre-metatag worlds.
            // Only real fix is to replace the blocks, but there should be no
            // serious downside to getting the wrong subclass so long as they really
            // wanted the superclass.
            println("Unknown block meta-tag: $meta")
            return EntityMetaTag.Basic.cls.getConstructor().newInstance() as BlockEntity
        } catch (e: InstantiationException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
        throw RuntimeException("Unable to continue creating tile entity")
    }

    val nodeUuid: String
        get() = "t"
}

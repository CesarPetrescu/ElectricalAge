package mods.eln.node.transparent

import mods.eln.Eln
import mods.eln.node.NodeBase
import mods.eln.node.NodeBlock
import mods.eln.node.NodeBlockEntity
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumBlockRenderType
import net.minecraft.util.EnumFacing
import net.minecraft.util.NonNullList
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import java.lang.RuntimeException
import java.lang.reflect.InvocationTargetException
import java.util.*
import mods.eln.misc.getBlockMetadata
import mods.eln.misc.getTileEntity

class TransparentNodeBlock(material: Material?, tileEntityClass: Class<*>?) : NodeBlock(material, tileEntityClass!!, 0) {

    override fun getSubBlocks(tab: CreativeTabs, subItems: NonNullList<ItemStack>) {
        Eln.transparentNodeItem.getSubItems(tab, subItems)
    }

    override fun isOpaqueCube(state: IBlockState): Boolean {
        return false
    }

    override fun isFullCube(state: IBlockState): Boolean {
        return false
    }

    override fun getRenderType(state: IBlockState): EnumBlockRenderType {
        return EnumBlockRenderType.INVISIBLE
    }

    override fun removedByPlayer(state: IBlockState, world: World, pos: BlockPos, entityPlayer: EntityPlayer, willHarvest: Boolean): Boolean {
        if (!world.isRemote) {
            val entity = world.getTileEntity(pos) as? NodeBlockEntity
            if (entity != null) {
                val nodeBase: NodeBase? = entity.node
                if (nodeBase is TransparentNode) {
                    nodeBase.removedByPlayer = entityPlayer as EntityPlayerMP
                }
            }
        }
        return super.removedByPlayer(state, world, pos, entityPlayer, willHarvest)
    }

    /** See the note on SixNodeBlock.getDamageValue: no longer an override on 1.12.2. */
    fun getDamageValue(world: World, pos: BlockPos): Int {
        val tile = world.getTileEntity(pos)
        return if (tile is TransparentNodeEntity) tile.getDamageValue(world, pos.x, pos.y, pos.z) else 0
    }

    override fun getLightOpacity(state: IBlockState, world: IBlockAccess, pos: BlockPos): Int {
        return getMetaFromState(state) and 3 shl 6
    }

    override fun getItemDropped(state: IBlockState, random: Random, fortune: Int): Item? {
        return null
    }

    override fun quantityDropped(par1Random: Random): Int {
        return 0
    }

    override fun canPlaceBlockOnSide(world: World, pos: BlockPos, side: EnumFacing): Boolean {
        return true
    }

    override fun addCollisionBoxToList(
        state: IBlockState, world: World, pos: BlockPos, entityBox: AxisAlignedBB,
        collidingBoxes: MutableList<AxisAlignedBB>, entity: Entity?, isActualState: Boolean
    ) {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity !is TransparentNodeEntity) {
            super.addCollisionBoxToList(state, world, pos, entityBox, collidingBoxes, entity, isActualState)
        } else {
            @Suppress("UNCHECKED_CAST")
            tileEntity.addCollisionBoxesToList(entityBox, collidingBoxes as MutableList<AxisAlignedBB?>, null)
        }
    }

    override fun createTileEntity(world: World, state: IBlockState): TileEntity {
        val meta = getMetaFromState(state)
        try {
            for (tag in EntityMetaTag.values()) {
                if (tag.meta == meta) {
                    return tag.cls.getConstructor().newInstance() as TileEntity
                }
            }
            // Sadly, this will happen a lot with pre-metatag worlds.
            // Only real fix is to replace the blocks, but there should be no
            // serious downside to getting the wrong subclass so long as they really
            // wanted the superclass.
            println("Unknown block meta-tag: $meta")
            return EntityMetaTag.Basic.cls.getConstructor().newInstance() as TileEntity
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

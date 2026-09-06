package mods.eln.node.transparent

import mods.eln.generic.GenericItemBlockUsingDamageDescriptor
import mods.eln.ghost.GhostGroup
import mods.eln.i18n.I18N.tr
import mods.eln.misc.Coordinate
import mods.eln.misc.Direction
import mods.eln.misc.Obj3D
import mods.eln.misc.Utils.entityLivingHorizontalViewDirection
import mods.eln.misc.Utils.entityLivingViewDirection
import mods.eln.misc.UtilsClient.drawIcon
import mods.eln.misc.VoltageLevelColor
import mods.eln.node.transparent.TransparentNode.FrontType
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HopperBlock
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import mods.eln.client.itemrender.IItemRenderer
import mods.eln.client.itemrender.IItemRenderer.ItemRenderType
import mods.eln.client.itemrender.IItemRenderer.ItemRendererHelper
import mods.eln.client.gl.GL11

open class TransparentNodeDescriptor @JvmOverloads constructor(
    name: String?,
    var ElementClass: Class<*>,
    var RenderClass: Class<*>,
    val tileEntityMetaTag: EntityMetaTag = EntityMetaTag.Basic) : GenericItemBlockUsingDamageDescriptor(name), IItemRenderer {
    @JvmField
    protected var voltageLevelColor = VoltageLevelColor.None
    @JvmField
    var ghostGroup: GhostGroup? = null

    override fun handleRenderType(item: ItemStack, type: ItemRenderType): Boolean {
        return voltageLevelColor !== VoltageLevelColor.None
    }

    override fun shouldUseRenderHelper(type: ItemRenderType, item: ItemStack, helper: ItemRendererHelper): Boolean {
        return false
    }

    open fun shouldUseRenderHelperEln(type: ItemRenderType?, item: ItemStack?, helper: ItemRendererHelper?): Boolean {
        return false
    }

    override fun renderItem(type: ItemRenderType, item: ItemStack, vararg data: Any) {
        // 1.12.2 has no IIcon; the block sprite is addressed by its path under textures/blocks.
        val icon = iconName ?: return
        if (!mods.eln.generic.GenericCreativeTab.isTabIcon(item)) voltageLevelColor.drawIconBackground(type)
        drawIcon(type, ResourceLocation.fromNamespaceAndPath("eln", "textures/blocks/$icon.png"))
    }

    fun objItemScale(obj: Obj3D?) {
        if (obj == null) return
        var factor = obj.yDim * 0.6f
        factor = factor.coerceAtLeast((obj.zMax.coerceAtLeast(-obj.xMin) + Math.max(obj.xMax, -obj.zMin)) * 0.7f)
        factor = 1f / factor
        GL11.glScalef(factor, factor, factor)
        GL11.glTranslatef((obj.zMin.coerceAtMost(obj.xMin) + obj.xMax.coerceAtLeast(obj.zMax)) / 2 - (obj.xMax + obj.xMin) / 2, 1.0f - (obj.xMax + obj.xMin) / 2 - (obj.zMax + obj.zMin) / 2 - (obj.yMax + obj.yMin) / 2, 0.0f)
    }

    open val frontType: FrontType?
        get() = FrontType.PlayerViewHorizontal

    open fun mustHaveFloor(): Boolean {
        return true
    }

    open fun mustHaveCeiling(): Boolean {
        return false
    }

    open fun mustHaveWall(): Boolean {
        return false
    }

    open fun mustHaveWallFrontInverse(): Boolean {
        return false
    }

    open fun checkCanPlace(coord: Coordinate?, front: Direction): String? {
        // 1.8+: opacity is a property of the block state, not the block.
        fun opaqueAt(direction: Direction): Boolean {
            val temp = Coordinate(coord!!)
            temp.move(direction)
            return temp.blockState.isSolidRender(temp.world(), temp.pos)
        }
        if (mustHaveFloor()) {
            val temp = Coordinate(coord!!)
            temp.move(Direction.YN)
            if (!temp.blockState.isSolidRender(temp.world(), temp.pos) && temp.block !is HopperBlock) return tr("You can't place this block at this side")
        }
        if (mustHaveCeiling()) {
            if (!opaqueAt(Direction.YP)) return tr("You can't place this block at this side")
        }
        if (mustHaveWallFrontInverse()) {
            if (!opaqueAt(front.inverse)) return tr("You can't place this block at this side")
        }
        if (mustHaveWall()) {
            val wall = opaqueAt(Direction.XN) || opaqueAt(Direction.XP) || opaqueAt(Direction.ZN) || opaqueAt(Direction.ZP)
            if (!wall) return tr("You can't place this block at this side")
        }
        val ghostGroup = getGhostGroupFront(front)
        return if (ghostGroup != null && !ghostGroup.canBePloted(coord!!)) tr("Not enough space for this block") else null
    }

    open fun getFrontFromPlace(side: Direction, entityLiving: LivingEntity?): Direction? {
        var front = Direction.XN
        when (frontType) {
            FrontType.BlockSide -> front = side
            FrontType.BlockSideInv -> front = side.inverse
            FrontType.PlayerView -> front = entityLivingViewDirection(entityLiving!!).inverse
            FrontType.PlayerViewHorizontal -> front = entityLivingHorizontalViewDirection(entityLiving!!).inverse
            null -> TODO()
        }
        return front
    }

    fun getGhostGroupFront(front: Direction?): GhostGroup? {
        return if (ghostGroup == null) null else ghostGroup!!.newRotate(front)
    }

    val ghostGroupUuid: Int
        get() = -1
    open val spawnDeltaX: Int
        get() = 0
    open val spawnDeltaY: Int
        get() = 0
    open val spawnDeltaZ: Int
        get() = 0

    open fun addCollisionBoxesToList(par5AxisAlignedBB: AABB, list: MutableList<AABB?>, world: Level?, x: Int, y: Int, z: Int) {
        // A full stone cube at (x, y, z); 1.12 block boxes are block-local, so offset explicitly.
        val bb = AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).move(x.toDouble(), y.toDouble(), z.toDouble())
        if (par5AxisAlignedBB.intersects(bb)) list.add(bb)
    }
}

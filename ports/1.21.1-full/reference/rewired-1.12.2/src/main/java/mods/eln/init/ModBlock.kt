package mods.eln.init

import mods.eln.Eln
import mods.eln.ghost.GhostBlock
import mods.eln.node.six.SixNodeBlock
import mods.eln.node.six.SixNodeEntity
import mods.eln.node.transparent.TransparentNodeBlock
import mods.eln.node.transparent.TransparentNodeEntity
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.lang.Math.abs

/**
 * Legacy compatibility object - provides static field access for Java code.
 * These are initialized by ElnContent during preInit.
 *
 * Note: Do not use these before ElnContent.preInit() is called during mod preInit.
 */
object ModBlock {
    @JvmStatic
    lateinit var copperOreBlock: Block
    @JvmStatic
    lateinit var leadOreBlock: Block
    @JvmStatic
    lateinit var tungstenOreBlock: Block
    @JvmStatic
    lateinit var cinnabarOreBlock: Block

    @JvmStatic
    lateinit var flubberBlock: RubberBlock

    @JvmStatic
    lateinit var ghostBlock: GhostBlock

    @JvmStatic
    lateinit var sixNodeBlock: SixNodeBlock

    @JvmStatic
    lateinit var transparentNodeBlock: TransparentNodeBlock

    /**
     * Initialize all blocks - called by ElnContent.preInit()
     */
    @JvmStatic
    internal fun init() {
        copperOreBlock = ElnBlockMod("copper_ore", Material.ROCK).setHardness(3.0f).setResistance(5.0f)
        leadOreBlock = ElnBlockMod("lead_ore", Material.ROCK).setHardness(3.0f).setResistance(5.0f)
        tungstenOreBlock = ElnBlockMod("tungsten_ore", Material.ROCK).setHardness(3.0f).setResistance(5.0f)
        cinnabarOreBlock = ElnBlockMod("cinnabar_ore", Material.ROCK).setHardness(3.0f).setResistance(5.0f)

        flubberBlock = RubberBlock("flubber", 2f)
        ghostBlock = GhostBlock().apply {
            setTranslationKey("ghost")
            setRegistryName(Eln.MODID, "ghost")
        }
        sixNodeBlock = SixNodeBlock(Material.ROCK, SixNodeEntity::class.java).apply {
            setRegistryName(Eln.MODID, "sixnode")
            setTranslationKey("sixnode")
            setCreativeTab(Eln.Tab)
        }
        transparentNodeBlock = TransparentNodeBlock(Material.ROCK, TransparentNodeEntity::class.java).apply {
            setRegistryName(Eln.MODID, "transparentnode")
            setTranslationKey("transparentnode")
            setCreativeTab(Eln.Tab)
        }

        // Set creative tab for all blocks
        val tab = Eln.Tab
        copperOreBlock.creativeTab = tab
        leadOreBlock.creativeTab = tab
        tungstenOreBlock.creativeTab = tab
        cinnabarOreBlock.creativeTab = tab
        
        sixNodeBlock.creativeTab = tab
        transparentNodeBlock.creativeTab = tab
        
        // flubber and ghost blocks are kept out of creative menu
        flubberBlock.creativeTab = null
        ghostBlock.creativeTab = null
    }
}

class RubberBlock(name: String, private val bounce: Float) : Block(Material.WOOD) {
    init {
        setTranslationKey(name)
        setRegistryName(Eln.MODID, name)
        setCreativeTab(Eln.Tab)
    }
    
    override fun onLanded(worldIn: World, entityIn: Entity) {
        if (abs(entityIn.motionY) > 0.1) {
            entityIn.motionY = abs(entityIn.motionY * bounce)
        } else {
            entityIn.motionY = 0.0
        }
    }

    override fun onFallenUpon(worldIn: World, pos: BlockPos, entityIn: Entity, fallDistance: Float) {
        super.onFallenUpon(worldIn, pos, entityIn, fallDistance / 8.0f)
    }
}

class ElnBlockMod(name: String, material: Material, val uuid: String = name.take(1)) : Block(material) {
    init {
        setTranslationKey("eln.ore.$name")
        setRegistryName(Eln.MODID, name)
        setCreativeTab(Eln.Tab)
    }
}

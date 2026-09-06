package mods.eln.registration

import mods.eln.Eln
import mods.eln.generic.CreativeTabPopulator
import mods.eln.i18n.I18N
import mods.eln.node.NodeManager.Companion.registerUuid
import mods.eln.node.simple.SimpleNodeItem
import mods.eln.simplenode.ConduitBlock
import mods.eln.simplenode.ConduitEntity
import mods.eln.simplenode.ConduitNode
import mods.eln.simplenode.ConduitNode.Companion.getNodeUuidStatic
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherBlock
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherDescriptor
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherEntity
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherNode
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherNode.Companion.nodeUuidStatic
import mods.eln.simplenode.computerprobe.ComputerProbeBlock
import mods.eln.simplenode.computerprobe.ComputerProbeEntity
import mods.eln.simplenode.computerprobe.ComputerProbeNode
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import java.util.function.Function
import java.util.function.Supplier

/** The single-purpose node blocks: the energy exporter, the computer probe, the dev-only conduit. */
object SingleNodeRegistration {

    fun registerSingle() {
        if (Eln.instance.isDevelopmentRun) {
            registerConduitSingles()
        }
        registerEnergyConverter()
        registerComputer()
    }

    /** The probe is a node with or without a computer mod; CC: Tweaked makes it a peripheral (ElnCapabilities). */
    private fun registerComputer() {
        if (!Eln.config.getBooleanOrElse("integrations.computerProbe.enabled", true)) return
        val entityName = I18N.TR_NAME(I18N.Type.TILE, "eln.ElnProbe")
        registerUuid(ComputerProbeNode.getNodeUuidStatic(), ComputerProbeNode::class.java)
        val block: Supplier<Block> = ElnRegistry.registerBlock(entityName, {
            Eln.instance.computerProbeBlock = ComputerProbeBlock().apply { translationName = entityName }
            Eln.instance.computerProbeBlock
        }, Function { SimpleNodeItem(it) })
        ComputerProbeEntity.TYPE = ElnRegistry.registerBlockEntity(entityName, block) { pos, state -> ComputerProbeEntity(pos, state) }
        CreativeTabPopulator.register(Eln.creativeTabSignalProcessing) { ItemStack(block.get()) }
        Eln.computerProbeRegistered = true
    }

    private fun registerConduitSingles() {
        // Registry names are flat now: the six-node "Conduit" cable already owns eln:conduit, so
        // this dev-only single node block is eln:conduitsingle.
        val entityName = I18N.TR_NAME(I18N.Type.TILE, "eln.ConduitSingle")
        registerUuid(getNodeUuidStatic(), ConduitNode::class.java)
        val block: Supplier<Block> = ElnRegistry.registerBlock(entityName, { ConduitBlock().apply { translationName = entityName } }, Function { SimpleNodeItem(it) })
        ConduitEntity.TYPE = ElnRegistry.registerBlockEntity(entityName, block) { pos, state -> ConduitEntity(pos, state) }
    }

    private fun registerEnergyConverter() {
        if (Eln.config.getBooleanOrElse("integrations.energyExporter.enabled", true)) {
            registerUuid(nodeUuidStatic, EnergyConverterElnToOtherNode::class.java)
            val blockName = I18N.TR_NAME(I18N.Type.TILE, "eln.EnergyConverter")
            val desc = EnergyConverterElnToOtherDescriptor("EnergyConverterElnToOtherLVU", Eln.instance.ELN_CONVERTER_MAX_POWER)
            val block: Supplier<Block> = ElnRegistry.registerBlock(blockName, {
                Eln.instance.elnToOtherBlockConverter = EnergyConverterElnToOtherBlock(desc).apply { translationName = blockName }
                Eln.instance.elnToOtherBlockConverter
            }, Function { SimpleNodeItem(it) })
            EnergyConverterElnToOtherEntity.TYPE = ElnRegistry.registerBlockEntity("eln.EnergyConverterElnToOtherEntity", block) { pos, state -> EnergyConverterElnToOtherEntity(pos, state) }
            CreativeTabPopulator.register(Eln.creativeTabPowerElectronics) { ItemStack(block.get()) }
        }
    }
}

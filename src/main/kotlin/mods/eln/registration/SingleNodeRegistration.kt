package mods.eln.registration

import mods.eln.Eln
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
import net.minecraft.world.level.block.Block
import java.util.function.Function
import java.util.function.Supplier

/**
 * The single-purpose node blocks. The computer probe (ComputerCraft/OpenComputers peripheral) is
 * not registered on 1.21: OpenComputers has no 1.21 release and the CC:Tweaked peripheral is
 * phase 4 work (see PORT-1.21.md).
 */
object SingleNodeRegistration {

    fun registerSingle() {
        if (Eln.instance.isDevelopmentRun) {
            registerConduitSingles()
        }
        registerEnergyConverter()
    }

    private fun registerConduitSingles() {
        // Registry names are flat now: the six-node "Conduit" cable already owns eln:conduit, so
        // this dev-only single node block is eln:conduitsingle.
        val entityName = I18N.TR_NAME(I18N.Type.TILE, "eln.ConduitSingle")
        registerUuid(getNodeUuidStatic(), ConduitNode::class.java)
        val block: Supplier<Block> = ElnRegistry.registerBlock(entityName, { ConduitBlock() }, Function { SimpleNodeItem(it) })
        ConduitEntity.TYPE = ElnRegistry.registerBlockEntity(entityName, block) { pos, state -> ConduitEntity(pos, state) }
    }

    private fun registerEnergyConverter() {
        if (Eln.config.getBooleanOrElse("integrations.energyExporter.enabled", true)) {
            registerUuid(nodeUuidStatic, EnergyConverterElnToOtherNode::class.java)
            val blockName = I18N.TR_NAME(I18N.Type.TILE, "eln.EnergyConverter")
            val desc = EnergyConverterElnToOtherDescriptor("EnergyConverterElnToOtherLVU", Eln.instance.ELN_CONVERTER_MAX_POWER)
            val block: Supplier<Block> = ElnRegistry.registerBlock(blockName, {
                Eln.instance.elnToOtherBlockConverter = EnergyConverterElnToOtherBlock(desc)
                Eln.instance.elnToOtherBlockConverter
            }, Function { SimpleNodeItem(it) })
            EnergyConverterElnToOtherEntity.TYPE = ElnRegistry.registerBlockEntity("eln.EnergyConverterElnToOtherEntity", block) { pos, state -> EnergyConverterElnToOtherEntity(pos, state) }
        }
    }
}

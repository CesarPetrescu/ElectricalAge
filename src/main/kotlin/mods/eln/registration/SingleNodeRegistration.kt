package mods.eln.registration

import net.minecraftforge.fml.common.registry.GameRegistry
import mods.eln.Eln
import mods.eln.i18n.I18N
import mods.eln.node.NodeManager.Companion.registerUuid
import mods.eln.node.simple.SimpleNodeItem
import mods.eln.simplenode.ConduitBlock
import mods.eln.simplenode.ConduitEntity
import mods.eln.simplenode.ConduitNode
import mods.eln.simplenode.ConduitNode.Companion.getNodeUuidStatic
import mods.eln.simplenode.computerprobe.ComputerProbeBlock
import mods.eln.simplenode.computerprobe.ComputerProbeEntity
import mods.eln.simplenode.computerprobe.ComputerProbeNode
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherBlock
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherDescriptor
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherEntity
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherNode
import mods.eln.simplenode.energyconverter.EnergyConverterElnToOtherNode.Companion.nodeUuidStatic
import net.minecraft.tileentity.TileEntity

object SingleNodeRegistration {

    fun registerSingle() {
        if (Eln.instance.isDevelopmentRun) {
            registerConduitSingles()
        }
        registerEnergyConverter()
        registerComputer()
    }


    private fun registerConduitSingles() {
        run {
            val entityName = I18N.TR_NAME(I18N.Type.TILE, "eln.Conduit")
            ElnRegistry.registerTileEntity(ConduitEntity::class.java, entityName)
            registerUuid(getNodeUuidStatic(), ConduitNode::class.java)


            val conduitBlock = ConduitBlock()
            conduitBlock.setCreativeTab(null).setTranslationKey(entityName)
            ElnRegistry.registerBlock(conduitBlock, entityName, SimpleNodeItem::class.java)
        }
    }

    private fun registerEnergyConverter() {
        if (Eln.config.getBooleanOrElse("integrations.energyExporter.enabled", true)) {
            val entityName = "eln.EnergyConverterElnToOtherEntity"

            ElnRegistry.registerTileEntity(EnergyConverterElnToOtherEntity::class.java, entityName)
            registerUuid(
                nodeUuidStatic,
                EnergyConverterElnToOtherNode::class.java
            )

            run {
                val blockName =
                    I18N.TR_NAME(I18N.Type.TILE, "eln.EnergyConverter")
                val desc = EnergyConverterElnToOtherDescriptor(
                    "EnergyConverterElnToOtherLVU", Eln.instance.ELN_CONVERTER_MAX_POWER
                )
                Eln.instance.elnToOtherBlockConverter = EnergyConverterElnToOtherBlock(desc)
                Eln.instance.elnToOtherBlockConverter.setCreativeTab(Eln.creativeTabPowerElectronics).setTranslationKey(blockName)
                ElnRegistry.registerBlock(Eln.instance.elnToOtherBlockConverter, blockName, SimpleNodeItem::class.java)
            }
        }
    }

    private fun registerComputer() {
        if (Eln.config.getBooleanOrElse("integrations.computerProbe.enabled", true)) {
            val entityName = I18N.TR_NAME(I18N.Type.TILE, "eln.ElnProbe")

            ElnRegistry.registerTileEntity(ComputerProbeEntity::class.java, entityName)
            registerUuid(ComputerProbeNode.getNodeUuidStatic(), ComputerProbeNode::class.java)


            Eln.instance.computerProbeBlock = ComputerProbeBlock()
            Eln.instance.computerProbeBlock.setCreativeTab(Eln.creativeTabSignalProcessing).setTranslationKey(entityName)
            ElnRegistry.registerBlock(Eln.instance.computerProbeBlock, entityName, SimpleNodeItem::class.java)
        }
        /*
        if (ComputerProbeEnable) {
            String name = TR_NAME(Type.TILE, "eln.ElnDeviceProbe");
            ElnRegistry.registerTileEntity(DeviceProbeEntity.class, name);
            NodeManager.registerUuid(DeviceProbeNode.Companion.getNodeUuidStatic(), DeviceProbeNode.class);
            DeviceProbeBlock deviceProbeBlock = new DeviceProbeBlock();
            deviceProbeBlock.setCreativeTab(creativeTab).setTranslationKey(name);
            ElnRegistry.registerBlock(deviceProbeBlock, name, SimpleNodeItem.class);
        }
        */
    }

}

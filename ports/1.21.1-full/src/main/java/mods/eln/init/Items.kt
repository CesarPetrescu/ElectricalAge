package mods.eln.init

import mods.eln.Eln
import mods.eln.generic.GenericItemUsingDamageDescriptor
import mods.eln.generic.GenericItemUsingDamageDescriptorWithComment
import mods.eln.init.Config
import mods.eln.item.*
import mods.eln.item.electricalitem.*
import mods.eln.item.regulator.RegulatorAnalogDescriptor
import mods.eln.item.regulator.RegulatorOnOffDescriptor
import mods.eln.sixnode.lampsocket.LampSocketType
import mods.eln.sixnode.wirelesssignal.WirelessSignalAnalyserItemDescriptor
import net.minecraft.init.Blocks
import net.minecraft.init.Items as McItems
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.OreDictionary

/**
 * Central registry for shared items in Electrical Age.
 * These are damage-based items that share a single Item instance.
 */
object Items {
    // Multi-meter, thermometer, all-meter descriptors
    lateinit var multiMeterElement: GenericItemUsingDamageDescriptor

    lateinit var thermometerElement: GenericItemUsingDamageDescriptor

    lateinit var allMeterElement: GenericItemUsingDamageDescriptor

    // Tree resin item
    lateinit var treeResin: TreeResin

    /**
     * Initialize shared items. Must be called during preInit after sharedItem is created.
     */
    @JvmStatic
    fun init() {
        val sharedItem = Eln.sharedItem
        val sharedItemStackOne = Eln.sharedItemStackOne

        // MultiMeter (id=14, subId=0)
        multiMeterElement = GenericItemUsingDamageDescriptor("MultiMeter")
        sharedItem.addElement(0 + (14 shl 6), multiMeterElement)

        // Thermometer (id=14, subId=1)
        thermometerElement = GenericItemUsingDamageDescriptor("Thermometer")
        sharedItem.addElement(1 + (14 shl 6), thermometerElement)

        // AllMeter (id=14, subId=2)
        allMeterElement = GenericItemUsingDamageDescriptor("AllMeter")
        sharedItem.addElement(2 + (14 shl 6), allMeterElement)

        // Wireless Analyser (id=122, subId=8)
        val wirelessAnalyser = WirelessSignalAnalyserItemDescriptor("Wireless Analyser")
        sharedItem.addElement(8 + (122 shl 6), wirelessAnalyser)

        registerHeatingCorp(sharedItem)
        registerRegulatorItem(sharedItem)
        registerLampItems(sharedItem)
        registerProtection(sharedItem)
        registerCombustionChamber(sharedItem)
        registerFerromagneticCore(sharedItem)
        registerIngot(sharedItem)
        registerDust(sharedItem)
        registerElectricalMotor(sharedItem)
        registerSolarTracker(sharedItem)
        registerTreeResinAndRubber(sharedItem)
        registerRawCable(sharedItem)
        registerMiscItem(sharedItem)
        registerBrush(sharedItem)
        registerPlate(sharedItem)
        registerMagnet(sharedItem)
        registerFuses(sharedItem)
        registerElectricalDrill(sharedItem) // id 15
        registerOreScanner(sharedItem) // id 16
        registerMiningPipe(sharedItem) // id 17
        
        registerElectricalTool(sharedItemStackOne)
        registerPortableItem(sharedItemStackOne)
        registerFuelBurnerItem(sharedItemStackOne)

        registerArmor()
        registerTool()
    }

    private fun registerArmor() {
        // Armor is handled in Eln.kt/ElnContent.kt usually, 
        // but Eln_old had them. We assume they are already defined in ElnContent.
    }

    private fun registerTool() {
        // Same as armor.
    }

    private fun registerPlate(sharedItem: mods.eln.generic.SharedItem) {
        val id = 12
        
        val copperPlate = GenericItemUsingDamageDescriptorWithComment("Copper Plate", arrayOf())
        sharedItem.addElement(0 + (id shl 6), copperPlate)
        OreDictionary.registerOre("plateCopper", copperPlate.newItemStack())

        val ironPlate = GenericItemUsingDamageDescriptorWithComment("Iron Plate", arrayOf())
        sharedItem.addElement(1 + (id shl 6), ironPlate)
        OreDictionary.registerOre("plateIron", ironPlate.newItemStack())

        val goldPlate = GenericItemUsingDamageDescriptorWithComment("Gold Plate", arrayOf())
        sharedItem.addElement(2 + (id shl 6), goldPlate)
        OreDictionary.registerOre("plateGold", goldPlate.newItemStack())

        val leadPlate = GenericItemUsingDamageDescriptorWithComment("Lead Plate", arrayOf())
        sharedItem.addElement(3 + (id shl 6), leadPlate)
        OreDictionary.registerOre("plateLead", leadPlate.newItemStack())

        val siliconPlate = GenericItemUsingDamageDescriptorWithComment("Silicon Plate", arrayOf())
        sharedItem.addElement(4 + (id shl 6), siliconPlate)
        OreDictionary.registerOre("plateSilicon", siliconPlate.newItemStack())

        val alloyPlate = GenericItemUsingDamageDescriptorWithComment("Alloy Plate", arrayOf())
        sharedItem.addElement(5 + (id shl 6), alloyPlate)
        OreDictionary.registerOre("plateAlloy", alloyPlate.newItemStack())

        val coalPlate = GenericItemUsingDamageDescriptorWithComment("Coal Plate", arrayOf())
        sharedItem.addElement(6 + (id shl 6), coalPlate)
        OreDictionary.registerOre("plateCoal", coalPlate.newItemStack())
        
        val tungstenPlate = GenericItemUsingDamageDescriptorWithComment("Tungsten Plate", arrayOf())
        sharedItem.addElement(7 + (id shl 6), tungstenPlate)
        OreDictionary.registerOre("plateTungsten", tungstenPlate.newItemStack())
    }

    private fun registerMagnet(sharedItem: mods.eln.generic.SharedItem) {
        val id = 13
        sharedItem.addElement(0 + (id shl 6), GenericItemUsingDamageDescriptor("Basic Magnet"))
        sharedItem.addElement(1 + (id shl 6), GenericItemUsingDamageDescriptor("Advanced Magnet"))
    }

    private fun registerFuses(sharedItem: mods.eln.generic.SharedItem) {
        val id = 98 // From Eln_old.java registerElectricalManager(98)
        
        val breakerObj = Eln.obj.getObj("ElectricalFuse")
        
        sharedItem.addElement(7 + (id shl 6), mods.eln.item.ElectricalFuseDescriptor("Lead Fuse for low voltage cables", Descriptors.lowVoltageCableDescriptor, breakerObj).apply({ setDefaultIcon("lowvoltageleadfuse") }))
        sharedItem.addElement(8 + (id shl 6), mods.eln.item.ElectricalFuseDescriptor("Lead Fuse for medium voltage cables", Descriptors.mediumVoltageCableDescriptor, breakerObj).apply { setDefaultIcon("mediumvoltageleadfuse") })
        sharedItem.addElement(9 + (id shl 6), mods.eln.item.ElectricalFuseDescriptor("Lead Fuse for high voltage cables", Descriptors.highVoltageCableDescriptor, breakerObj).apply { setDefaultIcon("highvoltageleadfuse") })
        sharedItem.addElement(10 + (id shl 6), mods.eln.item.ElectricalFuseDescriptor("Lead Fuse for very high voltage cables", Descriptors.veryHighVoltageCableDescriptor, breakerObj).apply { setDefaultIcon("veryhighvoltageleadfuse") })
        
        val blownFuse = mods.eln.item.ElectricalFuseDescriptor("Blown Lead Fuse", null, breakerObj)
        blownFuse.setDefaultIcon("blownelectricalfuse")
        mods.eln.item.ElectricalFuseDescriptor.BlownFuse = blownFuse
        sharedItem.addElement(11 + (id shl 6), blownFuse)
    }

    private fun registerHeatingCorp(sharedItem: mods.eln.generic.SharedItem) {
        val id = 1
        val lv = Cable.LVU
        val mv = Cable.MVU
        
        // Copper Heating Corps
        sharedItem.addElement(0 + (id shl 6), HeatingCorpElement("Small 50V Copper Heating Corp", lv, 150.0, 190.0, Descriptors.lowVoltageCableDescriptor))
        sharedItem.addElement(1 + (id shl 6), HeatingCorpElement("50V Copper Heating Corp", lv, 250.0, 320.0, Descriptors.lowVoltageCableDescriptor))
        sharedItem.addElement(2 + (id shl 6), HeatingCorpElement("Small 200V Copper Heating Corp", mv, 400.0, 500.0, Descriptors.mediumVoltageCableDescriptor))
        sharedItem.addElement(3 + (id shl 6), HeatingCorpElement("200V Copper Heating Corp", mv, 600.0, 750.0, Descriptors.mediumVoltageCableDescriptor))
        
        // Iron Heating Corps
        sharedItem.addElement(4 + (id shl 6), HeatingCorpElement("Small 50V Iron Heating Corp", lv, 100.0, 130.0, Descriptors.lowVoltageCableDescriptor))
        sharedItem.addElement(5 + (id shl 6), HeatingCorpElement("50V Iron Heating Corp", lv, 180.0, 230.0, Descriptors.lowVoltageCableDescriptor))
        sharedItem.addElement(6 + (id shl 6), HeatingCorpElement("Small 200V Iron Heating Corp", mv, 250.0, 320.0, Descriptors.mediumVoltageCableDescriptor))
        sharedItem.addElement(7 + (id shl 6), HeatingCorpElement("200V Iron Heating Corp", mv, 400.0, 500.0, Descriptors.mediumVoltageCableDescriptor))

        // Tungsten Heating Corps
        sharedItem.addElement(8 + (id shl 6), HeatingCorpElement("Small 50V Tungsten Heating Corp", lv, 300.0, 380.0, Descriptors.lowVoltageCableDescriptor))
        sharedItem.addElement(9 + (id shl 6), HeatingCorpElement("50V Tungsten Heating Corp", lv, 500.0, 640.0, Descriptors.lowVoltageCableDescriptor))
        sharedItem.addElement(10 + (id shl 6), HeatingCorpElement("Small 200V Tungsten Heating Corp", mv, 800.0, 1000.0, Descriptors.mediumVoltageCableDescriptor))
        sharedItem.addElement(11 + (id shl 6), HeatingCorpElement("200V Tungsten Heating Corp", mv, 1200.0, 1500.0, Descriptors.mediumVoltageCableDescriptor))
    }

    private fun registerElectricalMotor(sharedItem: mods.eln.generic.SharedItem) {
        val id = 10
        sharedItem.addElement(0 + (id shl 6), GenericItemUsingDamageDescriptorWithComment("Electrical Motor", arrayOf()))
        sharedItem.addElement(1 + (id shl 6), GenericItemUsingDamageDescriptorWithComment("Advanced Electrical Motor", arrayOf()))
    }

    private fun registerFerromagneticCore(sharedItem: mods.eln.generic.SharedItem) {
        val id = 7
        val obj = Eln.obj.getObj("feromagneticcorea")
        sharedItem.addElement(0 + (id shl 6), FerromagneticCoreDescriptor("Cheap Ferromagnetic Core", obj, 10.0))
        sharedItem.addElement(1 + (id shl 6), FerromagneticCoreDescriptor("Average Ferromagnetic Core", obj, 4.0))
        sharedItem.addElement(2 + (id shl 6), FerromagneticCoreDescriptor("Optimal Ferromagnetic Core", obj, 1.0))
    }

    private fun registerCombustionChamber(sharedItem: mods.eln.generic.SharedItem) {
        val id = 6
        sharedItem.addElement(0 + (id shl 6), CombustionChamber("Combustion Chamber"))
    }

    private fun registerSolarTracker(sharedItem: mods.eln.generic.SharedItem) {
        val id = 11
        sharedItem.addElement(0 + (id shl 6), SolarTrackerDescriptor("Solar Tracker"))
    }

    private fun registerIngot(sharedItem: mods.eln.generic.SharedItem) {
        val id = 8
        
        val copperIngot = GenericItemUsingDamageDescriptorWithComment("Copper Ingot", arrayOf())
        sharedItem.addElement(1 + (id shl 6), copperIngot)
        OreDictionary.registerOre("ingotCopper", copperIngot.newItemStack())

        val leadIngot = GenericItemUsingDamageDescriptorWithComment("Lead Ingot", arrayOf())
        sharedItem.addElement(4 + (id shl 6), leadIngot)
        OreDictionary.registerOre("ingotLead", leadIngot.newItemStack())

        val tungstenIngot = GenericItemUsingDamageDescriptorWithComment("Tungsten Ingot", arrayOf())
        sharedItem.addElement(5 + (id shl 6), tungstenIngot)
        OreDictionary.registerOre("ingotTungsten", tungstenIngot.newItemStack())

        val siliconIngot = GenericItemUsingDamageDescriptorWithComment("Silicon Ingot", arrayOf())
        sharedItem.addElement(7 + (id shl 6), siliconIngot)
        OreDictionary.registerOre("ingotSilicon", siliconIngot.newItemStack())

        val alloyIngot = GenericItemUsingDamageDescriptorWithComment("Alloy Ingot", arrayOf())
        sharedItem.addElement(8 + (id shl 6), alloyIngot)
        OreDictionary.registerOre("ingotAlloy", alloyIngot.newItemStack())

        val ferriteIngot = GenericItemUsingDamageDescriptorWithComment("Ferrite Ingot", arrayOf("Useless", "Really useless"))
        sharedItem.addElement(6 + (id shl 6), ferriteIngot)
        OreDictionary.registerOre("ingotFerrite", ferriteIngot.newItemStack())
    }

    private fun registerDust(sharedItem: mods.eln.generic.SharedItem) {
        val id = 9
        
        val copperDust = GenericItemUsingDamageDescriptorWithComment("Copper Dust", arrayOf())
        sharedItem.addElement(1 + (id shl 6), copperDust)
        OreDictionary.registerOre("dustCopper", copperDust.newItemStack())

        val ironDust = GenericItemUsingDamageDescriptorWithComment("Iron Dust", arrayOf())
        sharedItem.addElement(2 + (id shl 6), ironDust)
        OreDictionary.registerOre("dustIron", ironDust.newItemStack())

        val leadDust = GenericItemUsingDamageDescriptorWithComment("Lead Dust", arrayOf())
        sharedItem.addElement(5 + (id shl 6), leadDust)
        OreDictionary.registerOre("dustLead", leadDust.newItemStack())

        val tungstenDust = GenericItemUsingDamageDescriptorWithComment("Tungsten Dust", arrayOf())
        sharedItem.addElement(6 + (id shl 6), tungstenDust)
        OreDictionary.registerOre("dustTungsten", tungstenDust.newItemStack())

        val goldDust = GenericItemUsingDamageDescriptorWithComment("Gold Dust", arrayOf())
        sharedItem.addElement(7 + (id shl 6), goldDust)
        OreDictionary.registerOre("dustGold", goldDust.newItemStack())

        val coalDust = GenericItemUsingDamageDescriptorWithComment("Coal Dust", arrayOf())
        sharedItem.addElement(8 + (id shl 6), coalDust)
        OreDictionary.registerOre("dustCoal", coalDust.newItemStack())

        val siliconDust = GenericItemUsingDamageDescriptorWithComment("Silicon Dust", arrayOf())
        sharedItem.addElement(10 + (id shl 6), siliconDust)
        OreDictionary.registerOre("dustSilicon", siliconDust.newItemStack())

        val alloyDust = GenericItemUsingDamageDescriptorWithComment("Alloy Dust", arrayOf())
        sharedItem.addElement(11 + (id shl 6), alloyDust)
        OreDictionary.registerOre("dustAlloy", alloyDust.newItemStack())

        val cinnabarDust = GenericItemUsingDamageDescriptorWithComment("Cinnabar Dust", arrayOf())
        sharedItem.addElement(12 + (id shl 6), cinnabarDust)
        OreDictionary.registerOre("dustCinnabar", cinnabarDust.newItemStack())
    }

    private fun registerTreeResinAndRubber(sharedItem: mods.eln.generic.SharedItem) {
        val id = 64
        // Tree Resin (subId=0)
        treeResin = TreeResin("Tree Resin")
        sharedItem.addElement(0 + (id shl 6), treeResin)
        OreDictionary.registerOre("materialResin", treeResin.newItemStack())

        // Rubber (subId=1)
        val rubber = GenericItemUsingDamageDescriptor("Rubber")
        sharedItem.addElement(1 + (id shl 6), rubber)
        OreDictionary.registerOre("itemRubber", rubber.newItemStack())
    }

    private fun registerElectricalDrill(sharedItem: mods.eln.generic.SharedItem) {
        val id = 15
        sharedItem.addElement(0 + (id shl 6), ElectricalDrillDescriptor("Cheap Electrical Drill", 8.0, 4000.0))
        sharedItem.addElement(1 + (id shl 6), ElectricalDrillDescriptor("Average Electrical Drill", 5.0, 5000.0))
        sharedItem.addElement(2 + (id shl 6), ElectricalDrillDescriptor("Fast Electrical Drill", 3.0, 6000.0))
    }

    private fun registerOreScanner(sharedItem: mods.eln.generic.SharedItem) {
        val id = 16
        sharedItem.addElement(0 + (id shl 6), OreScanner("Ore Scanner"))
    }

    private fun registerMiningPipe(sharedItem: mods.eln.generic.SharedItem) {
        val id = 17
        Eln.miningPipeDescriptor = MiningPipeDescriptor("Mining Pipe")
        Eln.miningPipeDescriptor.setDefaultIcon("miningpipe")
        sharedItem.addElement(0 + (id shl 6), Eln.miningPipeDescriptor)
    }

    private fun registerRawCable(sharedItem: mods.eln.generic.SharedItem) {
        val id = 65
        val copperCable = CopperCableDescriptor("Copper Cable")
        sharedItem.addElement(0 + (id shl 6), copperCable)
        
        val ironCable = GenericItemUsingDamageDescriptor("Iron Cable")
        sharedItem.addElement(1 + (id shl 6), ironCable)
        
        val tungstenCable = GenericItemUsingDamageDescriptor("Tungsten Cable")
        sharedItem.addElement(2 + (id shl 6), tungstenCable)
    }

    private fun registerElectricalTool(sharedItem: mods.eln.generic.SharedItem) {
        val id = 121
        sharedItem.addElement(0 + (id shl 6), ElectricalLampItem("Small Flashlight", 10, 6, 20.0, 12, 8, 50.0, 6000.0, 100.0))
        sharedItem.addElement(1 + (id shl 6), ElectricalLampItem("Improved Flashlight", 15, 8, 20.0, 15, 12, 50.0, 24000.0, 400.0))
        sharedItem.addElement(8 + (id shl 6), ElectricalPickaxe("Portable Electrical Mining Drill", 8f, 3f, 40000.0, 200.0, 800.0))
        sharedItem.addElement(12 + (id shl 6), ElectricalAxe("Portable Electrical Axe", 8f, 3f, 40000.0, 200.0, 800.0))
    }

    private fun registerPortableItem(sharedItem: mods.eln.generic.SharedItem) {
        val id = 122
        sharedItem.addElement(0 + (id shl 6), BatteryItem("Portable Battery", 20000.0, 500.0, 100.0, 2))
        sharedItem.addElement(1 + (id shl 6), BatteryItem("Portable Battery Pack", 60000.0, 1500.0, 300.0, 2))
        sharedItem.addElement(16 + (id shl 6), BatteryItem("Portable Condensator", 5000.0, 2000.0, 500.0, 1))
        sharedItem.addElement(17 + (id shl 6), BatteryItem("Portable Condensator Pack", 15000.0, 6000.0, 1500.0, 1))
        
        val xRayScanner = PortableOreScannerItem("X-Ray Scanner", Eln.obj.getObj("XRayScanner"), 10000.0, 400.0, 300.0, 16f, (Math.PI / 2).toFloat(), 32, 20)
        sharedItem.addElement(32 + (id shl 6), xRayScanner)
    }

    private fun registerFuelBurnerItem(sharedItem: mods.eln.generic.SharedItem) {
        val id = 124
        val factor = 1.0 // fuelHeatFurnacePowerFactor
        sharedItem.addElement(0 + (id shl 6), FuelBurnerDescriptor("Small Fuel Burner", 5000.0 * factor, 2, 1.6f))
        sharedItem.addElement(1 + (id shl 6), FuelBurnerDescriptor("Medium Fuel Burner", 10000.0 * factor, 1, 1.4f))
        sharedItem.addElement(2 + (id shl 6), FuelBurnerDescriptor("Big Fuel Burner", 25000.0 * factor, 0, 1f))
    }

    private fun registerMiscItem(sharedItem: mods.eln.generic.SharedItem) {
        val id = 120
        sharedItem.addElement(0 + (id shl 6), GenericItemUsingDamageDescriptorWithComment("Cheap Chip", arrayOf()))
        sharedItem.addElement(1 + (id shl 6), GenericItemUsingDamageDescriptorWithComment("Advanced Chip", arrayOf()))
        sharedItem.addElement(2 + (id shl 6), GenericItemUsingDamageDescriptor("Dielectric"))
        sharedItem.addElement(3 + (id shl 6), GenericItemUsingDamageDescriptor("Mercury"))
    }

    private fun registerRegulatorItem(sharedItem: mods.eln.generic.SharedItem) {
        val id = 3
        sharedItem.addElement(0 + (id shl 6), RegulatorOnOffDescriptor("On/Off Regulator", "onoffregulator", 5.0))
        sharedItem.addElement(1 + (id shl 6), RegulatorAnalogDescriptor("Analogic Regulator", "analogicregulator"))
    }

    private fun registerProtection(sharedItem: mods.eln.generic.SharedItem) {
        val id = 5
        sharedItem.addElement(0 + (id shl 6), OverVoltageProtectionDescriptor("Overvoltage Protection"))
        sharedItem.addElement(1 + (id shl 6), OverHeatingProtectionDescriptor("Overheating Protection"))
    }

    private fun registerBrush(sharedItem: mods.eln.generic.SharedItem) {
        val id = 119
        val names = arrayOf(
            "Black Brush", "Red Brush", "Green Brush", "Brown Brush",
            "Blue Brush", "Purple Brush", "Cyan Brush", "Silver Brush",
            "Gray Brush", "Pink Brush", "Lime Brush", "Yellow Brush",
            "Light Blue Brush", "Magenta Brush", "Orange Brush", "White Brush"
        )
        for (i in 0..15) {
            sharedItem.addElement(i + (id shl 6), BrushDescriptor(names[i]))
        }
    }

    private fun registerLampItems(sharedItem: mods.eln.generic.SharedItem) {
        val lampItemId = 4
        val lightPower = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 15.0, 20.0, 25.0, 30.0, 40.0)
        val lightLevel = DoubleArray(16) { (it + 0.49) / 15.0 }
        val economicPowerFactor = 0.5
        val standardGrowRate = 0.0

        fun addLamp(subId: Int, descriptor: LampDescriptor) {
            sharedItem.addElement(subId + (lampItemId shl 6), descriptor)
        }

        addLamp(0, LampDescriptor(
            "Small 50V Incandescent Light Bulb",
            "small50vincandescentlightbulb",
            LampDescriptor.Type.Incandescent,
            LampSocketType.Douille,
            Cable.LVU,
            lightPower[12],
            lightLevel[12],
            Config.incandescentLampLife,
            standardGrowRate
        ))
        addLamp(1, LampDescriptor(
            "50V Incandescent Light Bulb",
            "50vincandescentlightbulb",
            LampDescriptor.Type.Incandescent,
            LampSocketType.Douille,
            Cable.LVU,
            lightPower[14],
            lightLevel[14],
            Config.incandescentLampLife,
            standardGrowRate
        ))
        addLamp(2, LampDescriptor(
            "200V Incandescent Light Bulb",
            "200vincandescentlightbulb",
            LampDescriptor.Type.Incandescent,
            LampSocketType.Douille,
            Cable.MVU,
            lightPower[14],
            lightLevel[14],
            Config.incandescentLampLife,
            standardGrowRate
        ))
        addLamp(4, LampDescriptor(
            "Small 50V Carbon Incandescent Light Bulb",
            "small50vcarbonincandescentlightbulb",
            LampDescriptor.Type.Incandescent,
            LampSocketType.Douille,
            Cable.LVU,
            lightPower[11],
            lightLevel[11],
            Config.carbonLampLife,
            standardGrowRate
        ))
        addLamp(5, LampDescriptor(
            "50V Carbon Incandescent Light Bulb",
            "50vcarbonincandescentlightbulb",
            LampDescriptor.Type.Incandescent,
            LampSocketType.Douille,
            Cable.LVU,
            lightPower[13],
            lightLevel[13],
            Config.carbonLampLife,
            standardGrowRate
        ))
        addLamp(16, LampDescriptor(
            "Small 50V Economic Light Bulb",
            "small50veconomiclightbulb",
            LampDescriptor.Type.eco,
            LampSocketType.Douille,
            Cable.LVU,
            lightPower[12] * economicPowerFactor,
            lightLevel[12],
            Config.economicLampLife,
            standardGrowRate
        ))
        addLamp(17, LampDescriptor(
            "50V Economic Light Bulb",
            "50veconomiclightbulb",
            LampDescriptor.Type.eco,
            LampSocketType.Douille,
            Cable.LVU,
            lightPower[14] * economicPowerFactor,
            lightLevel[14],
            Config.economicLampLife,
            standardGrowRate
        ))
        addLamp(18, LampDescriptor(
            "200V Economic Light Bulb",
            "200veconomiclightbulb",
            LampDescriptor.Type.eco,
            LampSocketType.Douille,
            Cable.MVU,
            lightPower[14] * economicPowerFactor,
            lightLevel[14],
            Config.economicLampLife,
            standardGrowRate
        ))
        addLamp(32, LampDescriptor(
            "50V Farming Lamp",
            "50vfarminglamp",
            LampDescriptor.Type.Incandescent,
            LampSocketType.Douille,
            Cable.LVU,
            120.0,
            lightLevel[15],
            Config.incandescentLampLife,
            0.50
        ))
        addLamp(36, LampDescriptor(
            "200V Farming Lamp",
            "200vfarminglamp",
            LampDescriptor.Type.Incandescent,
            LampSocketType.Douille,
            Cable.MVU,
            120.0,
            lightLevel[15],
            Config.incandescentLampLife,
            0.50
        ))
        addLamp(37, LampDescriptor(
            "50V LED Bulb",
            "50vledbulb",
            LampDescriptor.Type.LED,
            LampSocketType.Douille,
            Cable.LVU,
            lightPower[14] / 2.0,
            lightLevel[14],
            Config.ledLampLife,
            standardGrowRate
        ))
        addLamp(38, LampDescriptor(
            "200V LED Bulb",
            "200vledbulb",
            LampDescriptor.Type.LED,
            LampSocketType.Douille,
            Cable.MVU,
            lightPower[14] / 2.0,
            lightLevel[14],
            Config.ledLampLife,
            standardGrowRate
        ))
    }
}

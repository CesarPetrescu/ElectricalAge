package mods.eln.init
import java.util.UUID

import mods.eln.Eln
import mods.eln.misc.Utils
import net.minecraftforge.common.config.Config

@Config(modid = Eln.MODID, type = Config.Type.INSTANCE)
object Config {
    /* Sound */
    @Config.RequiresMcRestart
    var maxSoundDistance = 16.0

    /* Balancing */
    @Config.RequiresMcRestart
    var heatTurbinePowerFactor = 1.0

    @Config.RequiresMcRestart
    var solarPanelPowerFactor = 1.0

    @Config.RequiresMcRestart
    var windTurbinePowerFactor = 1.0

    @Config.RequiresMcRestart
    var waterTurbinePowerFactor = 1.0

    @Config.RequiresMcRestart
    var fuelGeneratorPowerFactor = 1.0

    @Config.RequiresMcRestart
    var fuelGeneratorTankCapacityInSeconds = 20.0 * 60.0

    @Config.RequiresMcRestart
    var fuelHeatFurnacePowerFactor = 1.0

    @Config.RequiresMcRestart
    var fuelEnergyContentFactor = 1.0
    val fuelHeatValueFactor get() = fuelEnergyContentFactor * 0.0000675

    var autominerRange = 24

    var plateConversionRatio = 1

    var batteryHalfLifeDays = 2.0
    val stdBatteryHalfLife get() = batteryHalfLifeDays * Utils.minecraftDay

    var batteryCapacityFactor = 1.0


    /* Signals */
    var wirelessTxRange = 32


    /* Compatibility */
    var elnToIc2ConversionRatio = 1.0 / 3.0

    var elnToOcConversionRatio = elnToIc2ConversionRatio / 2.5

    var elnToTeConversionRatio = elnToIc2ConversionRatio * 4.0

    @Config.RequiresMcRestart
    var computerProbeEnable = true

    @Config.RequiresMcRestart
    var elnToOtherEnergyConverterEnable = true

    @Config.RequiresMcRestart
    var oredictChips = true
    val dictCheapChip get() = if (oredictChips) "circuitBasic" else "circuitElnBasic"
    val dictAdvancedChip get() = if (oredictChips) "circuitAdvanced" else "circuitElnAdvanced"


    /* X-ray scanner */
    var scannerRange = 10.0

    @Config.RequiresMcRestart
    var addOtherModOreToXRay = true


    /* Difficulty */
    var explosionEnable = false

    var replicatorSpawn = true

    var replicatorSpawnPerSecondPerPlayer = 1.0 / 120.0

    var wailaEasyMode = false

    @Config.RangeDouble(min = 0.1, max = 4.0)
    var cablePowerFactor = 1.0


    /* Lamps */
    var incandescentLampLifeInHours = 16.0
    val incandescentLampLife get() = incandescentLampLifeInHours * 3600.0

    var economicLampLifeInHours = 64.0
    val economicLampLife get() = economicLampLifeInHours * 3600.0

    var carbonLampLifeInHours = 6.0
    val carbonLampLife get() = carbonLampLifeInHours * 3600.0

    var ledLampLifeInHours = 512.0
    val ledLampLife get() = ledLampLifeInHours * 3600.0

    var ledLampInfiniteLife = false


    /* Map generation */
    @Config.RequiresMcRestart
    var forceOreRegen = false

    @Config.RequiresMcRestart
    var generateCopper = true

    @Config.RequiresMcRestart
    var generateLead = true

    @Config.RequiresMcRestart
    var generateTungsten = true


    /* Modbus */
    @Config.RequiresWorldRestart
    var modbusEnable = false

    @Config.RequiresWorldRestart
    var modbusPort = 1502


    /* Simulation */
    var electricalFrequency = 20.0

    var electricalInterSystemOverSampling = 50

    var thermalFrequency = 400.0


    /* Debugging */
    var debugEnable = false


    /* Analytics */
    @Config.RequiresMcRestart
    var analyticsEnabled = true

    var playerUUID = UUID.randomUUID().toString()
}

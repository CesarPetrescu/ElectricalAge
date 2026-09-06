package mods.eln.init

import mods.eln.Eln
import mods.eln.cable.CableRenderDescriptor
import mods.eln.ghost.GhostGroup
import mods.eln.gridnode.electricalpole.ElectricalPoleDescriptor
import mods.eln.init.Cable.Companion.LVU
import mods.eln.init.Cable.Companion.MVU
import mods.eln.init.Config.fuelGeneratorPowerFactor
import mods.eln.misc.*
import mods.eln.misc.series.SerieEE
import mods.eln.node.six.SixNodeItem
import mods.eln.node.transparent.TransparentNodeItem
import mods.eln.signalinductor.SignalInductorDescriptor
import mods.eln.sim.ThermalLoadInitializer
import mods.eln.sim.ThermalLoadInitializerByPowerDrop
import mods.eln.sixnode.*
import mods.eln.sixnode.TreeResinCollector.TreeResinCollectorDescriptor
import mods.eln.sixnode.batterycharger.BatteryChargerDescriptor
import mods.eln.sixnode.diode.DiodeDescriptor
import mods.eln.sixnode.electricalalarm.ElectricalAlarmDescriptor
import mods.eln.sixnode.electricalbreaker.ElectricalBreakerDescriptor
import mods.eln.sixnode.electricalcable.ElectricalCableDescriptor
import mods.eln.sixnode.electricaldatalogger.ElectricalDataLoggerDescriptor
import mods.eln.sixnode.electricalentitysensor.ElectricalEntitySensorDescriptor
import mods.eln.sixnode.electricalfiredetector.ElectricalFireDetectorDescriptor
import mods.eln.sixnode.electricalgatesource.ElectricalGateSourceDescriptor
import mods.eln.sixnode.electricalgatesource.ElectricalGateSourceRenderObj
import mods.eln.sixnode.electricallightsensor.ElectricalLightSensorDescriptor
import mods.eln.sixnode.electricalredstoneinput.ElectricalRedstoneInputDescriptor
import mods.eln.sixnode.electricalredstoneoutput.ElectricalRedstoneOutputDescriptor
import mods.eln.sixnode.electricalrelay.ElectricalRelayDescriptor
import mods.eln.sixnode.electricalsensor.ElectricalSensorDescriptor
import mods.eln.sixnode.electricalswitch.ElectricalSwitchDescriptor
import mods.eln.sixnode.electricaltimeout.ElectricalTimeoutDescriptor
import mods.eln.sixnode.electricalvumeter.ElectricalVuMeterDescriptor
import mods.eln.sixnode.electricalwatch.ElectricalWatchDescriptor
import mods.eln.sixnode.electricalweathersensor.ElectricalWeatherSensorDescriptor
import mods.eln.sixnode.electricalwindsensor.ElectricalWindSensorDescriptor
import mods.eln.sixnode.energymeter.EnergyMeterDescriptor
import mods.eln.sixnode.groundcable.GroundCableDescriptor
import mods.eln.sixnode.hub.HubDescriptor
import mods.eln.sixnode.lampsocket.LampSocketDescriptor
import mods.eln.sixnode.lampsocket.LampSocketStandardObjRender
import mods.eln.sixnode.lampsocket.LampSocketSuspendedObjRender
import mods.eln.sixnode.lampsocket.LampSocketType
import mods.eln.sixnode.lampsupply.LampSupplyDescriptor
import mods.eln.sixnode.logicgate.LogicGateDescriptor
import mods.eln.sixnode.logicgate.PalDescriptor
import mods.eln.sixnode.modbusrtu.ModbusRtuDescriptor
import mods.eln.sixnode.powercapacitorsix.PowerCapacitorSixDescriptor
import mods.eln.sixnode.powerinductorsix.PowerInductorSixDescriptor
import mods.eln.sixnode.powersocket.PowerSocketDescriptor
import mods.eln.sixnode.resistor.ResistorDescriptor
import mods.eln.sixnode.thermalcable.ThermalCableDescriptor
import mods.eln.sixnode.thermalsensor.ThermalSensorDescriptor
import mods.eln.sixnode.tutorialsign.TutorialSignDescriptor
import mods.eln.sixnode.wirelesssignal.repeater.WirelessSignalRepeaterDescriptor
import mods.eln.sixnode.wirelesssignal.rx.WirelessSignalRxDescriptor
import mods.eln.sixnode.wirelesssignal.source.WirelessSignalSourceDescriptor
import mods.eln.sixnode.wirelesssignal.tx.WirelessSignalTxDescriptor
import mods.eln.transparentnode.FuelGeneratorDescriptor
import mods.eln.transparentnode.FuelHeatFurnaceDescriptor
import mods.eln.transparentnode.autominer.AutoMinerDescriptor
import mods.eln.transparentnode.battery.BatteryDescriptor
import mods.eln.transparentnode.eggincubator.EggIncubatorDescriptor
import mods.eln.transparentnode.electricalantennarx.ElectricalAntennaRxDescriptor
import mods.eln.transparentnode.electricalantennatx.ElectricalAntennaTxDescriptor
import mods.eln.transparentnode.electricalfurnace.ElectricalFurnaceDescriptor
import mods.eln.transparentnode.electricalmachine.CompressorDescriptor
import mods.eln.transparentnode.electricalmachine.MaceratorDescriptor
import mods.eln.transparentnode.electricalmachine.MagnetizerDescriptor
import mods.eln.transparentnode.electricalmachine.PlateMachineDescriptor
import mods.eln.transparentnode.heatfurnace.HeatFurnaceDescriptor
import mods.eln.transparentnode.powercapacitor.PowerCapacitorDescriptor
import mods.eln.transparentnode.powerinductor.PowerInductorDescriptor
import mods.eln.transparentnode.solarpanel.SolarPanelDescriptor
import mods.eln.transparentnode.teleporter.TeleporterDescriptor
import mods.eln.transparentnode.thermaldissipatoractive.ThermalDissipatorActiveDescriptor
import mods.eln.transparentnode.thermaldissipatorpassive.ThermalDissipatorPassiveDescriptor
import mods.eln.transparentnode.transformer.TransformerDescriptor
import mods.eln.transparentnode.turbine.TurbineDescriptor
import mods.eln.transparentnode.turret.TurretDescriptor
import mods.eln.transparentnode.windturbine.WindTurbineDescriptor

/**
 * Central descriptor registration for SixNode and TransparentNode items.
 */
object Descriptors {

    // Recipe lists
    @JvmField
    val maceratorRecipes = RecipesList()
    @JvmField
    val compressorRecipes = RecipesList()
    @JvmField
    val plateMachineRecipes = RecipesList()
    @JvmField
    val magnetizerRecipes = RecipesList()

    // Cable render descriptors (shared)
    lateinit var stdCableRenderSignal: CableRenderDescriptor

    lateinit var stdCableRender50V: CableRenderDescriptor

    lateinit var stdCableRender200V: CableRenderDescriptor

    lateinit var stdCableRender800V: CableRenderDescriptor

    lateinit var stdCableRender3200V: CableRenderDescriptor

    // Cable descriptors
    lateinit var signalDescriptor: ElectricalCableDescriptor

    lateinit var lowVoltageCableDescriptor: ElectricalCableDescriptor

    lateinit var batteryCableDescriptor: ElectricalCableDescriptor

    lateinit var mediumVoltageCableDescriptor: ElectricalCableDescriptor

    lateinit var highVoltageCableDescriptor: ElectricalCableDescriptor

    lateinit var veryHighVoltageCableDescriptor: ElectricalCableDescriptor

    lateinit var thermalCableDescriptor: ThermalCableDescriptor


    /**
     * Initialize all descriptors and add them to SixNodeItem/TransparentNodeItem.
     */
    @JvmStatic
    fun preInit() {
        Eln.logger.info("Registering Electrical Age descriptors...")

        val sixNodeItem = Eln.sixNodeItem
        val transparentNodeItem = Eln.transparentNodeItem

        Eln.logger.info("SixNodeItem: $sixNodeItem, TransparentNodeItem: $transparentNodeItem")

        // Initialize Cable objects
        Cable.battery = Cable()
        Cable.signal = Cable()
        Cable.lowVoltage = Cable()
        Cable.mediumVoltage = Cable()
        Cable.highVoltage = Cable()
        Cable.veryHighVoltage = Cable()

        // ========== SIX NODE REGISTRATION ==========
        registerGround(sixNodeItem, 2)
        registerElectricalSource(sixNodeItem, 3)
        registerElectricalCable(sixNodeItem, 32)
        registerThermalCable(sixNodeItem, 48)
        registerLampSocket(sixNodeItem, 64)
        registerLampSupply(sixNodeItem, 65)
        registerBatteryCharger(sixNodeItem, 66)
        registerPowerSocket(sixNodeItem, 67)

        registerWirelessSignal(sixNodeItem, 92)
        registerElectricalDataLogger(sixNodeItem, 93)
        registerElectricalRelay(sixNodeItem, 94)
        registerElectricalGateSource(sixNodeItem, 95)
        registerPassiveComponent(sixNodeItem, transparentNodeItem, 96)
        registerSwitch(sixNodeItem, 97)
        registerElectricalManager(sixNodeItem, 98)
        registerElectricalSensor(sixNodeItem, 100)
        registerThermalSensor(sixNodeItem, 101)
        registerElectricalVuMeter(sixNodeItem, 102)
        registerElectricalAlarm(sixNodeItem, 103)
        registerElectricalEnvironmentalSensor(sixNodeItem, 104)
        registerElectricalRedstone(sixNodeItem, 108)
        registerElectricalGate(sixNodeItem, 109)
        registerTreeResinCollector(sixNodeItem, 116)
        registerSixNodeMisc(sixNodeItem, 117)
        registerAnalogChips(sixNodeItem, 124)

        // ========== TRANSPARENT NODE REGISTRATION ==========
        registerPowerComponent(transparentNodeItem, 1)
        registerTransformer(transparentNodeItem, 2)
        registerHeatFurnace(transparentNodeItem, 3)
        registerTurbine(transparentNodeItem, 4)
        registerElectricalAntenna(transparentNodeItem, 7)
        registerBattery(transparentNodeItem, 16)
        registerElectricalFurnace(transparentNodeItem, 32)
        registerMacerator(transparentNodeItem, 33)
        registerCompressor(transparentNodeItem, 35)
        registerMagnetizer(transparentNodeItem, 36)
        registerPlateMachine(transparentNodeItem, 37)
        registerEggIncubator(transparentNodeItem, 41)
        registerAutoMiner(transparentNodeItem, 42)
        registerSolarPanel(transparentNodeItem, 48)
        registerWindTurbine(transparentNodeItem, 49)
        registerThermalDissipatorPassiveAndActive(transparentNodeItem, 64)
        registerTransparentNodeMisc(transparentNodeItem, 65)
        registerTurret(transparentNodeItem, 66)
        registerFuelGenerator(transparentNodeItem, 67)
        registerGridDevices(transparentNodeItem, 123)
        registerMechanical(transparentNodeItem, 8)

        Eln.logger.info("SixNodeItem orderList size: ${sixNodeItem.orderList.size}")
        Eln.logger.info("Registered Electrical Age descriptors")
    }

    // ========== SIX NODE REGISTRATION METHODS ==========

    @JvmStatic
    private fun registerGround(sixNodeItem: SixNodeItem, id: Int) {
        // Ground Cable (subId=0)
        val groundObj = Eln.obj.getObj("groundcable")
        val groundDescriptor = GroundCableDescriptor("Ground Cable", groundObj)
        sixNodeItem.addDescriptor(0 + (id shl 6), groundDescriptor)

        // Hub (subId=8)
        val hubObj = Eln.obj.getObj("hub")
        val hubDescriptor = HubDescriptor("Hub", hubObj)
        sixNodeItem.addDescriptor(8 + (id shl 6), hubDescriptor)
    }


    @JvmStatic
    private fun registerElectricalSource(sixNodeItem: SixNodeItem, id: Int) {
        val obj = Eln.obj.getObj("voltagesource")
        val powerSourceDescriptor = mods.eln.sixnode.electricalsource.ElectricalSourceDescriptor("Electrical Source", obj, false)
        powerSourceDescriptor.setDefaultIcon("electricalsource")
        sixNodeItem.addDescriptor(0 + (id shl 6), powerSourceDescriptor)

        val signalSourceDescriptor = mods.eln.sixnode.electricalsource.ElectricalSourceDescriptor("Signal Source", obj, true)
        signalSourceDescriptor.setDefaultIcon("signalsource")
        sixNodeItem.addDescriptor(8 + (id shl 6), signalSourceDescriptor)
    }


    @JvmStatic
    private fun registerElectricalCable(sixNodeItem: SixNodeItem, baseId: Int) {
        // Create render descriptors
        stdCableRenderSignal = CableRenderDescriptor("eln", "sprites/cable.png", 0.95f, 0.95f)
        stdCableRender50V = CableRenderDescriptor("eln", "sprites/cable.png", 1.95f, 0.95f)
        stdCableRender200V = CableRenderDescriptor("eln", "sprites/cable.png", 2.95f, 0.95f)
        stdCableRender800V = CableRenderDescriptor("eln", "sprites/cable.png", 3.95f, 0.95f)
        stdCableRender3200V = CableRenderDescriptor("eln", "sprites/cablevhv.png", 4.95f, 0.95f)

        // Signal Cable
        signalDescriptor = ElectricalCableDescriptor(
            "Signal Cable", stdCableRenderSignal,
            "For signal transmission.", true
        ).apply {
            setPhysicalConstantLikeNormalCable(
                Cable.SVU, Cable.SVP, 0.02 / 50 * Cable.gateOutputCurrent / Cable.SVIinv,
                Cable.SVU * 1.3, Cable.SVP * 1.2,
                0.5,
                Eln.cableWarmLimit, -100.0,
                Eln.cableHeatingTime, 1.0
            )
        }
        sixNodeItem.addDescriptor(0 + (baseId shl 6), signalDescriptor)
        Cable.signal.descriptor = signalDescriptor

        // Low Voltage Cable
        lowVoltageCableDescriptor = ElectricalCableDescriptor(
            "Low Voltage Cable", stdCableRender50V,
            "For low voltage with high current.", false
        ).apply {
            setPhysicalConstantLikeNormalCable(
                Cable.LVU, Cable.LVP(), 0.2 / 20,
                Cable.LVU * 1.3, Cable.LVP() * 1.2,
                20.0,
                Eln.cableWarmLimit, -100.0,
                Eln.cableHeatingTime, Eln.cableThermalConductionTao
            )
        }
        sixNodeItem.addDescriptor(4 + (baseId shl 6), lowVoltageCableDescriptor)
        Cable.lowVoltage.descriptor = lowVoltageCableDescriptor

        // Battery Cable
        batteryCableDescriptor = ElectricalCableDescriptor(
            "Low Voltage Cable", stdCableRender50V,
            "For low voltage with high current.", false
        ).apply {
            setPhysicalConstantLikeNormalCable(
                Cable.LVU, Cable.LVP() / 4, 0.2 / 20,
                Cable.LVU * 1.3, Cable.LVP() * 1.2,
                20.0,
                Eln.cableWarmLimit, -100.0,
                Eln.cableHeatingTime, Eln.cableThermalConductionTao
            )
        }
        //sixNodeItem.addDescriptor(8 + (baseId shl 6), batteryCableDescriptor) // internally used for batteries
        Cable.battery.descriptor = batteryCableDescriptor

        // Medium Voltage Cable
        mediumVoltageCableDescriptor = ElectricalCableDescriptor(
            "Medium Voltage Cable", stdCableRender200V,
            "For medium voltage distribution.", false
        ).apply {
            setPhysicalConstantLikeNormalCable(
                Cable.MVU, Cable.MVP(), 0.10 / 20,
                Cable.MVU * 1.3, Cable.MVP() * 1.2,
                20.0,
                Eln.cableWarmLimit, -100.0,
                Eln.cableHeatingTime, Eln.cableThermalConductionTao
            )
        }
        sixNodeItem.addDescriptor(12 + (baseId shl 6), mediumVoltageCableDescriptor)
        Cable.mediumVoltage.descriptor = mediumVoltageCableDescriptor

        // High Voltage Cable
        highVoltageCableDescriptor = ElectricalCableDescriptor(
            "High Voltage Cable", stdCableRender800V,
            "For high voltage transmission.", false
        ).apply {
            setPhysicalConstantLikeNormalCable(
                Cable.HVU, Cable.HVP(), 0.05 / 20,
                Cable.HVU * 1.3, Cable.HVP() * 1.2,
                20.0,
                Eln.cableWarmLimit, -100.0,
                Eln.cableHeatingTime, Eln.cableThermalConductionTao
            )
        }
        sixNodeItem.addDescriptor(16 + (baseId shl 6), highVoltageCableDescriptor)
        Cable.highVoltage.descriptor = highVoltageCableDescriptor

        // Very High Voltage Cable
        veryHighVoltageCableDescriptor = ElectricalCableDescriptor(
            "Very High Voltage Cable", stdCableRender3200V,
            "For very high voltage transmission.", false
        ).apply {
            setPhysicalConstantLikeNormalCable(
                Cable.VHVU, Cable.VHVP(), 0.02 / 20,
                Cable.VHVU * 1.3, Cable.VHVP() * 1.2,
                20.0,
                Eln.cableWarmLimit, -100.0,
                Eln.cableHeatingTime, Eln.cableThermalConductionTao
            )
        }
        sixNodeItem.addDescriptor(20 + (baseId shl 6), veryHighVoltageCableDescriptor)
        Cable.veryHighVoltage.descriptor = veryHighVoltageCableDescriptor
    }

    @JvmStatic
    private fun registerThermalCable(sixNodeItem: SixNodeItem, id: Int) {
        // Standard Thermal Cable
        thermalCableDescriptor = ThermalCableDescriptor(
            "Copper Thermal Cable",
            130.0, -100.0, 25.0, 100.0, 5.0, 10.0, 0.5,
            CableRenderDescriptor("eln", "sprites/thermalCable.png", 4.0f, 4.0f),
            "Transfers heat between nodes."
        )
        thermalCableDescriptor.setDefaultIcon("thermalcable")
        sixNodeItem.addDescriptor(0 + (id shl 6), thermalCableDescriptor)
    }

    @JvmStatic
    private fun registerLampSocket(sixNodeItem: SixNodeItem, id: Int) {
        val classicLampSocket = Eln.obj.getObj("classiclampsocket")
        val robustLamp = Eln.obj.getObj("robustlamp")
        val flatLamp = Eln.obj.getObj("flatlamp")
        val simpleLamp = Eln.obj.getObj("simplelamp")
        val fluorescentLamp = Eln.obj.getObj("fluorescentlamp")
        val streetLight = Eln.obj.getObj("streetlight")
        val sconceLamp = Eln.obj.getObj("sconcelamp")
        val suspendedLamp = Eln.obj.getObj("robustlampsuspended")
        val emergencyLamp = Eln.obj.getObj("emergencyexitlighting")

        val lampSocketA = LampSocketDescriptor(
            "Lamp Socket A",
            LampSocketStandardObjRender(classicLampSocket, false),
            LampSocketType.Douille,
            false, 4, 0f, 0f, 0f
        )
        sixNodeItem.addDescriptor(0 + (id shl 6), lampSocketA)

        val lampSocketProjector = LampSocketDescriptor(
            "Lamp Socket B Projector",
            LampSocketStandardObjRender(classicLampSocket, false),
            LampSocketType.Douille,
            false, 10, -90f, 90f, 0f
        )
        sixNodeItem.addDescriptor(1 + (id shl 6), lampSocketProjector)

        val robustLampSocket = LampSocketDescriptor(
            "Robust Lamp Socket",
            LampSocketStandardObjRender(robustLamp, true),
            LampSocketType.Douille,
            false, 3, 0f, 0f, 0f
        )
        robustLampSocket.setInitialOrientation(-90f)
        sixNodeItem.addDescriptor(4 + (id shl 6), robustLampSocket)

        val flatLampSocket = LampSocketDescriptor(
            "Flat Lamp Socket",
            LampSocketStandardObjRender(flatLamp, true),
            LampSocketType.Douille,
            false, 3, 0f, 0f, 0f
        )
        sixNodeItem.addDescriptor(5 + (id shl 6), flatLampSocket)

        val simpleLampSocket = LampSocketDescriptor(
            "Simple Lamp Socket",
            LampSocketStandardObjRender(simpleLamp, true),
            LampSocketType.Douille,
            false, 3, 0f, 0f, 0f
        )
        sixNodeItem.addDescriptor(6 + (id shl 6), simpleLampSocket)

        val fluorescentLampSocket = LampSocketDescriptor(
            "Fluorescent Lamp Socket",
            LampSocketStandardObjRender(fluorescentLamp, true),
            LampSocketType.Douille,
            false, 4, 0f, 0f, 0f
        )
        fluorescentLampSocket.cableLeft = false
        fluorescentLampSocket.cableRight = false
        sixNodeItem.addDescriptor(7 + (id shl 6), fluorescentLampSocket)

        val streetLightSocket = LampSocketDescriptor(
            "Street Light",
            LampSocketStandardObjRender(streetLight, true),
            LampSocketType.Douille,
            false, 0, 0f, 0f, 0f
        )
        streetLightSocket.setPlaceDirection(Direction.YN)
        val streetLightGhost = GhostGroup()
        streetLightGhost.addElement(1, 0, 0)
        streetLightGhost.addElement(2, 0, 0)
        streetLightSocket.setGhostGroup(streetLightGhost)
        streetLightSocket.cameraOpt = false
        sixNodeItem.addDescriptor(8 + (id shl 6), streetLightSocket)

        val sconceLampSocket = LampSocketDescriptor(
            "Sconce Lamp Socket",
            LampSocketStandardObjRender(sconceLamp, false),
            LampSocketType.Douille,
            true, 3, 0f, 0f, 0f
        )
        sconceLampSocket.setPlaceDirection(arrayOf(Direction.XP, Direction.XN, Direction.ZP, Direction.ZN))
        sconceLampSocket.setInitialOrientation(-90f)
        sconceLampSocket.setUserRotationLibertyDegrees(true)
        sixNodeItem.addDescriptor(9 + (id shl 6), sconceLampSocket)

        val suspendedLampSocket = LampSocketDescriptor(
            "Suspended Lamp Socket",
            LampSocketSuspendedObjRender(suspendedLamp, true, 3),
            LampSocketType.Douille,
            false, 3, 0f, 0f, 0f
        )
        suspendedLampSocket.setPlaceDirection(Direction.YP)
        suspendedLampSocket.cameraOpt = false
        sixNodeItem.addDescriptor(12 + (id shl 6), suspendedLampSocket)

        val longSuspendedLampSocket = LampSocketDescriptor(
            "Long Suspended Lamp Socket",
            LampSocketSuspendedObjRender(suspendedLamp, true, 4),
            LampSocketType.Douille,
            false, 4, 0f, 0f, 0f
        )
        longSuspendedLampSocket.setPlaceDirection(Direction.YP)
        longSuspendedLampSocket.cameraOpt = false
        sixNodeItem.addDescriptor(13 + (id shl 6), longSuspendedLampSocket)

        val emergencyLamp50V = EmergencyLampDescriptor(
                "50V Emergency Lamp",
                lowVoltageCableDescriptor,
                10.0 * 60.0 * 10.0,
                10.0,
                5.0,
                6,
                emergencyLamp
            )
        sixNodeItem.addDescriptor(15 + (id shl 6), emergencyLamp50V)

        val emergencyLamp200V = EmergencyLampDescriptor(
                "200V Emergency Lamp",
                mediumVoltageCableDescriptor,
                10.0 * 60.0 * 20.0,
                25.0,
                10.0,
                8,
                emergencyLamp
            )
        sixNodeItem.addDescriptor(16 + (id shl 6), emergencyLamp200V)
    }

    @JvmStatic
    private fun registerLampSupply(sixNodeItem: SixNodeItem, id: Int) {
        val obj = Eln.obj.getObj("distributionboard")
        val lampSupplyDescriptor = LampSupplyDescriptor("Lamp Supply", obj, 32)
        sixNodeItem.addDescriptor(0 + (id shl 6), lampSupplyDescriptor)
    }

    @JvmStatic
    private fun registerBatteryCharger(sixNodeItem: SixNodeItem, id: Int) {
        val obj = Eln.obj.getObj("batterychargera")
        
        // Weak 50V Battery Charger (subId=0)
        val weakBatteryChargerDescriptor = BatteryChargerDescriptor(
            "Weak 50V Battery Charger", obj,
            lowVoltageCableDescriptor,
            Cable.LVU, 200.0
        )
        sixNodeItem.addDescriptor(0 + (id shl 6), weakBatteryChargerDescriptor)

        // 50V Battery Charger (subId=1)
        val batteryChargerDescriptor = BatteryChargerDescriptor(
            "50V Battery Charger", obj,
            lowVoltageCableDescriptor,
            Cable.LVU, 400.0
        )
        sixNodeItem.addDescriptor(1 + (id shl 6), batteryChargerDescriptor)

        // 200V Battery Charger (subId=4)
        val mvBatteryChargerDescriptor = BatteryChargerDescriptor(
            "200V Battery Charger", obj,
            mediumVoltageCableDescriptor,
            Cable.MVU, 1000.0
        )
        sixNodeItem.addDescriptor(4 + (id shl 6), mvBatteryChargerDescriptor)
    }

    @JvmStatic
    private fun registerPowerSocket(sixNodeItem: SixNodeItem, id: Int) {
        val obj = Eln.obj.getObj("powersocket")
        // LV Power Socket (50V)
        val powerSocketLV = PowerSocketDescriptor(1, "50V Power Socket", obj, 10)
        powerSocketLV.setDefaultIcon("powersocketlv")
        sixNodeItem.addDescriptor(1 + (id shl 6), powerSocketLV)

        // MV Power Socket (200V)
        val powerSocketMV = PowerSocketDescriptor(2, "200V Power Socket", obj, 10)
        powerSocketMV.setDefaultIcon("powersocketmv")
        sixNodeItem.addDescriptor(2 + (id shl 6), powerSocketMV)
    }

    @JvmStatic
    private fun registerWirelessSignal(sixNodeItem: SixNodeItem, id: Int) {
        // Wireless Signal Receiver (subId=0)
        val rxObj = Eln.obj.getObj("wirelesssignalrx")
        val wirelessRxDescriptor = WirelessSignalRxDescriptor("Wireless Signal Receiver", rxObj)
        sixNodeItem.addDescriptor(0 + (id shl 6), wirelessRxDescriptor)

        // Wireless Signal Transmitter (subId=8)
        val txObj = Eln.obj.getObj("wirelesssignaltx")
        val wirelessTxDescriptor = WirelessSignalTxDescriptor("Wireless Signal Transmitter", txObj, 32)
        sixNodeItem.addDescriptor(8 + (id shl 6), wirelessTxDescriptor)

        // Wireless Signal Repeater (subId=16)
        val repeaterObj = Eln.obj.getObj("wirelesssignalrepeater")
        val wirelessRepeaterDescriptor = WirelessSignalRepeaterDescriptor(
            "Wireless Signal Repeater", repeaterObj, 32
        )
        sixNodeItem.addDescriptor(16 + (id shl 6), wirelessRepeaterDescriptor)
    }

    @JvmStatic
    private fun registerElectricalDataLogger(sixNodeItem: SixNodeItem, id: Int) {
        // Data Logger (subId=0)
        val dataLoggerDescriptor = ElectricalDataLoggerDescriptor(
            "Data Logger", true,
            "DataloggerCRTFloor", 1.0f, 0.5f, 0.0f, "\u00A76"
        )
        sixNodeItem.addDescriptor(0 + (id shl 6), dataLoggerDescriptor)

        // Modern Data Logger (subId=1)
        val modernDataLoggerDescriptor = ElectricalDataLoggerDescriptor(
            "Modern Data Logger", false,
            "FlatScreenMonitor", 0.0f, 1.0f, 0.0f, "\u00A7a"
        )
        sixNodeItem.addDescriptor(1 + (id shl 6), modernDataLoggerDescriptor)

        // Industrial Data Logger (subId=2)
        val industrialDataLoggerDescriptor = ElectricalDataLoggerDescriptor(
            "Industrial Data Logger", false,
            "IndustrialPanel", 0.0f, 0.0f, 1.0f, "\u00A7c"
        )
        sixNodeItem.addDescriptor(2 + (id shl 6), industrialDataLoggerDescriptor)
    }

    @JvmStatic
    private fun registerElectricalRelay(sixNodeItem: SixNodeItem, id: Int) {
        // Low Voltage Relay (subId=0)
        val lvRelayObj = Eln.obj.getObj("RelayBig")
        val lvRelayDescriptor = ElectricalRelayDescriptor(
            "Low Voltage Relay", lvRelayObj,
            lowVoltageCableDescriptor
        )
        sixNodeItem.addDescriptor(0 + (id shl 6), lvRelayDescriptor)

        // Medium Voltage Relay (subId=1)
        val mvRelayDescriptor = ElectricalRelayDescriptor(
            "Medium Voltage Relay", lvRelayObj,
            mediumVoltageCableDescriptor
        )
        sixNodeItem.addDescriptor(1 + (id shl 6), mvRelayDescriptor)

        // High Voltage Relay (subId=2)
        val hvRelayObj = Eln.obj.getObj("relay800")
        val hvRelayDescriptor = ElectricalRelayDescriptor(
            "High Voltage Relay", hvRelayObj,
            highVoltageCableDescriptor
        )
        sixNodeItem.addDescriptor(2 + (id shl 6), hvRelayDescriptor)

        // Very High Voltage Relay (subId=3)
        val vhvRelayDescriptor = ElectricalRelayDescriptor(
            "Very High Voltage Relay", hvRelayObj,
            veryHighVoltageCableDescriptor
        )
        sixNodeItem.addDescriptor(3 + (id shl 6), vhvRelayDescriptor)

        // Signal Relay (subId=4)
        val signalRelayObj = Eln.obj.getObj("RelaySmall")
        val signalRelayDescriptor = ElectricalRelayDescriptor(
            "Signal Relay", signalRelayObj,
            signalDescriptor
        )
        sixNodeItem.addDescriptor(4 + (id shl 6), signalRelayDescriptor)
    }

    @JvmStatic
    private fun registerElectricalGateSource(sixNodeItem: SixNodeItem, id: Int) {
        val signalsourcepotObj = Eln.obj.getObj("signalsourcepot")
        val signalsourcepotRender = ElectricalGateSourceRenderObj(signalsourcepotObj)
        val ledswitchObj = Eln.obj.getObj("ledswitch")
        val ledswitchRender = ElectricalGateSourceRenderObj(ledswitchObj)

        // Signal Trimmer (subId=0)
        val signalTrimmerDescriptor = ElectricalGateSourceDescriptor(
            "Signal Trimmer", signalsourcepotRender, false, "trimmer"
        )
        sixNodeItem.addDescriptor(0 + (id shl 6), signalTrimmerDescriptor)

        // Signal Switch (subId=1)
        val signalSwitchDescriptor = ElectricalGateSourceDescriptor(
            "Signal Switch", ledswitchRender, true,
            if (Eln.noSymbols) "signalswitch" else "switch"
        )
        sixNodeItem.addDescriptor(1 + (id shl 6), signalSwitchDescriptor)

        // Signal Button (subId=8)
        val signalButtonDescriptor = ElectricalGateSourceDescriptor(
            "Signal Button", ledswitchRender, true, "button"
        )
        signalButtonDescriptor.setWithAutoReset()
        sixNodeItem.addDescriptor(8 + (id shl 6), signalButtonDescriptor)

        // Wireless Button (subId=12)
        val wirelessButtonDescriptor = WirelessSignalSourceDescriptor(
            "Wireless Button", ledswitchRender, 32, true
        )
        wirelessButtonDescriptor.setDefaultIcon("wirelessbutton")
        sixNodeItem.addDescriptor(12 + (id shl 6), wirelessButtonDescriptor)

        // Wireless Switch (subId=16)
        val wirelessSwitchDescriptor = WirelessSignalSourceDescriptor(
            "Wireless Switch", ledswitchRender, 32, false
        )
        wirelessSwitchDescriptor.setDefaultIcon("wirelessswitch")
        sixNodeItem.addDescriptor(16 + (id shl 6), wirelessSwitchDescriptor)
    }

    @JvmStatic
    private fun registerPassiveComponent(sixNodeItem: SixNodeItem, transparentNodeItem: TransparentNodeItem, id: Int) {
        // Resistor - uses PowerElectricPrimitives model (subId=0)
        val resistorObj = Eln.obj.getObj("powerelectricprimitives")
        val resistorSeries = SerieEE(1.0, doubleArrayOf(10.0, 100.0, 1000.0, 10000.0, 100000.0, 1000000.0))
        val resistorDescriptor = ResistorDescriptor(
            "Resistor", resistorObj, resistorSeries, 0.0, false
        )
        sixNodeItem.addDescriptor(0 + (id shl 6), resistorDescriptor)

        // 10A Diode (subId=1)
        val diodeObj = Eln.obj.getObj("powerelectricprimitives")
        val diode10AFunction = mods.eln.misc.IFunction { v -> if (v > 0.7) (v - 0.7) / 0.01 else 0.0 }
        val diode10ADescriptor = DiodeDescriptor(
            "10A Diode", diode10AFunction, 10.0, 1.0, 10.0,
            Eln.cableThermalLoadInitializer, lowVoltageCableDescriptor, diodeObj
        )
        sixNodeItem.addDescriptor(1 + (id shl 6), diode10ADescriptor)

        // 25A Diode (subId=2)
        val diode25AFunction = mods.eln.misc.IFunction { v -> if (v > 0.7) (v - 0.7) / 0.01 else 0.0 }
        val diode25ADescriptor = DiodeDescriptor(
            "25A Diode", diode25AFunction, 25.0, 1.0, 25.0,
            Eln.cableThermalLoadInitializer, lowVoltageCableDescriptor, diodeObj
        )
        sixNodeItem.addDescriptor(2 + (id shl 6), diode25ADescriptor)

        // Signal Diode (subId=8)
        val signalDiodeBaseFunction = mods.eln.misc.FunctionTableYProtect(
            doubleArrayOf(0.0, 0.01, 0.03, 0.1, 0.2, 0.4, 0.8, 1.2), 1.0,
            0.0, 5.0
        )
        val signalDiodeFunction = signalDiodeBaseFunction.duplicate(1.0, 0.1)
        val signalDiodeDescriptor = DiodeDescriptor(
            "Signal Diode", signalDiodeFunction, 0.1, 1.0, 0.1,
            Eln.cableThermalLoadInitializer, signalDescriptor, diodeObj
        )
        sixNodeItem.addDescriptor(8 + (id shl 6), signalDiodeDescriptor)

        // Signal 20H Inductor (subId=16) - uses addWithoutRegistry
        val signalInductorDescriptor = SignalInductorDescriptor(
            "Signal 20H Inductor", 20.0, lowVoltageCableDescriptor
        )
        signalInductorDescriptor.setDefaultIcon("empty-texture")
        sixNodeItem.addWithoutRegistry(16 + (id shl 6), signalInductorDescriptor)

        // Power Capacitor (subId=32)
        val capacitorObj = Eln.obj.getObj("powerelectricprimitives")
        val capacitorSeries = SerieEE.newE6(-1.0)
        val capacitorDescriptor = PowerCapacitorSixDescriptor(
            "Power Capacitor", capacitorObj, capacitorSeries, 60.0 * 2000.0
        )
        sixNodeItem.addDescriptor(32 + (id shl 6), capacitorDescriptor)

        // Power Inductor (subId=34)
        val inductorObj = Eln.obj.getObj("powerelectricprimitives")
        val inductorSeries = SerieEE.newE6(-1.0)
        val inductorDescriptor = PowerInductorSixDescriptor(
            "Power Inductor", inductorObj, inductorSeries
        )
        sixNodeItem.addDescriptor(34 + (id shl 6), inductorDescriptor)

        // Rheostat (subId=37)
        val rheostatDescriptor = ResistorDescriptor(
            "Rheostat", resistorObj, SerieEE.newE12(-2.0), 0.0, true
        )
        sixNodeItem.addDescriptor(37 + (id shl 6), rheostatDescriptor)

        // Thermistor (subId=38)
        val thermistorDescriptor = ResistorDescriptor(
            "Thermistor", resistorObj, SerieEE.newE12(-2.0), -0.01, false
        )
        sixNodeItem.addDescriptor(38 + (id shl 6), thermistorDescriptor)


        // Large Rheostat (subId=39)
        val largeRheostatName = "Large Rheostat"
        val largeRheostatDissipator = ThermalDissipatorPassiveDescriptor(
            largeRheostatName,
            Eln.obj.getObj("LargeRheostat"),
            1000.0, -100.0,
            4000.0, 800.0,
            10.0, 1.0
        )
        val largeRheostat = mods.eln.transparentnode.LargeRheostatDescriptor(
            largeRheostatName, largeRheostatDissipator, veryHighVoltageCableDescriptor, SerieEE.newE12(0.0)
        )
        transparentNodeItem.addDescriptor(39 + (id shl 6), largeRheostat)
    }

    @JvmStatic
    private fun registerSwitch(sixNodeItem: SixNodeItem, id: Int) {
        // Very High Voltage Switch (subId=4) - 3200V
        val hvSwitchObj = Eln.obj.getObj("HighVoltageSwitch")
        val vhvSwitchDescriptor = ElectricalSwitchDescriptor(
            "Very High Voltage Switch",
            stdCableRender3200V, hvSwitchObj,
            Cable.VHVU, Cable.VHVP(), veryHighVoltageCableDescriptor.electricalRs * 2,
            Cable.VHVU * 1.5, Cable.VHVP() * 1.2,
            Eln.cableThermalLoadInitializer, false
        )
        vhvSwitchDescriptor.setDefaultIcon("veryhighvoltageswitch")
        sixNodeItem.addDescriptor(4 + (id shl 6), vhvSwitchDescriptor)

        // High Voltage Switch (subId=0) - 800V
        val hvSwitchDescriptor = ElectricalSwitchDescriptor(
            "High Voltage Switch",
            stdCableRender800V, hvSwitchObj,
            Cable.HVU, Cable.HVP(), highVoltageCableDescriptor.electricalRs * 2,
            Cable.HVU * 1.5, Cable.HVP() * 1.2,
            Eln.cableThermalLoadInitializer, false
        )
        hvSwitchDescriptor.setDefaultIcon("highvoltageswitch")
        sixNodeItem.addDescriptor(0 + (id shl 6), hvSwitchDescriptor)

        // Low Voltage Switch (subId=1) - 50V
        val lvSwitchObj = Eln.obj.getObj("LowVoltageSwitch")
        val lvSwitchDescriptor = ElectricalSwitchDescriptor(
            "Low Voltage Switch",
            stdCableRender50V, lvSwitchObj,
            Cable.LVU, Cable.LVP(), lowVoltageCableDescriptor.electricalRs * 2,
            Cable.LVU * 1.5, Cable.LVP() * 1.2,
            Eln.cableThermalLoadInitializer, false
        )
        lvSwitchDescriptor.setDefaultIcon("lowvoltageswitch")
        sixNodeItem.addDescriptor(1 + (id shl 6), lvSwitchDescriptor)

        // Medium Voltage Switch (subId=2) - 200V
        val mvSwitchDescriptor = ElectricalSwitchDescriptor(
            "Medium Voltage Switch",
            stdCableRender200V, lvSwitchObj,
            Cable.MVU, Cable.MVP(), mediumVoltageCableDescriptor.electricalRs * 2,
            Cable.MVU * 1.5, Cable.MVP() * 1.2,
            Eln.cableThermalLoadInitializer, false
        )
        mvSwitchDescriptor.setDefaultIcon("mediumvoltageswitch")
        sixNodeItem.addDescriptor(2 + (id shl 6), mvSwitchDescriptor)

        // Signal Switch (subId=3)
        val signalSwitchDescriptor = ElectricalSwitchDescriptor(
            "Signal Switch",
            stdCableRenderSignal, lvSwitchObj,
            Cable.SVU, Cable.SVP, 0.02,
            Cable.SVU * 1.5, Cable.SVP * 1.2,
            Eln.cableThermalLoadInitializer, true
        )
        signalSwitchDescriptor.setDefaultIcon("signalswitch")
        sixNodeItem.addWithoutRegistry(3 + (id shl 6), signalSwitchDescriptor)

        // Signal Switch with LED (subId=8)
        val ledSwitchObj = Eln.obj.getObj("ledswitch")
        val signalSwitchLedDescriptor = ElectricalSwitchDescriptor(
            "Signal Switch with LED",
            stdCableRenderSignal, ledSwitchObj,
            Cable.SVU, Cable.SVP, 0.02,
            Cable.SVU * 1.5, Cable.SVP * 1.2,
            Eln.cableThermalLoadInitializer, true
        )
        signalSwitchLedDescriptor.setDefaultIcon("signalswitch")
        sixNodeItem.addWithoutRegistry(8 + (id shl 6), signalSwitchLedDescriptor)
    }

    @JvmStatic
    private fun registerElectricalManager(sixNodeItem: SixNodeItem, id: Int) {
        // Electrical Breaker (subId=0)
        val breakerObj = Eln.obj.getObj("ElectricalBreaker")
        val breakerDescriptor = ElectricalBreakerDescriptor("Electrical Breaker", breakerObj)
        sixNodeItem.addDescriptor(0 + (id shl 6), breakerDescriptor)

        // Energy Meter (subId=4)
        val energyMeterObj = Eln.obj.getObj("energymeter")
        val energyMeterDescriptor = EnergyMeterDescriptor(
            "Energy Meter", energyMeterObj, 8, 0
        )
        sixNodeItem.addDescriptor(4 + (id shl 6), energyMeterDescriptor)

        // Advanced Energy Meter (subId=5)
        val advancedEnergyMeterObj = Eln.obj.getObj("AdvancedEnergyMeter")
        val advancedEnergyMeterDescriptor = EnergyMeterDescriptor(
            "Advanced Energy Meter", advancedEnergyMeterObj, 7, 8
        )
        sixNodeItem.addDescriptor(5 + (id shl 6), advancedEnergyMeterDescriptor)

        // Electrical Fuse Holder (subId=6)
        val fuseHolderObj = Eln.obj.getObj("ElectricalFuse")
        val fuseHolderDescriptor = mods.eln.sixnode.ElectricalFuseHolderDescriptor("Electrical Fuse Holder", fuseHolderObj)
        sixNodeItem.addDescriptor(6 + (id shl 6), fuseHolderDescriptor)

        // Lead Fuses (registered in sharedItem but count towards IDs here)
        val sharedItem = Eln.sharedItem
        
        // subId 7
        sharedItem.addElement(7 + (id shl 6), mods.eln.item.ElectricalFuseDescriptor("Lead Fuse for low voltage cables", lowVoltageCableDescriptor, fuseHolderObj))
        // subId 8
        sharedItem.addElement(8 + (id shl 6), mods.eln.item.ElectricalFuseDescriptor("Lead Fuse for medium voltage cables", mediumVoltageCableDescriptor, fuseHolderObj))
        // subId 9
        sharedItem.addElement(9 + (id shl 6), mods.eln.item.ElectricalFuseDescriptor("Lead Fuse for high voltage cables", highVoltageCableDescriptor, fuseHolderObj))
        // subId 10
        sharedItem.addElement(10 + (id shl 6), mods.eln.item.ElectricalFuseDescriptor("Lead Fuse for very high voltage cables", veryHighVoltageCableDescriptor, fuseHolderObj))
        // subId 11
        val blownFuse = mods.eln.item.ElectricalFuseDescriptor("Blown Lead Fuse", null, fuseHolderObj)
        mods.eln.item.ElectricalFuseDescriptor.BlownFuse = blownFuse
        sharedItem.addWithoutRegistry(11 + (id shl 6), blownFuse)
    }

    @JvmStatic
    private fun registerElectricalSensor(sixNodeItem: SixNodeItem, id: Int) {
        val sensorDescriptor = ElectricalSensorDescriptor(
            "Electrical Probe", "electricalsensor", false
        )
        sensorDescriptor.setDefaultIcon("electricalprobe")
        sixNodeItem.addDescriptor(0 + (id shl 6), sensorDescriptor)

        val voltageSensorDescriptor = ElectricalSensorDescriptor(
            "Voltage Probe", "voltagesensor", true
        )
        voltageSensorDescriptor.setDefaultIcon("voltageprobe")
        sixNodeItem.addDescriptor(1 + (id shl 6), voltageSensorDescriptor)
    }

    @JvmStatic
    private fun registerThermalSensor(sixNodeItem: SixNodeItem, id: Int) {
        val thermalObj = Eln.obj.getObj("thermalsensor")
        val thermalSensorDescriptor = ThermalSensorDescriptor(
            "Thermal Probe", thermalObj, false
        )
        thermalSensorDescriptor.setDefaultIcon("thermalprobe")
        sixNodeItem.addDescriptor(0 + (id shl 6), thermalSensorDescriptor)

        val tempObj = Eln.obj.getObj("temperaturesensor")
        val tempSensorDescriptor = ThermalSensorDescriptor(
            "Temperature Probe", tempObj, true
        )
        tempSensorDescriptor.setDefaultIcon("temperatureprobe")
        sixNodeItem.addDescriptor(1 + (id shl 6), tempSensorDescriptor)
    }

    @JvmStatic
    private fun registerElectricalVuMeter(sixNodeItem: SixNodeItem, id: Int) {
        val vuMeterDescriptor = ElectricalVuMeterDescriptor(
            "Electrical Vu Meter", "Vumeter", false
        )
        vuMeterDescriptor.setDefaultIcon("electricalvumeter")
        sixNodeItem.addDescriptor(0 + (id shl 6), vuMeterDescriptor)
    }

    @JvmStatic
    private fun registerElectricalAlarm(sixNodeItem: SixNodeItem, id: Int) {
        val obj = Eln.obj.getObj("alarmmedium")
        val alarmDescriptor = ElectricalAlarmDescriptor(
            "Nuclear Alarm", obj, 7, "eln:alarma", 11.0, 1.0f
        )
        sixNodeItem.addDescriptor(0 + (id shl 6), alarmDescriptor)

        val nuclearAlarmDescriptor = ElectricalAlarmDescriptor(
            "Standard Alarm", obj, 7, "eln:smallalarm_critical", 1.2, 2.0f
        )
        sixNodeItem.addDescriptor(1 + (id shl 6), nuclearAlarmDescriptor)
    }

    @JvmStatic
    private fun registerElectricalEnvironmentalSensor(sixNodeItem: SixNodeItem, id: Int) {
        // Weather Sensor
        val weatherObj = Eln.obj.getObj("electricalweathersensor")
        val weatherSensorDescriptor = ElectricalWeatherSensorDescriptor(
            "Electrical Weather Sensor", weatherObj
        )
        weatherSensorDescriptor.setDefaultIcon("weathersensor")
        sixNodeItem.addDescriptor(0 + (id shl 6), weatherSensorDescriptor)

        // Wind Sensor - using Anemometer model
        val windObj = Eln.obj.getObj("anemometer")
        val windSensorDescriptor = ElectricalWindSensorDescriptor(
            "Electrical Anemometer Sensor", windObj, 30.0
        )
        windSensorDescriptor.setDefaultIcon("anemometersensor")
        sixNodeItem.addDescriptor(1 + (id shl 6), windSensorDescriptor)

        // Light Sensor
        val lightObj = Eln.obj.getObj("lightsensor")
        val lightSensorDescriptor = ElectricalLightSensorDescriptor(
            "Electrical Light Sensor", lightObj, false
        )
        lightSensorDescriptor.setDefaultIcon("lightsensor")
        sixNodeItem.addDescriptor(2 + (id shl 6), lightSensorDescriptor)

        // Fire Detector
        val fireObj = Eln.obj.getObj("firedetector")
        val fireDetectorDescriptor = ElectricalFireDetectorDescriptor(
            "Electrical Fire Detector", fireObj, 8.0, false
        )
        fireDetectorDescriptor.setDefaultIcon("firedetector")
        sixNodeItem.addDescriptor(3 + (id shl 6), fireDetectorDescriptor)

        // Entity Sensor - using ProximitySensor model
        val entityObj = Eln.obj.getObj("proximitysensor")
        val entitySensorDescriptor = ElectricalEntitySensorDescriptor(
            "Electrical Entity Sensor", entityObj, 8.0
        )
        entitySensorDescriptor.setDefaultIcon("proximitysensor")
        sixNodeItem.addDescriptor(4 + (id shl 6), entitySensorDescriptor)
    }

    @JvmStatic
    private fun registerElectricalRedstone(sixNodeItem: SixNodeItem, id: Int) {
        // Redstone Input
        val redstoneInputObj = Eln.obj.getObj("redtoele")
        val redstoneInputDescriptor = ElectricalRedstoneInputDescriptor(
            "Redstone-to-Voltage Converter", redstoneInputObj
        )
        redstoneInputDescriptor.setDefaultIcon("redstoneinput")
        sixNodeItem.addDescriptor(0 + (id shl 6), redstoneInputDescriptor)

        // Redstone Output
        val redstoneOutputObj = Eln.obj.getObj("eletored")
        val redstoneOutputDescriptor = ElectricalRedstoneOutputDescriptor(
            "Voltage-to-Redstone Converter", redstoneOutputObj
        )
        redstoneInputDescriptor.setDefaultIcon("redstoneoutput")
        sixNodeItem.addDescriptor(1 + (id shl 6), redstoneOutputDescriptor)
    }

    @JvmStatic
    private fun registerElectricalGate(sixNodeItem: SixNodeItem, id: Int) {
        // Logic Gates - all use LogicGates model
        val logicObj = Eln.obj.getObj("LogicGates")

        // NOT Chip (subId=0)
        val notGateDescriptor = LogicGateDescriptor("NOT Chip", logicObj, "NOT", mods.eln.sixnode.logicgate.Not::class.java)
        sixNodeItem.addDescriptor(0 + (id shl 6), notGateDescriptor)

        // AND Chip (subId=1)
        val andGateDescriptor = LogicGateDescriptor("AND Chip", logicObj, "AND", mods.eln.sixnode.logicgate.And::class.java)
        sixNodeItem.addDescriptor(1 + (id shl 6), andGateDescriptor)

        // NAND Chip (subId=2)
        val nandGateDescriptor = LogicGateDescriptor("NAND Chip", logicObj, "NAND", mods.eln.sixnode.logicgate.Nand::class.java)
        sixNodeItem.addDescriptor(2 + (id shl 6), nandGateDescriptor)

        // OR Chip (subId=3)
        val orGateDescriptor = LogicGateDescriptor("OR Chip", logicObj, "OR", mods.eln.sixnode.logicgate.Or::class.java)
        sixNodeItem.addDescriptor(3 + (id shl 6), orGateDescriptor)

        // NOR Chip (subId=4)
        val norGateDescriptor = LogicGateDescriptor("NOR Chip", logicObj, "NOR", mods.eln.sixnode.logicgate.Nor::class.java)
        sixNodeItem.addDescriptor(4 + (id shl 6), norGateDescriptor)

        // XOR Chip (subId=5)
        val xorGateDescriptor = LogicGateDescriptor("XOR Chip", logicObj, "XOR", mods.eln.sixnode.logicgate.Xor::class.java)
        sixNodeItem.addDescriptor(5 + (id shl 6), xorGateDescriptor)

        // XNOR Chip (subId=6)
        val xnorGateDescriptor = LogicGateDescriptor("XNOR Chip", logicObj, "XNOR", mods.eln.sixnode.logicgate.XNor::class.java)
        sixNodeItem.addDescriptor(6 + (id shl 6), xnorGateDescriptor)

        // PAL Chip (subId=7)
        val palDescriptor = PalDescriptor("PAL Chip", logicObj)
        sixNodeItem.addDescriptor(7 + (id shl 6), palDescriptor)

        // Schmitt Trigger Chip (subId=8)
        val schmittTriggerDescriptor = LogicGateDescriptor("Schmitt Trigger Chip", logicObj, "SCHMITT", mods.eln.sixnode.logicgate.SchmittTrigger::class.java)
        sixNodeItem.addDescriptor(8 + (id shl 6), schmittTriggerDescriptor)

        // D Flip Flop Chip (subId=9)
        val dFlipFlopDescriptor = LogicGateDescriptor("D Flip Flop Chip", logicObj, "DFF", mods.eln.sixnode.logicgate.DFlipFlop::class.java)
        sixNodeItem.addDescriptor(9 + (id shl 6), dFlipFlopDescriptor)

        // Oscillator Chip (subId=10)
        val oscillatorDescriptor = LogicGateDescriptor("Oscillator Chip", logicObj, "OSC", mods.eln.sixnode.logicgate.Oscillator::class.java)
        sixNodeItem.addDescriptor(10 + (id shl 6), oscillatorDescriptor)

        // JK Flip Flop Chip (subId=11)
        val jkFlipFlopDescriptor = LogicGateDescriptor("JK Flip Flop Chip", logicObj, "JKFF", mods.eln.sixnode.logicgate.JKFlipFlop::class.java)
        sixNodeItem.addDescriptor(11 + (id shl 6), jkFlipFlopDescriptor)

        // Electrical Timer (subId=12)
        val timerObj = Eln.obj.getObj("electricaltimer")
        val timerDescriptor = ElectricalTimeoutDescriptor("Electrical Timer", timerObj)
        timerDescriptor.setTickSound("eln:timer", 0.01f)
        sixNodeItem.addDescriptor(12 + (id shl 6), timerDescriptor)

        // Signal Processor (subId=13)
        val plcObj = Eln.obj.getObj("PLC")
        val signalProcessorDescriptor = mods.eln.sixnode.electricalmath.ElectricalMathDescriptor("Signal Processor", plcObj)
        sixNodeItem.addDescriptor(13 + (id shl 6), signalProcessorDescriptor)
    }

    @JvmStatic
    private fun registerLogicalGates(sixNodeItem: SixNodeItem, id: Int) {
        // Logic chips already registered in registerElectricalGate
    }

    @JvmStatic
    private fun registerTreeResinCollector(sixNodeItem: SixNodeItem, id: Int) {
        val obj = Eln.obj.getObj("treeresincolector")
        val desc = TreeResinCollectorDescriptor("Tree Resin Collector", obj)
        sixNodeItem.addDescriptor(0 + (id shl 6), desc)
    }

    @JvmStatic
    private fun registerAnalogChips(sixNodeItem: SixNodeItem, id: Int) {
        val analogObj = Eln.obj.getObj("AnalogChips")

        // OpAmp (subId=0)
        val opAmpDescriptor = AnalogChipDescriptor("OpAmp", analogObj, "OP", OpAmp::class.java)
        sixNodeItem.addDescriptor(0 + (id shl 6), opAmpDescriptor)

        // PID Regulator (subId=1)
        val pidRegulatorDescriptor = AnalogChipDescriptor("PID Regulator", analogObj, "PID",
            PIDRegulator::class.java,
            PIDRegulatorElement::class.java,
            PIDRegulatorRender::class.java)
        sixNodeItem.addDescriptor(1 + (id shl 6), pidRegulatorDescriptor)

        // Voltage controlled sawtooth oscillator (subId=2)
        val vcoSawDescriptor = AnalogChipDescriptor("Voltage controlled sawtooth oscillator", analogObj, "VCO-SAW",
            VoltageControlledSawtoothOscillator::class.java)
        sixNodeItem.addDescriptor(2 + (id shl 6), vcoSawDescriptor)

        // Voltage controlled sine oscillator (subId=3)
        val vcoSinDescriptor = AnalogChipDescriptor("Voltage controlled sine oscillator", analogObj, "VCO-SIN",
            VoltageControlledSineOscillator::class.java)
        sixNodeItem.addDescriptor(3 + (id shl 6), vcoSinDescriptor)

        // Amplifier (subId=4)
        val amplifierDescriptor = AnalogChipDescriptor("Amplifier", analogObj, "AMP",
            Amplifier::class.java,
            AmplifierElement::class.java,
            AmplifierRender::class.java)
        sixNodeItem.addDescriptor(4 + (id shl 6), amplifierDescriptor)

        // Voltage controlled amplifier (subId=5)
        val vcaDescriptor = AnalogChipDescriptor("Voltage controlled amplifier", analogObj, "VCA",
            VoltageControlledAmplifier::class.java)
        sixNodeItem.addDescriptor(5 + (id shl 6), vcaDescriptor)

        // Configurable summing unit (subId=6)
        val summingUnitDescriptor = AnalogChipDescriptor("Configurable summing unit", analogObj, "SUM",
            SummingUnit::class.java,
            SummingUnitElement::class.java,
            SummingUnitRender::class.java)
        sixNodeItem.addDescriptor(6 + (id shl 6), summingUnitDescriptor)

        // Sample and hold (subId=7)
        val sahDescriptor = AnalogChipDescriptor("Sample and hold", analogObj, "SAH",
            SampleAndHold::class.java)
        sixNodeItem.addDescriptor(7 + (id shl 6), sahDescriptor)

        // Lowpass filter (subId=8)
        val lpfDescriptor = AnalogChipDescriptor("Lowpass filter", analogObj, "LPF",
            Filter::class.java,
            FilterElement::class.java,
            FilterRender::class.java)
        sixNodeItem.addDescriptor(8 + (id shl 6), lpfDescriptor)
    }

    @JvmStatic
    private fun registerSixNodeMisc(sixNodeItem: SixNodeItem, id: Int) {
        // Modbus RTU (subId=0)
        val modbusObj = Eln.obj.getObj("rtu")
        val modbusRtuDescriptor = ModbusRtuDescriptor("Modbus RTU", modbusObj)
        // Note: In original, this was only registered if modbusEnable was true
        if (Config.modbusEnable) {
            sixNodeItem.addDescriptor(0 + (id shl 6), modbusRtuDescriptor)
        } else {
            sixNodeItem.addWithoutRegistry(0 + (id shl 6), modbusRtuDescriptor)
        }

        // Analog Watch (subId=4)
        val analogWatchObj = Eln.obj.getObj("WallClock")
        val analogWatchDescriptor = ElectricalWatchDescriptor(
            "Analog Watch", analogWatchObj, 20000.0 / (3600 * 40)
        )
        sixNodeItem.addDescriptor(4 + (id shl 6), analogWatchDescriptor)

        // Digital Watch (subId=5)
        val digitalWatchObj = Eln.obj.getObj("DigitalWallClock")
        val digitalWatchDescriptor = ElectricalWatchDescriptor(
            "Digital Watch", digitalWatchObj, 20000.0 / (3600 * 15)
        )
        sixNodeItem.addDescriptor(5 + (id shl 6), digitalWatchDescriptor)

        // Tutorial Sign (subId=8)
        val tutorialSignObj = Eln.obj.getObj("tutoplate")
        val tutorialSignDescriptor = TutorialSignDescriptor("Tutorial Sign", tutorialSignObj)
        sixNodeItem.addDescriptor(8 + (id shl 6), tutorialSignDescriptor)
    }

    // ========== TRANSPARENT NODE REGISTRATION METHODS ==========

    @JvmStatic
    private fun registerPowerComponent(transparentNodeItem: TransparentNodeItem, id: Int) {
        // Power Capacitor - uses PowerElectricPrimitives model
        val capacitorObj = Eln.obj.getObj("powerelectricprimitives")
        val capacitorSeries = SerieEE(-2.0, doubleArrayOf(1.0, 1.5, 2.2, 3.3, 4.7, 6.8))
        val powerCapacitorDescriptor = PowerCapacitorDescriptor(
            "Power Capacitor", capacitorObj, capacitorSeries, 300.0
        )
        transparentNodeItem.addWithoutRegistry(20 + (id shl 6), powerCapacitorDescriptor)

        // Power Inductor - uses PowerElectricPrimitives model
        val inductorObj = Eln.obj.getObj("powerelectricprimitives")
        val inductorSeries = SerieEE(-1.0, doubleArrayOf(1.0, 1.2, 1.5, 1.8, 2.2, 2.7, 3.3, 3.9, 4.7, 5.6, 6.8, 8.2))
        val powerInductorDescriptor = PowerInductorDescriptor(
            "Power Inductor", inductorObj, inductorSeries
        )
        transparentNodeItem.addWithoutRegistry(16 + (id shl 6), powerInductorDescriptor)
    }

    @JvmStatic
    private fun registerTransformer(transparentNodeItem: TransparentNodeItem, id: Int) {
        val transformerObj = Eln.obj.getObj("transformator")
        val transformerDescriptor = TransformerDescriptor(
            "DC-DC Converter", transformerObj, null, null, 0.1f
        )
        transparentNodeItem.addDescriptor(0 + (id shl 6), transformerDescriptor)
    }

    @JvmStatic
    private fun registerBattery(transparentNodeItem: TransparentNodeItem, id: Int) {
        val voltageFunction = FunctionTable(doubleArrayOf(0.000, 0.9, 1.0, 1.025, 1.04, 1.05, 2.0), 6.0 / 5.0)
        val condoVoltageFunction = FunctionTable(doubleArrayOf(0.000, 0.89, 0.90, 0.905, 0.91, 1.1, 1.5), 6.0 / 5.0)

        val stdDischargeTime = 4.0 * 60.0
        val stdU = Cable.LVU
        val stdP = Cable.LVP() / 4.0
        val stdEfficiency = 1.0 - 2.0 / 50.0
        val condoEfficiency = 1.0 - 2.0 / 50.0
        val heatTime = 30.0
        val batteryCapacityFactor = 1.0
        val stdBatteryHalfLife = 2.0 * Utils.minecraftDay

        // Cost Oriented Battery (subId=0)
        val costBattery = BatteryDescriptor(
            "Cost Oriented Battery", "BatteryBig", batteryCableDescriptor, 0.5, true, true,
            voltageFunction, stdU, stdP * 1.2, 0.0,
            stdP, stdDischargeTime * batteryCapacityFactor, stdEfficiency, stdBatteryHalfLife,
            heatTime, 60.0, -100.0, "Cheap battery"
        ).apply {
            setRenderSpec("lowcost")
            setCurrentDrop(stdU * 1.2, stdP * 1.0)
        }
        transparentNodeItem.addDescriptor(0 + (id shl 6), costBattery)

        // Capacity Oriented Battery (subId=1)
        val capacityBattery = BatteryDescriptor(
            "Capacity Oriented Battery", "BatteryBig", batteryCableDescriptor, 0.5, true, true,
            voltageFunction, stdU / 4.0, stdP / 2.0 * 1.2, 0.0,
            stdP / 2.0, stdDischargeTime * 8.0 * batteryCapacityFactor, stdEfficiency, stdBatteryHalfLife,
            heatTime, 60.0, -100.0, "Large capacity battery"
        ).apply {
            setRenderSpec("capacity")
            setCurrentDrop(stdU / 4.0 * 1.2, stdP / 2.0 * 1.0)
        }
        transparentNodeItem.addDescriptor(1 + (id shl 6), capacityBattery)

        // Voltage Oriented Battery (subId=2)
        val voltageBattery = BatteryDescriptor(
            "Voltage Oriented Battery", "BatteryBig", mediumVoltageCableDescriptor, 0.5, true, true,
            voltageFunction, stdU * 4.0, stdP * 1.2, 0.0,
            stdP, stdDischargeTime * batteryCapacityFactor, stdEfficiency, stdBatteryHalfLife,
            heatTime, 60.0, -100.0, "High voltage battery"
        ).apply {
            setRenderSpec("highvoltage")
            setCurrentDrop(stdU * 4.0 * 1.2, stdP * 1.0)
        }
        transparentNodeItem.addDescriptor(2 + (id shl 6), voltageBattery)

        // Current Oriented Battery (subId=3)
        val currentBattery = BatteryDescriptor(
            "Current Oriented Battery", "BatteryBig", batteryCableDescriptor, 0.5, true, true,
            voltageFunction, stdU, stdP * 1.2 * 4.0, 0.0,
            stdP * 4.0, stdDischargeTime / 6.0 * batteryCapacityFactor, stdEfficiency, stdBatteryHalfLife,
            heatTime, 60.0, -100.0, "High current battery"
        ).apply {
            setRenderSpec("current")
            setCurrentDrop(stdU * 1.2, stdP * 4.0 * 1.0)
        }
        transparentNodeItem.addDescriptor(3 + (id shl 6), currentBattery)

        // Life Oriented Battery (subId=4)
        val lifeBattery = BatteryDescriptor(
            "Life Oriented Battery", "BatteryBig", batteryCableDescriptor, 0.5, true, true,
            voltageFunction, stdU, stdP * 1.2, 0.0,
            stdP, stdDischargeTime * batteryCapacityFactor, stdEfficiency, stdBatteryHalfLife * 8.0,
            heatTime, 60.0, -100.0, "Long life battery"
        ).apply {
            setRenderSpec("life")
            setCurrentDrop(stdU * 1.2, stdP * 1.0)
        }
        transparentNodeItem.addDescriptor(4 + (id shl 6), lifeBattery)

        // Single-use Battery (subId=5)
        val singleUseBattery = BatteryDescriptor(
            "Single-use Battery", "BatteryBig", batteryCableDescriptor, 1.0, false, false,
            voltageFunction, stdU, stdP * 1.2 * 2.0, 0.0,
            stdP * 2.0, stdDischargeTime * batteryCapacityFactor, stdEfficiency, stdBatteryHalfLife * 8.0,
            heatTime, 60.0, -100.0, "Non-rechargeable battery"
        ).apply {
            setRenderSpec("coal")
            setDefaultIcon("singleusebattery")
        }
        transparentNodeItem.addDescriptor(5 + (id shl 6), singleUseBattery)

        // 50V Condensator (subId=32)
        val condensator50V = BatteryDescriptor(
            "50V Condensator", "condo200", batteryCableDescriptor, 0.0, true, false,
            condoVoltageFunction, stdU, stdP * 1.2 * 8.0, 0.005,
            stdP * 8.0, 4.0, condoEfficiency, stdBatteryHalfLife,
            heatTime, 60.0, -100.0, "Low voltage capacitor"
        ).apply {
            setCurrentDrop(stdU * 1.2, stdP * 8.0 * 2.0)
        }
        transparentNodeItem.addWithoutRegistry(32 + (id shl 6), condensator50V)

        // 200V Condensator (subId=36)
        val condensator200V = BatteryDescriptor(
            "200V Condensator", "condo200", highVoltageCableDescriptor, 0.0, true, false,
            condoVoltageFunction, Cable.MVU, Cable.MVP() * 1.5, 0.005,
            Cable.MVP(), 4.0, condoEfficiency, stdBatteryHalfLife,
            heatTime, 60.0, -100.0, "Medium voltage capacitor"
        ).apply {
            setCurrentDrop(Cable.MVU * 1.2, Cable.MVP() * 2.0)
        }
        transparentNodeItem.addWithoutRegistry(36 + (id shl 6), condensator200V)
    }

    @JvmStatic
    private fun registerHeatFurnace(transparentNodeItem: TransparentNodeItem, id: Int) {
        val stoneHeatFurnace = HeatFurnaceDescriptor(
            "Stone Heat Furnace", "stonefurnace",
            1000.0, Utils.getCoalEnergyReference() * 2.0 / 3.0,
            2, 500.0,
            ThermalLoadInitializerByPowerDrop(780.0, -100.0, 10.0, 2.0)
        )
        transparentNodeItem.addDescriptor(0 + (id shl 6), stoneHeatFurnace)

        val fuelHeatFurnace = FuelHeatFurnaceDescriptor(
            "Fuel Heat Furnace", Eln.obj.getObj("fuelheater"),
            ThermalLoadInitializerByPowerDrop(780.0, -100.0, 10.0, 2.0)
        )
        transparentNodeItem.addDescriptor(1 + (id shl 6), fuelHeatFurnace)
    }

    @JvmStatic
    private fun registerTurbine(transparentNodeItem: TransparentNodeItem, id: Int) {
        val TtoU = FunctionTable(doubleArrayOf(0.0, 0.1, 0.85, 1.0, 1.1, 1.15, 1.18, 1.19, 1.25), 8.0 / 5.0)
        val PoutToPin = FunctionTable(doubleArrayOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0, 1.3, 1.8, 2.7), 8.0 / 5.0)

        // 50V Turbine (subId=1)
        val turbine50V = TurbineDescriptor(
            "50V Turbine", "turbineb", stdCableRender50V,
            TtoU.duplicate(250.0, Cable.LVU), PoutToPin.duplicate(300.0, 300.0),
            250.0, Cable.LVU, 300.0, 300.0 / 40.0,
            lowVoltageCableDescriptor.electricalRs * 0.1, 25.0,
            250.0 / 40.0, 300.0 / (Cable.LVU / 25.0), "eln:heat_turbine_50v"
        )
        transparentNodeItem.addDescriptor(1 + (id shl 6), turbine50V)

        // 200V Turbine (subId=8)
        val turbine200V = TurbineDescriptor(
            "200V Turbine", "turbinebblue", stdCableRender200V,
            TtoU.duplicate(350.0, Cable.MVU), PoutToPin.duplicate(500.0, 500.0),
            350.0, Cable.MVU, 500.0, 500.0 / 40.0,
            mediumVoltageCableDescriptor.electricalRs * 0.1, 50.0,
            350.0 / 40.0, 500.0 / (Cable.MVU / 25.0), "eln:heat_turbine_200v"
        )
        transparentNodeItem.addDescriptor(8 + (id shl 6), turbine200V)

        // Water Turbine (subId=16)
        val waterTurbinePowerFactor = 1.0 // Assume 1.0 or get from Config if available
        val waterTurbine = mods.eln.transparentnode.waterturbine.WaterTurbineDescriptor(
            "Water Turbine", Eln.obj.getObj("SmallWaterWheel"),
            lowVoltageCableDescriptor,
            30.0 * waterTurbinePowerFactor,
            Cable.LVU * 1.18,
            Coordinate(1, -1, 0, 0),
            "eln:water_turbine", 1.0f
        )
        val waterTurbineGhost = GhostGroup()
        waterTurbineGhost.addRectangle(1, 1, 0, 1, -1, 1)
        waterTurbine.setGhostGroup(waterTurbineGhost)
        transparentNodeItem.addDescriptor(16 + (id shl 6), waterTurbine)
    }

    @JvmStatic
    private fun registerElectricalFurnace(transparentNodeItem: TransparentNodeItem, id: Int) {
        val PfTTable = doubleArrayOf(0.0, 20.0, 40.0, 80.0, 160.0, 240.0, 360.0, 540.0, 756.0, 1058.4, 1481.76)
        val thermalPlostfTTable = DoubleArray(PfTTable.size) { idx ->
            PfTTable[idx] * Math.pow((idx + 1.0) / PfTTable.size, 2.0) * 2.0
        }

        val PfT = FunctionTableYProtect(PfTTable, 800.0, 0.0, 100000.0)
        val thermalPlostfT = FunctionTableYProtect(thermalPlostfTTable, 800.0, 0.001, 10000000.0)

        val furnaceDescriptor = ElectricalFurnaceDescriptor(
            "Electrical Furnace", PfT, thermalPlostfT, 40.0
        )
        transparentNodeItem.addDescriptor(0 + (id shl 6), furnaceDescriptor)
    }

    @JvmStatic
    private fun registerMacerator(transparentNodeItem: TransparentNodeItem, id: Int) {
        val macerator50V = MaceratorDescriptor(
            "50V Macerator", "maceratora", Cable.LVU, 200.0, Cable.LVU * 1.25,
            ThermalLoadInitializer(80.0, -100.0, 10.0, 100000.0),
            lowVoltageCableDescriptor, maceratorRecipes
        )
        transparentNodeItem.addDescriptor(0 + (id shl 6), macerator50V)
        macerator50V.setRunningSound("eln:macerator")

        val macerator200V = MaceratorDescriptor(
            "200V Macerator", "maceratorb", Cable.MVU, 2000.0, Cable.MVU * 1.25,
            ThermalLoadInitializer(80.0, -100.0, 10.0, 100000.0),
            mediumVoltageCableDescriptor, maceratorRecipes
        )
        transparentNodeItem.addDescriptor(1 + (id shl 6), macerator200V)
        macerator200V.setRunningSound("eln:macerator")
    }

    @JvmStatic
    private fun registerCompressor(transparentNodeItem: TransparentNodeItem, id: Int) {
        val compressor50V = CompressorDescriptor(
            "50V Compressor", Eln.obj.getObj("compressora"),
            Cable.LVU, 200.0, Cable.LVU * 1.25,
            ThermalLoadInitializer(80.0, -100.0, 10.0, 100000.0),
            lowVoltageCableDescriptor, compressorRecipes
        )
        transparentNodeItem.addDescriptor(0 + (id shl 6), compressor50V)
        compressor50V.setRunningSound("eln:compressor_run")

        val compressor200V = CompressorDescriptor(
            "200V Compressor", Eln.obj.getObj("compressorb"),
            Cable.MVU, 2000.0, Cable.MVU * 1.25,
            ThermalLoadInitializer(80.0, -100.0, 10.0, 100000.0),
            mediumVoltageCableDescriptor, compressorRecipes
        )
        transparentNodeItem.addDescriptor(1 + (id shl 6), compressor200V)
        compressor200V.setRunningSound("eln:compressor_run")
    }

    @JvmStatic
    private fun registerMagnetizer(transparentNodeItem: TransparentNodeItem, id: Int) {
        val magnetizer50V = MagnetizerDescriptor(
            "50V Magnetizer", Eln.obj.getObj("magnetizera"),
            Cable.LVU, 200.0, Cable.LVU * 1.25,
            ThermalLoadInitializer(80.0, -100.0, 10.0, 100000.0),
            lowVoltageCableDescriptor, magnetizerRecipes
        )
        transparentNodeItem.addDescriptor(0 + (id shl 6), magnetizer50V)

        val magnetizer200V = MagnetizerDescriptor(
            "200V Magnetizer", Eln.obj.getObj("magnetizerb"),
            Cable.MVU, 2000.0, Cable.MVU * 1.25,
            ThermalLoadInitializer(80.0, -100.0, 10.0, 100000.0),
            mediumVoltageCableDescriptor, magnetizerRecipes
        )
        transparentNodeItem.addDescriptor(4 + (id shl 6), magnetizer200V)
    }

    @JvmStatic
    private fun registerPlateMachine(transparentNodeItem: TransparentNodeItem, id: Int) {
        val plateMachine50V = PlateMachineDescriptor(
            "50V Plate Machine", Eln.obj.getObj("platemachinea"),
            Cable.LVU, 200.0, Cable.LVU * 1.25,
            ThermalLoadInitializer(80.0, -100.0, 10.0, 100000.0),
            lowVoltageCableDescriptor, plateMachineRecipes
        )
        transparentNodeItem.addDescriptor(0 + (id shl 6), plateMachine50V)

        val plateMachine200V = PlateMachineDescriptor(
            "200V Plate Machine", Eln.obj.getObj("platemachineb"),
            Cable.MVU, 2000.0, Cable.MVU * 1.25,
            ThermalLoadInitializer(80.0, -100.0, 10.0, 100000.0),
            mediumVoltageCableDescriptor, plateMachineRecipes
        )
        transparentNodeItem.addDescriptor(1 + (id shl 6), plateMachine200V)
    }

    @JvmStatic
    private fun registerEggIncubator(transparentNodeItem: TransparentNodeItem, id: Int) {
        val eggIncubator = EggIncubatorDescriptor(
            "50V Egg Incubator", Eln.obj.getObj("eggincubator"),
            lowVoltageCableDescriptor, 100.0, Cable.LVU * 1.1
        )
        eggIncubator.setDefaultIcon("eggincubator")
        transparentNodeItem.addDescriptor(0 + (id shl 6), eggIncubator)
    }

    @JvmStatic
    private fun registerAutoMiner(transparentNodeItem: TransparentNodeItem, id: Int) {
        val powerLoad = arrayOf(
            Coordinate(-2, -1, 1, 0),
            Coordinate(-2, -1, -1, 0)
        )
        val lightCoord = Coordinate(-3, 0, 0, 0)
        val miningCoord = Coordinate(-1, 0, 1, 0)

        val autoMiner = AutoMinerDescriptor(
            "Auto Miner", Eln.obj.getObj("AutoMiner"),
            powerLoad, lightCoord, miningCoord,
            2, 1, 0,
            highVoltageCableDescriptor, 1.0, 50.0
        )

        val ghostGroup = GhostGroup().apply {
            addRectangle(-2, -1, -1, 0, -1, 1)
            addRectangle(1, 1, -1, 0, 1, 1)
            addRectangle(1, 1, -1, 0, -1, -1)
            addElement(1, 0, 0)
            addElement(0, 0, 1)
            addElement(0, 1, 0)
            addElement(0, 0, -1)
            removeElement(-1, -1, 0)
        }
        autoMiner.setGhostGroup(ghostGroup)
        transparentNodeItem.addDescriptor(0 + (id shl 6), autoMiner)
    }

    @JvmStatic
    private fun registerSolarPanel(transparentNodeItem: TransparentNodeItem, id: Int) {
        val LVSolarU = 59.0
        val solarPanelPowerFactor = 1.0

        // Small Solar Panel
        val smallSolarPanel = SolarPanelDescriptor(
            "Small Solar Panel", Eln.obj.getObj("smallsolarpannel"), null,
            GhostGroup(), 0, 1, 0,
            null, LVSolarU / 4.0, 65.0 * solarPanelPowerFactor,
            0.01, Math.PI / 2.0, Math.PI / 2.0
        )
        transparentNodeItem.addDescriptor(1 + (id shl 6), smallSolarPanel)

        // Small Rotating Solar Panel
        val smallRotSolarPanel = SolarPanelDescriptor(
            "Small Rotating Solar Panel", Eln.obj.getObj("smallsolarpannelrot"), stdCableRender50V,
            GhostGroup(), 0, 1, 0,
            null, LVSolarU / 4.0, 65.0 * solarPanelPowerFactor,
            0.01, Math.PI / 4.0, Math.PI / 4.0 * 3.0
        )
        transparentNodeItem.addDescriptor(2 + (id shl 6), smallRotSolarPanel)

        // 2x3 Solar Panel
        val bigSolarPanel = SolarPanelDescriptor(
            "2x3 Solar Panel", Eln.obj.getObj("bigSolarPanel"), stdCableRender200V,
            GhostGroup().apply {
                addRectangle(0, 1, 0, 0, -1, 1)
                removeElement(0, 0, 0)
            }, 1, 1, 0,
            Coordinate(1, 0, 0, 0), LVSolarU * 2.0, 65.0 * solarPanelPowerFactor * 8.0,
            0.01, Math.PI / 2.0, Math.PI / 2.0
        )
        transparentNodeItem.addDescriptor(3 + (id shl 6), bigSolarPanel)

        // 2x3 Rotating Solar Panel
        val bigRotSolarPanel = SolarPanelDescriptor(
            "2x3 Rotating Solar Panel", Eln.obj.getObj("bigSolarPanelrot"), stdCableRender200V,
            GhostGroup().apply {
                addRectangle(0, 1, 0, 0, -1, 1)
                removeElement(0, 0, 0)
            }, 1, 1, 1,
            Coordinate(1, 0, 0, 0), LVSolarU * 2.0, 65.0 * solarPanelPowerFactor * 8.0,
            0.01, Math.PI / 8.0 * 3.0, Math.PI / 8.0 * 5.0
        )
        transparentNodeItem.addDescriptor(4 + (id shl 6), bigRotSolarPanel)
    }

    @JvmStatic
    private fun registerWindTurbine(transparentNodeItem: TransparentNodeItem, id: Int) {
        val PfW = FunctionTable(doubleArrayOf(0.0, 0.1, 0.4, 0.6, 0.8, 1.0), 1.0)
        val windTurbine = WindTurbineDescriptor(
            "Wind Turbine", Eln.obj.getObj("windturbine"),
            lowVoltageCableDescriptor, PfW, 500.0, 10.0, Cable.LVU * 1.1, 20.0,
            3, 5, 5, 5, 2, 0.1, "eln:wind", 1.0f
        )
        transparentNodeItem.addDescriptor(0 + (id shl 6), windTurbine)
    }

    @JvmStatic
    private fun registerThermalDissipatorPassiveAndActive(transparentNodeItem: TransparentNodeItem, id: Int) {
        val passiveDissipator = ThermalDissipatorPassiveDescriptor(
            "Small Passive Thermal Dissipator", Eln.obj.getObj("passivethermaldissipatora"),
            200.0, -100.0, 100.0, 50.0, 10.0, 1.0
        )
        transparentNodeItem.addDescriptor(0 + (id shl 6), passiveDissipator)

        val activeDissipator50V = ThermalDissipatorActiveDescriptor(
            "Small Active Thermal Dissipator", Eln.obj.getObj("activethermaldissipatora"),
            Cable.LVU, 50.0, 500.0, lowVoltageCableDescriptor,
            200.0, -100.0, 200.0, 50.0, 10.0, 1.0
        )
        transparentNodeItem.addDescriptor(1 + (id shl 6), activeDissipator50V)

        val activeDissipator200V = ThermalDissipatorActiveDescriptor(
            "200V Active Thermal Dissipator", Eln.obj.getObj("200vactivethermaldissipatora"),
            Cable.MVU, 200.0, 2000.0, mediumVoltageCableDescriptor,
            200.0, -100.0, 200.0, 50.0, 10.0, 1.0
        )
        transparentNodeItem.addDescriptor(2 + (id shl 6), activeDissipator200V)
    }

    @JvmStatic
    private fun registerTransparentNodeMisc(transparentNodeItem: TransparentNodeItem, id: Int) {
        val powerLoad = arrayOf(
            Coordinate(-1, 0, 1, 0),
            Coordinate(-1, 0, -1, 0)
        )
        val doorOpen = GhostGroup().apply { addRectangle(-4, -3, 2, 2, 0, 0) }
        val doorClose = GhostGroup().apply { addRectangle(-2, -2, 0, 1, 0, 0) }

        val teleporter = TeleporterDescriptor(
            "Experimental Transporter", Eln.obj.getObj("Transporter"),
            highVoltageCableDescriptor,
            Coordinate(-1, 0, 0, 0), Coordinate(-1, 1, 0, 0),
            2, powerLoad,
            doorOpen, doorClose
        ).setChargeSound("eln:transporter", 0.5f)

        val g = GhostGroup().apply {
            addRectangle(-2, 0, 0, 1, -1, -1)
            addRectangle(-2, 0, 0, 1, 1, 1)
            addRectangle(-4, -1, 2, 2, 0, 0)
            addElement(0, 1, 0)
            addRectangle(-3, -3, 0, 1, -1, -1)
            addRectangle(-3, -3, 0, 1, 1, 1)
        }
        teleporter.setGhostGroup(g)
        transparentNodeItem.addDescriptor(0 + (id shl 6), teleporter)

    }

    @JvmStatic
    private fun registerTurret(transparentNodeItem: TransparentNodeItem, id: Int) {
        val turret = TurretDescriptor("800V Defence Turret", "Turret")
        transparentNodeItem.addDescriptor(0 + (id shl 6), turret)
    }

    @JvmStatic
    private fun registerFuelGenerator(transparentNodeItem: TransparentNodeItem, id: Int) {
        val fuelGeneratorTankCapacity = (20 * 60).toDouble()

        val fuelGenerator50V = FuelGeneratorDescriptor(
            "50V Fuel Generator", Eln.obj.getObj("fuelgenerator50v"),
            lowVoltageCableDescriptor, fuelGeneratorPowerFactor * 300, LVU * 1.05, fuelGeneratorTankCapacity
        )
        transparentNodeItem.addDescriptor(1 + (id shl 6), fuelGenerator50V)

        val fuelGenerator200V =
            FuelGeneratorDescriptor(
                "200V Fuel Generator", Eln.obj.getObj("fuelgenerator200v"),
                mediumVoltageCableDescriptor, fuelGeneratorPowerFactor * 1500, MVU * 1.05,
                fuelGeneratorTankCapacity
            )
        transparentNodeItem.addDescriptor(2 + (id shl 6), fuelGenerator200V)
    }

    @JvmStatic
    private fun registerGridDevices(transparentNodeItem: TransparentNodeItem, id: Int) {
        val poleObj = Eln.obj.getObj("utilitypole")
        val transformerObj = Eln.obj.getObj("transformator")
        val downlinkObj = Eln.obj.getObj("downlink")

        // Downlink (subId=2)
        if (downlinkObj != null) {
            val downlink = mods.eln.gridnode.downlink.DownlinkDescriptor("Downlink", downlinkObj, "eln:textures/wire.png", Descriptors.highVoltageCableDescriptor)
            transparentNodeItem.addDescriptor(2 + (id shl 6), downlink)
        }

        // Grid Transformer (subId=3)
        if (transformerObj != null) {
            val gridTransformer = mods.eln.gridnode.transformer.GridTransformerDescriptor("Grid Transformer", transformerObj, "eln:textures/wire.png", Descriptors.highVoltageCableDescriptor)
            val g = GhostGroup().apply {
                addElement(1, 0, 0)
                addElement(0, 0, -1)
                addElement(1, 0, -1)
                addElement(1, 1, 0)
                addElement(0, 1, 0)
                addElement(1, 1, -1)
                addElement(0, 1, -1)
            }
            gridTransformer.setGhostGroup(g)
            transparentNodeItem.addDescriptor(3 + (id shl 6), gridTransformer)
        }

        val poleGhost = GhostGroup().apply {
            addElement(0, 1, 0)
            addElement(0, 2, 0)
            addElement(0, 3, 0)
        }

        // Utility Pole (subId=4)
        val utilityPole = ElectricalPoleDescriptor(
            "Utility Pole", poleObj,
            "eln:textures/wire.png", highVoltageCableDescriptor, false
        )
        utilityPole.setGhostGroup(poleGhost)
        transparentNodeItem.addDescriptor(4 + (id shl 6), utilityPole)

        // Utility Pole w/ DC-DC (subId=5)
        val utilityPoleDCDC = ElectricalPoleDescriptor(
            "Utility Pole w/DC-DC Converter", poleObj,
            "eln:textures/wire.png", highVoltageCableDescriptor, true
        )
        utilityPoleDCDC.setDefaultIcon("utilitypolewdcdcconverter")
        utilityPoleDCDC.setGhostGroup(poleGhost)
        transparentNodeItem.addDescriptor(5 + (id shl 6), utilityPoleDCDC)
    }

    @JvmStatic
    private fun registerElectricalAntenna(transparentNodeItem: TransparentNodeItem, id: Int) {
        val P = 250.0

        // Low Power Transmitter Antenna
        val txDescriptor = ElectricalAntennaTxDescriptor(
            "Low Power Transmitter Antenna", Eln.obj.getObj("lowpowertransmitterantenna"),
            200, 0.9, 0.7, Cable.LVU, P, Cable.LVU * 1.3, P * 1.3, lowVoltageCableDescriptor
        )
        transparentNodeItem.addDescriptor(0 + (id shl 6), txDescriptor)

        // Low Power Receiver Antenna
        val rxDescriptor = ElectricalAntennaRxDescriptor(
            "Low Power Receiver Antenna", Eln.obj.getObj("lowpowerreceiverantenna"),
            Cable.LVU, P, Cable.LVU * 1.3, P * 1.3, lowVoltageCableDescriptor
        )
        transparentNodeItem.addDescriptor(1 + (id shl 6), rxDescriptor)
    }

    @JvmStatic
    private fun registerMechanical(transparentNodeItem: TransparentNodeItem, id: Int) {
        val stdGeneratorObj = Eln.obj.getObj("generator")
        val turbineObj = Eln.obj.getObj("turbine")
        val gasTurbineObj = Eln.obj.getObj("gasturbine")
        val flywheelObj = Eln.obj.getObj("flywheel")
        val jointObj = Eln.obj.getObj("straightjoint")
        val jointHubObj = Eln.obj.getObj("jointhub")
        val tachometerObj = Eln.obj.getObj("tachometer")

        // Generators
        val generator50V = mods.eln.mechanical.GeneratorDescriptor(
            "50V Generator", stdGeneratorObj, lowVoltageCableDescriptor,
            800f, Cable.LVU.toFloat(), 0.5f, 500f,
            Eln.sixNodeThermalLoadInitializer
        )
        transparentNodeItem.addDescriptor(0 + (id shl 6), generator50V)

        val generator200V = mods.eln.mechanical.GeneratorDescriptor(
            "200V Generator", stdGeneratorObj, mediumVoltageCableDescriptor,
            1200f, Cable.MVU.toFloat(), 1.0f, 2500f,
            Eln.sixNodeThermalLoadInitializer
        )
        transparentNodeItem.addDescriptor(1 + (id shl 6), generator200V)

        // Turbines
        val steamTurbine = mods.eln.mechanical.SteamTurbineDescriptor("Steam Turbine", turbineObj)
        transparentNodeItem.addDescriptor(8 + (id shl 6), steamTurbine)

        val gasTurbine = mods.eln.mechanical.GasTurbineDescriptor("Gas Turbine", gasTurbineObj)
        transparentNodeItem.addDescriptor(9 + (id shl 6), gasTurbine)

        // Shafts & Joints
        val flywheel = mods.eln.mechanical.FlywheelDescriptor("Flywheel", flywheelObj)
        transparentNodeItem.addDescriptor(16 + (id shl 6), flywheel)

        val simpleShaft = mods.eln.mechanical.StraightJointDescriptor("Simple Shaft", jointObj)
        transparentNodeItem.addDescriptor(17 + (id shl 6), simpleShaft)

        val jointHub = mods.eln.mechanical.JointHubDescriptor("Joint Hub", jointHubObj)
        transparentNodeItem.addDescriptor(18 + (id shl 6), jointHub)

        val tachometer = mods.eln.mechanical.TachometerDescriptor("Tachometer", tachometerObj)
        transparentNodeItem.addDescriptor(19 + (id shl 6), tachometer)
    }
}

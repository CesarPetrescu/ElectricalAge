package mods.eln.transparentnode

import mods.eln.sim.mna.SubSystem
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.misc.IPowerTransferProcess
import mods.eln.sim.mna.misc.MnaConst
import mods.eln.sim.mna.process.PowerTransferChecks
import mods.eln.sim.mna.state.State
import mods.eln.sim.power.*
import kotlin.math.abs
import kotlin.math.max

/** Runtime adapter contract kept independent of Minecraft, so the actual MNA transfer is testable. */
interface OneWayDcDcAccess {
    val primaryLoad: State
    val secondaryLoad: State
    val primaryReferenceLoad: State
    val secondaryReferenceLoad: State
    val primaryConversionLoad: State get() = primaryLoad
    val secondaryConversionLoad: State get() = secondaryLoad
    val inputSink: VoltageSource
    val outputSource: VoltageSource
    val mode: OneWayDcDcMode
    val isolated: Boolean
    val populated: Boolean
    val requestedOutputVoltage: Double? get() = null
    val maxInputVoltage: Double get() = 120_000.0
    val maxOutputVoltage: Double get() = 120_000.0
    val protectedMode: Boolean
    val primaryMeltCurrent: Double
    val secondaryMeltCurrent: Double
    var activeRatio: Double
    var movedPower: Double
    var converterStatus: OneWayDcDcStatus
    fun computeRatio(): Double
}

enum class OneWayDcDcStatus {
    UNCONFIGURED, RUNNING, LIMITED, NO_INPUT, OUTPUT_HIGH, OVERLOAD, INVALID_NETWORK, NON_CONVERGENT
}

class OneWayDcDcProcess(private val element: OneWayDcDcAccess) : IPowerTransferProcess {
    private var retryAfterSeconds = 0.0
    private val inputLimit get() = if (element.protectedMode) element.primaryMeltCurrent else Double.POSITIVE_INFINITY
    private val outputLimit get() = if (element.protectedMode) element.secondaryMeltCurrent else Double.POSITIVE_INFINITY

    override fun beginTransferStep(seconds: Double) {
        retryAfterSeconds = max(0.0, retryAfterSeconds - seconds)
    }

    override fun rootSystemPreStepProcess() {
        if (retryAfterSeconds > 0.0) return open(OneWayDcDcStatus.NON_CONVERGENT)
        element.activeRatio = element.computeRatio()
        if (!element.populated) return open(OneWayDcDcStatus.UNCONFIGURED)
        if (!element.activeRatio.isFinite() || element.activeRatio <= 0.0)
            return open(OneWayDcDcStatus.UNCONFIGURED)
        val inputSystem = element.inputSink.subSystem ?: return open(OneWayDcDcStatus.NO_INPUT)
        val outputSystem = element.outputSource.subSystem ?: return open(OneWayDcDcStatus.NO_INPUT)
        val input = inputSystem.getTh(element.primaryConversionLoad,
            if (element.isolated) element.primaryReferenceLoad else null, element.inputSink)
        val output = outputSystem.getTh(element.secondaryConversionLoad,
            if (element.isolated) element.secondaryReferenceLoad else null, element.outputSource)
        if (!input.valid || !output.valid) return open(OneWayDcDcStatus.INVALID_NETWORK)
        if (input.voltage <= 0.0 || input.resistance >= MnaConst.highImpedance * 0.1)
            return open(OneWayDcDcStatus.NO_INPUT)

        if (element.mode == OneWayDcDcMode.FIXED || element.mode == OneWayDcDcMode.ISOLATION) {
            // Fixed really means fixed ratio under load, not regulation to unloaded input * ratio.
            val point = RatioTransformer.solve(PortThevenin(input.voltage, input.resistance),
                PortThevenin(output.voltage, output.resistance), element.activeRatio)
                ?: return open(OneWayDcDcStatus.INVALID_NETWORK)
            if (point.inputAmps < -1e-9 || point.outputAmps < -1e-9 || point.outputVolts < 0.0)
                return open(OneWayDcDcStatus.OUTPUT_HIGH)
            if (point.inputVolts > element.maxInputVoltage || point.outputVolts > element.maxOutputVoltage || abs(point.inputAmps) > inputLimit || abs(point.outputAmps) > outputLimit)
                return open(OneWayDcDcStatus.OVERLOAD)
            apply(point.inputVolts, point.outputVolts, point.outputWatts, OneWayDcDcStatus.RUNNING)
            return
        }
        if (!element.protectedMode) {
            val transfer = OneWayDcDcMath.solve(
                Th(input.voltage, input.resistance), Th(output.voltage, output.resistance),
                (element.requestedOutputVoltage?.div(input.voltage) ?: element.activeRatio), element.maxOutputVoltage
            ) ?: return open(OneWayDcDcStatus.OUTPUT_HIGH)
            apply(transfer.inputSourceVoltage, transfer.outputSourceVoltage, transfer.power, OneWayDcDcStatus.RUNNING)
            return
        }
        if (!inputLimit.isFinite() || inputLimit <= 0.0 || !outputLimit.isFinite() || outputLimit <= 0.0)
            return open(OneWayDcDcStatus.UNCONFIGURED)
        // The electronics are ideal here; winding copper loss remains in the MNA circuit.
        val limits = ConverterLimits(0.001, element.maxInputVoltage, element.maxOutputVoltage, inputLimit, outputLimit,
            minOf(element.maxInputVoltage * inputLimit, element.maxOutputVoltage * outputLimit), 1.0,
            if (element.mode == OneWayDcDcMode.BOOST) 1.0 else 1.0 / 256.0,
            if (element.mode == OneWayDcDcMode.BUCK) 1.0 else 256.0)
        when (val result = RegulatedConverter.solve(PortThevenin(input.voltage, input.resistance),
            PortThevenin(output.voltage, output.resistance), (element.requestedOutputVoltage ?: (input.voltage * element.activeRatio)), limits)) {
            is TransferResult.Running -> {
                val p = result.point
                apply(p.inputVolts, p.outputVolts, p.outputWatts,
                    if (p.limitedBy.isEmpty()) OneWayDcDcStatus.RUNNING else OneWayDcDcStatus.LIMITED)
            }
            is TransferResult.Off -> open(when (result.reason) {
                StopReason.OUTPUT_ALREADY_HIGH -> OneWayDcDcStatus.OUTPUT_HIGH
                StopReason.NO_INPUT, StopReason.INPUT_UNDERVOLTAGE -> OneWayDcDcStatus.NO_INPUT
                StopReason.INVALID_NETWORK -> OneWayDcDcStatus.INVALID_NETWORK
                else -> OneWayDcDcStatus.OVERLOAD
            })
        }
    }

    private fun apply(inputVolts: Double, outputVolts: Double, watts: Double, status: OneWayDcDcStatus) {
        if (!inputVolts.isFinite() || !outputVolts.isFinite() || !watts.isFinite())
            return open(OneWayDcDcStatus.INVALID_NETWORK)
        element.inputSink.setVoltage(inputVolts)
        element.outputSource.setVoltage(outputVolts)
        element.inputSink.isEnabled = true
        element.outputSource.isEnabled = true
        element.movedPower = max(0.0, watts)
        element.converterStatus = status
    }

    private fun open(status: OneWayDcDcStatus) {
        element.inputSink.isEnabled = false
        element.outputSource.isEnabled = false
        element.movedPower = 0.0
        element.converterStatus = status
    }

    override fun isTransferBalanced(): Boolean = PowerTransferChecks.balanced(
        element.inputSink, element.outputSource, true, inputLimit, outputLimit)

    override fun transferSystems(): Array<SubSystem?> = arrayOf(element.inputSink.subSystem, element.outputSource.subSystem)

    override fun transferSources(): Array<VoltageSource> = arrayOf(element.inputSink, element.outputSource)

    override fun suspendTransfer() {
        open(OneWayDcDcStatus.NON_CONVERGENT)
        retryAfterSeconds = 1.0
    }

    private data class Th(override val voltage: Double, override val resistance: Double) : OneWayDcDcThevenin
}

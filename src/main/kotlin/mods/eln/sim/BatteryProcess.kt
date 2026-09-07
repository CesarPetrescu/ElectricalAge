package mods.eln.sim

import mods.eln.Eln
import mods.eln.misc.FunctionTable
import mods.eln.sim.mna.component.VoltageSource
import mods.eln.sim.mna.state.VoltageState

open class BatteryProcess(
    var positiveLoad: VoltageState?,
    var negativeLoad: VoltageState?,
    var voltageFunction: FunctionTable,
    @JvmField var IMax: Double,
    var voltageSource: VoltageSource,
    private var thermalLoad: ThermalLoad
) : IProcess {

    // TODO: Change these to charge, and change the charge getter/setter to reflect
    @JvmField
    var Q = 0.0
    var QNominal = 0.0
    var uNominal = 0.0
    @JvmField
    var life = 1.0
    var isRechargeable = true

    override fun process(time: Double) {
        if (!time.isFinite() || time <= 0.0 || !QNominal.isFinite() || QNominal <= 0.0) return
        sanitizeState()
        val lastQ = Q
        val current = voltageSource.current.takeIf { it.isFinite() } ?: 0.0
        val deltaQ = current * time / QNominal
        var wasteQ = 0.0
        if (!isRechargeable && deltaQ < 0.0) {
            if (Eln.config.getBooleanOrElse("debug.logging.enabled", false)) {
                Eln.logger.warn("Battery is recharging when it shouldn't! current=${voltageSource.current}")
            }
            wasteQ = -deltaQ
            Q = lastQ
        } else {
            val requestedQ = Q - deltaQ
            Q = requestedQ.coerceIn(0.0, life)
            // Once full, charging becomes heat rather than unlimited stored energy.
            wasteQ = (requestedQ - life).coerceAtLeast(0.0)
        }
        val voltage = computeVoltage()
        voltageSource.voltage = voltage
        if (wasteQ > 0) {
            thermalLoad.movePowerTo(wasteQ * QNominal * voltage / time)
        }
    }

    fun computeVoltage(): Double {
        if (charge <= 0.0) return 0.0
        val voltage = voltageFunction.getValue(charge)
        return Math.max(0.0, voltage * uNominal)
    }

    fun sanitizeState() {
        life = life.takeIf { it.isFinite() && it > 0.0 }?.coerceIn(0.1, 1.0) ?: 1.0
        Q = Q.takeIf { it.isFinite() }?.coerceIn(0.0, life) ?: 0.0
    }

    fun changeLife(newLife: Double) {
        sanitizeState()
        val safeLife = newLife.takeIf { it.isFinite() }?.coerceIn(0.1, 1.0) ?: life
        if (safeLife < life) {
            Q *= safeLife / life
        }
        life = safeLife
    }

    var charge: Double
        get() = if (life > 0.0 && life.isFinite() && Q.isFinite()) (Q / life).coerceIn(0.0, 1.0) else 0.0
        set(charge) {
            sanitizeState()
            Q = life * (charge.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0)
        }
    val energy: Double
        get() {
            val stepNbr = 50
            val chargeStep = charge / stepNbr
            var chargeIntegrator = 0.0
            var energy = 0.0
            val QperStep = QNominal * life * chargeStep
            for (step in 0 until stepNbr) {
                val voltage = voltageFunction.getValue(chargeIntegrator) * uNominal
                energy += voltage * QperStep
                chargeIntegrator += chargeStep
            }
            return energy
        }
    val energyMax: Double
        get() {
            val stepNbr = 50
            val chargeStep = 1.0 / stepNbr
            var chargeIntegrator = 0.0
            var energy = 0.0
            val QperStep = QNominal * life * 1.0 / stepNbr
            for (step in 0 until stepNbr) {
                val voltage = voltageFunction.getValue(chargeIntegrator) * uNominal
                energy += voltage * QperStep
                chargeIntegrator += chargeStep
            }
            return energy
        }
    val u: Double
        get() = computeVoltage()
    val dischargeCurrent: Double
        get() = voltageSource.current
}

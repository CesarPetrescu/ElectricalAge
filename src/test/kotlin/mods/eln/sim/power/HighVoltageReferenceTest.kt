package mods.eln.sim.power

import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

private var deterministic = 0
private var propertyTrials = 0
private var propertyRunning = 0

private fun near(actual: Double, expected: Double, relative: Double = 1e-8) {
    check(actual.isFinite() && abs(actual - expected) <= relative * max(1.0, abs(expected))) {
        "Expected $expected; actual $actual"
    }
}
private fun test(name: String, body: () -> Unit) {
    try { body(); deterministic++; println("PASS $name") }
    catch (e: Throwable) { throw AssertionError("FAIL $name", e) }
}
private fun running(result: TransferResult): OperatingPoint =
    (result as? TransferResult.Running)?.point ?: error("Expected Running, got $result")
private fun off(result: TransferResult, why: StopReason) {
    check(result is TransferResult.Off && result.reason == why) { "Expected Off($why), got $result" }
}
private inline fun rejects(block: () -> Unit) {
    var rejected = false
    try { block() } catch (_: IllegalArgumentException) { rejected = true } catch (_: IllegalStateException) { rejected = true }
    check(rejected) { "Expected rejection" }
}
private fun ratings() = ConverterLimits(1.0, 150_000.0, 150_000.0, 1000.0, 1000.0,
    1_000_000.0, .95, 1.0 / 256, 256.0)

fun runReferenceCorpus() {
    test("log mapping has a safe unity midpoint") {
        near(ControlMapping.ratio(ConverterKind.VARIABLE, .5, MappingVersion.LOGARITHMIC_V2), 1.0)
    }
    test("log mapping keeps full 1/256..256 range") {
        near(ControlMapping.ratio(ConverterKind.VARIABLE, 0.0, MappingVersion.LOGARITHMIC_V2), 1.0 / 256)
        near(ControlMapping.ratio(ConverterKind.VARIABLE, 1.0, MappingVersion.LOGARITHMIC_V2), 256.0)
    }
    test("legacy midpoint preserved exactly") {
        near(ControlMapping.ratio(ConverterKind.VARIABLE, .5, MappingVersion.LEGACY_LINEAR_V1), 128.001953125)
    }
    test("legacy boost remains 50x, new boost allows 256x") {
        near(ControlMapping.ratio(ConverterKind.BOOST, 1.0, MappingVersion.LEGACY_LINEAR_V1), 50.0)
        near(ControlMapping.ratio(ConverterKind.BOOST, 1.0, MappingVersion.LOGARITHMIC_V2), 256.0)
    }
    test("legacy buck and buck-boost endpoints preserved") {
        near(ControlMapping.ratio(ConverterKind.BUCK, 0.0, MappingVersion.LEGACY_LINEAR_V1), .02)
        near(ControlMapping.ratio(ConverterKind.BUCK_BOOST, .5, MappingVersion.LEGACY_LINEAR_V1), 1.0)
        near(ControlMapping.ratio(ConverterKind.BUCK_BOOST, 1.0, MappingVersion.LEGACY_LINEAR_V1), 50.0)
    }
    test("missing saved version stays legacy") { check(loadMapping(null) == MappingVersion.LEGACY_LINEAR_V1) }
    test("unknown saved version rejected") { rejects { loadMapping("FUTURE_V99") } }
    test("nonfinite control rejected") { rejects { ControlMapping.ratio(ConverterKind.BUCK, Double.NaN, MappingVersion.LOGARITHMIC_V2) } }
    test("50V to 800V at 100W includes electronics losses") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, 0.0), PortThevenin(0.0, 6400.0), 800.0, ratings()))
        near(p.outputVolts, 800.0); near(p.outputWatts, 100.0); near(p.inputAmps, 100 / .95 / 50)
        near(p.electronicsHeatWatts, 100 / .95 - 100)
    }
    test("50V to 3200V at 100W") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, 0.0), PortThevenin(0.0, 102400.0), 3200.0, ratings()))
        near(p.outputVolts, 3200.0); near(p.outputWatts, 100.0)
    }
    test("50V to 12800V at 100W") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, 0.0), PortThevenin(0.0, 1638400.0), 12800.0, ratings()))
        near(p.outputVolts, 12800.0); near(p.outputWatts, 100.0)
    }
    test("3200V to 50V at 100W") {
        val p = running(RegulatedConverter.solve(PortThevenin(3200.0, 0.0), PortThevenin(0.0, 25.0), 50.0, ratings()))
        near(p.outputVolts, 50.0); near(p.outputWatts, 100.0)
    }
    test("source resistance gives a real power ceiling") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, 100.0), PortThevenin(0.0, 100.0), 50.0,
            ratings().copy(electronicsEfficiency = 1.0)))
        near(p.inputVolts, 25.0); near(p.outputVolts, 25.0); near(p.outputWatts, 6.25)
    }
    test("input current limit includes input sag") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, .2), PortThevenin(0.0, 6400.0), 800.0,
            ratings().copy(maxInputAmps = 2.0)))
        near(p.inputAmps, 2.0); near(p.inputVolts, 49.6); near(p.outputWatts, .95 * 2 * 49.6)
    }
    test("output current limit reduces voltage") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, 0.0), PortThevenin(0.0, 6400.0), 800.0,
            ratings().copy(maxOutputAmps = .05)))
        near(p.outputVolts, 320.0); near(p.outputAmps, .05)
    }
    test("output power limit") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, 0.0), PortThevenin(0.0, 6400.0), 800.0,
            ratings().copy(maxOutputWatts = 80.0)))
        near(p.outputWatts, 80.0); near(p.outputVolts, sqrt(80 * 6400.0))
    }
    test("undervoltage boundary respected under load") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, 10.0), PortThevenin(0.0, 100.0), 800.0,
            ratings().copy(minInputVolts = 40.0)))
        near(p.inputVolts, 40.0); near(p.inputAmps, 1.0)
    }
    test("buck cannot boost the loaded input voltage") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, .1), PortThevenin(0.0, 100.0), 800.0,
            ratings().copy(maxGain = 1.0)))
        near(p.outputVolts, p.inputVolts)
    }
    test("boost refuses an unreachable below-input output") {
        off(RegulatedConverter.solve(PortThevenin(50.0, 0.0), PortThevenin(0.0, 100.0), 25.0,
            ratings().copy(minGain = 1.0)), StopReason.GAIN_UNAVAILABLE)
    }
    test("gain ceiling uses loaded, not open-circuit, input") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, 1.0), PortThevenin(0.0, 6400.0), 800.0,
            ratings().copy(maxGain = 16.0)))
        check(p.outputVolts < 800.0); near(p.outputVolts, 16 * p.inputVolts)
    }
    test("output-voltage ceiling") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, 0.0), PortThevenin(0.0, 6400.0), 800.0,
            ratings().copy(maxOutputVolts = 600.0)))
        near(p.outputVolts, 600.0)
    }
    test("input loss returns an explicit open-state instruction") {
        off(RegulatedConverter.solve(PortThevenin(0.0, .1), PortThevenin(800.0, 6400.0), 800.0, ratings()), StopReason.NO_INPUT)
    }
    test("open input cannot supply power") {
        off(RegulatedConverter.solve(PortThevenin(50.0, Double.POSITIVE_INFINITY), PortThevenin(0.0, 6400.0), 800.0, ratings()), StopReason.NO_INPUT)
    }
    test("open output has voltage but no fabricated load power") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, 0.0), PortThevenin(0.0, Double.POSITIVE_INFINITY), 800.0, ratings()))
        near(p.outputVolts, 800.0); near(p.inputWatts, 0.0); near(p.outputAmps, 0.0)
    }
    test("back-fed output is not clamped or sunk") {
        off(RegulatedConverter.solve(PortThevenin(50.0, .1), PortThevenin(900.0, .1), 800.0, ratings()), StopReason.OUTPUT_ALREADY_HIGH)
    }
    test("negative differential output Thevenin preserved") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, 0.0), PortThevenin(-50.0, 100.0), 50.0,
            ratings().copy(electronicsEfficiency = 1.0)))
        near(p.outputVolts, 50.0); near(p.outputAmps, 1.0); near(p.outputWatts, 50.0)
    }
    test("zero-ohm output is rejected instead of hidden by an epsilon") {
        off(RegulatedConverter.solve(PortThevenin(50.0, 0.0), PortThevenin(0.0, 0.0), 800.0, ratings()), StopReason.INVALID_NETWORK)
    }
    test("invalid Thevenin resistance rejected") {
        off(RegulatedConverter.solve(PortThevenin(50.0, -1.0), PortThevenin(0.0, 10.0), 800.0, ratings()), StopReason.INVALID_NETWORK)
    }
    test("input overvoltage rejected") {
        off(RegulatedConverter.solve(PortThevenin(50.0, .1), PortThevenin(0.0, 10.0), 800.0,
            ratings().copy(maxInputVolts = 40.0)), StopReason.INPUT_OVERVOLTAGE)
    }
    test("zero current limit disables transfer") {
        off(RegulatedConverter.solve(PortThevenin(50.0, .1), PortThevenin(0.0, 10.0), 800.0,
            ratings().copy(maxInputAmps = 0.0)), StopReason.LIMIT_ZERO)
    }
    test("disabled request is explicitly off") {
        off(RegulatedConverter.solve(PortThevenin(50.0, .1), PortThevenin(0.0, 10.0), 800.0, ratings(), false), StopReason.DISABLED)
    }
    test("tiny input load avoids quadratic cancellation") {
        val p = running(RegulatedConverter.solve(PortThevenin(100000.0, .001), PortThevenin(0.0, 1e12), 100000.0, ratings()))
        near(p.outputWatts, .01); check(p.inputAmps > 0)
        near(p.inputWatts * .95, p.outputWatts)
    }
    test("fixed transformer supports forward power") {
        val p = checkNotNull(RatioTransformer.solve(PortThevenin(50.0, .1), PortThevenin(0.0, 6400.0), 16.0))
        near(p.outputVolts / p.inputVolts, 16.0); near(p.inputAmps, 16 * p.outputAmps)
        near(p.inputWatts, p.outputWatts); check(p.inputWatts > 0)
    }
    test("fixed transformer supports reverse power") {
        val p = checkNotNull(RatioTransformer.solve(PortThevenin(0.0, 100.0), PortThevenin(50.0, 1.0), 4.0))
        check(p.inputWatts < 0 && p.outputWatts < 0); near(p.inputWatts, p.outputWatts)
    }
    test("incompatible ideal transformer sources rejected") {
        check(RatioTransformer.solve(PortThevenin(50.0, 0.0), PortThevenin(100.0, 0.0), 4.0) == null)
    }
    test("winding length changes both resistance and conductor volume") {
        val a = WindingGeometry(10.0, 2.5, 20.0)
        val b = a.copy(lengthMeters = 20.0, turns = 40.0)
        near(b.resistanceOhms(.017241, .00393, 20.0), 2 * a.resistanceOhms(.017241, .00393, 20.0))
        near(b.metalVolumeCubicMeters(), 2 * a.metalVolumeCubicMeters(), 1e-12)
    }
    test("winding parallel paths explicit, not accidental multicore short") {
        val a = WindingGeometry(10.0, 2.5, 20.0)
        val b = a.copy(parallelPaths = 2)
        near(b.resistanceOhms(.017241, .00393, 20.0), a.resistanceOhms(.017241, .00393, 20.0) / 2)
        near(b.metalVolumeCubicMeters(), 2 * a.metalVolumeCubicMeters(), 1e-12)
    }
    test("hot winding has greater resistance") {
        val a = WindingGeometry(10.0, 2.5, 20.0)
        check(a.resistanceOhms(.017241, .00393, 100.0) > a.resistanceOhms(.017241, .00393, 20.0))
    }
    test("800V survives 1kV insulation") {
        near(worstInsulationStress(doubleArrayOf(800.0), GameInsulation.LV_1KV).ratio, .8)
    }
    test("plus and minus 800V means 1600V between cores") {
        val stress = worstInsulationStress(doubleArrayOf(800.0, -800.0), GameInsulation.LV_1KV)
        check(stress.toCore == 1 && stress.fromCore == 0); near(stress.volts, 1600.0)
    }
    test("insulation faults select a pair, not all cores") {
        val stress = worstInsulationStress(doubleArrayOf(800.0, -800.0, 0.0), GameInsulation.LV_1KV)
        val failed = InsulationDamage().commit(stress, .01)
        check(failed != null && failed.fromCore == 0 && failed.toCore == 1)
    }
    test("below-rating insulation has no accumulated damage") {
        val damage = InsulationDamage()
        damage.commit(worstInsulationStress(doubleArrayOf(800.0), GameInsulation.LV_1KV), 3600.0)
        near(damage.exposure, 0.0); check(damage.fault == null)
    }
    test("insulation dose survives a simulated save/load") {
        val s = worstInsulationStress(doubleArrayOf(1250.0), GameInsulation.LV_1KV)
        val a = InsulationDamage(); a.commit(s, .5)
        val b = InsulationDamage(a.exposure, a.fault); b.commit(s, .5)
        check(b.fault != null)
    }
    test("heat is integrated in joules, drained once") {
        val h = HeatAccumulator(); repeat(100) { h.commit(10.0, .01) }
        near(h.drainJoules(), 10.0); near(h.drainJoules(), 0.0)
    }
    test("legacy registry identity remains unchanged when adding a cable") {
        val old = mapOf("legacy" to RegistrationIdentity(2000, "eln:legacy"))
        validateRegistrationMigration(old, old + ("new" to RegistrationIdentity(3000, "eln:new")))
        rejects { validateRegistrationMigration(old, mapOf("legacy" to RegistrationIdentity(2001, "eln:legacy"))) }
    }
    test("duplicate descriptor ids are rejected") {
        rejects { validateRegistrationMigration(emptyMap(), mapOf("a" to RegistrationIdentity(1, "eln:a"), "b" to RegistrationIdentity(1, "eln:b"))) }
    }

    test("insulation damage is invariant to time-step subdivision") {
        val stress = worstInsulationStress(doubleArrayOf(1250.0), GameInsulation.LV_1KV)
        val one = InsulationDamage(); one.commit(stress, 1.0)
        val many = InsulationDamage(); repeat(100) { many.commit(stress, .01) }
        check(one.fault != null && many.fault != null)
        near(one.exposure, many.exposure)
    }
    test("isolated voltage readings use the local reference") {
        val port = PortVoltages(1550.0, 1500.0)
        near(port.differential, 50.0); near(port.peakToGround, 1550.0)
    }
    test("primary-secondary insulation is separate from winding voltage") {
        val p = PortVoltages(50.0, 0.0)
        val s = PortVoltages(1050.0, 1000.0)
        near(p.differential, 50.0); near(s.differential, 50.0)
        near(primarySecondaryInsulationStress(p, s), 1050.0)
    }
    test("heat integration is independent of electrical substep size") {
        val one = HeatAccumulator(); one.commit(100.0, 1.0)
        val many = HeatAccumulator(); repeat(1000) { many.commit(100.0, .001) }
        near(one.drainJoules(), many.drainJoules())
    }

    test("floating open-output reading does not cause no-load cycling") {
        val p = running(RegulatedConverter.solve(PortThevenin(50.0, 0.0),
            PortThevenin(800.0, Double.POSITIVE_INFINITY), 800.0, ratings()))
        near(p.outputVolts, 800.0); near(p.inputWatts, 0.0)
    }

    val rng = Random(9042026L)
    repeat(10000) {
        val ei = 10.0 + rng.nextDouble() * 990
        val ri = 10.0.pow(-3 + rng.nextDouble() * 4)
        val ro = 10.0.pow(-1 + rng.nextDouble() * 8)
        val target = ei * 10.0.pow(-2 + rng.nextDouble() * 4)
        val eo = target * (rng.nextDouble() - .3) * .5
        val l = ratings().copy(maxInputAmps = .1 + rng.nextDouble() * 100,
            maxOutputAmps = .01 + rng.nextDouble() * 20, maxOutputWatts = 1 + rng.nextDouble() * 10000,
            electronicsEfficiency = .8 + rng.nextDouble() * .2)
        val result = RegulatedConverter.solve(PortThevenin(ei, ri), PortThevenin(eo, ro), target, l)
        propertyTrials++
        if (result is TransferResult.Running) {
            propertyRunning++
            val p = result.point
            near(p.inputVolts, ei - ri * p.inputAmps)
            near(p.outputVolts, eo + ro * p.outputAmps)
            near(p.inputWatts, p.inputVolts * p.inputAmps)
            near(p.outputWatts, p.outputVolts * p.outputAmps)
            near(p.outputWatts, l.electronicsEfficiency * p.inputWatts)
            near(p.inputWatts, p.outputWatts + p.electronicsHeatWatts)
            check(p.electronicsHeatWatts >= 0 && p.inputAmps >= 0 && p.outputAmps >= 0)
            check(p.inputAmps <= l.maxInputAmps * (1 + 1e-7))
            check(p.outputAmps <= l.maxOutputAmps * (1 + 1e-7))
            check(p.outputWatts <= l.maxOutputWatts * (1 + 1e-7))
            check(p.inputVolts >= l.minInputVolts - 1e-7)
            check(p.outputVolts <= minOf(target, l.maxOutputVolts) * (1 + 1e-7))
            check(p.outputVolts / p.inputVolts <= l.maxGain * (1 + 1e-7))
            check(p.outputVolts / p.inputVolts >= l.minGain * (1 - 1e-7))
        }
    }
    println("PASS $propertyTrials seeded converter property trials ($propertyRunning running points checked)")
    var transformerTrials = 0
    repeat(2000) {
        val a = PortThevenin((rng.nextDouble() - .5) * 10000, .01 + rng.nextDouble() * 100)
        val b = PortThevenin((rng.nextDouble() - .5) * 10000, .01 + rng.nextDouble() * 100)
        val ratio = 256.0.pow(2 * rng.nextDouble() - 1)
        val p = checkNotNull(RatioTransformer.solve(a, b, ratio))
        near(p.outputVolts, b.volts + b.ohms * p.outputAmps)
        near(p.inputWatts, p.outputWatts)
        transformerTrials++
    }
    println("PASS $transformerTrials seeded reversible-transformer property trials")
    println("RESULT: $deterministic deterministic tests + ${propertyTrials + transformerTrials} seeded property trials passed.")
    println("SCOPE: algebra corpus; MNA integration and world tests are separate.")
}

class HighVoltageReferenceTest {
    @org.junit.Test fun referenceCorpus() { runReferenceCorpus() }
}

package mods.eln.mechanical

import kotlin.math.PI
import kotlin.test.*

class AdapterDriveTest {
    @Test fun ratiosAndDirections() {
        val drive = AdapterDrive(4000.0, 40.0)
        for (ratio in AdapterDrive.RATIOS) {
            assertEquals(256 * PI / 30 * ratio, drive.target(256.0, ratio), 1e-10)
            assertEquals(drive.target(256.0, ratio), drive.target(-256.0, ratio))
        }
    }
    @Test fun limitedPowerAndTorqueWithInertia() {
        for ((power, torque) in listOf(4000.0 to 40.0, 16000.0 to 160.0)) {
            val drive = AdapterDrive(power, torque)
            val inertia = 100.0; val dt = 0.05
            val watts = drive.requestedPower(256.0, 8, 0.0, inertia, dt)
            assertTrue(watts > 0 && watts <= power)
            val nextSpeed = kotlin.math.sqrt(2 * watts * dt / inertia)
            assertTrue(nextSpeed <= torque / inertia * dt + 1e-9)
        }
    }
    @Test fun stressPaysForEveryJoule() {
        val drive = AdapterDrive(4000.0, 40.0)
        val impact = drive.stressImpact(4000.0, 128.0)
        assertEquals(4000.0 / 0.9, impact * 128, 1e-9)
        assertEquals(200.0, drive.permittedEnergy(4000.0, impact, 128.0, 0.05), 1e-9)
        assertEquals(100.0, drive.permittedEnergy(4000.0, impact / 2, 128.0, 0.05), 1e-9)
        assertEquals(0.0, drive.permittedEnergy(4000.0, 0.0, 128.0, 0.05))
        assertEquals(0.0, drive.permittedEnergy(4000.0, impact, 0.0, 0.05))
    }
    @Test fun freewheelOverspeedAndInvalidInputs() {
        val d = AdapterDrive(4000.0, 40.0)
        assertEquals(0.0, d.requestedPower(256.0, 8, 230.0, 10.0, 0.05))
        assertEquals(0.0, d.requestedPower(512.0, 8, 0.0, 10.0, 0.05))
        for (rpm in listOf(0.0, Double.NaN, Double.POSITIVE_INFINITY)) assertEquals(0.0, d.requestedPower(rpm, 8, 0.0, 10.0, 0.05))
        assertEquals(0.0, d.requestedPower(128.0, 8, 0.0, 0.0, 0.05))
    }
    @Test fun startupCoastsAndRecoversUnderLoad() {
        val d = AdapterDrive(4000.0, 40.0)
        var energy = 0.0; val inertia = 5.0; val dt = 0.05
        repeat(2000) {
            val omega = kotlin.math.sqrt(2 * energy / inertia)
            val request = d.requestedPower(256.0, 8, omega, inertia, dt)
            energy += d.permittedEnergy(request, d.stressImpact(request, 256.0), 256.0, dt)
            energy = (energy - 5.0 * omega * dt).coerceAtLeast(0.0)
        }
        val speed = kotlin.math.sqrt(2 * energy / inertia)
        assertTrue(speed in 190.0..214.5)
        val before = energy
        energy -= 1000 * dt // Input disconnected: load keeps consuming stored energy.
        assertTrue(energy < before)
    }
}

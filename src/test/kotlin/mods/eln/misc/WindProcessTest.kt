package mods.eln.misc

import kotlin.test.Test
import kotlin.test.assertEquals

class WindProcessTest {
    @Test
    fun windScalesWithAltitudeInsteadOfReturningAltitudeAsSpeed() {
        val wind = WindProcess().apply { windHit = 5.0 }

        assertEquals(4.95, wind.getWind(99), 1e-9)
        assertEquals(5.0, wind.getWind(100), 1e-9)
        assertEquals(0.0, wind.getWind(0), 1e-9)
    }
}

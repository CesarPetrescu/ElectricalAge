package mods.eln.misc

import mods.eln.Eln
import mods.eln.disableLog4jJmx
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every shaft machine's model loads, and the parts its descriptor draws are there with faces:
 * a descriptor whose `obj.getPart(...)` came back null drew nothing at all in play (the
 * turbines and the radial motor), and the model loader only says so under debug logging.
 */
class ShaftModelsTest {
    private fun partsOf(obj: Obj3D, vararg names: String): List<String> =
        names.filter { name -> obj.getPart(name)?.faceGroup?.any { it.face.isNotEmpty() } != true }

    @Test
    fun everyShaftMachineModelHasItsParts() {
        disableLog4jJmx()
        val folder = Eln.obj
        val expected = mapOf(
            "Turbine" to arrayOf("Cowl", "Stand", "Shaft", "Fan"),
            "GasTurbine" to arrayOf("Cowl", "Stand", "Shaft", "Fan"),
            "Generator" to arrayOf("Cowl", "Stand", "Shaft", "LED_0", "LED_1", "LED_2", "LED_3", "LED_4", "LED_5", "LED_6"),
            "PolarizedShaftGenerator" to arrayOf("Cowl", "Stand", "Shaft", "LED_0", "LED_6"),
            "Motor" to arrayOf("Cowl", "Stand", "Shaft", "LED_0", "LED_6"),
            "PolarizedShaftMotor" to arrayOf("Cowl", "Stand", "Shaft", "LED_0", "LED_6"),
            "Flywheel" to arrayOf("Stand", "Cowl", "Flywheel", "Shaft"),
            "Clutch" to arrayOf("Cowl", "Stand", "ShaftXN", "ShaftXP"),
            "FixedShaft" to arrayOf("Stand", "Shaft"),
            "StraightJoint" to arrayOf("Stand", "Cowl", "Shaft"),
            "VerticalHub" to arrayOf("Stand", "Cowl", "Shaft", "Cap"),
            "Tachometer" to arrayOf("Stand", "Cowl", "Shaft"),
            "Starter_Motor" to arrayOf("Shaft", "Body_Cylinder.001"),
            "platemachinea" to arrayOf("main", "rot1", "rot2"),
        )
        val problems = ArrayList<String>()
        for ((model, parts) in expected) {
            val obj = folder.getObj(model)
            val missing = partsOf(obj, *parts)
            if (missing.isNotEmpty()) problems.add("$model: ${missing.joinToString()}")
        }
        assertTrue(problems.isEmpty(), "models without their parts: $problems")
    }
}

package mods.eln.mechanical

import com.google.gson.JsonParser
import kotlin.test.*

/** Asset contracts; actual appearance is also captured by the Create client smoke run. */
class CreateAdapterModelTest {
    private fun model(path: String) = javaClass.getResourceAsStream("/assets/eln/models/$path.json")!!.bufferedReader().use {
        JsonParser.parseReader(it).asJsonObject
    }

    @Test fun coversAreConsistentAndDoNotRestoreExposedControls() {
        val block = model("block/create_shaft_adapter")
        val item = model("item/create_shaft_adapter")
        val elements = block.getAsJsonArray("elements")
        val preview = item.getAsJsonArray("elements")
        assertEquals(elements.size() + 2, preview.size())
        elements.forEachIndexed { index, element ->
            assertEquals(element, preview[index], "Inventory housing must match the placed block")
            val obj = element.asJsonObject
            for (axis in 0..2) {
                val from = obj.getAsJsonArray("from")[axis].asDouble
                val to = obj.getAsJsonArray("to")[axis].asDouble
                assertTrue(from >= 0 && to <= 16 && from < to, "Geometry stays inside the block")
            }
        }
        for (side in listOf("top", "bottom", "left", "right")) {
            assertEquals(4, elements.count { it.asJsonObject.get("name")?.asString == "flush bolt $side" })
            assertEquals(1, elements.count { it.asJsonObject.get("name")?.asString == "recessed signal socket $side" })
        }
        assertFalse(block.toString().contains("copper_block"))
        assertFalse(block.toString().contains("redstone_block"))
        assertEquals("eln:block/create_shaft_adapter", model("block/industrial_create_shaft_adapter")["parent"].asString)
        assertEquals("eln:item/create_shaft_adapter", model("item/industrial_create_shaft_adapter")["parent"].asString)
    }
}

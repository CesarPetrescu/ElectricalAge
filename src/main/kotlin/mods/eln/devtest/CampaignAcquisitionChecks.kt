package mods.eln.devtest

import mods.eln.Eln
import mods.eln.misc.RecipesList
import mods.eln.transparentnode.WireProductionRecipes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.tick.ServerTickEvent

/** Targeted survival acquisition contract. Hidden, damaged, creative and seasonal entries are not blanket failures. */
@EventBusSubscriber(modid = Eln.MODID)
object CampaignAcquisitionChecks {
    private var ticks = 0
    @SubscribeEvent fun tick(event: ServerTickEvent.Post) {
        if (System.getProperty("eln.campaign") != "acquisition" || ++ticks != 30) return
        val report = ContractReport("survival-acquisition")
        report.write(false)
        try {
            val world = event.server.overworld()
            fun key(stack: ItemStack) = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            val recipes = world.recipeManager.recipes
            val machineOutputs = RecipesList.listOfList.flatMap { it.recipes }.flatMap { it.output.toList() }
            val wireOutputs = WireProductionRecipes.steps().map { it.output }
            for (id in listOf("eln:wire_snips", "eln:polarized_shaft_generator", "eln:polarized_shaft_motor")) {
                report.test(id, "normal-item-registered") {
                    check(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id)))
                }
                report.test(id, "declared-crafting-or-processing-acquisition") {
                    val direct = recipes.filter { key(it.value().getResultItem(world.registryAccess())) == id }.map { it.id().toString() }
                    val processing = (machineOutputs + wireOutputs).count { key(it) == id }
                    check(direct.isNotEmpty() || processing > 0) {
                        "No loaded recipe output across any RecipeManager type or registered processing/wire recipe: $id. This proves absent declared recipes, not impossibility under arbitrary external loot/datapacks."
                    }
                }
            }
            report.write(true)
        } catch (t: Throwable) {
            report.test("campaign", "unexpected") { throw t }
            report.write(false)
        }
        if (report.failures > 0) {
            val thread = event.server.runningThread
            Thread({ thread.join(); System.exit(1) }, "acquisition-campaign-exit").start()
        }
        event.server.halt(false)
    }
}

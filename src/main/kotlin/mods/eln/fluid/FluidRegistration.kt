package mods.eln.fluid

import mods.eln.Eln
import mods.eln.registration.ElnRegistry
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.BucketItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LiquidBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.FlowingFluid
import net.minecraft.world.level.material.Fluid
import net.neoforged.neoforge.fluids.BaseFlowingFluid
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

/**
 * The mod's own fluids ([ElnFluidRegistry]) on the 1.21 shape: a [FluidType] carries what
 * 1.7.10's `Fluid` did (density, viscosity, light, temperature), the fluid itself is a
 * still/flowing pair, the world block is a [LiquidBlock], and the bucket is an item of its own
 * (the universal bucket of 1.12 is gone again). Textures and tint come from the client
 * extensions registered in [mods.eln.client.ClientSetup].
 */
object FluidRegistration {
    class Entry(val def: ElnFluidRegistry) {
        lateinit var type: Supplier<FluidType>
        lateinit var source: Supplier<Fluid>
        lateinit var flowing: Supplier<Fluid>
        lateinit var block: Supplier<Block>
        lateinit var bucket: Supplier<Item>
    }

    @JvmStatic
    val entries = LinkedHashMap<ElnFluidRegistry, Entry>()

    @JvmStatic
    fun registerElnFluids() {
        for (def in ElnFluidRegistry.values()) {
            val entry = Entry(def)
            entries[def] = entry
            entry.type = ElnRegistry.register(NeoForgeRegistries.Keys.FLUID_TYPES, def.name) {
                FluidType(
                    FluidType.Properties.create()
                        .descriptionId("fluid.eln.${def.name}")
                        .density(def.density).viscosity(def.viscosity)
                        .lightLevel(def.luminosity).temperature(def.temperature)
                        .canConvertToSource(false)
                )
            }
            val properties = BaseFlowingFluid.Properties({ entry.type.get() }, { entry.source.get() }, { entry.flowing.get() })
                .block { entry.block.get() as LiquidBlock }
                .bucket { entry.bucket.get() }
            entry.source = ElnRegistry.register(Registries.FLUID, def.name) { BaseFlowingFluid.Source(properties) }
            entry.flowing = ElnRegistry.register(Registries.FLUID, "flowing_${def.name}") { BaseFlowingFluid.Flowing(properties) }
            entry.block = ElnRegistry.registerBlock(def.name, {
                LiquidBlock(entry.source.get() as FlowingFluid, BlockBehaviour.Properties.ofFullCopy(Blocks.WATER))
            }, null)
            if (def.isBucketable) {
                entry.bucket = ElnRegistry.registerItem("${def.name}_bucket", {
                    BucketItem(entry.source.get(), Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1))
                })
                val bucket = entry.bucket
                ElnRegistry.registerCustomItemStack("${def.name}_bucket") { net.minecraft.world.item.ItemStack(bucket.get()) }
            } else {
                entry.bucket = Supplier { Items.AIR }
            }
            ElnRegistry.afterItems {
                Eln.fluids[def] = entry.source.get()
                Eln.fluidBlocks[def] = entry.block.get()
            }
        }
    }
}

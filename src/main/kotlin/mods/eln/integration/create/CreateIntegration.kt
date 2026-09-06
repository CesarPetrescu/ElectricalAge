package mods.eln.integration.create

import mods.eln.Eln
import mods.eln.generic.CreativeTabPopulator
import mods.eln.i18n.I18N
import mods.eln.i18n.I18N.tr
import mods.eln.registration.ElnRegistry
import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.inventory.MenuType
import java.util.function.Supplier

/** Reached only after ModList confirms Create is installed. */
object CreateIntegration {
    lateinit var basic: Supplier<Block>
    lateinit var industrial: Supplier<Block>
    lateinit var basicType: Supplier<BlockEntityType<CreateAdapterEntity>>
    lateinit var industrialType: Supplier<BlockEntityType<CreateAdapterEntity>>
    lateinit var menu: Supplier<MenuType<CreateAdapterMenu>>

    @JvmStatic fun register() {
        val basicName = I18N.TR_NAME(I18N.Type.NONE, "Create Shaft Adapter")
        val industrialName = I18N.TR_NAME(I18N.Type.NONE, "Industrial Create Shaft Adapter")
        fun item(block: Block, name: String) = object : BlockItem(block, Item.Properties()) {
            override fun getName(stack: ItemStack): Component = Component.literal(tr(name))
        }
        basic = ElnRegistry.registerBlock("create_shaft_adapter", { CreateAdapterBlock(false) }, { item(it, basicName) })
        industrial = ElnRegistry.registerBlock("industrial_create_shaft_adapter", { CreateAdapterBlock(true) }, { item(it, industrialName) })
        basicType = ElnRegistry.registerBlockEntity("create_shaft_adapter", basic) { pos, state -> CreateAdapterEntity(basicType.get(), pos, state, false) }
        industrialType = ElnRegistry.registerBlockEntity("industrial_create_shaft_adapter", industrial) { pos, state -> CreateAdapterEntity(industrialType.get(), pos, state, true) }
        menu = ElnRegistry.registerMenu("create_adapter") { id, inventory, _ -> CreateAdapterMenu(id, inventory) }
        CreativeTabPopulator.register(Eln.creativeTabMechanics) { ItemStack(basic.get()) }
        CreativeTabPopulator.register(Eln.creativeTabMechanics) { ItemStack(industrial.get()) }
        if (System.getProperty("eln.createSmoke") != null) CreateAdapterSmoke.register()
    }
}

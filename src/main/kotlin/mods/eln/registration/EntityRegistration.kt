package mods.eln.registration

import mods.eln.Eln
import mods.eln.entity.ReplicatorEntity
import mods.eln.i18n.I18N
import mods.eln.railroad.EntityElectricMinecart
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.neoforged.neoforge.common.DeferredSpawnEggItem

/**
 * The mod's entities: the replicator and the electric minecart. Up to 1.12 these were registered
 * from CraftingRecipes (`EntityRegistry.registerModEntity`); 1.21 makes the type, its attributes
 * and the spawn egg registry objects. The tracking range (80) and update frequency (3) are
 * 1.7.10's; the replicator's egg colours are upstream's red/orange.
 */
object EntityRegistration {

    fun registerEntities() {
        registerReplicator()
        registerElectricMinecart()
    }

    private fun registerReplicator() {
        val redColor = (255 shl 16)
        val orangeColor = (255 shl 16) + (200 shl 8)
        val name = I18N.TR_NAME(I18N.Type.ENTITY, "EAReplicator")

        ReplicatorEntity.TYPE = ElnRegistry.registerEntityType("replicator") {
            EntityType.Builder.of({ type, level -> ReplicatorEntity(type, level) }, MobCategory.MONSTER)
                .sized(0.3f, 0.7f)
                .clientTrackingRange(80 / 16)
                .updateInterval(3)
        }
        ElnRegistry.registerAttributes({ ReplicatorEntity.TYPE.get() }) { ReplicatorEntity.createAttributes() }
        ReplicatorEntity.SPAWN_EGG = ElnRegistry.registerItem("replicator_spawn_egg", {
            DeferredSpawnEggItem(ReplicatorEntity.TYPE, redColor, orangeColor, Item.Properties())
        })
        ElnRegistry.afterItems {
            ReplicatorEntity.dropList.add(Eln.findItemStack("Iron Dust", 1))
            ReplicatorEntity.dropList.add(Eln.findItemStack("Copper Dust", 1))
            ReplicatorEntity.dropList.add(Eln.findItemStack("Gold Dust", 1))
            ReplicatorEntity.dropList.add(ItemStack(Items.REDSTONE))
            ReplicatorEntity.dropList.add(ItemStack(Items.GLOWSTONE_DUST))
        }
        // EntityRegistry.addSpawn(ReplicatorEntity.class, 1, 1, 2, EnumCreatureType.monster, Biome.plains);
        Eln.LOGGER.debug("registered entity {}", name)
    }

    private fun registerElectricMinecart() {
        EntityElectricMinecart.TYPE = ElnRegistry.registerEntityType("electric_minecart") {
            EntityType.Builder.of({ type, level -> EntityElectricMinecart(type, level) }, MobCategory.MISC)
                .sized(0.98f, 0.7f)
                .clientTrackingRange(80 / 16)
                .updateInterval(3)
        }
    }
}

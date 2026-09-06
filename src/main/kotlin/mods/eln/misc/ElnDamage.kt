package mods.eln.misc

import mods.eln.Eln
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.entity.Entity

/**
 * The mod's damage sources. 1.7.10 made them by name (`new DamageSource("electrical_cable")`);
 * since 1.19.4 a damage type is a data-pack registry entry (data/eln/damage_type/<name>.json) with a
 * `death.attack.<message_id>` lang key.
 */
object ElnDamage {
    @JvmField
    val ELECTRICAL: ResourceKey<DamageType> = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Eln.MODID, "electrical"))

    @JvmField
    val TURRET: ResourceKey<DamageType> = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Eln.MODID, "turret"))

    @JvmStatic
    fun of(entity: Entity, type: ResourceKey<DamageType>): DamageSource {
        val holder = entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(type).orElse(null)
        return if (holder != null) DamageSource(holder) else entity.damageSources().generic()
    }

    @JvmStatic
    fun electrical(entity: Entity): DamageSource = of(entity, ELECTRICAL)

    @JvmStatic
    fun turret(entity: Entity): DamageSource = of(entity, TURRET)
}

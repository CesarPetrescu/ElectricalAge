package mods.eln.worldgen

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import mods.eln.Eln
import net.minecraft.core.Holder
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.placement.PlacedFeature
import net.neoforged.neoforge.common.world.BiomeModifier
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo

/**
 * Adds one of the mod's ore features to every overworld biome, if Eln.cfg allows it. The
 * feature and its placement are data (generated from the [mods.eln.ore.OreDescriptor] spawn
 * numbers); 1.7.10 gated generation on `worldgen.ores.<ore>.enabled` at registration, which a
 * data pack cannot read, so the modifier reads the config when biomes are assembled.
 */
class ElnOreBiomeModifier(val feature: Holder<PlacedFeature>, val configKey: String, val configDefault: Boolean) : BiomeModifier {

    override fun modify(biome: Holder<Biome>, phase: BiomeModifier.Phase, builder: ModifiableBiomeInfo.BiomeInfo.Builder) {
        if (phase != BiomeModifier.Phase.ADD) return
        if (!biome.`is`(BiomeTags.IS_OVERWORLD)) return
        if (!Eln.config.getBooleanOrElse(configKey, configDefault)) return
        builder.generationSettings.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, feature)
    }

    override fun codec(): MapCodec<out BiomeModifier> = CODEC

    companion object {
        @JvmField
        val CODEC: MapCodec<ElnOreBiomeModifier> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                PlacedFeature.CODEC.fieldOf("feature").forGetter { it.feature },
                Codec.STRING.fieldOf("config").forGetter { it.configKey },
                Codec.BOOL.optionalFieldOf("enabled_by_default", true).forGetter { it.configDefault }
            ).apply(instance, ::ElnOreBiomeModifier)
        }
    }
}

package mods.eln.generic

import mods.eln.Eln
import mods.eln.mechanical.SimpleShaftDescriptor
import net.minecraft.world.item.CreativeModeTab

/** Route by descriptor family, without changing registry identities or using translated names. */
object CreativeCategories {
    @JvmStatic
    fun resolve(descriptor: GenericItemBlockUsingDamageDescriptor, fallback: CreativeModeTab): CreativeModeTab {
        val type = descriptor.javaClass.name
        val name = descriptor.javaClass.simpleName.lowercase(java.util.Locale.ROOT)
        if (name.contains("portablenan")) return Eln.creativeTabCreative
        if (descriptor is SimpleShaftDescriptor) return Eln.creativeTabMechanics
        if (name.contains("gridtransformer")) return Eln.creativeTabPowerElectronics
        if (type.contains(".groundcable.") || type.contains(".electricalcable.") ||
            type.contains(".thermalcable.") || type.contains(".cable.")) {
            return if (fallback == Eln.creativeTabCreative) fallback else Eln.creativeTabCables
        }
        if (name.contains("sensor") || name.contains("vumeter") || name.contains("alarm") || name.contains("nixietube") ||
            name.contains("regulator") || type.contains(".electricalrelay.") || type.contains(".electricalswitch.") ||
            type.contains(".electricaldatalogger.") || type.contains(".electricalgate.") ||
            type.contains(".electricalsensor.")) return Eln.creativeTabSignalProcessing
        return fallback
    }
}

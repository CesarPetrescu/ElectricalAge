package mods.eln.solver

import mods.eln.Eln
import mods.eln.bootstrapMinecraft
import mods.eln.misc.FunctionTable

internal fun ensureBatteryVoltageTable(table: FunctionTable) {
    bootstrapMinecraft()
    if (Eln.instance == null) {
        Eln.instance = Eln()
    }
    Eln.instance.batteryVoltageFunctionTable = table
}

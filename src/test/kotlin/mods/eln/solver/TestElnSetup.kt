package mods.eln.solver

import mods.eln.Eln
import mods.eln.bootstrapMinecraft
import mods.eln.misc.FunctionTable

internal fun ensureBatteryVoltageTable(table: FunctionTable) {
    bootstrapMinecraft()
    if (Eln.instance == null) {
        Eln.newTestInstance()
    }
    Eln.instance.batteryVoltageFunctionTable = table
}

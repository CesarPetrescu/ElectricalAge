package mods.eln.sixnode.electricaldatalogger

object MultiplayerMonitorClient {
    fun matches(r: ElectricalDataLoggerRender): Boolean = !r.waitFistSync && r.pause && r.log.size() == 3 &&
        r.log.maxValue == 12f && r.log.minValue == -12f && r.log.samplingPeriod == 2f &&
        r.log.read(0) == 127.toByte() && r.log.read(2) == (-128).toByte()

    fun clickPrint(gui: ElectricalDataLoggerGui) {
        check(gui.printBt.enabled && gui.printBt.visible)
        // Go through the actual screen/button hit test, not directly to the element handler.
        gui.mouseClicked((gui.guiLeft + 88).toDouble(), (gui.guiTop + 188).toDouble(), 0)
        gui.mouseReleased((gui.guiLeft + 88).toDouble(), (gui.guiTop + 188).toDouble(), 0)
    }
}

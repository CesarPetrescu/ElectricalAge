package mods.eln.transparentnode.evaporative;

/** Server-owned bounded controls. Menu actions are discrete commands, not trusted client values. */
public final class EvaporativeControls {
    public static final int OFF = 0, DRY = 1, AUTO = 2, WET = 3;
    private int mode = AUTO, targetCelsius = 40, fanPercent = 100, redstoneMode;
    private boolean autoFan, autoWet;
    public int mode() { return mode; }
    public int targetCelsius() { return targetCelsius; }
    public int fanPercent() { return fanPercent; }
    public int redstoneMode() { return redstoneMode; }

    public void restore(int mode, int target, int fan, int redstone) {
        this.mode = Math.clamp(mode, OFF, WET);
        targetCelsius = Math.clamp(target, 5, 90);
        fanPercent = Math.clamp(fan, 10, 100);
        redstoneMode = Math.clamp(redstone, 0, 2);
        autoFan = autoWet = false;
    }
    public boolean command(int id) {
        switch (id) {
            case 0, 1, 2, 3 -> mode = id;
            case 10 -> targetCelsius = Math.max(5, targetCelsius - 5);
            case 11 -> targetCelsius = Math.max(5, targetCelsius - 1);
            case 12 -> targetCelsius = Math.min(90, targetCelsius + 1);
            case 13 -> targetCelsius = Math.min(90, targetCelsius + 5);
            case 20 -> fanPercent = Math.max(10, fanPercent - 10);
            case 21 -> fanPercent = Math.min(100, fanPercent + 10);
            case 30 -> redstoneMode = (redstoneMode + 1) % 3;
            default -> { return false; }
        }
        return true;
    }
    public record Demand(double speed, boolean wet) {}
    public Demand demand(double temperature, boolean redstonePowered) {
        if (!Double.isFinite(temperature) || mode == OFF
                || (redstoneMode == 1 && !redstonePowered) || (redstoneMode == 2 && redstonePowered)) {
            autoFan = autoWet = false;
            return new Demand(0, false);
        }
        if (temperature >= targetCelsius + 2) autoFan = true;
        if (temperature <= targetCelsius) autoFan = false;
        if (temperature >= targetCelsius + 5) autoWet = true;
        if (temperature <= targetCelsius + 2) autoWet = false;
        boolean fan = mode != AUTO || autoFan;
        return new Demand(fan ? fanPercent / 100.0 : 0.0, fan && (mode == WET || (mode == AUTO && autoWet)));
    }
}

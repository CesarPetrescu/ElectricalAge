package mods.eln.transparentnode.evaporative;

/**
 * Lumped, Lewis-relation wetted heat exchanger. SI units; independent of the game and tick rate.
 * Saturation pressure uses the ASHRAE equations documented by PsychroLib (see the feature guide).
 * This is a bounded engineering approximation, not a detailed cooling-tower performance model.
 */
public final class EvaporationModel {
    private EvaporationModel() {}
    public static final double PRESSURE_PA = 101325.0;
    public static final double KG_PER_MB = 0.001;
    public static final double AIR_CP = 1006.0;

    public static double saturationPressure(double celsius) {
        requireFinite(celsius);
        if (celsius < -100 || celsius > 150) throw new IllegalArgumentException("Temperature outside psychrometric domain");
        double t = celsius + 273.15;
        double ln = celsius <= 0.01
            ? -5674.5359/t + 6.3925247 - .009677843*t + 6.2215701e-7*t*t
              + 2.0747825e-9*t*t*t - 9.484024e-13*t*t*t*t + 4.1635019*Math.log(t)
            : -5800.2206/t + 1.3914993 - .048640239*t + 4.1764768e-5*t*t
              - 1.4452093e-8*t*t*t + 6.5459673*Math.log(t);
        return Math.exp(ln);
    }

    public static double humidityRatio(double airCelsius, double relativeHumidityPercent) {
        requireFinite(relativeHumidityPercent);
        if (relativeHumidityPercent < 0 || relativeHumidityPercent > 100) throw new IllegalArgumentException("RH outside 0..100");
        double p = saturationPressure(airCelsius) * relativeHumidityPercent / 100.0;
        if (p >= PRESSURE_PA) throw new IllegalArgumentException("Vapor pressure exceeds atmospheric pressure");
        return .621945 * p / (PRESSURE_PA - p);
    }

    public static double latentHeat(double surfaceCelsius) {
        requireFinite(surfaceCelsius);
        return 2_501_000.0 - 2360.0 * Math.clamp(surfaceCelsius, 0.0, 100.0);
    }

    /** Lewis-model equilibrium. Uses water, not ice; wet operation is disabled at freezing. */
    public static double wetBulbEstimate(double airCelsius, double relativeHumidityPercent) {
        double w = humidityRatio(airCelsius, relativeHumidityPercent);
        double lo = -99.0, hi = airCelsius;
        for (int i = 0; i < 48; i++) {
            double t = (lo + hi) * .5;
            double balance = AIR_CP * (t - airCelsius)
                + latentHeat(t) * (humidityRatio(t, 100.0) - w);
            if (balance > 0) hi = t; else lo = t;
        }
        return (lo + hi) * .5;
    }

    public record Step(double sensibleWatts, double evaporationWatts, double waterMb,
                       double electricalHeatWatts, double wetBulbCelsius) {
        /** Positive means heat rejected by the cooler. Sensible heat may be negative. */
        public double netCoolingWatts() { return sensibleWatts + evaporationWatts - electricalHeatWatts; }
    }

    public static Step step(double surfaceCelsius, double airCelsius, double relativeHumidityPercent,
                            double airConductance, double evaporationLimitWatts, double availableWaterMb,
                            double electricalHeatWatts, double heatCapacity, double seconds, boolean wetEnabled) {
        for (double v : new double[]{surfaceCelsius, airCelsius, relativeHumidityPercent, airConductance,
                evaporationLimitWatts, availableWaterMb, electricalHeatWatts, heatCapacity, seconds}) requireFinite(v);
        if (seconds <= 0 || seconds > 1 || heatCapacity <= 0 || airConductance < 0 || evaporationLimitWatts < 0
                || availableWaterMb < 0 || electricalHeatWatts < 0) throw new IllegalArgumentException("Invalid model input");
        return step(surfaceCelsius, environment(airCelsius, relativeHumidityPercent), airConductance,
                    evaporationLimitWatts, availableWaterMb, electricalHeatWatts, heatCapacity, seconds, wetEnabled);
    }

    /** Cache this at the environmental sampling rate, never recalculate wet-bulb at 400 Hz. */
    public record Environment(double airCelsius, double humidityRatio, double wetBulbCelsius) {}
    public static Environment environment(double airCelsius, double relativeHumidityPercent) {
        return new Environment(airCelsius, humidityRatio(airCelsius, relativeHumidityPercent),
                               wetBulbEstimate(airCelsius, relativeHumidityPercent));
    }
    public static Step step(double surfaceCelsius, Environment environment, double airConductance,
                            double evaporationLimitWatts, double availableWaterMb, double electricalHeatWatts,
                            double heatCapacity, double seconds, boolean wetEnabled) {
        for (double v : new double[]{surfaceCelsius, environment.airCelsius, environment.humidityRatio,
                environment.wetBulbCelsius, airConductance, evaporationLimitWatts, availableWaterMb,
                electricalHeatWatts, heatCapacity, seconds}) requireFinite(v);
        if (seconds <= 0 || seconds > 1 || heatCapacity <= 0 || airConductance < 0 || evaporationLimitWatts < 0
                || availableWaterMb < 0 || electricalHeatWatts < 0) throw new IllegalArgumentException("Invalid model input");
        double airCelsius = environment.airCelsius;
        double sensible = airConductance * (surfaceCelsius - airCelsius);
        double wAir = environment.humidityRatio;
        double wetBulb = environment.wetBulbCelsius;
        double latent = 0.0;
        if (wetEnabled && surfaceCelsius > 1 && surfaceCelsius < 95 && airCelsius > 0 && availableWaterMb > 0) {
            double massRate = airConductance / AIR_CP * Math.max(0, humidityRatio(surfaceCelsius, 100.0) - wAir);
            latent = Math.min(evaporationLimitWatts, massRate * latentHeat(surfaceCelsius));
            latent = Math.min(latent, availableWaterMb * KG_PER_MB * latentHeat(surfaceCelsius) / seconds);
            // Do not numerically overcool past the wet-bulb equilibrium in a single step.
            // At equilibrium, evaporation is still allowed to balance heat arriving from the air.
            double equilibriumBudget = heatCapacity * Math.max(0, surfaceCelsius - wetBulb) / seconds
                - sensible + electricalHeatWatts;
            latent = Math.min(latent, Math.max(0, equilibriumBudget));
        }
        return new Step(sensible, latent, latent * seconds / (KG_PER_MB * latentHeat(surfaceCelsius)),
                        electricalHeatWatts, wetBulb);
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite model input");
    }
}

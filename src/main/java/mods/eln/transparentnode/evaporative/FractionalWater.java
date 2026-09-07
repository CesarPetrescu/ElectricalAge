package mods.eln.transparentnode.evaporative;

import java.util.function.IntUnaryOperator;

/** A pre-paid film of less than one mB, kept separately from the integer-capacity tank. */
public final class FractionalWater {
    private double filmMb;
    public double filmMb() { return filmMb; }
    public void restore(double value) { filmMb = Double.isFinite(value) ? Math.clamp(value, 0.0, Math.nextDown(1.0)) : 0; }
    public double consume(double requestedMb, int tankMb, IntUnaryOperator executeDrain) {
        if (!Double.isFinite(requestedMb) || requestedMb < 0 || tankMb < 0) throw new IllegalArgumentException("Invalid water request");
        double wanted = Math.min(requestedMb, tankMb + filmMb);
        int needed = (int)Math.ceil(Math.max(0, wanted - filmMb));
        int drained = needed == 0 ? 0 : executeDrain.applyAsInt(needed);
        if (drained < 0 || drained > needed) throw new IllegalStateException("Fluid handler violated drain contract");
        double paid = filmMb + drained;
        double consumed = Math.min(wanted, paid);
        filmMb = Math.max(0, paid - consumed);
        return consumed;
    }
}

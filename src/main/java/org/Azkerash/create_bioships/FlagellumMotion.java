package org.Azkerash.create_bioships;

public final class FlagellumMotion {
    private FlagellumMotion() {}
    public static double strength(int available, long ticks) {
        double stroke = Math.sin(ticks * Math.PI / 15.0);
        return Math.clamp(available, 0, 6) / 6.0 * (0.75 + 0.25 * stroke * stroke);
    }
}

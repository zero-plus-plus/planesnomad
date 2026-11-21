package com.planesnomad.gui.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * Colors, fonts and sizes for the match screen.
 *
 * Sizes are expressed at the reference resolution (1280x720) and multiplied by
 * {@link #scale()}, which follows the window size. That keeps the layout proportional, so a
 * separate small-screen layout can be added later by rearranging the same panels.
 */
public final class Theme {

    public static final int REF_WIDTH = 1280;
    public static final int REF_HEIGHT = 720;

    /** Card width / height (standard card proportions, 63 x 88 mm). */
    public static final double CARD_ASPECT = 63.0 / 88.0;

    // Table and panels
    public static final Color TABLE = new Color(0x1E2A24);
    public static final Color TABLE_ENEMY = new Color(0x22262E);
    public static final Color MIDLINE = new Color(0x6B7F73);
    public static final Color PANEL = new Color(0x15181D);
    public static final Color PANEL_EDGE = new Color(0x2C323B);
    public static final Color TOPBAR = new Color(0x101317);
    public static final Color TEXT = new Color(0xE8E4D8);
    public static final Color TEXT_DIM = new Color(0x9AA0A8);
    public static final Color ACCENT = new Color(0xE3B341);
    public static final Color YOU = new Color(0x4F9D69);
    public static final Color ENEMY = new Color(0xC4544B);
    public static final Color LIFE = new Color(0xE0605A);

    // Card states
    public static final Color ATTACKING = new Color(0xFF6B4A);
    public static final Color BLOCKING = new Color(0x58A6FF);
    public static final Color DAMAGE = new Color(0xFF3B30);

    // Relic states
    public static final Color RELIC_GLOW = new Color(0xFFE08A);
    public static final Color RELIC_USED = new Color(0x5A5F66);

    private static double scale = 1.0;

    private Theme() { }

    /** Current UI scale; 1.0 at 1280x720. */
    public static double scale() {
        return scale;
    }

    /** Recompute the scale from the window's content size. */
    public static void updateScale(int width, int height) {
        double s = Math.min(width / (double) REF_WIDTH, height / (double) REF_HEIGHT);
        scale = Math.max(0.75, Math.min(3.0, s));
    }

    public static int px(double referencePixels) {
        return (int) Math.round(referencePixels * scale);
    }

    public static Font font(int style, double referenceSize) {
        return new Font(Font.SANS_SERIF, style, Math.max(8, px(referenceSize)));
    }

    /** Frame color for a card from its color letters (W U B R G), like a card border. */
    public static Color cardFrame(String colors, boolean land) {
        if (colors == null || colors.isEmpty()) {
            return land ? new Color(0x8C7A5B) : new Color(0x9EA3A8);
        }
        if (colors.length() > 1) {
            return new Color(0xC9A646);
        }
        return switch (colors.charAt(0)) {
            case 'W' -> new Color(0xE9E2C8);
            case 'U' -> new Color(0x3A7BC8);
            case 'B' -> new Color(0x4A4048);
            case 'R' -> new Color(0xC8503A);
            case 'G' -> new Color(0x3F8F4E);
            default -> new Color(0x9EA3A8);
        };
    }

    /** Placeholder "art" fill for the picture box until card images are downloaded. */
    public static Color cardArt(String colors, boolean land) {
        Color frame = cardFrame(colors, land);
        return blend(frame, new Color(0x202020), 0.45);
    }

    public static Color blend(Color a, Color b, double t) {
        return new Color(
            (int) (a.getRed() * (1 - t) + b.getRed() * t),
            (int) (a.getGreen() * (1 - t) + b.getGreen() * t),
            (int) (a.getBlue() * (1 - t) + b.getBlue() * t));
    }

    /** Readable text color on top of the given background. */
    public static Color textOn(Color background) {
        double lum = 0.299 * background.getRed() + 0.587 * background.getGreen() + 0.114 * background.getBlue();
        return lum > 150 ? new Color(0x1A1A1A) : new Color(0xF4F1E8);
    }
}

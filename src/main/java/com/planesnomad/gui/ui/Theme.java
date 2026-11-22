package com.planesnomad.gui.ui;

import java.awt.Color;
import java.awt.Font;
import com.planesnomad.gui.ui.theme.ThemePack;

/**
 * Colors, fonts and sizes for the match screen, from the current theme ({@link ThemePack}).
 * The values below are the built-in look; {@link #apply(ThemePack)} replaces them with a
 * theme's (a designer's files, see {@code themes/README.md}). Panels, pop-ups, badges and
 * buttons are drawn by {@link com.planesnomad.gui.ui.theme.Skin}.
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
    public static Color TABLE = new Color(0x1E2A24);
    public static Color TABLE_ENEMY = new Color(0x22262E);
    public static Color MIDLINE = new Color(0x6B7F73);
    public static Color PANEL = new Color(0x15181D);
    public static Color PANEL_EDGE = new Color(0x2C323B);
    public static Color TOPBAR = new Color(0x101317);
    public static Color TEXT = new Color(0xE8E4D8);
    public static Color TEXT_DIM = new Color(0x9AA0A8);
    public static Color ACCENT = new Color(0xE3B341);
    public static Color YOU = new Color(0x4F9D69);
    public static Color ENEMY = new Color(0xC4544B);
    public static Color LIFE = new Color(0xE0605A);

    // Card states
    public static Color ATTACKING = new Color(0xFF6B4A);
    public static Color BLOCKING = new Color(0x58A6FF);
    public static Color DAMAGE = new Color(0xFF3B30);

    // Relic states
    public static Color RELIC_GLOW = new Color(0xFFE08A);
    public static Color RELIC_USED = new Color(0x5A5F66);

    private static double scale = 1.0;

    // Fonts per role (null = the built-in sans-serif), and a size factor.
    private static Font bodyFont;
    private static Font headingFont;
    private static Font numberFont;
    private static double fontScale = 1.0;
    private static ThemePack pack = ThemePack.builtIn();
    private static final java.util.Map<String, Color> cardFrames = new java.util.HashMap<>();

    private Theme() { }

    /** The theme in use. */
    public static ThemePack pack() {
        return pack;
    }

    /**
     * Use this theme: colors, fonts and the elements drawn by Skin. The screen must be repainted
     * (and fonts re-applied) afterwards; MatchView.applyTheme() does both.
     */
    public static void apply(ThemePack theme) {
        pack = theme == null ? ThemePack.builtIn() : theme;
        ThemePack t = pack;
        TABLE = t.color("color.table", new Color(0x1E2A24));
        TABLE_ENEMY = t.color("color.tableEnemy", new Color(0x22262E));
        MIDLINE = t.color("color.midline", new Color(0x6B7F73));
        PANEL = t.color("color.panel", new Color(0x15181D));
        PANEL_EDGE = t.color("color.panelEdge", new Color(0x2C323B));
        TOPBAR = t.color("color.topbar", new Color(0x101317));
        TEXT = t.color("color.text", new Color(0xE8E4D8));
        TEXT_DIM = t.color("color.textDim", new Color(0x9AA0A8));
        ACCENT = t.color("color.accent", new Color(0xE3B341));
        YOU = t.color("color.you", new Color(0x4F9D69));
        ENEMY = t.color("color.enemy", new Color(0xC4544B));
        LIFE = t.color("color.life", new Color(0xE0605A));
        ATTACKING = t.color("color.attacking", new Color(0xFF6B4A));
        BLOCKING = t.color("color.blocking", new Color(0x58A6FF));
        DAMAGE = t.color("color.damage", new Color(0xFF3B30));
        RELIC_GLOW = t.color("color.relicGlow", new Color(0xFFE08A));
        RELIC_USED = t.color("color.relicUsed", new Color(0x5A5F66));
        bodyFont = t.font("font.body");
        headingFont = t.font("font.heading");
        numberFont = t.font("font.numbers");
        fontScale = t.number("font.scale", 1.0);
        cardFrames.clear();
        for (String k : new String[] {"W", "U", "B", "R", "G", "multi", "colorless", "land"}) {
            Color c = t.color("card.frame." + k, null);
            if (c != null) {
                cardFrames.put(k, c);
            }
        }
    }

    /** Font roles: body text, headings (names, titles), numbers (life totals, counts). */
    public enum FontRole { BODY, HEADING, NUMBERS }

    /** A font in a role; sizes are at 1280x720 and scale with the window. */
    public static Font font(FontRole role, int style, double referenceSize) {
        Font base = switch (role) {
            case HEADING -> headingFont != null ? headingFont : bodyFont;
            case NUMBERS -> numberFont != null ? numberFont : headingFont != null ? headingFont : bodyFont;
            case BODY -> bodyFont;
        };
        float size = (float) Math.max(8, px(referenceSize * fontScale));
        if (base == null) {
            return new Font(Font.SANS_SERIF, style, Math.round(size));
        }
        return base.deriveFont(style, size);
    }

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

    /** Body text; bold text uses the heading font (names, titles, labels). */
    public static Font font(int style, double referenceSize) {
        return font((style & Font.BOLD) != 0 ? FontRole.HEADING : FontRole.BODY, style, referenceSize);
    }

    /** Frame color for a card from its color letters (W U B R G), like a card border. */
    public static Color cardFrame(String colors, boolean land) {
        if (colors == null || colors.isEmpty()) {
            return land ? cardFrames.getOrDefault("land", new Color(0x8C7A5B)) : cardFrames.getOrDefault("colorless", new Color(0x9EA3A8));
        }
        if (colors.length() > 1) {
            return cardFrames.getOrDefault("multi", new Color(0xC9A646));
        }
        Color custom = cardFrames.get(colors.substring(0, 1));
        if (custom != null) {
            return custom;
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

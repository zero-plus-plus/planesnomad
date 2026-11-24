package com.planesnomad.gui.ui.theme;

import com.planesnomad.gui.ui.Theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Paints the screen's swappable elements (panels, pop-ups, badges, buttons, ...) from the theme.
 *
 * Each element has a key (e.g. {@code popup}) and these theme settings, all optional:
 * <pre>
 *   popup.image       = frames/popup.png    picture drawn as the element (see popup.mode)
 *   popup.mode        = slice | stretch | cover | tile
 *                       slice: 9-slice frame - corners keep their size, edges and middle stretch
 *   popup.slice       = 24,24,24,24         slice: corner sizes in the picture (top,left,bottom,right)
 *   popup.sliceScale  = 1.0                 slice: how big corners are drawn (1.0 = picture pixels at 1280x720)
 *   popup.fill        = #15181D             background color (when there is no picture, or behind it)
 *   popup.border      = #2C323B             border color ("none" for no border)
 *   popup.borderWidth = 2                   border thickness (pixels at 1280x720)
 *   popup.radius      = 10 | pill           corner rounding (pixels at 1280x720; pill = fully round ends)
 *   popup.text        = #E8E4D8             text color on this element
 *   popup.sideBorder  = true                pop-ups only: border in the color of the player it is about
 * </pre>
 * Without any settings an element looks like the built-in (default) design.
 */
public final class Skin {

    /** Built-in look per element: fill, border, borderWidth, radius (-1 = pill). */
    private record Base(Color fill, Color border, double borderWidth, double radius) { }

    /** Element keys a theme can style. */
    private static final java.util.List<String> ELEMENTS = java.util.List.of(
        "table.you", "table.enemy", "hand", "panel", "column", "topbar", "popup", "stack",
        "badge.life", "badge.count", "badge.selected", "badge.target", "tag",
        "button", "button.hover", "button.pressed", "button.disabled", "relic.slot", "relic.frame", "consumable.frame", "consumable.empty", "inspect", "prompt", "phases", "choice",
        "reward.backdrop", "reward", "reward.item", "reward.item.hover", "reward.item.claimed", "bubble");

    /**
     * Built-in look per element, from the theme's colors (so changing e.g. color.panel changes
     * every panel unless the element sets its own fill).
     */
    private static Base base(String element) {
        return switch (element) {
            case "table.you" -> new Base(Theme.TABLE, null, 0, 0);
            case "table.enemy" -> new Base(Theme.TABLE_ENEMY, null, 0, 0);
            case "hand" -> new Base(new Color(0, 0, 0, 110), null, 0, 0);
            case "panel", "column" -> new Base(Theme.PANEL, null, 0, 0);
            case "topbar" -> new Base(Theme.TOPBAR, null, 0, 0);
            case "popup" -> new Base(Theme.PANEL, Theme.PANEL_EDGE, 2, 0);
            case "stack" -> new Base(withAlpha(Theme.blend(Theme.PANEL, Color.BLACK, 0.3), 215), Theme.PANEL_EDGE, 1, 5);
            case "badge.life" -> new Base(new Color(10, 12, 16, 210), Theme.LIFE, 1.5, -1);
            case "badge.count" -> new Base(new Color(10, 12, 16, 225), Theme.TEXT_DIM, 1, -1);
            case "badge.selected" -> new Base(Theme.ACCENT, Theme.ACCENT.brighter(), 1, -1);
            case "badge.target" -> new Base(new Color(10, 12, 16, 225), Theme.TEXT_DIM, 1, -1);
            case "tag" -> new Base(Theme.blend(Theme.PANEL, Color.WHITE, 0.12), null, 0, -1);
            case "button" -> new Base(Theme.blend(Theme.PANEL, Color.WHITE, 0.10), Theme.blend(Theme.PANEL_EDGE, Color.WHITE, 0.15), 1, 2);
            case "button.hover" -> new Base(Theme.blend(Theme.PANEL, Color.WHITE, 0.16), Theme.TEXT_DIM, 1, 2);
            case "button.pressed" -> new Base(Theme.blend(Theme.PANEL, Color.BLACK, 0.2), Theme.ACCENT, 1, 2);
            case "button.disabled" -> new Base(Theme.blend(Theme.PANEL, Color.WHITE, 0.04), Theme.PANEL_EDGE, 1, 2);
            case "relic.slot" -> new Base(new Color(0x5B6574), null, 0, -1);
            case "relic.frame", "consumable.frame", "consumable.empty" -> new Base(new Color(0, 0, 0, 0), null, 0, 0);
            case "bubble" -> new Base(new Color(0xF3EEDF), new Color(0x2A2A2A), 1.5, 12);
            case "reward.backdrop" -> new Base(new Color(0, 0, 0, 165), null, 0, 0);
            case "reward" -> new Base(new Color(18, 21, 27, 244), Theme.ACCENT, 2, 12);
            case "reward.item" -> new Base(Theme.blend(Theme.PANEL, Color.WHITE, 0.06), Theme.PANEL_EDGE, 1, 8);
            case "reward.item.hover" -> new Base(Theme.blend(Theme.PANEL, Color.WHITE, 0.14), Theme.ACCENT, 1.5, 8);
            case "reward.item.claimed" -> new Base(Theme.blend(Theme.PANEL, Color.BLACK, 0.25), Theme.PANEL_EDGE, 1, 8);
            case "choice" -> new Base(new Color(10, 12, 16, 225), Theme.ACCENT, 2, 10);
            case "phases" -> new Base(Theme.blend(Theme.PANEL, Color.BLACK, 0.15), Theme.PANEL_EDGE, 0, 0);
            case "prompt" -> new Base(Theme.PANEL, Theme.ACCENT, 2, 8);
            case "inspect" -> new Base(new Color(14, 16, 21, 236), new Color(0x3A414D), 1, 4);
            default -> new Base(Theme.PANEL, null, 0, 0);
        };
    }

    private static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Element keys a theme can style. */
    public static java.util.List<String> elements() {
        return ELEMENTS;
    }

    private static ThemePack pack() {
        return Theme.pack();
    }

    public static void paint(Graphics2D g, String element, int x, int y, int w, int h) {
        paint(g, element, x, y, w, h, null);
    }

    /**
     * Draws the element filling (x, y, w, h).
     * @param sideColor for pop-ups: the color of the player it is about (used when the theme asks
     *                  for side-colored borders, the default); null to use the theme's border
     */
    public static void paint(Graphics2D g0, String element, int x, int y, int w, int h, Color sideColor) {
        if (w <= 0 || h <= 0) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        ThemePack t = pack();
        Base base = base(element);
        BufferedImage img = t.image(element + ".image");
        // With a picture, the picture is the look: no default fill or border (unless set).
        Color fill = t.color(element + ".fill", img != null ? null : base.fill());
        double radiusRef = radiusRef(t, element, base.radius());
        double r = radiusRef < 0 ? Math.min(w, h) : Theme.px(radiusRef) * 2.0;
        Shape shape = r > 0 ? new RoundRectangle2D.Double(x, y, w, h, r, r) : new Rectangle2D.Double(x, y, w, h);

        if (fill != null && fill.getAlpha() > 0) {
            g.setColor(fill);
            g.fill(shape);
        }
        if (img != null) {
            Graphics2D clip = (Graphics2D) g.create();
            if (r > 0 && !"slice".equals(mode(t, element))) {
                clip.clip(shape);
            }
            drawImage(clip, t, element, img, x, y, w, h);
            clip.dispose();
        }
        Color border = sideColor != null && t.flag(element + ".sideBorder", img == null) && element.startsWith("popup")
            ? sideColor : t.color(element + ".border", img != null ? null : base.border());
        double bw = t.number(element + ".borderWidth", base.borderWidth());
        if (border != null && border.getAlpha() > 0 && bw > 0) {
            float stroke = (float) Math.max(1.0, Theme.scale() * bw);
            g.setColor(border);
            g.setStroke(new BasicStroke(stroke));
            double inset = stroke / 2.0;
            g.draw(r > 0 ? new RoundRectangle2D.Double(x + inset, y + inset, w - stroke, h - stroke, r, r)
                : new Rectangle2D.Double(x + inset, y + inset, w - stroke, h - stroke));
        }
        g.dispose();
    }

    /** The element's text color. */
    public static Color text(String element, Color fallback) {
        return pack().color(element + ".text", fallback);
    }

    /** The element's fill color (e.g. for Swing components that need a background color). */
    public static Color fill(String element) {
        return pack().color(element + ".fill", base(element).fill());
    }

    /** True if the theme gives this element a picture. */
    public static boolean hasImage(String element) {
        return pack().image(element + ".image") != null;
    }

    private static double radiusRef(ThemePack t, String element, double fallback) {
        String v = t.string(element + ".radius", null);
        if (v == null) {
            return fallback;
        }
        if (v.equalsIgnoreCase("pill")) {
            return -1;
        }
        return t.number(element + ".radius", fallback);
    }

    private static String mode(ThemePack t, String element) {
        return t.string(element + ".mode", "slice").toLowerCase(java.util.Locale.ROOT);
    }

    private static void drawImage(Graphics2D g, ThemePack t, String element, BufferedImage img, int x, int y, int w, int h) {
        switch (mode(t, element)) {
            case "stretch" -> g.drawImage(img, x, y, w, h, null);
            case "cover" -> {
                double s = Math.max(w / (double) img.getWidth(), h / (double) img.getHeight());
                int iw = (int) Math.ceil(img.getWidth() * s);
                int ih = (int) Math.ceil(img.getHeight() * s);
                g.clipRect(x, y, w, h);
                g.drawImage(img, x + (w - iw) / 2, y + (h - ih) / 2, iw, ih, null);
            }
            case "tile" -> {
                double s = t.number(element + ".tileScale", 1.0) * Theme.scale();
                int tw = Math.max(1, (int) Math.round(img.getWidth() * s));
                int th = Math.max(1, (int) Math.round(img.getHeight() * s));
                g.clipRect(x, y, w, h);
                for (int ty = y; ty < y + h; ty += th) {
                    for (int tx = x; tx < x + w; tx += tw) {
                        g.drawImage(img, tx, ty, tw, th, null);
                    }
                }
            }
            default -> nineSlice(g, img, t.insets(element + ".slice", defaultSlice(img)),
                t.number(element + ".sliceScale", 1.0) * Theme.scale(), x, y, w, h);
        }
    }

    private static Insets defaultSlice(BufferedImage img) {
        int s = Math.min(img.getWidth(), img.getHeight()) / 3;
        return new Insets(s, s, s, s);
    }

    /**
     * 9-slice: the four corners are drawn at a fixed size, the edges stretch along one direction,
     * the middle stretches both ways. So one frame picture fits any panel size.
     */
    public static void nineSlice(Graphics2D g, BufferedImage img, Insets src, double scale, int x, int y, int w, int h) {
        int iw = img.getWidth();
        int ih = img.getHeight();
        int st = Math.min(src.top, ih / 2), sb = Math.min(src.bottom, ih / 2);
        int sl = Math.min(src.left, iw / 2), sr = Math.min(src.right, iw / 2);
        // Destination corner sizes, shrunk if the element is smaller than its corners.
        double fit = Math.min(1.0, Math.min(w / ((sl + sr) * scale + 1e-9), h / ((st + sb) * scale + 1e-9)));
        int dt = (int) Math.round(st * scale * fit), db = (int) Math.round(sb * scale * fit);
        int dl = (int) Math.round(sl * scale * fit), dr = (int) Math.round(sr * scale * fit);
        int[] sx = {0, sl, iw - sr, iw};
        int[] sy = {0, st, ih - sb, ih};
        int[] dx = {x, x + dl, x + w - dr, x + w};
        int[] dy = {y, y + dt, y + h - db, y + h};
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (dx[col + 1] > dx[col] && dy[row + 1] > dy[row] && sx[col + 1] > sx[col] && sy[row + 1] > sy[row]) {
                    g.drawImage(img, dx[col], dy[row], dx[col + 1], dy[row + 1],
                        sx[col], sy[row], sx[col + 1], sy[row + 1], null);
                }
            }
        }
    }

    /**
     * Lets a pop-up window show see-through corners (for rounded or ornate frames), where the
     * system supports it; otherwise the window stays rectangular.
     */
    public static void makeTranslucent(java.awt.Window w) {
        try {
            java.awt.GraphicsDevice dev = w.getGraphicsConfiguration() != null
                ? w.getGraphicsConfiguration().getDevice()
                : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (dev.isWindowTranslucencySupported(java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
                w.setBackground(new Color(0, 0, 0, 0));
                if (w instanceof javax.swing.RootPaneContainer rpc) {
                    ((javax.swing.JComponent) rpc.getContentPane()).setOpaque(false);
                }
            }
        } catch (RuntimeException e) {
            // keep the plain window
        }
    }

    /** An icon picture from the theme (e.g. {@code icon.history.attack}), or null to draw the built-in one. */
    public static BufferedImage icon(String key) {
        return pack().image("icon." + key);
    }

    private Skin() { }
}

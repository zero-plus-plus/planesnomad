package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.RelicSnapshot;
import com.planesnomad.gui.model.RelicSnapshot.Kind;
import com.planesnomad.gui.model.RelicSnapshot.State;

import javax.swing.JComponent;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * One relic or consumable icon in the top bar (placeholder art: a colored shape and a letter).
 *
 * - Relics are round; consumables are rounded squares, and an empty consumable slot is an outline.
 * - READY (can be activated right now): small glowing border.
 * - USED (ability spent): greyed out until it can be activated again.
 */
public final class RelicIcon extends JComponent {

    private static final int SIZE = 34;
    /** Dark backing behind icon pictures, so any emoji reads on the top bar. */
    /** Icon picture size as a share of the backing shape. */
    private static final double ICON_SHARE = 0.72;

    private RelicSnapshot relic;
    private final boolean consumableSlot;

    /** @param consumableSlot true to draw an empty consumable slot when no relic is set */
    /** Icon size at the reference resolution (top bar default; smaller in the enemy's effects bar). */
    private final int size;

    public RelicIcon(boolean consumableSlot) {
        this(consumableSlot, SIZE);
    }

    public RelicIcon(boolean consumableSlot, int size) {
        this.size = size;
        this.consumableSlot = consumableSlot;
        setOpaque(false);
    }

    public void setRelic(RelicSnapshot relic) {
        this.relic = relic;
        setToolTipText(tooltip(relic));
        repaint();
    }

    private String tooltip(RelicSnapshot r) {
        if (r == null) {
            return consumableSlot ? "Empty consumable slot" : null;
        }
        String kind = switch (r.kind()) {
            case PASSIVE -> "Relic";
            case TRIGGERED -> "Relic";
            case ACTIVATED -> "Relic (activated)";
            case CONSUMABLE -> "Consumable (one use)";
        };
        String state = switch (r.state()) {
            case READY -> "<br><i>Ready to use</i>";
            case USED -> "<br><i>Used, available again next turn</i>";
            case IDLE -> "";
        };
        return "<html><b>" + escape(r.name()) + "</b> - " + kind + "<br>"
            + escape(r.description()).replace("\n", "<br>") + state + "</html>";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public Dimension getPreferredSize() {
        int s = Theme.px(size + size * 12 / 34);   // room for the glow
        return new Dimension(s, s);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    /** Background behind relic pictures (theme element "relic.slot"). */
    private static Color backing() {
        return com.planesnomad.gui.ui.theme.Skin.fill("relic.slot");
    }

    /** A theme's frame picture over the icon ("relic.frame" / "consumable.frame"), if it has one. */
    private static void frame(Graphics2D g, boolean consumable, int x, int y, int d) {
        String element = consumable ? "consumable.frame" : "relic.frame";
        if (com.planesnomad.gui.ui.theme.Skin.hasImage(element)) {
            int ext = (int) Math.round(d * Theme.pack().number(element + ".extend", 0.12));
            com.planesnomad.gui.ui.theme.Skin.paint(g, element, x - ext, y - ext, d + 2 * ext, d + 2 * ext);
        }
    }

    /** Draws a relic's placeholder badge (no glow) at the given spot, e.g. inside a card frame. */
    public static void paintBadge(Graphics2D g0, RelicSnapshot relic, int x, int y, int d) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Shape shape = relic.kind() == Kind.CONSUMABLE
            ? new RoundRectangle2D.Double(x, y, d, d, d / 3.0, d / 3.0)
            : new Ellipse2D.Double(x, y, d, d);
        Color base = new Color(relic.color());
        java.awt.image.BufferedImage icon = RelicImages.get(relic.icon(), false);
        g.setColor(icon != null ? backing() : Theme.blend(base, Color.BLACK, 0.25));
        g.fill(shape);
        g.setColor(icon != null ? Theme.blend(base, backing(), 0.4) : Theme.blend(base, Color.WHITE, 0.35));
        g.setStroke(new BasicStroke(Math.max(1f, d / 22f)));
        g.draw(shape);
        if (icon != null) {
            int s = (int) (d * ICON_SHARE);
            RelicImages.draw(g, icon, x + (d - s) / 2, y + (d - s) / 2, s);
        } else {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, (int) (d * 0.47))));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Theme.TEXT);
            g.drawString(relic.glyph(), x + (d - fm.stringWidth(relic.glyph())) / 2, y + (d + fm.getAscent() - fm.getDescent()) / 2);
        }
        frame(g, relic.kind() == Kind.CONSUMABLE, x, y, d);
        g.dispose();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int d = Theme.px(size);
        int x = (getWidth() - d) / 2;
        int y = (getHeight() - d) / 2;
        boolean square = consumableSlot || (relic != null && relic.kind() == Kind.CONSUMABLE);
        Shape shape = square
            ? new RoundRectangle2D.Double(x, y, d, d, d / 3.0, d / 3.0)
            : new Ellipse2D.Double(x, y, d, d);

        if (relic == null) {
            if (com.planesnomad.gui.ui.theme.Skin.hasImage("consumable.empty")) {
                com.planesnomad.gui.ui.theme.Skin.paint(g, "consumable.empty", x, y, d, d);
                g.dispose();
                return;
            }
            g.setColor(new Color(255, 255, 255, 50));
            g.setStroke(new BasicStroke(Math.max(1f, Theme.px(1.5)), BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND, 1f, new float[] {Theme.px(4), Theme.px(3)}, 0f));
            g.draw(shape);
            g.dispose();
            return;
        }

        if (relic.state() == State.READY) {
            // Small glow: soft halo rings just outside the icon, brightest at the edge.
            int rings = 4;
            for (int i = rings; i >= 1; i--) {
                double grow = Theme.px(1.3) * i;
                Shape halo = square
                    ? new RoundRectangle2D.Double(x - grow, y - grow, d + 2 * grow, d + 2 * grow, d / 3.0 + grow, d / 3.0 + grow)
                    : new Ellipse2D.Double(x - grow, y - grow, d + 2 * grow, d + 2 * grow);
                g.setColor(new Color(Theme.RELIC_GLOW.getRed(), Theme.RELIC_GLOW.getGreen(),
                    Theme.RELIC_GLOW.getBlue(), 40 + (rings - i) * 40));
                g.fill(halo);
            }
        }

        boolean used = relic.state() == State.USED;
        Color base = new Color(relic.color());
        java.awt.image.BufferedImage picture = RelicImages.get(relic.icon(), used);
        Color fill = picture != null ? backing() : used ? Theme.RELIC_USED : base;
        g.setColor(picture != null ? fill : Theme.blend(fill, Color.BLACK, 0.25));
        g.fill(shape);
        g.setColor(used ? Theme.blend(Theme.RELIC_USED, Color.WHITE, 0.2)
            : relic.state() == State.READY ? Theme.RELIC_GLOW
            : picture != null ? Theme.blend(base, backing(), 0.4)
            : Theme.blend(base, Color.WHITE, 0.35));
        g.setStroke(new BasicStroke(Math.max(1f, Theme.px(1.5))));
        g.draw(shape);

        if (picture != null) {
            // The greyed-out picture is already faded; color pictures draw as they are.
            int s = (int) (d * ICON_SHARE);
            RelicImages.draw(g, picture, x + (d - s) / 2, y + (d - s) / 2, s);
        } else {
            if (used) {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            }
            g.setFont(Theme.font(Font.BOLD, 16));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Theme.TEXT);
            String glyph = relic.glyph();
            g.drawString(glyph, x + (d - fm.stringWidth(glyph)) / 2, y + (d + fm.getAscent() - fm.getDescent()) / 2);
        }
        g.setComposite(AlphaComposite.SrcOver);
        frame(g, square, x, y, d);
        g.dispose();
    }
}

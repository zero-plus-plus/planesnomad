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

    private RelicSnapshot relic;
    private final boolean consumableSlot;

    /** @param consumableSlot true to draw an empty consumable slot when no relic is set */
    public RelicIcon(boolean consumableSlot) {
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
        int s = Theme.px(SIZE + 12);   // room for the glow
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

    /** Draws a relic's placeholder badge (no glow) at the given spot, e.g. inside a card frame. */
    public static void paintBadge(Graphics2D g0, RelicSnapshot relic, int x, int y, int d) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Shape shape = relic.kind() == Kind.CONSUMABLE
            ? new RoundRectangle2D.Double(x, y, d, d, d / 3.0, d / 3.0)
            : new Ellipse2D.Double(x, y, d, d);
        Color base = new Color(relic.color());
        g.setColor(Theme.blend(base, Color.BLACK, 0.25));
        g.fill(shape);
        g.setColor(Theme.blend(base, Color.WHITE, 0.35));
        g.setStroke(new BasicStroke(Math.max(1f, d / 22f)));
        g.draw(shape);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, (int) (d * 0.47))));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Theme.TEXT);
        g.drawString(relic.glyph(), x + (d - fm.stringWidth(relic.glyph())) / 2, y + (d + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int d = Theme.px(SIZE);
        int x = (getWidth() - d) / 2;
        int y = (getHeight() - d) / 2;
        boolean square = consumableSlot || (relic != null && relic.kind() == Kind.CONSUMABLE);
        Shape shape = square
            ? new RoundRectangle2D.Double(x, y, d, d, d / 3.0, d / 3.0)
            : new Ellipse2D.Double(x, y, d, d);

        if (relic == null) {
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
        Color fill = used ? Theme.RELIC_USED : base;
        g.setColor(Theme.blend(fill, Color.BLACK, 0.25));
        g.fill(shape);
        g.setColor(used ? Theme.blend(Theme.RELIC_USED, Color.WHITE, 0.2)
            : relic.state() == State.READY ? Theme.RELIC_GLOW
            : Theme.blend(base, Color.WHITE, 0.35));
        g.setStroke(new BasicStroke(Math.max(1f, Theme.px(1.5))));
        g.draw(shape);

        if (used) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        }
        g.setFont(Theme.font(Font.BOLD, 16));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Theme.TEXT);
        String glyph = relic.glyph();
        g.drawString(glyph, x + (d - fm.stringWidth(glyph)) / 2, y + (d + fm.getAscent() - fm.getDescent()) / 2);
        g.dispose();
    }
}

package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.MatchSnapshot.StackItem;
import com.planesnomad.gui.model.MatchSnapshot.Target;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How a stack item's targets are marked, so a player sees what an enemy spell is aimed at without
 * reading the log: the targeted card or portrait gets a pulsing glow and a crosshair in the color
 * of the player who aimed it, plus a "TARGET" tag (theme element {@code badge.target}); the
 * {@link TargetArrows} overlay draws an arrow from the stack card to it.
 */
public final class TargetMarks {

    /** Card id -> color of the player targeting it, for every item on the stack. */
    public static Map<Integer, Color> cardTargets(List<StackItem> stack) {
        Map<Integer, Color> out = new HashMap<>();
        for (int i = stack.size() - 1; i >= 0; i--) {   // the top item wins when two aim at the same card
            StackItem item = stack.get(i);
            for (Target t : item.targets()) {
                if (t.kind() == Target.Kind.CARD) {
                    out.put(t.id(), colorOf(item));
                }
            }
        }
        return out;
    }

    /** Player name -> color of the player targeting them. */
    public static Map<String, Color> playerTargets(List<StackItem> stack) {
        Map<String, Color> out = new HashMap<>();
        for (int i = stack.size() - 1; i >= 0; i--) {
            StackItem item = stack.get(i);
            for (Target t : item.targets()) {
                if (t.kind() == Target.Kind.PLAYER) {
                    out.put(t.name(), colorOf(item));
                }
            }
        }
        return out;
    }

    public static Color colorOf(StackItem item) {
        return item.byYou() ? Theme.YOU : Theme.ENEMY;
    }

    /** 0..1, slowly going up and down (about once a second), shared so all marks pulse together. */
    public static double pulse() {
        return 0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1000.0 * 2 * Math.PI);
    }

    /** Glow, outline, crosshair and tag over a targeted card's (or portrait's) rectangle. */
    public static void paint(Graphics2D g0, Rectangle r, Color color) {
        Graphics2D g = (Graphics2D) g0.create();
        double p = pulse();
        int arc = Math.max(4, r.width / 10);
        // Glow
        for (int i = 4; i >= 1; i--) {
            int grow = (int) Math.round(Theme.px(1.6) * i);
            g.setColor(withAlpha(color, (int) (40 + 50 * p) + (4 - i) * 25));
            g.setStroke(new BasicStroke(Math.max(1.5f, Theme.px(1.8))));
            g.drawRoundRect(r.x - grow, r.y - grow, r.width - 1 + 2 * grow, r.height - 1 + 2 * grow, arc + grow, arc + grow);
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(2.5f, Theme.px(3))));
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);
        // Darken the card a little so the crosshair reads on any art.
        g.setColor(new Color(0, 0, 0, 60));
        g.fillRoundRect(r.x, r.y, r.width, r.height, arc, arc);
        crosshair(g, r.x + r.width / 2.0, r.y + r.height / 2.0, Math.min(r.width, r.height) * (0.28 + 0.04 * p), color);
        // Tag at the bottom
        g.setFont(Theme.font(Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        String text = "TARGET";
        int tw = fm.stringWidth(text) + Theme.px(8);
        int th = fm.getHeight() + Theme.px(1);
        if (tw <= r.width + Theme.px(6)) {
            int tx = r.x + (r.width - tw) / 2;
            int ty = r.y + r.height - th - Theme.px(3);
            com.planesnomad.gui.ui.theme.Skin.paint(g, "badge.target", tx, ty, tw, th);
            g.setColor(com.planesnomad.gui.ui.theme.Skin.text("badge.target", Color.WHITE));
            g.drawString(text, tx + Theme.px(4), ty + fm.getAscent());
        }
        g.dispose();
    }

    /** A ring with four ticks: the "aimed at" sign. */
    public static void crosshair(Graphics2D g, double cx, double cy, double radius, Color color) {
        float w = (float) Math.max(2, radius * 0.16);
        for (int pass = 0; pass < 2; pass++) {
            // First a dark outline, then the color on top, so it shows on light and dark art.
            g.setColor(pass == 0 ? new Color(0, 0, 0, 170) : color);
            g.setStroke(new BasicStroke(pass == 0 ? w + 2.5f : w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Ellipse2D.Double(cx - radius, cy - radius, 2 * radius, 2 * radius));
            double in = radius * 0.45;
            double out = radius * 1.35;
            g.drawLine((int) (cx - out), (int) cy, (int) (cx - in), (int) cy);
            g.drawLine((int) (cx + in), (int) cy, (int) (cx + out), (int) cy);
            g.drawLine((int) cx, (int) (cy - out), (int) cx, (int) (cy - in));
            g.drawLine((int) cx, (int) (cy + in), (int) cx, (int) (cy + out));
        }
    }

    static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    private TargetMarks() { }
}

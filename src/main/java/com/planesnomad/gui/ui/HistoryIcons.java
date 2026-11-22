package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.HistoryEntry.Kind;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Small drawn symbols for the kinds of history entries (no font glyphs: fonts differ between
 * systems). Each is a colored disc with a white symbol: sword = attack, shield = blocks,
 * skull = died, bolt = trigger, cog = ability, mountain = land, swirl = exiled, arrow = returned,
 * plus = tokens, burst = damage, crown = game over.
 */
public final class HistoryIcons {

    public static Color color(Kind kind) {
        return switch (kind) {
            case CAST -> new Color(0x6C7A89);
            case ABILITY -> new Color(0x5B7FA6);
            case TRIGGER -> new Color(0xC9952C);
            case LAND -> new Color(0x6B8E4E);
            case ATTACK -> new Color(0xD9603B);
            case BLOCK -> new Color(0x3F7CC4);
            case DEATH -> new Color(0x5C4B6B);
            case EXILE -> new Color(0x8E6CC9);
            case BOUNCE -> new Color(0x2F9C95);
            case TOKENS -> new Color(0x4E9A5A);
            case DAMAGE -> new Color(0xC23B3B);
            case GAME_END -> new Color(0xD4AF37);
        };
    }

    /** Disc with the kind's symbol, size x size at (x, y). */
    public static void paint(Graphics2D g0, Kind kind, int x, int y, int size) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(new Ellipse2D.Double(x + 0.5, y + 1, size, size));
        g.setColor(color(kind));
        g.fill(new Ellipse2D.Double(x, y, size, size));
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(Math.max(1f, size / 18f)));
        g.draw(new Ellipse2D.Double(x + 0.5, y + 0.5, size - 1, size - 1));
        g.translate(x, y);
        g.scale(size / 100.0, size / 100.0);   // symbols are drawn in a 100 x 100 box
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        symbol(g, kind);
        g.dispose();
    }

    private static void symbol(Graphics2D g, Kind kind) {
        switch (kind) {
            case CAST -> {   // a small card
                g.draw(new RoundRectangle2D.Double(32, 24, 36, 52, 8, 8));
            }
            case ABILITY -> {   // cog
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    g.draw(new Line2D.Double(50 + Math.cos(a) * 18, 50 + Math.sin(a) * 18,
                        50 + Math.cos(a) * 30, 50 + Math.sin(a) * 30));
                }
                g.draw(new Ellipse2D.Double(32, 32, 36, 36));
            }
            case TRIGGER -> {   // lightning bolt
                g.fill(new Polygon(new int[] {56, 30, 48, 42, 70, 52}, new int[] {18, 54, 54, 82, 44, 44}, 6));
            }
            case LAND -> {   // mountain
                g.fill(new Polygon(new int[] {18, 42, 52, 60, 82}, new int[] {72, 34, 48, 40, 72}, 5));
            }
            case ATTACK -> {   // sword
                g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(30, 70, 72, 28));        // blade
                g.draw(new Line2D.Double(24, 56, 44, 76));        // guard
                g.draw(new Line2D.Double(30, 70, 22, 78));        // grip
            }
            case BLOCK -> {   // shield
                GeneralPath p = new GeneralPath();
                p.moveTo(50, 20);
                p.lineTo(76, 30);
                p.curveTo(76, 58, 64, 72, 50, 82);
                p.curveTo(36, 72, 24, 58, 24, 30);
                p.closePath();
                g.fill(p);
            }
            case DEATH -> {   // skull
                g.fill(new Ellipse2D.Double(26, 20, 48, 44));
                g.fill(new RoundRectangle2D.Double(36, 52, 28, 24, 8, 8));
                g.setColor(color(Kind.DEATH));
                g.fill(new Ellipse2D.Double(34, 36, 12, 13));
                g.fill(new Ellipse2D.Double(54, 36, 12, 13));
                g.setStroke(new BasicStroke(4f));
                g.draw(new Line2D.Double(46, 66, 46, 76));
                g.draw(new Line2D.Double(54, 66, 54, 76));
            }
            case EXILE -> {   // swirl
                g.draw(new Arc2D.Double(24, 24, 52, 52, 90, 270, Arc2D.OPEN));
                g.draw(new Arc2D.Double(36, 36, 28, 28, 270, 240, Arc2D.OPEN));
            }
            case BOUNCE -> {   // curved arrow back
                g.draw(new Arc2D.Double(28, 30, 46, 42, 90, -200, Arc2D.OPEN));
                g.fill(new Polygon(new int[] {20, 40, 42}, new int[] {32, 18, 44}, 3));
            }
            case TOKENS -> {   // plus
                g.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(50, 26, 50, 74));
                g.draw(new Line2D.Double(26, 50, 74, 50));
            }
            case DAMAGE -> {   // burst
                Polygon star = new Polygon();
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16 - Math.PI / 2;
                    double r = i % 2 == 0 ? 32 : 15;
                    star.addPoint((int) Math.round(50 + Math.cos(a) * r), (int) Math.round(50 + Math.sin(a) * r));
                }
                g.fill(star);
            }
            case GAME_END -> {   // crown
                g.fill(new Polygon(new int[] {22, 22, 36, 50, 64, 78, 78}, new int[] {72, 34, 50, 28, 50, 34, 72}, 7));
            }
        }
    }

    private HistoryIcons() { }
}

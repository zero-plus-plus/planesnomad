package com.planesnomad.gui.ui;

import forge.game.phase.PhaseType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Small drawn symbols for the phases of a turn: hourglass = upkeep, card = draw, two cards =
 * main phases, crossed swords = beginning of combat, sword = attackers, shield = blockers,
 * burst = combat damage (with "1" for first strike), sheathed sword = end of combat,
 * moon = end step, broom = cleanup. A theme can replace any with a picture:
 * {@code icon.phase.<key>} (see {@link #key}).
 */
public final class PhaseIcons {

    /** Theme key part: upkeep, draw, main1, combatBegin, attackers, blockers, firstStrike, damage, combatEnd, main2, end, cleanup. */
    public static String key(PhaseType p) {
        return switch (p) {
            case UNTAP -> "untap";
            case UPKEEP -> "upkeep";
            case DRAW -> "draw";
            case MAIN1 -> "main1";
            case COMBAT_BEGIN -> "combatBegin";
            case COMBAT_DECLARE_ATTACKERS -> "attackers";
            case COMBAT_DECLARE_BLOCKERS -> "blockers";
            case COMBAT_FIRST_STRIKE_DAMAGE -> "firstStrike";
            case COMBAT_DAMAGE -> "damage";
            case COMBAT_END -> "combatEnd";
            case MAIN2 -> "main2";
            case END_OF_TURN -> "end";
            case CLEANUP -> "cleanup";
        };
    }

    /** Draws the phase's symbol in color {@code c}, filling a size x size square at (x, y). */
    public static void paint(Graphics2D g0, PhaseType phase, int x, int y, int size, Color c) {
        java.awt.image.BufferedImage custom = com.planesnomad.gui.ui.theme.Skin.icon("phase." + key(phase));
        if (custom != null) {
            Graphics2D gi = (Graphics2D) g0.create();
            gi.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gi.drawImage(custom, x, y, size, size, null);
            gi.dispose();
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y);
        g.scale(size / 100.0, size / 100.0);   // drawn in a 100 x 100 box
        g.setColor(c);
        g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (phase) {
            case UNTAP -> {   // circular arrow
                g.draw(new Arc2D.Double(22, 22, 56, 56, 60, 290, Arc2D.OPEN));
                g.fill(new Polygon(new int[] {64, 84, 70}, new int[] {12, 26, 38}, 3));
            }
            case UPKEEP -> {   // hourglass
                g.draw(new Line2D.Double(28, 18, 72, 18));
                g.draw(new Line2D.Double(28, 82, 72, 82));
                GeneralPath p = new GeneralPath();
                p.moveTo(32, 18);
                p.lineTo(68, 18);
                p.lineTo(50, 50);
                p.lineTo(68, 82);
                p.lineTo(32, 82);
                p.lineTo(50, 50);
                p.closePath();
                g.draw(p);
                g.fill(new Polygon(new int[] {38, 62, 50}, new int[] {80, 80, 66}, 3));
            }
            case DRAW -> {   // a card coming up from the deck
                g.draw(new RoundRectangle2D.Double(22, 46, 56, 38, 8, 8));
                g.fill(new RoundRectangle2D.Double(32, 16, 36, 44, 8, 8));
            }
            case MAIN1, MAIN2 -> {   // two cards fanned, with the phase number
                g.draw(new RoundRectangle2D.Double(18, 26, 34, 50, 8, 8));
                g.fill(new RoundRectangle2D.Double(40, 20, 34, 50, 8, 8));
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
                FontMetrics fm = g.getFontMetrics();
                String n = phase == PhaseType.MAIN1 ? "1" : "2";
                g.setColor(Color.BLACK);
                g.drawString(n, 57 - fm.stringWidth(n) / 2, 58);
            }
            case COMBAT_BEGIN -> {   // crossed swords
                g.draw(new Line2D.Double(22, 78, 74, 26));
                g.draw(new Line2D.Double(78, 78, 26, 26));
                g.draw(new Line2D.Double(16, 64, 36, 84));
                g.draw(new Line2D.Double(84, 64, 64, 84));
            }
            case COMBAT_DECLARE_ATTACKERS -> {   // sword
                g.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.draw(new Line2D.Double(30, 70, 76, 24));
                g.draw(new Line2D.Double(22, 56, 44, 78));
                g.draw(new Line2D.Double(30, 70, 20, 80));
            }
            case COMBAT_DECLARE_BLOCKERS -> {   // shield
                GeneralPath p = new GeneralPath();
                p.moveTo(50, 14);
                p.lineTo(80, 26);
                p.curveTo(80, 58, 66, 76, 50, 88);
                p.curveTo(34, 76, 20, 58, 20, 26);
                p.closePath();
                g.fill(p);
            }
            case COMBAT_FIRST_STRIKE_DAMAGE, COMBAT_DAMAGE -> {   // burst (first strike: with a "1")
                Polygon star = new Polygon();
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16 - Math.PI / 2;
                    double r = i % 2 == 0 ? 40 : 18;
                    star.addPoint((int) Math.round(50 + Math.cos(a) * r), (int) Math.round(50 + Math.sin(a) * r));
                }
                g.fill(star);
                if (phase == PhaseType.COMBAT_FIRST_STRIKE_DAMAGE) {
                    g.setColor(Color.BLACK);
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
                    g.drawString("1", 42, 61);
                }
            }
            case COMBAT_END -> {   // sword in its sheath, pointing down
                g.draw(new Line2D.Double(50, 14, 50, 30));
                g.draw(new Line2D.Double(34, 30, 66, 30));
                g.fill(new RoundRectangle2D.Double(41, 32, 18, 54, 10, 10));
            }
            case END_OF_TURN -> {   // crescent moon
                java.awt.geom.Area moon = new java.awt.geom.Area(new Ellipse2D.Double(18, 18, 64, 64));
                moon.subtract(new java.awt.geom.Area(new Ellipse2D.Double(36, 8, 60, 60)));
                g.fill(moon);
            }
            case CLEANUP -> {   // broom
                g.draw(new Line2D.Double(70, 14, 46, 56));
                g.fill(new Polygon(new int[] {36, 58, 52, 30, 22}, new int[] {52, 62, 88, 86, 74}, 5));
            }
        }
        g.dispose();
    }

    private PhaseIcons() { }
}

package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.PlayerSnapshot;
import com.planesnomad.gui.model.RelicSnapshot;

import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Hovering a player's portrait: the portrait's art crop (not the whole card), the player's name
 * and theme, life total, and the effects on that player (relics and consumables, or an enemy's
 * effects and guaranteed cards), each with its description.
 */
public final class PlayerInfoPopup {

    /** Reference width of the pop-up (scaled with the window). */
    static final int WIDTH = 280;

    private final boolean enemy;
    private JWindow window;
    private Canvas canvas;

    public PlayerInfoPopup(boolean enemy) {
        this.enemy = enemy;
    }

    public void show(PlayerSnapshot p, Component source, Point pointInSource) {
        if (p == null || GraphicsEnvironment.isHeadless()) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(source);
        if (window == null || window.getOwner() != owner) {
            window = new JWindow(owner);
            canvas = new Canvas();
            window.setContentPane(canvas);
            window.setFocusableWindowState(false);
        }
        canvas.player = p;
        canvas.enemy = enemy;
        Dimension size = size(p, enemy);
        window.setSize(size);
        Point screen = new Point(pointInSource);
        SwingUtilities.convertPointToScreen(screen, source);
        Rectangle bounds = owner != null ? owner.getGraphicsConfiguration().getBounds()
            : new Rectangle(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        int gap = Theme.px(18);
        int x = screen.x + gap;
        if (x + size.width > bounds.x + bounds.width) {
            x = screen.x - gap - size.width;
        }
        int y = Math.max(bounds.y, Math.min(screen.y - size.height / 3, bounds.y + bounds.height - size.height));
        window.setLocation(x, y);
        canvas.repaint();
        if (!window.isVisible()) {
            window.setVisible(true);
        }
    }

    public void hide() {
        if (window != null) {
            window.setVisible(false);
        }
    }

    /** One line of the effects list. */
    record Entry(String title, String text) { }

    static List<Entry> entries(PlayerSnapshot p, boolean enemy) {
        List<Entry> out = new ArrayList<>();
        for (RelicSnapshot r : p.relics()) {
            String kind = switch (r.kind()) {
                case CONSUMABLE -> "Consumable";
                case ACTIVATED -> "Relic (activated)";
                default -> enemy ? "Effect" : "Relic";
            };
            String state = r.state() == RelicSnapshot.State.USED ? " - used" : "";
            out.add(new Entry(r.name() + "  · " + kind + state, r.description()));
        }
        if (enemy) {
            for (CardSnapshot c : p.featured()) {
                out.add(new Entry(c.name() + "  · Guaranteed card", ""));
            }
        }
        return out;
    }

    /** Size for this player's pop-up (the effects list sets the height). */
    static Dimension size(PlayerSnapshot p, boolean enemy) {
        Canvas probe = new Canvas();
        probe.player = p;
        probe.enemy = enemy;
        int w = Theme.px(WIDTH);
        java.awt.image.BufferedImage scratch = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        int h = probe.layout(g, w, false);
        g.dispose();
        return new Dimension(w, h);
    }

    /** Paints the pop-up's content (also used for screenshots and tests). */
    public static void paint(Graphics2D g, PlayerSnapshot p, boolean enemy, int w, int h) {
        Canvas c = new Canvas();
        c.player = p;
        c.enemy = enemy;
        c.paintInto(g, w, h);
    }

    private static final class Canvas extends JComponent {
        PlayerSnapshot player;
        boolean enemy;

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            paintInto(g, getWidth(), getHeight());
            g.dispose();
        }

        void paintInto(Graphics2D g, int w, int h) {
            if (player == null) {
                return;
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color accent = enemy ? Theme.ENEMY : Theme.YOU;
            g.setColor(Theme.PANEL);
            g.fillRect(0, 0, w, h);
            layout(g, w, true);
            g.setColor(accent);
            g.setStroke(new BasicStroke(Math.max(1.5f, Theme.px(2))));
            g.drawRect(1, 1, w - 3, h - 3);
        }

        /** Lays out (and, if {@code draw}, paints) the content; returns the height used. */
        int layout(Graphics2D g, int w, boolean draw) {
            int pad = Theme.px(10);
            int y = pad;
            int inner = w - 2 * pad;
            Color accent = enemy ? Theme.ENEMY : Theme.YOU;

            // Art crop (or the placeholder color), with the life badge, like the panel.
            int artH = (int) (inner * 0.62);
            if (draw) {
                int arc = Theme.px(10);
                if (player.portrait() != null) {
                    Graphics2D clip = (Graphics2D) g.create();
                    clip.clip(new RoundRectangle2D.Double(pad, y, inner, artH, arc, arc));
                    CardPainter.paintArt(clip, player.portrait(), pad, y, inner, artH);
                    clip.dispose();
                } else {
                    g.setColor(Theme.blend(accent, Color.BLACK, 0.5));
                    g.fillRoundRect(pad, y, inner, artH, arc, arc);
                }
                g.setColor(accent);
                g.setStroke(new BasicStroke(Math.max(1f, Theme.px(1.5))));
                g.drawRoundRect(pad, y, inner, artH, arc, arc);
            }
            y += artH + Theme.px(8);

            // Name, theme
            g.setFont(Theme.font(Font.BOLD, 14));
            FontMetrics fm = g.getFontMetrics();
            if (draw) {
                g.setColor(accent);
                g.drawString(player.name(), pad, y + fm.getAscent());
            }
            y += fm.getHeight();
            if (player.subtitle() != null && !player.subtitle().isBlank()) {
                g.setFont(Theme.font(Font.PLAIN, 11));
                y = wrap(g, player.subtitle(), pad, y, inner, Theme.TEXT_DIM, draw);
            }
            y += Theme.px(6);

            // Life
            g.setFont(Theme.font(Font.BOLD, 13));
            fm = g.getFontMetrics();
            String life = player.lost() ? "Lost the game" : "Life " + player.life()
                + (player.life() != player.startingLife() ? "   (started at " + player.startingLife() + ")" : "");
            if (draw) {
                g.setColor(Theme.LIFE);
                g.drawString(life, pad, y + fm.getAscent());
            }
            y += fm.getHeight() + Theme.px(4);

            // Effects
            List<Entry> list = entries(player, enemy);
            g.setFont(Theme.font(Font.BOLD, 11));
            fm = g.getFontMetrics();
            if (draw) {
                g.setColor(Theme.PANEL_EDGE);
                g.drawLine(pad, y, w - pad, y);
            }
            y += Theme.px(6);
            if (draw) {
                g.setColor(Theme.TEXT_DIM);
                g.drawString(list.isEmpty() ? "No effects" : "Effects", pad, y + fm.getAscent());
            }
            y += fm.getHeight();
            for (Entry e : list) {
                g.setFont(Theme.font(Font.BOLD, 11.5));
                y = wrap(g, e.title(), pad, y, inner, Theme.ACCENT, draw);
                if (e.text() != null && !e.text().isBlank()) {
                    g.setFont(Theme.font(Font.PLAIN, 11));
                    y = wrap(g, e.text(), pad + Theme.px(8), y, inner - Theme.px(8), Theme.TEXT, draw);
                }
                y += Theme.px(4);
            }
            return y + pad;
        }

        /** Draws text wrapped to the width (mana symbols as pictures); returns the y below it. */
        private static int wrap(Graphics2D g, String text, int x, int y, int width, Color color, boolean draw) {
            FontMetrics fm = g.getFontMetrics();
            g.setColor(color);
            for (String para : text.split("\n")) {
                for (String line : SymbolText.wrap(para, fm, width)) {
                    if (draw) {
                        SymbolText.draw(g, line, x, y + fm.getAscent());
                    }
                    y += fm.getHeight();
                }
            }
            return y;
        }
    }
}

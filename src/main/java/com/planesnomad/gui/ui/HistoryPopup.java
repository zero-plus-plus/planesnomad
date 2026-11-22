package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.HistoryEntry;

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
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Hovering a history icon: what happened there. Kind and turn, the headline, details (targets,
 * attackers and blockers, damage per source, ...), and the cards involved as small pictures.
 */
public final class HistoryPopup {

    static final int WIDTH = 330;
    /** Cards shown in the pop-up; more are counted. */
    static final int MAX_CARDS = 8;

    private JWindow window;
    private Canvas canvas;

    public void show(HistoryEntry entry, Component source, Point pointInSource) {
        if (entry == null || GraphicsEnvironment.isHeadless()) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(source);
        if (window == null || window.getOwner() != owner) {
            window = new JWindow(owner);
            canvas = new Canvas();
            window.setContentPane(canvas);
            window.setFocusableWindowState(false);
        }
        canvas.entry = entry;
        Dimension size = size(entry);
        window.setSize(size);
        Point screen = new Point(pointInSource);
        SwingUtilities.convertPointToScreen(screen, source);
        Rectangle bounds = owner != null ? owner.getGraphicsConfiguration().getBounds()
            : new Rectangle(0, 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        int gap = Theme.px(16);
        int x = screen.x - gap - size.width;          // the bar is at the right edge: open to the left
        if (x < bounds.x) {
            x = screen.x + gap;
        }
        int y = Math.max(bounds.y, Math.min(screen.y - Theme.px(20), bounds.y + bounds.height - size.height));
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

    public static Dimension size(HistoryEntry e) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        int h = layout(g, e, Theme.px(WIDTH), false);
        g.dispose();
        return new Dimension(Theme.px(WIDTH), h);
    }

    /** Paints the pop-up's content (also for screenshots and tests). */
    public static void paint(Graphics2D g, HistoryEntry e, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Theme.PANEL);
        g.fillRect(0, 0, w, h);
        layout(g, e, w, true);
        g.setColor(e.byYou() ? Theme.YOU : Theme.ENEMY);
        g.setStroke(new BasicStroke(Math.max(1.5f, Theme.px(2))));
        g.drawRect(1, 1, w - 3, h - 3);
    }

    private static int layout(Graphics2D g, HistoryEntry e, int w, boolean draw) {
        int pad = Theme.px(10);
        int inner = w - 2 * pad;
        int y = pad;

        // Header: symbol, kind, turn, who.
        int icon = Theme.px(22);
        if (draw) {
            HistoryIcons.paint(g, e.kind(), pad, y, icon);
        }
        g.setFont(Theme.font(Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        String head = e.kind().label.toUpperCase() + (e.count() > 1 ? " ×" + e.count() : "") + "  ·  Turn " + e.turn();
        if (draw) {
            g.setColor(Theme.TEXT_DIM);
            g.drawString(head, pad + icon + Theme.px(8), y + (icon + fm.getAscent()) / 2 - Theme.px(1));
        }
        y += icon + Theme.px(6);

        // Headline
        g.setFont(Theme.font(Font.BOLD, 13));
        y = wrap(g, e.title(), pad, y, inner, e.byYou() ? Theme.YOU.brighter() : Theme.ENEMY.brighter(), draw);
        y += Theme.px(2);

        // Details
        g.setFont(Theme.font(Font.PLAIN, 11.5));
        for (String d : e.details()) {
            y = wrap(g, d, pad, y, inner, Theme.TEXT, draw);
        }
        y += Theme.px(6);

        // What it led to
        if (!e.effects().isEmpty()) {
            g.setFont(Theme.font(Font.BOLD, 10));
            fm = g.getFontMetrics();
            if (draw) {
                g.setColor(Theme.TEXT_DIM);
                g.drawString("LED TO", pad, y + fm.getAscent());
            }
            y += fm.getHeight();
            g.setFont(Theme.font(Font.PLAIN, 11.5));
            int indent = Theme.px(14);
            for (String fx : e.effects()) {
                if (draw) {
                    g.setColor(Theme.ACCENT);
                    g.setFont(Theme.font(Font.BOLD, 11.5));
                    g.drawString("\u203a", pad + Theme.px(3), y + g.getFontMetrics().getAscent());
                    g.setFont(Theme.font(Font.PLAIN, 11.5));
                }
                y = wrap(g, fx, pad + indent, y, inner - indent, Theme.TEXT, draw);
            }
            y += Theme.px(6);
        }

        // Cards involved
        List<CardSnapshot> cards = e.cards();
        if (!cards.isEmpty()) {
            int shown = Math.min(cards.size(), MAX_CARDS);
            boolean big = e.large() || shown == 1;
            int cw = big ? Theme.px(120) : Theme.px(62);
            int ch = (int) (cw / Theme.CARD_ASPECT);
            int gap = Theme.px(6);
            int perRow = Math.max(1, (inner + gap) / (cw + gap));
            for (int i = 0; i < shown; i++) {
                int col = i % perRow;
                int row = i / perRow;
                int cx = pad + col * (cw + gap);
                int cy = y + row * (ch + gap);
                if (draw) {
                    CardSnapshot c = cards.get(i);
                    CardPainter.paint(g, c, cx, cy, cw, ch);
                    if (i == 0 && e.relic() != null) {
                        int d = (int) (cw * 0.34);
                        RelicIcon.paintBadge(g, e.relic(), cx + (cw - d) / 2, cy + (int) (ch * 0.14), d);
                    }
                }
            }
            int rows = (shown + perRow - 1) / perRow;
            y += rows * (ch + gap);
            int more = Math.max(e.count(), cards.size()) - shown;
            if (more > 0) {
                g.setFont(Theme.font(Font.ITALIC, 11));
                fm = g.getFontMetrics();
                if (draw) {
                    g.setColor(Theme.TEXT_DIM);
                    g.drawString("+" + more + " more", pad, y + fm.getAscent());
                }
                y += fm.getHeight();
            }
        }
        return y + pad;
    }

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

    private static final class Canvas extends JComponent {
        HistoryEntry entry;

        @Override
        protected void paintComponent(Graphics g0) {
            if (entry == null) {
                return;
            }
            Graphics2D g = (Graphics2D) g0.create();
            HistoryPopup.paint(g, entry, getWidth(), getHeight());
            g.dispose();
        }
    }
}

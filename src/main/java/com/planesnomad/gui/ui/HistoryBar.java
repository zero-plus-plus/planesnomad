package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.HistoryEntry;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The match history as a column of icons beside the board, newest at the top: spells cast as
 * small cards, everything else (abilities, triggers, lands, attacks, blocks, deaths, damage, ...)
 * as smaller art icons with a symbol for what happened. Each icon's border shows who did it;
 * merged entries (e.g. 37 goblins dying at once) show a count. Turns are separated by a line.
 * Hover an icon for what happened; the mouse wheel scrolls back through older entries.
 * The full text log is one click away (the column's expand button).
 */
public final class HistoryBar extends JComponent {

    /** Bar width at the reference resolution. */
    public static final int WIDTH = 66;

    private List<HistoryEntry> entries = List.of();
    private final List<Rectangle> boxes = new ArrayList<>();
    private final List<HistoryEntry> boxEntries = new ArrayList<>();
    private final HistoryPopup popup = new HistoryPopup();
    private HistoryEntry hovered;
    /** Pixels scrolled down into older entries (0 = newest at the top). */
    private int scroll;
    private int maxScroll;

    public HistoryBar() {
        setOpaque(true);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                hover(e.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = null;
                popup.hide();
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(e -> {
            int v = Math.max(0, Math.min(maxScroll, scroll + (int) Math.round(e.getPreciseWheelRotation() * Theme.px(48))));
            if (v != scroll) {
                scroll = v;
                repaint();
                hover(e.getPoint());
            }
        });
    }

    public void setEntries(List<HistoryEntry> list) {
        if (list.equals(entries)) {
            return;
        }
        // Keep the view still while scrolled back into older entries.
        int added = list.size() - entries.size();
        if (scroll > 0 && added > 0) {
            scroll += added * Theme.px(32);
        }
        entries = List.copyOf(list);
        if (hovered != null && entries.stream().noneMatch(e -> e.seq() == hovered.seq())) {
            hovered = null;
            popup.hide();
        }
        repaint();
    }

    public List<HistoryEntry> entries() {
        return entries;
    }

    /** Back to the newest entries. */
    public void scrollToNewest() {
        scroll = 0;
        repaint();
    }

    public int maxScroll() {
        return maxScroll;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(WIDTH), Theme.px(200));
    }

    private void hover(Point p) {
        HistoryEntry hit = null;
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).contains(p)) {
                hit = boxEntries.get(i);
                break;
            }
        }
        if (hit == null) {
            if (hovered != null) {
                hovered = null;
                popup.hide();
                repaint();
            }
            return;
        }
        if (hovered == null || hovered.seq() != hit.seq()) {
            hovered = hit;
            repaint();
        }
        popup.show(hit, this, p);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        com.planesnomad.gui.ui.theme.Skin.paint(g, "column", 0, 0, getWidth(), getHeight());
        boxes.clear();
        boxEntries.clear();

        int w = getWidth();
        int pad = Theme.px(6);
        int gap = Theme.px(5);
        int largeW = w - 2 * pad;
        int largeH = (int) (largeW / Theme.CARD_ASPECT);
        // Small icons: the full width, but short (an art strip), so more fit.
        int smallH = Math.max(14, (int) Math.round(largeW * 0.46));

        if (entries.isEmpty()) {
            g.setFont(Theme.font(Font.ITALIC, 10));
            g.setColor(Theme.TEXT_DIM);
            FontMetrics fm = g.getFontMetrics();
            String t = "History";
            g.drawString(t, (w - fm.stringWidth(t)) / 2, pad + fm.getAscent());
            g.dispose();
            return;
        }

        int y = pad - scroll;
        int prevTurn = -1;
        for (int i = entries.size() - 1; i >= 0; i--) {
            HistoryEntry e = entries.get(i);
            if (e.turn() != prevTurn) {
                y += turnDivider(g, e.turn(), y, w, pad);   // each turn's icons start under its label
                prevTurn = e.turn();
            }
            int bw = largeW;
            int bh = e.large() ? largeH : smallH;
            Rectangle box = new Rectangle((w - bw) / 2, y, bw, bh);
            if (y + bh >= 0 && y <= getHeight()) {
                drawEntry(g, e, box);
            }
            boxes.add(box);
            boxEntries.add(e);
            y += bh + gap;
        }
        maxScroll = Math.max(0, y + scroll - getHeight() + pad);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        }
        if (scroll > 0) {
            // Scrolled back: a hint that newer entries are above.
            g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0, 0, 0, 200), 0, Theme.px(22), new Color(0, 0, 0, 0)));
            g.fillRect(0, 0, w, Theme.px(22));
            g.setColor(Theme.TEXT);
            int a = Theme.px(6);
            int cx = w / 2;
            g.fillPolygon(new int[] {cx - a, cx + a, cx}, new int[] {Theme.px(10), Theme.px(10), Theme.px(3)}, 3);
        }
        g.dispose();
    }

    /** "T7" label with a line, above the entries of that turn; returns the height used. */
    private int turnDivider(Graphics2D g, int turn, int y, int w, int pad) {
        g.setFont(Theme.font(Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        String t = "T" + turn;
        int h = fm.getHeight() + Theme.px(4);
        int mid = y + h / 2;
        int tw = fm.stringWidth(t);
        g.setColor(Theme.PANEL_EDGE.brighter());
        g.drawLine(pad, mid, (w - tw) / 2 - Theme.px(3), mid);
        g.drawLine((w + tw) / 2 + Theme.px(3), mid, w - pad, mid);
        g.setColor(Theme.TEXT_DIM);
        g.drawString(t, (w - tw) / 2, mid + fm.getAscent() / 2 - Theme.px(1));
        return h;
    }

    private void drawEntry(Graphics2D g, HistoryEntry e, Rectangle r) {
        CardSnapshot card = e.icon();
        int arc = Math.max(6, Math.min(r.width, r.height) / 4);
        Color side = e.byYou() ? Theme.YOU : Theme.ENEMY;
        if (e.large()) {
            if (card != null) {
                CardPainter.paint(g, card, r.x, r.y, r.width, r.height);
            }
        } else {
            Graphics2D clip = (Graphics2D) g.create();
            clip.clip(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, arc, arc));
            if (e.relic() != null) {
                clip.setColor(new Color(0x5B6574));
                clip.fillRect(r.x, r.y, r.width, r.height);
                int d = (int) (r.height * 0.85);
                RelicIcon.paintBadge(clip, e.relic(), r.x + Theme.px(4), r.y + (r.height - d) / 2, d);
            } else if (card != null) {
                CardPainter.paintArt(clip, card, r.x, r.y, r.width, r.height);
            } else {
                clip.setColor(new Color(0x23272E));
                clip.fillRect(r.x, r.y, r.width, r.height);
            }
            clip.dispose();
            if (card == null && e.relic() == null) {
                // Nothing to picture (e.g. game over): the symbol itself, large and centered.
                int s = (int) (r.height * 0.85);
                HistoryIcons.paint(g, e.kind(), r.x + (r.width - s) / 2, r.y + (r.height - s) / 2, s);
            }
        }
        // Who did it: border color (thicker when hovered).
        boolean hot = hovered != null && hovered.seq() == e.seq();
        g.setColor(hot ? Theme.ACCENT : side);
        g.setStroke(new BasicStroke(Math.max(1.5f, Theme.px(hot ? 2.5 : 1.8))));
        g.draw(new RoundRectangle2D.Double(r.x + 0.5, r.y + 0.5, r.width - 1, r.height - 1, arc, arc));
        // What happened: the kind's symbol in the corner (spells are recognizable as cards).
        if (!e.large() && (card != null || e.relic() != null)) {
            int s = Math.max(12, (int) (r.height * 0.78));
            HistoryIcons.paint(g, e.kind(), r.x + r.width - s + Theme.px(4), r.y + r.height - s + Theme.px(4), s);
        }
        if (e.count() > 1) {
            String t = "×" + e.count();
            g.setFont(Theme.font(Theme.FontRole.NUMBERS, Font.BOLD, 10));
            FontMetrics fm = g.getFontMetrics();
            int bw = fm.stringWidth(t) + Theme.px(6);
            int bh = fm.getAscent() + Theme.px(2);
            com.planesnomad.gui.ui.theme.Skin.paint(g, "badge.count", r.x - Theme.px(2), r.y - Theme.px(2), bw, bh);
            g.setColor(com.planesnomad.gui.ui.theme.Skin.text("badge.count", Theme.TEXT));
            g.drawString(t, r.x - Theme.px(2) + Theme.px(3), r.y - Theme.px(2) + fm.getAscent());
        }
    }
}

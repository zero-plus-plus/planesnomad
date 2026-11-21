package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

/**
 * One horizontal row of cards (a hand, or the lands / other permanents / creatures of a
 * battlefield). Cards are sized from the row's height; tapped cards are turned sideways;
 * when cards don't fit they overlap. Hovering a card shows its preview.
 */
public final class CardRow extends JComponent {

    private final String label;
    private final CardPreview preview;
    private final boolean towardBottom;
    private List<CardSnapshot> cards = List.of();
    private final List<Rectangle> hitBoxes = new ArrayList<>();
    private CardSnapshot hovered;

    /**
     * @param label         faint label drawn when the row is empty (e.g. "Lands")
     * @param towardBottom  true if the middle of the table is below this row (enemy side),
     *                      used to nudge attackers toward the middle line
     */
    public CardRow(String label, CardPreview preview, boolean towardBottom) {
        this.label = label;
        this.preview = preview;
        this.towardBottom = towardBottom;
        setOpaque(false);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = null;
                preview.exit();
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setCards(List<CardSnapshot> cards) {
        this.cards = List.copyOf(cards);
        if (hovered != null) {
            // Keep the preview in sync if the hovered card changed (e.g. took damage).
            CardSnapshot updated = cards.stream().filter(c -> c.id() == hovered.id()).findFirst().orElse(null);
            hovered = updated;
            if (updated == null) {
                preview.exit();
            }
        }
        repaint();
    }

    private void updateHover(Point p) {
        CardSnapshot hit = null;
        // Later cards are drawn on top, so search from the end.
        for (int i = hitBoxes.size() - 1; i >= 0; i--) {
            if (hitBoxes.get(i).contains(p)) {
                hit = cards.get(i);
                break;
            }
        }
        if (hit == null) {
            if (hovered != null) {
                hovered = null;
                preview.exit();
                repaint();
            }
            return;
        }
        hovered = hit;
        preview.hover(hit, this, p);
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(400), Theme.px(80));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        hitBoxes.clear();

        int pad = Math.max(2, Theme.px(4));
        int ch = getHeight() - 2 * pad;          // card height
        int cw = (int) (ch * Theme.CARD_ASPECT); // card width
        if (ch <= 8 || cards.isEmpty()) {
            if (cards.isEmpty() && label != null) {
                g.setFont(Theme.font(java.awt.Font.ITALIC, 11));
                g.setColor(new Color(255, 255, 255, 40));
                g.drawString(label, pad * 2, getHeight() / 2 + g.getFontMetrics().getAscent() / 2);
            }
            g.dispose();
            return;
        }

        int gap = Math.max(2, Theme.px(6));
        int[] widths = new int[cards.size()];
        int total = 0;
        for (int i = 0; i < cards.size(); i++) {
            widths[i] = cards.get(i).tapped() ? ch : cw;   // a tapped card lies sideways
            total += widths[i] + (i > 0 ? gap : 0);
        }
        int available = getWidth() - 2 * pad;
        double squeeze = 1.0;
        if (total > available && cards.size() > 1) {
            // Overlap cards evenly so they all fit.
            int cardsOnly = total - gap * (cards.size() - 1);
            squeeze = (available - cardsOnly) / (double) (cards.size() - 1); // negative = overlap
        }
        int x = pad + Math.max(0, (available - Math.min(total, available)) / 2);
        for (int i = 0; i < cards.size(); i++) {
            CardSnapshot card = cards.get(i);
            int boxW = widths[i];
            int nudge = card.attacking() ? (towardBottom ? pad * 2 : -pad * 2) : 0;
            Rectangle box = new Rectangle(x, pad + nudge, boxW, ch);
            hitBoxes.add(box);
            drawCard(g, card, box, cw, ch);
            int step = squeeze == 1.0 ? boxW + gap : (int) Math.round(boxW + squeeze);
            x += step;
        }
        g.dispose();
    }

    private void drawCard(Graphics2D g, CardSnapshot card, Rectangle box, int cw, int ch) {
        if (card.tapped()) {
            // Rotate 90 degrees clockwise around the box: the card's width becomes its height.
            Graphics2D r = (Graphics2D) g.create();
            AffineTransform t = new AffineTransform();
            t.translate(box.x + box.width, box.y + (ch - cw) / 2.0);
            t.rotate(Math.PI / 2);
            r.transform(t);
            CardPainter.paint(r, card, 0, 0, cw, ch);
            r.dispose();
            Rectangle visible = new Rectangle(box.x, box.y + (ch - cw) / 2, ch, cw);
            outline(g, card, visible);
        } else {
            CardPainter.paint(g, card, box.x, box.y, cw, ch);
            outline(g, card, box);
        }
    }

    private void outline(Graphics2D g, CardSnapshot card, Rectangle r) {
        Color color = card.attacking() ? Theme.ATTACKING
            : card.blocking() ? Theme.BLOCKING
            : hovered != null && hovered.id() == card.id() ? Theme.ACCENT : null;
        if (color == null) {
            return;
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(2f, Theme.px(2.5))));
        int arc = Math.max(4, r.width / 10);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);
    }
}

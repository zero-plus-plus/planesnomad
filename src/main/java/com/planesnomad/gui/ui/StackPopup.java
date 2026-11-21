package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.MatchSnapshot.StackItem;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
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
import java.util.ArrayList;
import java.util.List;

/**
 * The stack as a small pop-up of card images floating over one side of the board.
 *
 * - One item: a single card. Several: a cascade, the item that resolves next in front.
 * - Abilities and triggers are shown with their source card and an "Ability"/"Trigger" tag.
 * - A colored outline shows who controls each item (yours / the enemy's).
 * - The item currently resolving (e.g. waiting for a choice) is tagged "Resolving".
 * - Hover a card for its preview; drag the pop-up to move it; right-click (or Tab, handled by
 *   {@link MatchView}) to show the full text list in the right column.
 */
public final class StackPopup extends JComponent {

    /** Card width at the reference resolution. */
    private static final int CARD_WIDTH = 118;
    /** How much of each card behind the front one stays visible, as a share of card height. */
    private static final double CASCADE_STEP = 0.24;
    /** At most this many cards are drawn; the rest are counted. */
    private static final int MAX_SHOWN = 6;

    private final CardPreview preview;
    private final Runnable onToggleDetails;
    private List<StackItem> items = List.of();
    private final List<Rectangle> hitBoxes = new ArrayList<>();
    private final List<StackItem> hitItems = new ArrayList<>();
    private boolean movedByUser;
    private Point dragOffset;

    public StackPopup(CardPreview preview, Runnable onToggleDetails) {
        this.preview = preview;
        this.onToggleDetails = onToggleDetails;
        setOpaque(false);
        setToolTipText(null);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
                    onToggleDetails.run();
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    dragOffset = e.getPoint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragOffset = null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset == null || getParent() == null) {
                    return;
                }
                preview.exit();
                Point p = SwingUtilities.convertPoint(StackPopup.this, e.getPoint(), getParent());
                moveWithin(p.x - dragOffset.x, p.y - dragOffset.y);
                movedByUser = true;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                StackItem hit = itemAt(e.getPoint());
                if (hit != null && hit.source() != null) {
                    preview.hover(hit.source(), StackPopup.this, e.getPoint());
                } else {
                    preview.exit();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                preview.exit();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setItems(List<StackItem> items) {
        this.items = List.copyOf(items);
        setSize(getPreferredSize());
        repaint();
    }

    public List<StackItem> items() {
        return items;
    }

    public boolean movedByUser() {
        return movedByUser;
    }

    public void forgetUserPosition() {
        movedByUser = false;
    }

    /** Keep the pop-up inside its parent. */
    public void moveWithin(int x, int y) {
        Container parent = getParent();
        if (parent == null) {
            setLocation(x, y);
            return;
        }
        int maxX = Math.max(0, parent.getWidth() - getWidth());
        int maxY = Math.max(0, parent.getHeight() - getHeight());
        setLocation(Math.max(0, Math.min(x, maxX)), Math.max(0, Math.min(y, maxY)));
    }

    private int cardW() {
        return Theme.px(CARD_WIDTH);
    }

    private int cardH() {
        return (int) (cardW() / Theme.CARD_ASPECT);
    }

    private int header() {
        return Theme.px(20);
    }

    private int pad() {
        return Theme.px(8);
    }

    private int shownCount() {
        return Math.min(items.size(), MAX_SHOWN);
    }

    @Override
    public Dimension getPreferredSize() {
        int n = Math.max(1, shownCount());
        int step = (int) (cardH() * CASCADE_STEP);
        int extra = items.size() > MAX_SHOWN ? Theme.px(16) : 0;
        return new Dimension(cardW() + 2 * pad(), header() + cardH() + (n - 1) * step + pad() + extra);
    }

    private StackItem itemAt(Point p) {
        for (int i = hitBoxes.size() - 1; i >= 0; i--) {   // front-most last
            if (hitBoxes.get(i).contains(p)) {
                return hitItems.get(i);
            }
        }
        return null;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        hitBoxes.clear();
        hitItems.clear();

        int arc = Theme.px(10);
        g.setColor(new Color(12, 14, 18, 215));
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        g.setColor(Theme.PANEL_EDGE);
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);

        // Header: title and a hint; the whole pop-up is draggable.
        g.setFont(Theme.font(Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Theme.TEXT_DIM);
        String title = "STACK" + (items.size() > 1 ? " · " + items.size() : "");
        g.drawString(title, pad(), (header() + fm.getAscent()) / 2 + Theme.px(1));
        g.setFont(Theme.font(Font.PLAIN, 9));
        FontMetrics hm = g.getFontMetrics();
        String hint = "Tab: details";
        g.drawString(hint, getWidth() - pad() - hm.stringWidth(hint), (header() + hm.getAscent()) / 2 + Theme.px(1));

        int n = shownCount();
        int step = (int) (cardH() * CASCADE_STEP);
        // items.get(0) is the top of the stack (resolves next): draw it last, in front.
        for (int i = n - 1; i >= 0; i--) {
            StackItem item = items.get(i);
            int depth = n - 1 - i;            // 0 = furthest back
            int x = pad();
            int y = header() + depth * step;
            Rectangle box = new Rectangle(x, y, cardW(), i == 0 ? cardH() : step);
            hitBoxes.add(box);
            hitItems.add(item);
            CardSnapshot card = item.source();
            if (card != null) {
                CardPainter.paint(g, card, x, y, cardW(), cardH());
                if (item.relic() != null) {
                    // Relics are shown as relics: their badge sits in the art box.
                    int d = (int) (cardW() * 0.34);
                    RelicIcon.paintBadge(g, item.relic(), x + (cardW() - d) / 2, y + (int) (cardH() * 0.14), d);
                }
            } else {
                g.setColor(Theme.PANEL);
                g.fillRoundRect(x, y, cardW(), cardH(), arc, arc);
            }
            // Controller outline
            g.setColor(item.byYou() ? Theme.YOU : Theme.ENEMY);
            g.setStroke(new BasicStroke(Math.max(2f, Theme.px(2.5))));
            g.drawRoundRect(x, y, cardW() - 1, cardH() - 1, Theme.px(8), Theme.px(8));
            // Tags, on the front card only (others show just their title strip)
            if (i == 0) {
                int tagY = y + cardH() - Theme.px(22);
                if (item.kind() != StackItem.Kind.SPELL) {
                    tagY = tag(g, item.kind() == StackItem.Kind.TRIGGER ? "Trigger" : "Ability",
                        x + Theme.px(4), tagY, new Color(0x2B2F36), Theme.TEXT) - Theme.px(18);
                }
                if (item.resolving()) {
                    tag(g, "Resolving…", x + Theme.px(4), tagY, Theme.ACCENT, new Color(0x1A1A1A));
                }
            } else if (item.kind() != StackItem.Kind.SPELL) {
                tag(g, item.kind() == StackItem.Kind.TRIGGER ? "T" : "A",
                    x + cardW() - Theme.px(18), y + Theme.px(2), new Color(0x2B2F36), Theme.TEXT);
            }
        }
        if (items.size() > MAX_SHOWN) {
            g.setFont(Theme.font(Font.ITALIC, 10));
            g.setColor(Theme.TEXT_DIM);
            g.drawString("+" + (items.size() - MAX_SHOWN) + " more (Tab)", pad(), getHeight() - pad() / 2);
        }
        g.dispose();
    }

    /** Draws a small label; returns its top y. */
    private static int tag(Graphics2D g, String text, int x, int y, Color bg, Color fg) {
        g.setFont(Theme.font(Font.BOLD, 9));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + Theme.px(8);
        int h = fm.getHeight() + Theme.px(2);
        g.setColor(bg);
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(fg);
        g.drawString(text, x + Theme.px(4), y + Theme.px(1) + fm.getAscent());
        return y;
    }
}

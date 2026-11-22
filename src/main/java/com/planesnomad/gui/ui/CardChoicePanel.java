package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.ui.theme.Skin;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Cards to decide about, shown large on the board (scry, surveil, top-or-bottom): click a card
 * to send it away (bottom / graveyard), click again to keep it. The prompt bar has "Done".
 * Hovering a card shows its preview. Themed element "choice" (the panel behind the cards).
 */
public final class CardChoicePanel extends JComponent {

    /** What the panel shows and how a click is answered. */
    public interface Model {
        List<CardSnapshot> cards();

        boolean isAway(CardSnapshot c);

        String awayLabel();

        void toggle(CardSnapshot c);
    }

    private static final int CARD_W = 150;

    private Model model;
    private final List<Rectangle> boxes = new ArrayList<>();
    private final CardPreview preview;

    public CardChoicePanel(CardPreview preview) {
        this.preview = preview;
        setOpaque(false);
        setVisible(false);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = indexAt(e);
                if (i >= 0 && model != null) {
                    model.toggle(model.cards().get(i));
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int i = indexAt(e);
                if (i >= 0 && model != null) {
                    preview.hover(model.cards().get(i), CardChoicePanel.this, e.getPoint());
                } else {
                    preview.exit();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                preview.exit();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    /** Shows these cards (null hides the panel). */
    public void setModel(Model m) {
        model = m;
        setVisible(m != null);
        if (m != null) {
            setSize(getPreferredSize());
        }
        repaint();
    }

    private int indexAt(MouseEvent e) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).contains(e.getPoint())) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public Dimension getPreferredSize() {
        int n = model == null ? 1 : Math.max(1, model.cards().size());
        int cw = Theme.px(CARD_W);
        int ch = (int) (cw / Theme.CARD_ASPECT);
        int gap = Theme.px(14);
        int pad = Theme.px(18);
        return new Dimension(2 * pad + n * cw + (n - 1) * gap, 2 * pad + ch + Theme.px(24));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (model == null) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        Skin.paint(g, "choice", 0, 0, getWidth(), getHeight());
        boxes.clear();
        int cw = Theme.px(CARD_W);
        int ch = (int) (cw / Theme.CARD_ASPECT);
        int gap = Theme.px(14);
        int pad = Theme.px(18);
        int x = pad;
        g.setFont(Theme.font(Font.BOLD, 12));
        FontMetrics fm = g.getFontMetrics();
        for (CardSnapshot c : model.cards()) {
            Rectangle r = new Rectangle(x, pad, cw, ch);
            boxes.add(r);
            boolean away = model.isAway(c);
            CardPainter.paint(g, c, r.x, r.y, r.width, r.height);
            if (away) {
                g.setColor(new Color(0, 0, 0, 150));
                g.fillRoundRect(r.x, r.y, r.width, r.height, cw / 12, cw / 12);
            }
            String label = away ? model.awayLabel() : "Top";
            Color tagColor = away ? Theme.ENEMY : Theme.YOU;
            int tw = fm.stringWidth(label) + Theme.px(14);
            int th = fm.getHeight() + Theme.px(2);
            int tx = r.x + (cw - tw) / 2;
            int ty = r.y + ch + Theme.px(4);
            g.setColor(tagColor);
            g.fillRoundRect(tx, ty, tw, th, th, th);
            g.setColor(Theme.textOn(tagColor));
            g.drawString(label, tx + Theme.px(7), ty + Theme.px(1) + fm.getAscent());
            x += cw + gap;
        }
        g.dispose();
    }
}

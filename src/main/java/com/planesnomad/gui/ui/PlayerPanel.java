package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.PlayerSnapshot;
import com.planesnomad.gui.model.RelicSnapshot;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Left-column panel for one player:
 * - a large portrait (art of the player's portrait card; placeholder otherwise) with the life
 *   total on its corner,
 * - the name and theme in small text under it,
 * - for the enemy: an effects bar with its passive effects (relic-style icons) and guaranteed
 *   cards (small art thumbnails, hover for the card),
 * - hand / library / graveyard / exile counts (click graveyard or exile to list the cards).
 */
public final class PlayerPanel extends JPanel {

    /** Portrait height as a share of its width. */
    private static final double PORTRAIT_ASPECT = 0.62;

    private final boolean enemy;
    private final CardPreview preview;
    private final Portrait portrait = new Portrait();
    private final JLabel name = new JLabel();
    private final JLabel subtitle = new JLabel();
    private final JPanel effects = new JPanel();
    private final JLabel library = zoneLabel();
    private final JButton graveyard = zoneButton();
    private final JButton exile = zoneButton();
    private final JLabel hand = zoneLabel();

    private List<CardSnapshot> graveyardCards = List.of();
    private List<CardSnapshot> exileCards = List.of();
    private List<RelicSnapshot> shownEffects = List.of();
    private List<CardSnapshot> shownFeatured = List.of();
    /** While a person plays: highlight for the portrait (e.g. a legal target), and its click. */
    private java.util.function.Function<String, Color> portraitMark;
    private java.util.function.Consumer<String> portraitClick;

    public void setPortraitInteraction(java.util.function.Function<String, Color> mark,
                                       java.util.function.Consumer<String> click) {
        portraitMark = mark;
        portraitClick = click;
        portrait.repaint();
    }

    /** Color of the player whose stack item targets this player, or null. */
    private Color targetedBy;

    /** Marks the portrait as the target of a stack item (crosshair, glow, "TARGET" tag). */
    public void setTargetMark(Color c) {
        if (!java.util.Objects.equals(c, targetedBy)) {
            targetedBy = c;
            portrait.repaint();
        }
    }

    public Color targetMark() {
        return targetedBy;
    }

    /** The portrait, e.g. for arrows pointing at it. */
    public javax.swing.JComponent portraitComponent() {
        return portrait;
    }

    /** Latest snapshot of this player, for the portrait's info pop-up. */
    private PlayerSnapshot last;
    private final PlayerInfoPopup info;

    public PlayerPanel(boolean enemy, CardPreview preview) {
        this.enemy = enemy;
        this.preview = preview;
        this.info = new PlayerInfoPopup(enemy);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);

        effects.setLayout(new BoxLayout(effects, BoxLayout.X_AXIS));
        effects.setOpaque(false);
        effects.setVisible(false);

        JPanel zones = new JPanel(new GridLayout(4, 1, 0, 0)) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);   // stay compact
            }
        };
        zones.setOpaque(false);
        zones.add(hand);
        zones.add(library);
        zones.add(graveyard);
        zones.add(exile);

        for (JComponent c : new JComponent[] {portrait, name, subtitle, effects, zones}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        add(portrait);
        add(Box.createVerticalStrut(4));
        add(name);
        add(subtitle);
        add(Box.createVerticalStrut(4));
        add(effects);
        add(Box.createVerticalStrut(4));
        add(zones);
        add(Box.createVerticalGlue());

        graveyard.addActionListener(e -> showList("Graveyard", graveyardCards));
        exile.addActionListener(e -> showList("Exile", exileCards));
        applyFonts();
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        com.planesnomad.gui.ui.theme.Skin.paint((java.awt.Graphics2D) g, "panel", 0, 0, getWidth(), getHeight());
    }

    /** Re-apply fonts, sizes and theme colors (after the UI scale or the theme changed). */
    public void applyFonts() {
        int pad = Theme.px(8);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(enemy ? 0 : 1, 0, enemy ? 1 : 0, 1, Theme.PANEL_EDGE),
            BorderFactory.createEmptyBorder(pad, pad, pad, pad)));
        name.setFont(Theme.font(Font.BOLD, 12));
        name.setForeground(enemy ? Theme.ENEMY : Theme.YOU);
        subtitle.setFont(Theme.font(Font.PLAIN, 10));
        subtitle.setForeground(Theme.TEXT_DIM);
        for (JComponent c : new JComponent[] {library, graveyard, exile, hand}) {
            c.setFont(Theme.font(Font.PLAIN, 11));
            c.setForeground(Theme.TEXT_DIM);
        }
        rebuildEffects();
        revalidate();
    }

    public void update(PlayerSnapshot p) {
        last = p;
        String nameText = p.name() + (p.activeTurn() ? "  ●" : "");
        if (!nameText.equals(name.getText())) {
            name.setText(nameText);
            name.setPreferredSize(null);   // measure the new text (a short name grew by the turn marker)
            revalidate();
        }
        subtitle.setText(p.subtitle() == null ? "" : p.subtitle());
        subtitle.setToolTipText(p.subtitle());
        portrait.card = p.portrait();
        portrait.life = p.lost() ? "✕" : Integer.toString(p.life());
        portrait.initial = p.name().isEmpty() ? "?" : p.name().substring(0, 1);
        portrait.color = enemy ? Theme.ENEMY : Theme.YOU;
        portrait.repaint();
        library.setText("Library " + p.librarySize());
        hand.setText("Hand " + p.hand().size());
        graveyardCards = p.graveyard();
        exileCards = p.exile();
        graveyard.setText("Graveyard " + graveyardCards.size());
        exile.setText("Exile " + exileCards.size());
        // The top card's art (the engine adds new cards at the end of these lists).
        graveyard.setIcon(topIcon(graveyardCards));
        exile.setIcon(topIcon(exileCards));

        // The enemy's effects bar: its passive effects and guaranteed cards.
        List<RelicSnapshot> fx = enemy ? p.relics() : List.of();
        List<CardSnapshot> featured = enemy ? p.featured() : List.of();
        if (!fx.equals(shownEffects) || !featured.equals(shownFeatured)) {
            shownEffects = fx;
            shownFeatured = featured;
            rebuildEffects();
        }
    }

    private void rebuildEffects() {
        effects.removeAll();
        for (RelicSnapshot r : shownEffects) {
            RelicIcon icon = new RelicIcon(false, 24);
            icon.setRelic(r);
            effects.add(icon);
        }
        if (!shownEffects.isEmpty() && !shownFeatured.isEmpty()) {
            effects.add(Box.createHorizontalStrut(Theme.px(6)));
        }
        for (CardSnapshot c : shownFeatured) {
            effects.add(new FeaturedCard(c, last == null ? null : last.featuredNotes().get(c.name())));
        }
        effects.setVisible(!shownEffects.isEmpty() || !shownFeatured.isEmpty());
        effects.revalidate();
        effects.repaint();
    }

    private static ArtIcon topIcon(List<CardSnapshot> cards) {
        if (cards.isEmpty()) {
            return null;
        }
        return new ArtIcon(cards.get(cards.size() - 1), Theme.px(28), Theme.px(20));
    }

    private void showList(String title, List<CardSnapshot> cards) {
        StringBuilder sb = new StringBuilder();
        if (cards.isEmpty()) {
            sb.append("(empty)");
        }
        for (CardSnapshot c : cards) {
            sb.append(c.name()).append('\n');
        }
        JOptionPane.showMessageDialog(this, sb.toString(), name.getText() + " - " + title,
            JOptionPane.PLAIN_MESSAGE);
    }

    private static JLabel zoneLabel() {
        JLabel l = new JLabel();
        l.setForeground(Theme.TEXT_DIM);
        return l;
    }

    private static JButton zoneButton() {
        JButton b = new JButton();
        b.setForeground(Theme.TEXT_DIM);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setIconTextGap(4);
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setToolTipText("Click to list the cards");
        return b;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(220), super.getPreferredSize().height);
    }

    /**
     * Large portrait: the portrait card's art, with the life total on the lower right. Hovering
     * shows the player's info pop-up (art crop, life, effects), not the portrait's card.
     */
    private final class Portrait extends JComponent {
        CardSnapshot card;
        String life = "";
        String initial = "?";
        Color color = Color.GRAY;

        Portrait() {
            MouseAdapter hover = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    // The player, not the portrait's card: art crop, life and effects.
                    info.show(last, Portrait.this, e.getPoint());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    info.hide();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (javax.swing.SwingUtilities.isLeftMouseButton(e) && portraitClick != null && last != null) {
                        portraitClick.accept(last.name());
                    }
                }
            };
            addMouseListener(hover);
            addMouseMotionListener(hover);
        }

        @Override
        public Dimension getPreferredSize() {
            int w = Theme.px(204);
            return new Dimension(w, (int) (w * PORTRAIT_ASPECT));
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            int arc = Theme.px(12);
            if (card != null) {
                CardPainter.paintArt(g, card, 0, 0, w, h);
            } else {
                // Placeholder until portraits exist: the player's color and initial.
                g.setPaint(new GradientPaint(0, 0, Theme.blend(color, Color.BLACK, 0.3), 0, h, Theme.blend(color, Color.BLACK, 0.7)));
                g.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, arc, arc));
                g.setFont(Theme.font(Font.BOLD, 40));
                FontMetrics fm = g.getFontMetrics();
                g.setColor(new Color(255, 255, 255, 120));
                g.drawString(initial, (w - fm.stringWidth(initial)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
            }
            Color mark = portraitMark == null || last == null ? null : portraitMark.apply(last.name());
            g.setColor(mark != null ? mark : color);
            g.setStroke(new BasicStroke(Math.max(1.5f, Theme.px(mark != null ? 4 : 2))));
            g.draw(new RoundRectangle2D.Double(1, 1, w - 3, h - 3, arc, arc));
            if (targetedBy != null) {
                TargetMarks.paint(g, new java.awt.Rectangle(Theme.px(6), Theme.px(6), w - Theme.px(12), h - Theme.px(12)), targetedBy);
            }

            // Life badge (themed element "badge.life", number font)
            g.setFont(Theme.font(Theme.FontRole.NUMBERS, Font.BOLD, 24));
            FontMetrics fm = g.getFontMetrics();
            int bw = Math.max(fm.stringWidth(life) + Theme.px(16), Theme.px(44));
            int bh = fm.getHeight() + Theme.px(2);
            int bx = w - bw - Theme.px(6);
            int by = h - bh - Theme.px(6);
            com.planesnomad.gui.ui.theme.Skin.paint(g, "badge.life", bx, by, bw, bh);
            g.setColor(com.planesnomad.gui.ui.theme.Skin.text("badge.life", Theme.LIFE));
            g.drawString(life, bx + (bw - fm.stringWidth(life)) / 2, by + Theme.px(1) + fm.getAscent());
            g.dispose();
        }
    }

    /** A guaranteed card in the enemy's effects bar: art thumbnail, hover for the full card. */
    private final class FeaturedCard extends JComponent {
        private final CardSnapshot card;
        /** e.g. "Turn 2": drawn as a small badge ("T2") on the thumbnail. */
        private final String note;

        FeaturedCard(CardSnapshot card, String note) {
            this.card = card;
            this.note = note;
            MouseAdapter hover = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    preview.hover(card, FeaturedCard.this, e.getPoint());
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    preview.exit();
                }
            };
            addMouseListener(hover);
            addMouseMotionListener(hover);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(Theme.px(40), Theme.px(28));
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            int pad = Theme.px(2);
            CardPainter.paintArt(g, card, pad, pad, getWidth() - 2 * pad, getHeight() - 2 * pad);
            if (note != null && !note.isBlank()) {
                String text = note.replaceFirst("(?i)^turn\\s*", "T");
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(Theme.font(Theme.FontRole.NUMBERS, java.awt.Font.BOLD, 9));
                java.awt.FontMetrics fm = g.getFontMetrics();
                int bw = fm.stringWidth(text) + Theme.px(5);
                int bh = fm.getAscent() + Theme.px(1);
                int bx = getWidth() - bw;
                int by = getHeight() - bh;
                com.planesnomad.gui.ui.theme.Skin.paint(g, "badge.count", bx, by, bw, bh);
                g.setColor(com.planesnomad.gui.ui.theme.Skin.text("badge.count", Theme.TEXT));
                g.drawString(text, bx + Theme.px(2.5), by + fm.getAscent());
            }
        }
    }
}

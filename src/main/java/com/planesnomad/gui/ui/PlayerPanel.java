package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.PlayerSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.List;

/**
 * Left-column panel for one player: portrait (placeholder for now), name, life, a subtitle
 * (the enemy's theme), and library / graveyard / exile counts. Clicking graveyard or exile
 * lists the cards.
 */
public final class PlayerPanel extends JPanel {

    private final boolean enemy;
    private final Portrait portrait = new Portrait();
    private final JLabel name = new JLabel();
    private final JLabel subtitle = new JLabel();
    private final JLabel life = new JLabel("", SwingConstants.RIGHT);
    private final JLabel library = zoneLabel();
    private final JButton graveyard = zoneButton();
    private final JButton exile = zoneButton();
    private final JLabel hand = zoneLabel();

    private List<CardSnapshot> graveyardCards = List.of();
    private List<CardSnapshot> exileCards = List.of();

    public PlayerPanel(boolean enemy) {
        super(new BorderLayout());
        this.enemy = enemy;
        setBackground(Theme.PANEL);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.PANEL_EDGE));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel names = new JPanel(new GridLayout(2, 1));
        names.setOpaque(false);
        names.add(name);
        names.add(subtitle);
        header.add(portrait, BorderLayout.WEST);
        header.add(names, BorderLayout.CENTER);
        header.add(life, BorderLayout.EAST);

        JPanel zones = new JPanel(new GridLayout(4, 1, 0, Theme.px(2)));
        zones.setOpaque(false);
        zones.add(hand);
        zones.add(library);
        zones.add(graveyard);
        zones.add(exile);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(zones, BorderLayout.NORTH);
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);

        graveyard.addActionListener(e -> showList("Graveyard", graveyardCards));
        exile.addActionListener(e -> showList("Exile", exileCards));
        applyFonts();
    }

    /** Re-apply fonts after the UI scale changed. */
    public void applyFonts() {
        int pad = Theme.px(8);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(enemy ? 0 : 1, 0, enemy ? 1 : 0, 1, Theme.PANEL_EDGE),
            BorderFactory.createEmptyBorder(pad, pad, pad, pad)));
        name.setFont(Theme.font(Font.BOLD, 14));
        name.setForeground(enemy ? Theme.ENEMY : Theme.YOU);
        subtitle.setFont(Theme.font(Font.PLAIN, 11));
        subtitle.setForeground(Theme.TEXT_DIM);
        life.setFont(Theme.font(Font.BOLD, 26));
        life.setForeground(Theme.LIFE);
        for (JComponent c : new JComponent[] {library, graveyard, exile, hand}) {
            c.setFont(Theme.font(Font.PLAIN, 11));
        }
        portrait.setPreferredSize(new Dimension(Theme.px(44), Theme.px(44)));
    }

    public void update(PlayerSnapshot p) {
        name.setText(p.name() + (p.activeTurn() ? "  ●" : ""));
        subtitle.setText(p.subtitle() == null ? "" : p.subtitle());
        life.setText(p.lost() ? "✕" : Integer.toString(p.life()));
        life.setToolTipText("Life (started at " + p.startingLife() + ")");
        library.setText("Library " + p.librarySize());
        hand.setText("Hand " + p.hand().size());
        graveyardCards = p.graveyard();
        exileCards = p.exile();
        graveyard.setText("Graveyard " + graveyardCards.size());
        exile.setText("Exile " + exileCards.size());
        // The top card's art (the engine adds new cards at the end of these lists).
        graveyard.setIcon(topIcon(graveyardCards));
        exile.setIcon(topIcon(exileCards));
        portrait.initial = p.name().isEmpty() ? "?" : p.name().substring(0, 1);
        portrait.color = enemy ? Theme.ENEMY : Theme.YOU;
        portrait.repaint();
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
        b.setMargin(new java.awt.Insets(0, 0, 0, 0));
        b.setToolTipText("Click to list the cards");
        return b;
    }

    /** Placeholder portrait: a colored circle with the player's initial. */
    private static final class Portrait extends JComponent {
        String initial = "?";
        Color color = Color.GRAY;

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = Math.min(getWidth(), getHeight()) - 4;
            g.setColor(Theme.blend(color, Color.BLACK, 0.4));
            g.fillOval(2, 2, d, d);
            g.setColor(color);
            g.drawOval(2, 2, d, d);
            g.setFont(Theme.font(Font.BOLD, 18));
            var fm = g.getFontMetrics();
            g.setColor(Theme.TEXT);
            g.drawString(initial, 2 + (d - fm.stringWidth(initial)) / 2, 2 + (d + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(210), super.getPreferredSize().height);
    }
}

package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.PlayerSnapshot;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;

/**
 * The table: the enemy's half on top, yours below, split by the middle line.
 *
 * Each half has three battlefield rows plus the hand. Lands sit at the outer edge,
 * creatures nearest the middle line, other permanents in between (as in Cockatrice).
 */
public final class BoardPanel extends JPanel {

    // Row weights (share of each half's height)
    // The three battlefield rows are equal, so every permanent is drawn at the same size.
    private static final double HAND_YOU = 0.28;
    private static final double HAND_ENEMY = 0.14;
    private static final double LANDS = 0.27;
    private static final double OTHER = 0.27;
    private static final double CREATURES = 0.27;

    private final CardRow enemyHand;
    private final CardRow enemyLands;
    private final CardRow enemyOther;
    private final CardRow enemyCreatures;
    private final CardRow youCreatures;
    private final CardRow youOther;
    private final CardRow youLands;
    private final CardRow youHand;

    private int midlineY;
    private CardSnapshot youPlaymat;
    private CardSnapshot enemyPlaymat;
    private boolean playmatsOn = true;

    public BoardPanel(CardPreview preview) {
        super(new WeightedColumn());
        setOpaque(true);
        setBackground(Theme.TABLE);

        enemyHand = new CardRow("Enemy hand", preview, true);
        enemyLands = new CardRow("Lands", preview, true);
        enemyOther = new CardRow("Other permanents", preview, true);
        enemyCreatures = new CardRow("Creatures", preview, true);
        youCreatures = new CardRow("Creatures", preview, false);
        youOther = new CardRow("Other permanents", preview, false);
        youLands = new CardRow("Lands", preview, false);
        youHand = new CardRow("Your hand", preview, false);

        // Battlefield rows pile identical cards; hands never do.
        for (CardRow row : List.of(enemyOther, enemyCreatures, youCreatures, youOther)) {
            row.setGrouping(true, false);
        }
        enemyLands.setGrouping(true, true);
        youLands.setGrouping(true, true);

        add(enemyHand, HAND_ENEMY);
        add(enemyLands, LANDS);
        add(enemyOther, OTHER);
        add(enemyCreatures, CREATURES);
        add(youCreatures, CREATURES);
        add(youOther, OTHER);
        add(youLands, LANDS);
        add(youHand, HAND_YOU);
    }

    /** Setting: pile identical lands without the default limit of three per pile. */
    public void setStackLandsFully(boolean on) {
        enemyLands.setStackLandsFully(on);
        youLands.setStackLandsFully(on);
    }

    /** Setting: different arts of the same card in separate piles (default: together). */
    public void setSeparateArts(boolean on) {
        battlefieldRows().forEach(r -> r.setSeparateArts(on));
    }

    /** Your battlefield rows (creatures, other permanents, lands), e.g. to read a pile selection. */
    public List<CardRow> yourBattlefieldRows() {
        return List.of(youCreatures, youOther, youLands);
    }

    /** All battlefield rows, enemy's first. */
    public List<CardRow> battlefieldRows() {
        return List.of(enemyLands, enemyOther, enemyCreatures, youCreatures, youOther, youLands);
    }

    /** Setting: playmat backgrounds on or off. */
    public void setPlaymats(boolean on) {
        playmatsOn = on;
        repaint();
    }

    public void update(MatchSnapshot s) {
        youPlaymat = s.you().playmat();
        enemyPlaymat = s.enemy().playmat();
        fill(s.enemy(), enemyHand, enemyLands, enemyOther, enemyCreatures);
        fill(s.you(), youHand, youLands, youOther, youCreatures);
    }

    private static void fill(PlayerSnapshot p, CardRow hand, CardRow lands, CardRow other, CardRow creatures) {
        hand.setCards(p.hand());
        lands.setCards(filter(p.battlefield(), Kind.LAND));
        other.setCards(filter(p.battlefield(), Kind.OTHER));
        creatures.setCards(filter(p.battlefield(), Kind.CREATURE));
    }

    private enum Kind { LAND, OTHER, CREATURE }

    private static List<CardSnapshot> filter(List<CardSnapshot> cards, Kind kind) {
        return cards.stream().filter(c -> switch (kind) {
            // Animated lands (land creatures) go with creatures.
            case CREATURE -> c.creature();
            case LAND -> c.land() && !c.creature();
            case OTHER -> c.other();
        }).toList();
    }

    @Override
    public void doLayout() {
        super.doLayout();
        midlineY = youCreatures.getY();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        // Each half: the player's playmat (darkened art), or the plain table color.
        if (!playmatsOn || !Playmats.paint(g, enemyPlaymat, false, 0, 0, getWidth(), midlineY)) {
            g.setColor(Theme.TABLE_ENEMY);
            g.fillRect(0, 0, getWidth(), midlineY);
        }
        if (!playmatsOn || !Playmats.paint(g, youPlaymat, true, 0, midlineY, getWidth(), getHeight() - midlineY)) {
            g.setColor(Theme.TABLE);
            g.fillRect(0, midlineY, getWidth(), getHeight() - midlineY);
        }
        g.setColor(Theme.MIDLINE);
        g.setStroke(new BasicStroke(Math.max(1f, Theme.px(1.5))));
        g.drawLine(0, midlineY, getWidth(), midlineY);
        // Faint separator above your hand
        // Hands sit on a darker strip (translucent, so the playmat still shows through).
        g.setColor(new java.awt.Color(0, 0, 0, 110));
        g.fillRect(0, youHand.getY(), getWidth(), youHand.getHeight());
        g.fillRect(0, enemyHand.getY(), getWidth(), enemyHand.getHeight());
    }
}

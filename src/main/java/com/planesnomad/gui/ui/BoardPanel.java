package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.PlayerSnapshot;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Map;
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

        // Blockers line up across from their attackers: when one creature row moves, the other follows.
        enemyCreatures.onLayoutChanged(() -> {
            if (!blocks.isEmpty()) {
                youCreatures.repaint();
                repaint();
            }
        });
        youCreatures.onLayoutChanged(() -> {
            if (!blocks.isEmpty()) {
                enemyCreatures.repaint();
                repaint();
            }
        });

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
    /** y of the middle line between the two halves, in this panel's coordinates. */
    public int midline() {
        return midlineY;
    }

    /** Every row: hands and battlefields. */
    public List<CardRow> allRows() {
        return List.of(enemyHand, enemyLands, enemyOther, enemyCreatures, youCreatures, youOther, youLands, youHand);
    }

    /** Top of your hand row, in this panel's coordinates (the prompt bar sits just above it). */
    public int yourHandTop() {
        return youHand.getY();
    }

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
        setBlocks(s.blocks());
    }

    /** Blocker id -> attacker ids: from the engine, plus blocks you're still choosing. */
    private Map<Integer, List<Integer>> blocks = Map.of();

    /**
     * Shows who blocks whom: each blocker sits across from the attacker it blocks (several blockers
     * side by side), with a small arrow from blocker to attacker.
     */
    public void setBlocks(Map<Integer, List<Integer>> b) {
        Map<Integer, List<Integer>> m = b == null ? Map.of() : Map.copyOf(b);
        if (m.equals(blocks)) {
            return;
        }
        blocks = m;
        for (CardRow row : List.of(enemyCreatures, youCreatures)) {
            CardRow other = row == enemyCreatures ? youCreatures : enemyCreatures;
            row.setAnchors(m.keySet(), id -> {
                List<Integer> attackers = blocks.get(id);
                if (attackers == null || attackers.isEmpty()) {
                    return null;
                }
                Rectangle r = other.cardBounds(attackers.get(0));
                if (r == null) {
                    return null;
                }
                Point c = SwingUtilities.convertPoint(other, r.x + r.width / 2, 0, row);
                return c.x;
            });
        }
        repaint();
    }

    /** Small arrows from each blocker to the attacker it blocks, over the rows. */
    @Override
    protected void paintChildren(Graphics g0) {
        super.paintChildren(g0);
        if (blocks.isEmpty()) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        for (Map.Entry<Integer, List<Integer>> e : blocks.entrySet()) {
            Rectangle b = bounds(e.getKey());
            if (b == null) {
                continue;
            }
            for (int attacker : e.getValue()) {
                Rectangle a = bounds(attacker);
                if (a != null) {
                    blockArrow(g, b, a);
                }
            }
        }
        g.dispose();
    }

    /** A card's rectangle in this panel, from whichever creature row holds it. */
    private Rectangle bounds(int id) {
        for (CardRow row : List.of(enemyCreatures, youCreatures)) {
            Rectangle r = row.cardBounds(id);
            if (r != null) {
                return SwingUtilities.convertRectangle(row, r, this);
            }
        }
        return null;
    }

    private static void blockArrow(Graphics2D g, Rectangle blocker, Rectangle attacker) {
        boolean up = attacker.y < blocker.y;
        double x1 = blocker.getCenterX();
        double y1 = up ? blocker.y + Theme.px(4) : blocker.y + blocker.height - Theme.px(4);
        double x2 = attacker.getCenterX();
        double y2 = up ? attacker.y + attacker.height - Theme.px(4) : attacker.y + Theme.px(4);
        double len = Math.hypot(x2 - x1, y2 - y1);
        if (len < 4) {
            return;
        }
        float w = Math.max(2.5f, Theme.px(3.5));
        java.awt.geom.Line2D line = new java.awt.geom.Line2D.Double(x1, y1, x2, y2);
        g.setColor(new Color(0, 0, 0, 160));
        g.setStroke(new BasicStroke(w + 2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);
        g.setColor(Theme.BLOCKING);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);
        double ang = Math.atan2(y2 - y1, x2 - x1);
        double hl = Math.max(9, Theme.px(13));
        double hw = hl * 0.65;
        java.awt.geom.Path2D.Double head = new java.awt.geom.Path2D.Double();
        head.moveTo(x2, y2);
        head.lineTo(x2 - hl * Math.cos(ang) + hw * Math.sin(ang), y2 - hl * Math.sin(ang) - hw * Math.cos(ang));
        head.lineTo(x2 - hl * Math.cos(ang) - hw * Math.sin(ang), y2 - hl * Math.sin(ang) + hw * Math.cos(ang));
        head.closePath();
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(0, 0, 0, 170));
        g.draw(head);
        g.setColor(Theme.BLOCKING);
        g.fill(head);
        // A small shield-like dot where the arrow leaves the blocker.
        int d = (int) Math.max(7, w * 2.4);
        g.fillOval((int) x1 - d / 2, (int) y1 - d / 2, d, d);
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
        // Each half: the player's playmat, or the theme's table (element table.enemy / table.you).
        if (!playmatsOn || !Playmats.paint(g, enemyPlaymat, false, 0, 0, getWidth(), midlineY)) {
            com.planesnomad.gui.ui.theme.Skin.paint(g, "table.enemy", 0, 0, getWidth(), midlineY);
        }
        if (!playmatsOn || !Playmats.paint(g, youPlaymat, true, 0, midlineY, getWidth(), getHeight() - midlineY)) {
            com.planesnomad.gui.ui.theme.Skin.paint(g, "table.you", 0, midlineY, getWidth(), getHeight() - midlineY);
        }
        g.setColor(Theme.MIDLINE);
        g.setStroke(new BasicStroke(Math.max(1f, Theme.px(1.5))));
        g.drawLine(0, midlineY, getWidth(), midlineY);
        // Faint separator above your hand
        // Hands sit on a darker strip (translucent, so the playmat still shows through).
        com.planesnomad.gui.ui.theme.Skin.paint(g, "hand", 0, youHand.getY(), getWidth(), youHand.getHeight());
        com.planesnomad.gui.ui.theme.Skin.paint(g, "hand", 0, enemyHand.getY(), getWidth(), enemyHand.getHeight());
    }
}

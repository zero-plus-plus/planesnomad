package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.ui.CardGroups.Pile;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
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
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One horizontal row of cards (a hand, or the lands / other permanents / creatures of a
 * battlefield). Cards are sized from the row's height; tapped cards are turned sideways;
 * when cards don't fit they overlap. Hovering a card shows its preview.
 *
 * Battlefield rows group identical cards into piles ({@link CardGroups}): a pile is drawn as up
 * to three offset cards with a "×N" count. Right-click a card or pile to select a number of a
 * pile's cards (e.g. 37 of 200 goblins; shown as "37/200" and kept while the pile's cards stay
 * unchanged), to separate a pile back into single cards, or to combine identical cards into one.
 */
public final class CardRow extends JComponent {

    private final String label;
    private final CardPreview preview;
    private final boolean towardBottom;
    private boolean grouping;
    private boolean landRow;
    private boolean stackLandsFully;
    private List<CardSnapshot> cards = List.of();
    private List<Pile> piles = List.of();
    /** One hit box per pile (a single card is a pile of one), in drawing order. */
    private final List<Rectangle> hitBoxes = new ArrayList<>();
    private CardSnapshot hovered;
    /** Pile key -> number of its cards selected. */
    private final Map<String, Integer> selection = new LinkedHashMap<>();
    private final List<Runnable> selectionListeners = new ArrayList<>();
    /** Player's choices from the right-click menu, by base key: shown card by card / as one pile. */
    private final Set<String> separated = new HashSet<>();
    private final Set<String> combined = new HashSet<>();

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

            @Override
            public void mousePressed(MouseEvent e) {
                maybeMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeMenu(e);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    /**
     * Battlefield rows: group identical cards into piles.
     * @param lands true for a land row (lands pile in threes unless {@link #setStackLandsFully})
     */
    public void setGrouping(boolean on, boolean lands) {
        grouping = on;
        landRow = lands;
        regroup();
    }

    /** Setting: pile identical lands without the limit of three. */
    public void setStackLandsFully(boolean on) {
        stackLandsFully = on;
        regroup();
    }

    public void setCards(List<CardSnapshot> cards) {
        this.cards = List.copyOf(cards);
        regroup();
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

    private void regroup() {
        if (grouping) {
            // Forget choices for cards that are gone, so a new group starts with the default.
            Set<String> present = new HashSet<>();
            CardGroups.group(cards, landRow, stackLandsFully).forEach(p -> present.add(p.baseKey()));
            separated.retainAll(present);
            combined.retainAll(present);
            piles = CardGroups.group(cards, landRow, stackLandsFully, separated, combined);
        } else {
            List<Pile> singles = new ArrayList<>();
            for (CardSnapshot c : cards) {
                singles.add(new Pile("#" + c.id(), "#" + c.id(), List.of(c)));
            }
            piles = singles;
        }
        // Keep selections whose pile still exists (same state), clamped to its size.
        Map<String, Integer> sizes = new HashMap<>();
        piles.forEach(p -> sizes.put(p.key(), p.count()));
        boolean changed = selection.keySet().removeIf(k -> !sizes.containsKey(k));
        for (Map.Entry<String, Integer> e : selection.entrySet()) {
            int max = sizes.get(e.getKey());
            if (e.getValue() > max) {
                e.setValue(max);
                changed = true;
            }
        }
        if (changed) {
            selectionListeners.forEach(Runnable::run);
        }
        repaint();
    }

    // ------------------------------------------------------------------ selection

    /** The piles as currently grouped (a single card is a pile of one). */
    public List<Pile> piles() {
        return piles;
    }

    /** Select {@code n} cards of the pile with this key (0 clears it). */
    public void select(String pileKey, int n) {
        Pile pile = piles.stream().filter(p -> p.key().equals(pileKey)).findFirst().orElse(null);
        if (pile == null) {
            return;
        }
        int v = Math.max(0, Math.min(n, pile.count()));
        if (v == 0) {
            selection.remove(pileKey);
        } else {
            selection.put(pileKey, v);
        }
        selectionListeners.forEach(Runnable::run);
        repaint();
    }

    public int selectedIn(String pileKey) {
        return selection.getOrDefault(pileKey, 0);
    }

    /** Engine ids of the selected cards (the first n of each pile). */
    public List<Integer> selectedCardIds() {
        List<Integer> ids = new ArrayList<>();
        for (Pile p : piles) {
            int n = selection.getOrDefault(p.key(), 0);
            for (int i = 0; i < n; i++) {
                ids.add(p.cards().get(i).id());
            }
        }
        return ids;
    }

    public void clearSelection() {
        if (!selection.isEmpty()) {
            selection.clear();
            selectionListeners.forEach(Runnable::run);
            repaint();
        }
    }

    public void onSelectionChanged(Runnable r) {
        selectionListeners.add(r);
    }

    // ------------------------------------------------------------------ combine / separate

    /** Show these identical cards card by card (right-click "Separate"). */
    public void separate(String baseKey) {
        combined.remove(baseKey);
        separated.add(baseKey);
        regroup();
    }

    /** Show these identical cards as one pile, even 2 or 3 of them (right-click "Combine"). */
    public void combine(String baseKey) {
        separated.remove(baseKey);
        combined.add(baseKey);
        regroup();
    }

    /** Separate every pile in this row. */
    public void separateAll() {
        for (Pile p : piles) {
            if (p.isPile()) {
                combined.remove(p.baseKey());
                separated.add(p.baseKey());
            }
        }
        regroup();
    }

    /** Combine every group of identical cards in this row (2 or more) into one pile each. */
    public void combineAll() {
        for (Map.Entry<String, Integer> g : groupSizes().entrySet()) {
            String base = g.getKey();
            if (g.getValue() > 1) {
                separated.remove(base);
                combined.add(base);
            }
        }
        regroup();
    }

    /** Back to the automatic rules for this row. */
    public void resetGrouping() {
        separated.clear();
        combined.clear();
        regroup();
    }

    /** Base key -> how many identical cards are in the row (whether piled or not). */
    private Map<String, Integer> groupSizes() {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (Pile p : piles) {
            sizes.merge(p.baseKey(), p.count(), Integer::sum);
        }
        return sizes;
    }

    // ------------------------------------------------------------------ right-click menu

    private void maybeMenu(MouseEvent e) {
        if (!e.isPopupTrigger() && !(SwingUtilities.isRightMouseButton(e) && e.getID() == MouseEvent.MOUSE_PRESSED)) {
            return;
        }
        if (!grouping) {
            return;   // hands: a card menu comes with human play
        }
        int i = pileAt(e.getPoint());
        if (i < 0) {
            return;
        }
        JPopupMenu menu = buildMenu(piles.get(i));
        menu.show(this, e.getX(), e.getY());
        e.consume();
    }

    private JPopupMenu buildMenu(Pile pile) {
        JPopupMenu menu = new JPopupMenu();
        String name = pile.top().name();
        int identical = groupSizes().getOrDefault(pile.baseKey(), pile.count());

        // 1. Selecting part of a pile.
        if (pile.isPile()) {
            int count = pile.count();
            item(menu, "Select how many... (" + count + " " + name + ")", () -> {
                Integer n = PileCountPicker.ask(this, name, count, Math.max(1, selectedIn(pile.key())));
                if (n != null) {
                    select(pile.key(), n);
                }
            });
            item(menu, "Select 1", () -> select(pile.key(), 1));
            item(menu, "Select all " + count, () -> select(pile.key(), count));
            if (selectedIn(pile.key()) > 0) {
                item(menu, "Clear selection", () -> select(pile.key(), 0));
            }
            menu.addSeparator();
        }

        // 2. Combining into piles / separating back.
        boolean split = identical > 1 && (!pile.isPile() || pile.count() < identical);
        if (pile.isPile()) {
            item(menu, "Separate this pile (" + pile.count() + " cards)", () -> separate(pile.baseKey()));
        }
        if (split) {
            item(menu, "Combine all " + identical + " identical " + name + " into one pile", () -> combine(pile.baseKey()));
        }
        boolean anyPile = piles.stream().anyMatch(Pile::isPile);
        boolean anyCombinable = groupSizes().entrySet().stream().anyMatch(en -> en.getValue() > 1
            && piles.stream().filter(p -> p.baseKey().equals(en.getKey())).count() > 1);
        if (anyPile) {
            item(menu, "Separate all piles in this row", this::separateAll);
        }
        if (anyCombinable) {
            item(menu, "Combine identical cards in this row", this::combineAll);
        }
        if (!separated.isEmpty() || !combined.isEmpty()) {
            item(menu, "Automatic piles for this row", this::resetGrouping);
        }

        // 3. Card actions: placeholders until human play exists.
        menu.addSeparator();
        JMenuItem later = new JMenuItem("Tap / activate / attack (exert...) - comes with human play");
        later.setEnabled(false);
        menu.add(later);
        return menu;
    }

    private static void item(JPopupMenu menu, String text, Runnable action) {
        JMenuItem it = new JMenuItem(text);
        it.addActionListener(a -> action.run());
        menu.add(it);
    }

    // ------------------------------------------------------------------ hover

    private int pileAt(Point p) {
        // Later piles are drawn on top, so search from the end.
        for (int i = Math.min(hitBoxes.size(), piles.size()) - 1; i >= 0; i--) {
            if (hitBoxes.get(i).contains(p)) {
                return i;
            }
        }
        return -1;
    }

    private void updateHover(Point p) {
        int i = pileAt(p);
        if (i < 0) {
            if (hovered != null) {
                hovered = null;
                preview.exit();
                repaint();
            }
            return;
        }
        CardSnapshot hit = piles.get(i).top();
        hovered = hit;
        preview.hover(hit, this, p);
        repaint();
    }

    // ------------------------------------------------------------------ painting

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
        if (ch <= 8 || piles.isEmpty()) {
            if (piles.isEmpty() && label != null) {
                g.setFont(Theme.font(Font.ITALIC, 11));
                g.setColor(new Color(255, 255, 255, 40));
                g.drawString(label, pad * 2, getHeight() / 2 + g.getFontMetrics().getAscent() / 2);
            }
            g.dispose();
            return;
        }

        int gap = Math.max(2, Theme.px(6));
        int offset = pileOffset(cw);
        int n = piles.size();
        int[] widths = new int[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            Pile p = piles.get(i);
            int base = p.top().tapped() ? ch : cw;   // a tapped card lies sideways
            widths[i] = base + offset * (Math.min(p.count(), CardGroups.DRAWN) - 1);
            total += widths[i] + (i > 0 ? gap : 0);
        }
        int available = getWidth() - 2 * pad;
        // When the cards don't fit, the space between them shrinks (and goes negative = overlap)
        // so they all fit. A flag, not a sentinel value: the computed spacing can be exactly 1.0.
        boolean squeezed = total > available && n > 1;
        double squeeze = 0;
        if (squeezed) {
            int cardsOnly = total - gap * (n - 1);
            squeeze = (available - cardsOnly) / (double) (n - 1);
        }
        int x = pad + Math.max(0, (available - Math.min(total, available)) / 2);
        for (int i = 0; i < n; i++) {
            Pile pile = piles.get(i);
            CardSnapshot top = pile.top();
            int nudge = top.attacking() ? (towardBottom ? pad * 2 : -pad * 2) : 0;
            Rectangle box = new Rectangle(x, pad + nudge, widths[i], ch);
            hitBoxes.add(box);
            drawPile(g, pile, box, cw, ch, offset);
            x += squeezed ? (int) Math.floor(widths[i] + squeeze) : widths[i] + gap;
        }
        g.dispose();
    }

    /** How far each card behind the top one of a pile peeks out to the right. */
    static int pileOffset(int cardWidth) {
        return Math.max(3, (int) Math.round(cardWidth * 0.12));
    }

    /**
     * The top card sits at the pile's left edge (the part that stays visible when the next pile
     * overlaps it); up to two copies peek out behind it to the right.
     */
    private void drawPile(Graphics2D g, Pile pile, Rectangle box, int cw, int ch, int offset) {
        int drawn = Math.min(pile.count(), CardGroups.DRAWN);
        CardSnapshot top = pile.top();
        int baseW = top.tapped() ? ch : cw;
        for (int k = drawn - 1; k >= 1; k--) {
            CardSnapshot behind = pile.cards().get(pile.count() - 1 - k);
            Rectangle r = new Rectangle(box.x + k * offset, box.y, baseW, ch);
            drawCard(g, behind, r, cw, ch, false);
            // Darken the copies behind so the pile reads as depth.
            g.setColor(new Color(0, 0, 0, 70));
            Rectangle vis = visible(behind, r, cw, ch);
            g.fillRoundRect(vis.x, vis.y, vis.width, vis.height, Math.max(4, vis.width / 10), Math.max(4, vis.width / 10));
        }
        Rectangle topBox = new Rectangle(box.x, box.y, baseW, ch);
        int selected = selectedIn(pile.key());
        drawCard(g, top, topBox, cw, ch, selected > 0);
        if (pile.count() > drawn || selected > 0) {
            badge(g, visible(top, topBox, cw, ch), pile.count(), selected);
        }
    }

    /** The card's on-screen rectangle (a tapped card is turned sideways inside its box). */
    private static Rectangle visible(CardSnapshot card, Rectangle box, int cw, int ch) {
        return card.tapped() ? new Rectangle(box.x, box.y + (ch - cw) / 2, ch, cw) : box;
    }

    /** "×200", or "37/200" when some are selected; at the top left, the part that stays visible. */
    private static void badge(Graphics2D g, Rectangle card, int count, int selected) {
        String text = selected > 0 ? selected + "/" + count : "×" + count;
        g.setFont(Theme.font(Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        int padX = Math.max(2, Theme.px(3));
        int w = fm.stringWidth(text) + 2 * padX;
        int h = fm.getAscent() + Math.max(2, Theme.px(2));
        int x = card.x + Math.max(1, Theme.px(2));
        int y = card.y + Math.max(1, Theme.px(2));
        g.setColor(selected > 0 ? Theme.ACCENT : new Color(10, 12, 16, 225));
        g.fillRoundRect(x, y, w, h, h / 2, h / 2);
        g.setColor(selected > 0 ? Theme.ACCENT.brighter() : Theme.TEXT_DIM);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, w - 1, h - 1, h / 2, h / 2);
        g.setColor(selected > 0 ? new Color(0x1A1A1A) : Theme.TEXT);
        g.drawString(text, x + padX, y + fm.getAscent());
    }

    private void drawCard(Graphics2D g, CardSnapshot card, Rectangle box, int cw, int ch, boolean selected) {
        if (card.tapped()) {
            // Rotate 90 degrees clockwise around the box: the card's width becomes its height.
            Graphics2D r = (Graphics2D) g.create();
            AffineTransform t = new AffineTransform();
            t.translate(box.x + box.width, box.y + (ch - cw) / 2.0);
            t.rotate(Math.PI / 2);
            r.transform(t);
            CardPainter.paint(r, card, 0, 0, cw, ch);
            r.dispose();
            outline(g, card, new Rectangle(box.x, box.y + (ch - cw) / 2, ch, cw), selected);
        } else {
            CardPainter.paint(g, card, box.x, box.y, cw, ch);
            outline(g, card, box, selected);
        }
    }

    private void outline(Graphics2D g, CardSnapshot card, Rectangle r, boolean selected) {
        Color color = card.attacking() ? Theme.ATTACKING
            : card.blocking() ? Theme.BLOCKING
            : selected || hovered != null && hovered.id() == card.id() ? Theme.ACCENT : null;
        if (color == null) {
            return;
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(2f, Theme.px(2.5))));
        int arc = Math.max(4, r.width / 10);
        g.drawRoundRect(r.x, r.y, r.width - 1, r.height - 1, arc, arc);
    }
}

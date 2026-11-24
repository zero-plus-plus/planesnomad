package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.MatchSnapshot.StackItem;
import com.planesnomad.gui.model.MatchSnapshot.Target;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.util.List;
import java.util.function.Function;

/**
 * A see-through layer over the board that draws an arrow from each stack card to what it targets:
 * a card on the battlefield or in a hand, or (for a player) toward that player's portrait at the
 * left. The top of the stack and the item under the mouse are drawn strongest. It never takes the
 * mouse, so the board under it works as usual. Arrow color = the player who aimed it.
 */
public final class TargetArrows extends JComponent {

    private final StackPopup popup;
    private final List<CardRow> rows;
    /** Player name -> the side (true = bottom) whose portrait is at the left of that half. */
    private final Function<String, Boolean> isBottomPlayer;
    private final java.util.function.IntSupplier midline;
    private List<StackItem> items = List.of();
    /** Repaints while targets are shown: the marks pulse and the arrows follow moving cards. */
    private final javax.swing.Timer pulse = new javax.swing.Timer(80, e -> {
        repaint();
        for (CardRow r : rowsWithMarks()) {
            r.repaint();
        }
    });

    public TargetArrows(StackPopup popup, List<CardRow> rows, Function<String, Boolean> isBottomPlayer,
                        java.util.function.IntSupplier midline, Runnable portraitsRepaint) {
        this.popup = popup;
        this.rows = rows;
        this.isBottomPlayer = isBottomPlayer;
        this.midline = midline;
        this.portraitsRepaint = portraitsRepaint;
        setOpaque(false);
        popup.onHoverChanged(this::repaint);
    }

    private final Runnable portraitsRepaint;

    @Override
    public boolean contains(int x, int y) {
        return false;   // clicks and hovers go to the board underneath
    }

    public void setItems(List<StackItem> stack) {
        items = List.copyOf(stack);
        boolean any = items.stream().anyMatch(i -> !i.targets().isEmpty());
        if (any && !pulse.isRunning()) {
            pulse.start();
        } else if (!any && pulse.isRunning()) {
            pulse.stop();
        }
        repaint();
    }

    private List<CardRow> rowsWithMarks() {
        portraitsRepaint.run();
        return rows.stream().filter(CardRow::hasTargetMarks).toList();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        if (items.isEmpty() || !popup.isVisible()) {
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        StackItem hovered = popup.hoveredItem();
        // Back to front, so the strongest arrows end up on top.
        for (int i = items.size() - 1; i >= 0; i--) {
            StackItem item = items.get(i);
            if (item.targets().isEmpty() || item == hovered) {
                continue;
            }
            paintItem(g, item, i == 0 && hovered == null ? 1.0 : hovered == null ? 0.55 : 0.3);
        }
        if (hovered != null && !hovered.targets().isEmpty()) {
            paintItem(g, hovered, 1.0);
        }
        g.dispose();
    }

    private void paintItem(Graphics2D g, StackItem item, double strength) {
        Rectangle src = popup.itemBounds(item);
        if (src == null) {
            return;
        }
        src = SwingUtilities.convertRectangle(popup, src, this);
        Color color = TargetMarks.colorOf(item);
        for (Target t : item.targets()) {
            Point to = null;
            boolean portrait = false;
            if (t.kind() == Target.Kind.CARD) {
                for (CardRow row : rows) {
                    Rectangle r = row.isVisible() ? row.cardBounds(t.id()) : null;
                    if (r != null) {
                        r = SwingUtilities.convertRectangle(row, r, this);
                        to = new Point(r.x + r.width / 2, r.y + r.height / 2);
                        break;
                    }
                }
            } else if (t.kind() == Target.Kind.PLAYER) {
                // Toward the portrait, at the left edge of that player's half of the board.
                boolean bottom = Boolean.TRUE.equals(isBottomPlayer.apply(t.name()));
                int mid = midline.getAsInt();
                to = new Point(Theme.px(6), bottom ? mid + (getHeight() - mid) / 2 : mid / 2);
                portrait = true;
            }
            if (to == null) {
                continue;   // not on the board (graveyard, another stack item): the card's label says it
            }
            // Start at the side of the stack card that faces the target.
            Point from = new Point(to.x < src.x ? src.x : to.x > src.x + src.width ? src.x + src.width : src.x + src.width / 2,
                Math.max(src.y + Theme.px(10), Math.min(src.y + src.height - Theme.px(10), to.y)));
            arrow(g, from, to, color, strength, portrait);
        }
    }

    /** A curved arrow with a dark outline (so it shows on any art), ending in a head. */
    private void arrow(Graphics2D g, Point from, Point to, Color color, double strength, boolean head) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double len = Math.hypot(dx, dy);
        if (len < Theme.px(20)) {
            return;
        }
        // Bend the arrow sideways a little.
        double bend = Math.min(len * 0.18, Theme.px(70));
        double cx = (from.x + to.x) / 2.0 - dy / len * bend;
        double cy = (from.y + to.y) / 2.0 + dx / len * bend;
        // Stop short of the card's center (the crosshair is there) unless pointing at a portrait.
        double stop = head ? 0 : Theme.px(14);
        double ex = to.x;
        double ey = to.y;
        double tdx = to.x - cx;
        double tdy = to.y - cy;
        double tl = Math.hypot(tdx, tdy);
        if (tl > 0 && stop > 0) {
            ex -= tdx / tl * stop;
            ey -= tdy / tl * stop;
        }
        QuadCurve2D curve = new QuadCurve2D.Double(from.x, from.y, cx, cy, ex, ey);
        float w = (float) Math.max(2.5, Theme.px(4.5) * (0.6 + 0.4 * strength));
        int alpha = (int) (255 * (0.35 + 0.65 * strength));
        g.setStroke(new BasicStroke(w + 3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0, 0, 0, (int) (alpha * 0.6)));
        g.draw(curve);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            10f, strength >= 1.0 ? null : new float[] {w * 2.5f, w * 1.6f}, 0f));
        g.setColor(TargetMarks.withAlpha(color, alpha));
        g.draw(curve);
        // Arrow head along the curve's last direction.
        double ang = Math.atan2(ey - cy, ex - cx);
        double hl = Math.max(10, Theme.px(16)) * (0.7 + 0.3 * strength);
        double hw = hl * 0.6;
        Path2D.Double tri = new Path2D.Double();
        tri.moveTo(ex, ey);
        tri.lineTo(ex - hl * Math.cos(ang) + hw * Math.sin(ang), ey - hl * Math.sin(ang) - hw * Math.cos(ang));
        tri.lineTo(ex - hl * Math.cos(ang) - hw * Math.sin(ang), ey - hl * Math.sin(ang) + hw * Math.cos(ang));
        tri.closePath();
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(0, 0, 0, (int) (alpha * 0.7)));
        g.draw(tri);
        g.setColor(TargetMarks.withAlpha(color, alpha));
        g.fill(tri);
        // Start dot on the stack card.
        int d = (int) Math.max(6, w * 2);
        g.fillOval(from.x - d / 2, from.y - d / 2, d, d);
    }
}

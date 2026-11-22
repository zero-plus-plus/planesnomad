package com.planesnomad.gui.ui;

import com.planesnomad.gui.ui.theme.Skin;
import forge.game.phase.PhaseType;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * The phases of a turn as a column of small icons along the left of the battlefield, like
 * Forge's phase indicators: the top half (beside the enemy's side) is the enemy's turn, the
 * bottom half (beside yours) is your turn.
 *
 * - The phase now is lit in the color of the player whose turn it is.
 * - A gold ring marks a stop: play pauses there; other phases pass by. Click an icon to change it.
 * - First-strike damage and cleanup appear only while they happen.
 *
 * Themed: element "phases" (the column's background), icons {@code icon.phase.<key>}.
 */
public final class PhaseColumn extends JComponent {

    /** Where stops are kept (the match runner's stops). */
    public interface Stops {
        boolean isStop(boolean yourTurn, PhaseType phase);

        void setStop(boolean yourTurn, PhaseType phase, boolean on);
    }

    /** The phases shown (and that can be stops); first strike and cleanup are added while they happen. */
    public static final PhaseType[] PHASES = {
        PhaseType.UPKEEP, PhaseType.DRAW, PhaseType.MAIN1, PhaseType.COMBAT_BEGIN,
        PhaseType.COMBAT_DECLARE_ATTACKERS, PhaseType.COMBAT_DECLARE_BLOCKERS, PhaseType.COMBAT_DAMAGE,
        PhaseType.COMBAT_END, PhaseType.MAIN2, PhaseType.END_OF_TURN};

    /** Width at the reference resolution. */
    public static final int WIDTH = 30;

    private final IntSupplier midline;
    private Stops stops;
    private Runnable onChange = () -> { };
    /** The phase now and whether it is your turn (null phase: not started). */
    private PhaseType current;
    private boolean currentIsYours;

    private record Hit(Rectangle box, boolean yours, PhaseType phase) { }

    private final List<Hit> hits = new ArrayList<>();
    private Hit hovered;

    /** @param midline y of the board's middle line, in this column's coordinates */
    public PhaseColumn(IntSupplier midline) {
        this.midline = midline;
        setOpaque(true);
        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Hit h = hitAt(e.getX(), e.getY());
                if (h != null && stops != null && isToggleable(h.phase())) {
                    stops.setStop(h.yours(), h.phase(), !stops.isStop(h.yours(), h.phase()));
                    onChange.run();
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                Hit h = hitAt(e.getX(), e.getY());
                if (h != hovered) {
                    hovered = h;
                    setToolTipText(h == null ? null : tooltip(h));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = null;
                repaint();
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    private String tooltip(Hit h) {
        String whose = h.yours() ? "Your turn" : "Enemy's turn";
        if (!isToggleable(h.phase())) {
            return whose + ": " + h.phase().nameForUi;
        }
        boolean stop = stops != null && stops.isStop(h.yours(), h.phase());
        return "<html>" + whose + ": <b>" + h.phase().nameForUi + "</b><br>"
            + (stop ? "Stop: play pauses here (click: pass it by)" : "Passes by (click: stop here)") + "</html>";
    }

    private static boolean isToggleable(PhaseType p) {
        for (PhaseType q : PHASES) {
            if (q == p) {
                return true;
            }
        }
        return false;
    }

    public void setStops(Stops s, Runnable changed) {
        stops = s;
        onChange = changed == null ? () -> { } : changed;
        repaint();
    }

    /** The phase now (by its name for the UI) and whose turn it is. */
    public void setCurrent(String phaseName, boolean yourTurn) {
        current = null;
        for (PhaseType p : PhaseType.values()) {
            if (p.nameForUi.equals(phaseName)) {
                current = p;
            }
        }
        currentIsYours = yourTurn;
        repaint();
    }

    private Hit hitAt(int x, int y) {
        for (Hit h : hits) {
            if (h.box().contains(x, y)) {
                return h;
            }
        }
        return null;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(WIDTH), 100);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Skin.paint(g, "phases", 0, 0, getWidth(), getHeight());
        hits.clear();
        int mid = Math.max(1, Math.min(getHeight() - 1, midline.getAsInt()));
        g.setColor(Theme.MIDLINE);
        g.drawLine(0, mid, getWidth(), mid);
        drawHalf(g, false, 0, mid);
        drawHalf(g, true, mid, getHeight() - mid);
        g.dispose();
    }

    /** One player's turn: the phases top to bottom, centered in that half. */
    private void drawHalf(Graphics2D g, boolean yours, int top, int height) {
        List<PhaseType> shown = new ArrayList<>();
        boolean thisTurn = current != null && currentIsYours == yours;
        for (PhaseType p : PHASES) {
            if (p == PhaseType.COMBAT_DAMAGE && thisTurn && current == PhaseType.COMBAT_FIRST_STRIKE_DAMAGE) {
                shown.add(PhaseType.COMBAT_FIRST_STRIKE_DAMAGE);   // only while it happens
            }
            shown.add(p);
        }
        if (thisTurn && current == PhaseType.CLEANUP) {
            shown.add(PhaseType.CLEANUP);   // only while it happens
        }
        int w = getWidth();
        int slot = Math.max(8, Math.min(w - Theme.px(4), (height - Theme.px(8)) / Math.max(1, shown.size())));
        int icon = (int) Math.round(slot * 0.72);
        int y = top + (height - slot * shown.size()) / 2;
        Color side = yours ? Theme.YOU : Theme.ENEMY;
        for (PhaseType p : shown) {
            Rectangle box = new Rectangle((w - slot) / 2, y, slot, slot);
            Hit hit = new Hit(box, yours, p);
            hits.add(hit);
            boolean now = thisTurn && p == current;
            boolean stop = stops != null && isToggleable(p) && stops.isStop(yours, p);
            boolean hot = hovered != null && hovered.yours() == yours && hovered.phase() == p;
            int pad = Math.max(1, Theme.px(1.5));
            if (now) {
                g.setColor(side);
                g.fillOval(box.x + pad, box.y + pad, box.width - 2 * pad, box.height - 2 * pad);
            } else if (hot) {
                g.setColor(new Color(255, 255, 255, 30));
                g.fillOval(box.x + pad, box.y + pad, box.width - 2 * pad, box.height - 2 * pad);
            }
            if (stop) {
                g.setColor(Theme.ACCENT);
                g.setStroke(new BasicStroke(Math.max(1.2f, Theme.px(1.6))));
                g.drawOval(box.x + pad, box.y + pad, box.width - 2 * pad - 1, box.height - 2 * pad - 1);
            }
            Color ink = now ? Theme.textOn(side) : stop ? Theme.ACCENT : hot ? Theme.TEXT : Theme.TEXT_DIM;
            PhaseIcons.paint(g, p, box.x + (slot - icon) / 2, box.y + (slot - icon) / 2, icon, ink);
            y += slot;
        }
    }
}

package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.RelicSnapshot;
import com.planesnomad.gui.model.RelicSnapshot.Kind;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/**
 * The persistent top bar: relics, consumable slots and lives on the left (meant to stay on
 * non-combat screens too), match status and viewer controls on the right, and a small
 * collapse button in the corner.
 */
public final class TopBar extends JPanel {

    /** Viewer controls for the AI-vs-AI test (the human-vs-AI match will replace these). */
    public interface Controls {
        void setPaused(boolean paused);
        void step();
        void setSpeed(double speed);
        void setRevealEnemyHand(boolean reveal);
        void newMatch();
    }

    public static final int CONSUMABLE_SLOTS = 3;
    private static final String[] SPEEDS = {"0.5x", "1x", "2x", "4x", "8x"};

    private final JPanel content = new JPanel(new BorderLayout());
    private final JPanel relicRow = row();
    private final List<RelicIcon> consumableSlots = new ArrayList<>();
    private final JLabel lives = new JLabel();
    private final JLabel status = new JLabel();
    private final JButton pause = new com.planesnomad.gui.ui.theme.SkinButton("Pause");
    private final JButton step = new com.planesnomad.gui.ui.theme.SkinButton("Step");
    private final JComboBox<String> speed = new JComboBox<>(SPEEDS);
    private final JCheckBox revealHand = new JCheckBox("Show enemy hand");
    private final JButton newMatch = new com.planesnomad.gui.ui.theme.SkinButton("New match");
    private final JButton collapse = new JButton();
    private boolean collapsed;
    private Runnable onCollapseChanged = () -> { };

    public TopBar(Controls controls) {
        super(new BorderLayout());
        setOpaque(true);
        content.setOpaque(false);

        JPanel left = row();
        left.add(relicRow);
        left.add(Box.createHorizontalStrut(Theme.px(16)));
        JPanel slots = row();
        for (int i = 0; i < CONSUMABLE_SLOTS; i++) {
            RelicIcon slot = new RelicIcon(true);
            slot.setRelic(null);
            consumableSlots.add(slot);
            slots.add(slot);
        }
        left.add(slots);
        left.add(Box.createHorizontalStrut(Theme.px(16)));
        left.add(lives);

        JPanel right = row();
        right.add(status);
        right.add(Box.createHorizontalStrut(Theme.px(12)));
        for (JComponent c : new JComponent[] {pause, step, speed, revealHand, newMatch}) {
            right.add(c);
        }
        speed.setSelectedItem("1x");
        step.setEnabled(false);
        newMatch.setEnabled(false);
        revealHand.setOpaque(false);
        revealHand.setToolTipText("Test option: show the enemy's hand face up");

        pause.addActionListener(e -> {
            boolean nowPaused = pause.getText().equals("Pause");
            pause.setText(nowPaused ? "Play" : "Pause");
            step.setEnabled(nowPaused);
            controls.setPaused(nowPaused);
        });
        step.addActionListener(e -> controls.step());
        speed.addActionListener(e -> controls.setSpeed(
            Double.parseDouble(((String) speed.getSelectedItem()).replace("x", ""))));
        revealHand.addActionListener(e -> controls.setRevealEnemyHand(revealHand.isSelected()));
        newMatch.addActionListener(e -> controls.newMatch());

        content.add(left, BorderLayout.WEST);
        content.add(right, BorderLayout.EAST);

        // Small, out-of-the-way collapse button in the top-right corner.
        // The arrow is drawn, not a text character: some fonts (e.g. on Windows) lack the triangles.
        collapse.setToolTipText("Collapse / expand the top bar");
        collapse.setFocusable(false);
        collapse.setBorderPainted(false);
        collapse.setContentAreaFilled(false);
        collapse.setRolloverEnabled(true);
        collapse.setMargin(new Insets(0, 0, 0, 0));
        collapse.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        collapse.addActionListener(e -> setCollapsed(!collapsed));
        JPanel corner = new JPanel(new BorderLayout());
        corner.setOpaque(false);
        corner.add(collapse, BorderLayout.NORTH);

        add(content, BorderLayout.CENTER);
        add(corner, BorderLayout.EAST);
        applyFonts();
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        com.planesnomad.gui.ui.theme.Skin.paint((java.awt.Graphics2D) g, "topbar", 0, 0, getWidth(), getHeight());
    }

    public void onCollapseChanged(Runnable r) {
        onCollapseChanged = r;
    }

    public void setCollapsed(boolean value) {
        collapsed = value;
        content.setVisible(!collapsed);
        applyFonts();   // the arrow icon flips
        revalidate();
        onCollapseChanged.run();
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void applyFonts() {
        int pad = Theme.px(4);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.PANEL_EDGE),
            BorderFactory.createEmptyBorder(pad, Theme.px(8), pad, Theme.px(4))));
        lives.setFont(Theme.font(Font.BOLD, 15));
        lives.setForeground(Theme.LIFE);
        status.setFont(Theme.font(Font.BOLD, 13));
        status.setForeground(Theme.TEXT);
        for (JComponent c : new JComponent[] {pause, step, speed, revealHand, newMatch}) {
            c.setFont(Theme.font(Font.PLAIN, 11));
        }
        revealHand.setForeground(Theme.TEXT_DIM);
        speed.setBackground(com.planesnomad.gui.ui.theme.Skin.fill("button"));
        speed.setForeground(com.planesnomad.gui.ui.theme.Skin.text("button", Theme.TEXT));
        int arrow = Math.max(22, Theme.px(24));   // click area, never tiny on small windows
        collapse.setIcon(new Arrow(arrow, collapsed, false));
        collapse.setRolloverIcon(new Arrow(arrow, collapsed, true));
        collapse.setPreferredSize(new Dimension(arrow, arrow));
        collapse.setMinimumSize(new Dimension(arrow, arrow));
        for (JComponent c : new JComponent[] {pause, step, speed, revealHand, newMatch}) {
            c.setMaximumSize(null);
            c.setPreferredSize(null);
            Dimension pref = c.getPreferredSize();
            c.setMaximumSize(pref);
            c.setPreferredSize(pref);
        }
    }

    /**
     * @param runLives lives left in the run (no run layer yet, so the viewer passes a placeholder)
     */
    public void update(MatchSnapshot s, int runLives) {
        List<RelicSnapshot> relics = new ArrayList<>();
        List<RelicSnapshot> consumables = new ArrayList<>();
        for (RelicSnapshot r : s.you().relics()) {
            (r.kind() == Kind.CONSUMABLE ? consumables : relics).add(r);
        }
        relicRow.removeAll();
        if (relics.isEmpty()) {
            JLabel none = new JLabel("No relics");
            none.setFont(Theme.font(Font.ITALIC, 11));
            none.setForeground(Theme.TEXT_DIM);
            relicRow.add(none);
        }
        for (RelicSnapshot r : relics) {
            RelicIcon icon = new RelicIcon(false);
            icon.setRelic(r);
            relicRow.add(icon);
        }
        for (int i = 0; i < consumableSlots.size(); i++) {
            consumableSlots.get(i).setRelic(i < consumables.size() ? consumables.get(i) : null);
        }
        lives.setText("♥ " + runLives);
        lives.setToolTipText("Lives left in this run");

        status.setText(s.finished()
            ? "Turn " + s.turn() + " · " + s.result()
            : "Turn " + s.turn() + " · " + s.activePlayer() + " · " + s.phase());
        newMatch.setEnabled(s.finished());
        relicRow.revalidate();
        relicRow.repaint();
    }

    /** A drawn up/down arrow in a faint rounded box (brighter on hover). */
    private static final class Arrow implements javax.swing.Icon {
        private final int size;
        private final boolean down;
        private final boolean hover;

        Arrow(int size, boolean down, boolean hover) {
            this.size = size;
            this.down = down;
            this.hover = hover;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g0, int x, int y) {
            // A theme can supply its own pictures: icon.collapse (bar open) / icon.expand (bar closed).
            java.awt.image.BufferedImage custom = com.planesnomad.gui.ui.theme.Skin.icon(down ? "expand" : "collapse");
            if (custom != null) {
                java.awt.Graphics2D gi = (java.awt.Graphics2D) g0.create();
                gi.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                if (hover) {
                    gi.setComposite(java.awt.AlphaComposite.SrcOver);
                } else {
                    gi.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.8f));
                }
                gi.drawImage(custom, x, y, size, size, null);
                gi.dispose();
                return;
            }
            java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = size / 3;
            g.setColor(hover ? new java.awt.Color(255, 255, 255, 40) : new java.awt.Color(255, 255, 255, 18));
            g.fillRoundRect(x, y, size - 1, size - 1, arc, arc);
            g.setColor(hover ? Theme.TEXT : Theme.TEXT_DIM);
            g.drawRoundRect(x, y, size - 1, size - 1, arc, arc);
            int w = (int) (size * 0.5);
            int h = (int) (size * 0.28);
            int cx = x + size / 2;
            int cy = y + size / 2;
            java.awt.Polygon tri = down
                ? new java.awt.Polygon(new int[] {cx - w / 2, cx + w / 2, cx}, new int[] {cy - h / 2, cy - h / 2, cy + h / 2}, 3)
                : new java.awt.Polygon(new int[] {cx - w / 2, cx + w / 2, cx}, new int[] {cy + h / 2, cy + h / 2, cy - h / 2}, 3);
            g.fillPolygon(tri);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    private static JPanel row() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setOpaque(false);
        p.setAlignmentY(CENTER_ALIGNMENT);
        return p;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        // Collapsed: a thin strip, but always tall enough for the whole expand button.
        int strip = collapse.getPreferredSize().height + 2 * Math.max(2, Theme.px(3)) + 1;
        return collapsed ? new Dimension(d.width, strip) : d;
    }
}

package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.MatchSnapshot;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;

/**
 * The whole match screen, independent of any window so it can also be rendered headless
 * (screenshot tests). Layout, Cockatrice-style:
 *
 * <pre>
 * +------------------------------- top bar --------------------------------+
 * | enemy panel   |  enemy hand / lands / other / creatures    | stack     |
 * | (pinned card  |  ------------------ middle ------------------|-----------|
 * |  preview)     |  your creatures / other / lands / hand      | game log  |
 * | your panel    |                                              |           |
 * +------------------------------------------------------------------------+
 * </pre>
 *
 * The arrangement of panels lives only here, so a small-screen layout can rearrange the same
 * panels later.
 */
public final class MatchView extends JPanel {

    /** Default hotkey for the large card view. */
    public static final int LARGE_PREVIEW_KEY = KeyEvent.VK_Q;

    /** Default hotkey for the full stack list (WASD is kept free for future keyboard navigation). */
    public static final int STACK_DETAILS_KEY = KeyEvent.VK_TAB;

    /** Left column width at the reference resolution. */
    private static final int LEFT_WIDTH = 220;

    private final CardPreview preview = new CardPreview();
    private final TopBar topBar;
    private final PlayerPanel enemyPanel = new PlayerPanel(true, preview);
    private final PlayerPanel youPanel = new PlayerPanel(false, preview);
    private final JPanel pinnedPreview = new JPanel(new BorderLayout());
    private final CardPreview.CardCanvas pinnedCanvas = new CardPreview.CardCanvas();
    private final JLabel pinnedHint = new JLabel("Hover a card", JLabel.CENTER);
    private final BoardPanel board = new BoardPanel(preview);
    private final StackAndLogPanel stackAndLog = new StackAndLogPanel(preview);
    private final JPanel left = new JPanel(new WeightedColumn());
    private final StackPopup stackPopup = new StackPopup(preview, this::toggleStackDetails);
    private final JLayeredPane boardLayer = new JLayeredPane() {
        @Override
        public void doLayout() {
            board.setBounds(0, 0, getWidth(), getHeight());
            stackPopup.setSize(stackPopup.getPreferredSize());
            if (stackPopup.movedByUser()) {
                stackPopup.moveWithin(stackPopup.getX(), stackPopup.getY());   // keep it on screen
            } else {
                placeStackOutOfTheWay();   // board size known now (first show, or window resized)
            }
        }
    };
    private boolean rememberStackPosition;
    private KeyEventDispatcher tabDispatcher;
    /** Setting: the large card view shows while Q is held (true, default) or toggles on each press. */
    private boolean holdForLarge = true;
    /** Closes the large view shortly after Q is released, unless it's pressed again (auto-repeat). */
    private final javax.swing.Timer largeRelease = new javax.swing.Timer(60, e -> preview.closeLarge());
    {
        largeRelease.setRepeats(false);
    }

    // Right column (stack + log): resizable with a divider, collapsible to a thin strip.
    private final JPanel rightColumn = new JPanel(new BorderLayout());
    private final javax.swing.JSplitPane split =
        new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, true);
    private final JPanel collapsedStrip = new JPanel(new BorderLayout());
    private final javax.swing.JButton collapseButton = smallButton("»", "Hide the stack & log column");
    private final javax.swing.JButton expandButton = smallButton("«", "Show the stack & log column");
    private final JPanel center = new JPanel(new BorderLayout());
    private boolean columnCollapsed;
    /** The column's width (reference pixels at 1280x720), kept while collapsed and across resizes. */
    private int columnWidthRef = 260;
    private boolean dividerPlaced;
    private Runnable onColumnChanged = () -> { };

    private MatchSnapshot last;
    private int runLives = 3;

    public MatchView(TopBar.Controls controls) {
        super(new BorderLayout());
        setBackground(Theme.TABLE);
        topBar = new TopBar(controls);
        topBar.onCollapseChanged(this::revalidate);

        pinnedPreview.setBackground(Theme.PANEL);
        pinnedPreview.add(pinnedCanvas, BorderLayout.CENTER);
        pinnedPreview.add(pinnedHint, BorderLayout.SOUTH);
        pinnedHint.setForeground(Theme.TEXT_DIM);
        pinnedCanvas.setBackground(Theme.PANEL);
        pinnedPreview.setVisible(false);
        preview.addListener(card -> {
            pinnedCanvas.setCard(card);
            pinnedHint.setText(card.name());
        });

        left.setBackground(Theme.PANEL);
        left.add(enemyPanel, 1.0);
        left.add(pinnedPreview, 1.6);
        left.add(youPanel, 1.0);
        left.setPreferredSize(new java.awt.Dimension(Theme.px(LEFT_WIDTH), 0));

        add(topBar, BorderLayout.NORTH);
        add(left, BorderLayout.WEST);
        boardLayer.add(board, JLayeredPane.DEFAULT_LAYER);
        boardLayer.add(stackPopup, JLayeredPane.PALETTE_LAYER);
        stackPopup.setVisible(false);
        // Right column: a small header with the collapse button, then stack + log.
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.PANEL);
        header.add(collapseButton, BorderLayout.EAST);
        rightColumn.add(header, BorderLayout.NORTH);
        rightColumn.add(stackAndLog, BorderLayout.CENTER);
        rightColumn.setMinimumSize(new java.awt.Dimension(Theme.px(160), 0));
        boardLayer.setMinimumSize(new java.awt.Dimension(Theme.px(320), 0));
        collapsedStrip.setBackground(Theme.PANEL);
        collapsedStrip.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.PANEL_EDGE));
        collapsedStrip.add(expandButton, BorderLayout.NORTH);
        split.setLeftComponent(boardLayer);
        split.setRightComponent(rightColumn);
        split.setResizeWeight(1.0);          // the board takes extra space; the column keeps its width
        split.setBorder(null);
        split.setDividerSize(Math.max(4, Theme.px(5)));
        split.setBackground(Theme.PANEL_EDGE);
        split.addPropertyChangeListener(javax.swing.JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (dividerPlaced && !columnCollapsed && split.getWidth() > 0) {
                int w = split.getWidth() - split.getDividerLocation() - split.getDividerSize();
                if (w > 0) {
                    columnWidthRef = (int) Math.round(w / Theme.scale());
                }
            }
        });
        collapseButton.addActionListener(e -> setStackColumnCollapsed(true));
        expandButton.addActionListener(e -> setStackColumnCollapsed(false));
        center.add(split, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        installKeys();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                rescale();
            }
        });
    }

    private void installKeys() {
        // Q: hold to see the card large (default), or press to open / press again to close (setting).
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(LARGE_PREVIEW_KEY, 0, false), "largePreview");
        getActionMap().put("largePreview", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                largeRelease.stop();   // key auto-repeat on some systems sends release + press pairs
                if (holdForLarge) {
                    preview.openLarge(SwingUtilities.getWindowAncestor(MatchView.this));
                } else {
                    preview.toggleLarge(SwingUtilities.getWindowAncestor(MatchView.this));
                }
            }
        });
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(LARGE_PREVIEW_KEY, 0, true), "largePreviewReleased");
        getActionMap().put("largePreviewReleased", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (holdForLarge) {
                    largeRelease.restart();
                }
            }
        });
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeLarge");
        getActionMap().put("closeLarge", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                preview.closeLarge();
            }
        });
    }

    /** Tab (default key) shows or hides the full stack list; works even though Swing normally uses Tab for focus. */
    @Override
    public void addNotify() {
        super.addNotify();
        tabDispatcher = e -> {
            if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == STACK_DETAILS_KEY && e.getModifiersEx() == 0
                && SwingUtilities.getWindowAncestor(this) == KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow()) {
                toggleStackDetails();
                return true;
            }
            return false;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(tabDispatcher);
    }

    @Override
    public void removeNotify() {
        if (tabDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(tabDispatcher);
        }
        super.removeNotify();
    }

    /**
     * Right-click on the stack pop-up, or Tab: show/hide the full text list in the right column.
     * If the column is collapsed, it opens with the list shown.
     */
    public void toggleStackDetails() {
        if (columnCollapsed) {
            setStackColumnCollapsed(false);
            stackAndLog.setStackExpanded(true);
            return;
        }
        stackAndLog.setStackExpanded(!stackAndLog.isStackExpanded());
    }

    /** Hide the stack & log column (a thin strip with a button stays), or show it again. */
    public void setStackColumnCollapsed(boolean collapse) {
        if (collapse == columnCollapsed) {
            return;
        }
        columnCollapsed = collapse;
        center.removeAll();
        if (collapse) {
            split.remove(boardLayer);
            center.add(boardLayer, BorderLayout.CENTER);
            center.add(collapsedStrip, BorderLayout.EAST);
        } else {
            center.add(split, BorderLayout.CENTER);
            split.setLeftComponent(boardLayer);
            dividerPlaced = false;   // restore the remembered width on the next layout
        }
        center.revalidate();
        center.repaint();
        onColumnChanged.run();
    }

    public boolean isStackColumnCollapsed() {
        return columnCollapsed;
    }

    /** Current width of the stack & log column in pixels (0 when collapsed). */
    public int stackColumnWidth() {
        return columnCollapsed ? 0 : rightColumn.getWidth();
    }

    /** Set the column width (e.g. from a saved setting), in reference pixels at 1280x720. */
    public void setStackColumnWidthRef(int ref) {
        columnWidthRef = Math.max(160, ref);
        dividerPlaced = false;
        revalidate();
    }

    public void onStackColumnChanged(Runnable r) {
        onColumnChanged = r == null ? () -> { } : r;
    }

    @Override
    public void doLayout() {
        super.doLayout();
        if (!columnCollapsed && !dividerPlaced && split.getWidth() > 0) {
            dividerPlaced = true;
            int col = Math.min(Theme.px(columnWidthRef), split.getWidth() - Theme.px(320));
            split.setDividerLocation(Math.max(0, split.getWidth() - col - split.getDividerSize()));
            split.doLayout();
        }
    }

    private static javax.swing.JButton smallButton(String text, String tip) {
        javax.swing.JButton b = new javax.swing.JButton(text);
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setForeground(Theme.TEXT_DIM);
        b.setMargin(new java.awt.Insets(0, 4, 0, 4));
        return b;
    }

    public boolean isStackDetailsShown() {
        return stackAndLog.isStackExpanded();
    }

    /** Setting: keep the stack pop-up where the player dragged it (off = pop up out of the way each time). */
    public void setRememberStackPosition(boolean remember) {
        rememberStackPosition = remember;
        if (!remember) {
            stackPopup.forgetUserPosition();
        }
    }

    public boolean isStackPopupShown() {
        return stackPopup.isVisible();
    }

    private void updateStackPopup(MatchSnapshot s) {
        boolean show = !s.stack().isEmpty();
        boolean appearing = show && !stackPopup.isVisible();
        stackPopup.setItems(s.stack());
        if (appearing && !rememberStackPosition) {
            stackPopup.forgetUserPosition();   // pop up out of the way each time
        }
        if (show && !stackPopup.movedByUser()) {
            placeStackOutOfTheWay();           // also re-centers as the cascade grows
        }
        stackPopup.setVisible(show);
        boardLayer.repaint();
    }

    /** Default spot: the right edge of the board, centered on the middle line. */
    private void placeStackOutOfTheWay() {
        java.awt.Dimension d = stackPopup.getPreferredSize();
        stackPopup.setSize(d);
        int margin = Theme.px(10);
        int x = boardLayer.getWidth() - d.width - margin;
        int y = (boardLayer.getHeight() - d.height) / 2;
        stackPopup.moveWithin(x, y);
    }

    /** Follow the window size: fonts and fixed sizes scale with it. */
    public void rescale() {
        Theme.updateScale(getWidth(), getHeight());
        topBar.applyFonts();
        enemyPanel.applyFonts();
        youPanel.applyFonts();
        stackAndLog.applyFonts();
        pinnedHint.setFont(Theme.font(Font.PLAIN, 11));
        pinnedPreview.setBorder(BorderFactory.createEmptyBorder(Theme.px(6), Theme.px(6), Theme.px(6), Theme.px(6)));
        left.setPreferredSize(new java.awt.Dimension(Theme.px(LEFT_WIDTH), 0));
        collapseButton.setFont(Theme.font(Font.BOLD, 12));
        expandButton.setFont(Theme.font(Font.BOLD, 12));
        split.setDividerSize(Math.max(4, Theme.px(5)));
        dividerPlaced = false;   // keep the column's width in step with the scale
        if (last != null) {
            topBar.update(last, runLives);
        }
        revalidate();
        repaint();
    }

    /** Must be called on the Swing thread. */
    public void update(MatchSnapshot s) {
        last = s;
        topBar.update(s, runLives);
        enemyPanel.update(s.enemy());
        youPanel.update(s.you());
        board.update(s);
        stackAndLog.update(s);
        updateStackPopup(s);
        repaint();
    }

    /** Setting: show a fixed preview panel in the left column (off by default). */
    public void setPinnedPreview(boolean show) {
        pinnedPreview.setVisible(show);
        left.revalidate();
        left.repaint();
    }

    /** Setting: show the preview next to the cursor (on by default). */
    public void setHoverPreview(boolean show) {
        preview.setHoverEnabled(show);
    }

    /** Setting: playmat backgrounds on each half of the table. */
    /** Setting: pile identical lands without the default limit of three per pile. */
    public void setStackLandsFully(boolean on) {
        board.setStackLandsFully(on);
    }

    /** Setting: hold Q for the large card view (default), or press Q to open and again to close. */
    public void setHoldForLargeView(boolean hold) {
        holdForLarge = hold;
    }

    public boolean isHoldForLargeView() {
        return holdForLarge;
    }

    /** Setting: different arts of the same card in separate piles (default: together). */
    public void setSeparateArts(boolean on) {
        board.setSeparateArts(on);
    }

    public BoardPanel board() {
        return board;
    }

    public void setPlaymats(boolean on) {
        board.setPlaymats(on);
    }

    public void setRunLives(int lives) {
        runLives = lives;
    }

    public TopBar topBar() {
        return topBar;
    }
}

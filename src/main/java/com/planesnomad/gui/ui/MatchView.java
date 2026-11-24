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
import com.planesnomad.gui.ui.theme.SkinPanel;
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

    /**
     * Default hotkeys for the two ways to pass the turn while you have priority: Enter = "Pass
     * turn" (stops when the enemy does something), Shift+Enter = "Auto-pass all" (passes
     * everything until the end of the turn). The hard one needs Shift so it's not hit by accident.
     */
    public static KeyStroke PASS_TURN_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
    public static KeyStroke AUTO_PASS_ALL_KEY = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, java.awt.event.InputEvent.SHIFT_DOWN_MASK);

    /** "Enter", "Shift+Enter": how a hotkey is shown on buttons. */
    public static String keyText(KeyStroke k) {
        String mods = java.awt.event.InputEvent.getModifiersExText(k.getModifiers());
        return (mods.isEmpty() ? "" : mods + "+") + KeyEvent.getKeyText(k.getKeyCode());
    }

    private static boolean matches(KeyEvent e, KeyStroke k) {
        return e.getKeyCode() == k.getKeyCode() && e.getModifiersEx() == k.getModifiers();
    }

    /** Left column width at the reference resolution. */
    private static final int LEFT_WIDTH = 220;

    private final CardPreview preview = new CardPreview();
    private final TopBar topBar;
    private final PlayerPanel enemyPanel = new PlayerPanel(true, preview);
    private final PlayerPanel youPanel = new PlayerPanel(false, preview);
    private final JPanel pinnedPreview = new SkinPanel("panel", new BorderLayout());
    private final CardPreview.CardCanvas pinnedCanvas = new CardPreview.CardCanvas();
    private final JLabel pinnedHint = new JLabel("Hover a card", JLabel.CENTER);
    private final BoardPanel board = new BoardPanel(preview);
    private final StackAndLogPanel stackAndLog = new StackAndLogPanel(preview);
    private final JPanel left = new SkinPanel("panel", new WeightedColumn());
    private final StackPopup stackPopup = new StackPopup(preview, this::toggleStackDetails);
    private final JLayeredPane boardLayer = new JLayeredPane() {
        @Override
        public void doLayout() {
            board.setBounds(0, 0, getWidth(), getHeight());
            board.doLayout();
            targetArrows.setBounds(0, 0, getWidth(), getHeight());
            rewardScreen.setBounds(0, 0, getWidth(), getHeight());
            rewardScreen.doLayout();
            placeBubble();
            if (choicePanel.isVisible()) {
                java.awt.Dimension c = choicePanel.getPreferredSize();
                choicePanel.setBounds((getWidth() - c.width) / 2, Math.max(0, (getHeight() - c.height) / 2 - Theme.px(30)),
                    c.width, c.height);
            }
            if (promptBar.isVisible()) {
                promptBar.setMaxWidth(getWidth() - Theme.px(20));   // wraps its text rather than overflow
                java.awt.Dimension d = promptBar.getPreferredSize();
                int w = Math.min(d.width, getWidth() - Theme.px(20));
                int y = Math.max(0, board.yourHandTop() - d.height - Theme.px(6));
                promptBar.setBounds((getWidth() - w) / 2, y, w, d.height);
                promptBar.doLayout();
            }
            stackPopup.setSize(stackPopup.getPreferredSize());
            if (stackPopup.movedByUser()) {
                stackPopup.moveWithin(stackPopup.getX(), stackPopup.getY());   // keep it on screen
            } else {
                placeStackOutOfTheWay();   // board size known now (first show, or window resized)
            }
        }
    };
    /** Arrows from stack cards to their targets (see-through, never takes the mouse). */
    private final TargetArrows targetArrows = new TargetArrows(stackPopup, board.allRows(),
        name -> this.last != null && name.equals(this.last.you().name()), board::midline,
        () -> {
            enemyPanel.portraitComponent().repaint();
            youPanel.portraitComponent().repaint();
        });
    /** What the enemy says (barks), by its portrait. */
    private final SpeechBubble bubble = new SpeechBubble();
    /** After a won fight: the rewards to claim (covers the board). */
    private final RewardScreen rewardScreen = new RewardScreen(preview);
    private boolean rememberStackPosition;
    /** While a person plays: the question bar, and what clicks mean (null when watching AI vs AI). */
    private final PromptBar promptBar = new PromptBar();
    /** Cards to decide about (scry, surveil): shown large in the middle of the board. */
    private final CardChoicePanel choicePanel = new CardChoicePanel(preview);
    /** Phases of each player's turn, with stops: left of the battlefield (enemy's turn on top, yours below). */
    private final PhaseColumn phaseColumn = new PhaseColumn(() -> {
        java.awt.Point p = SwingUtilities.convertPoint(board, 0, board.midline(), getPhaseColumnSafe());
        return p.y;
    });

    private java.awt.Component getPhaseColumnSafe() {
        return phaseColumn == null ? this : phaseColumn;
    }
    private PlayInput input;
    private KeyEventDispatcher tabDispatcher;
    /** Setting: the large card view shows while Q is held (true, default) or toggles on each press. */
    private boolean holdForLarge = true;
    /** Closes the large view shortly after Q is released, unless it's pressed again (auto-repeat). */
    private final javax.swing.Timer largeRelease = new javax.swing.Timer(60, e -> preview.closeLarge());
    {
        largeRelease.setRepeats(false);
    }

    // Right column (stack + log): resizable with a divider, collapsible to a thin strip.
    private final JPanel rightColumn = new SkinPanel("column", new BorderLayout());
    private final javax.swing.JSplitPane split =
        new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, true);
    private final JPanel collapsedStrip = new SkinPanel("column", new BorderLayout());
    private final javax.swing.JButton collapseButton = smallButton("»", "Back to the history bar");
    private final javax.swing.JButton expandButton = smallButton("«", "Show the full game log and stack list");
    /** Shown while the log column is collapsed (the default): the match history as icons. */
    private final HistoryBar historyBar = new HistoryBar();
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

        pinnedPreview.add(pinnedCanvas, BorderLayout.CENTER);
        pinnedPreview.add(pinnedHint, BorderLayout.SOUTH);
        pinnedHint.setForeground(Theme.TEXT_DIM);
        pinnedCanvas.setBackground(Theme.PANEL);
        pinnedPreview.setVisible(false);
        preview.addListener(card -> {
            pinnedCanvas.setCard(card);
            pinnedHint.setText(card.name());
        });

        left.add(enemyPanel, 1.0);
        left.add(pinnedPreview, 1.6);
        left.add(youPanel, 1.0);
        left.setPreferredSize(new java.awt.Dimension(Theme.px(LEFT_WIDTH), 0));

        add(topBar, BorderLayout.NORTH);
        add(left, BorderLayout.WEST);
        boardLayer.add(board, JLayeredPane.DEFAULT_LAYER);
        boardLayer.add(targetArrows, Integer.valueOf(JLayeredPane.PALETTE_LAYER - 1));
        boardLayer.add(stackPopup, JLayeredPane.PALETTE_LAYER);
        boardLayer.add(promptBar, JLayeredPane.MODAL_LAYER);
        boardLayer.add(choicePanel, JLayeredPane.MODAL_LAYER);
        boardLayer.add(bubble, Integer.valueOf(JLayeredPane.PALETTE_LAYER + 1));
        boardLayer.add(rewardScreen, JLayeredPane.DRAG_LAYER);
        for (CardRow row : board.allRows()) {
            row.setEmptyMenu(this::boardMenu);
        }
        stackPopup.setVisible(false);
        // Right column: a small header with the collapse button, then stack + log.
        JPanel header = new SkinPanel("column", new BorderLayout());
        header.add(collapseButton, BorderLayout.EAST);
        rightColumn.add(header, BorderLayout.NORTH);
        rightColumn.add(stackAndLog, BorderLayout.CENTER);
        rightColumn.setMinimumSize(new java.awt.Dimension(Theme.px(160), 0));
        boardLayer.setMinimumSize(new java.awt.Dimension(Theme.px(320), 0));
        collapsedStrip.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.PANEL_EDGE));
        collapsedStrip.add(expandButton, BorderLayout.NORTH);
        collapsedStrip.add(historyBar, BorderLayout.CENTER);
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
        // The phase column runs along the left of the battlefield; its halves follow the board's middle line.
        JPanel boardArea = new JPanel(new BorderLayout());
        boardArea.add(phaseColumn, BorderLayout.WEST);
        boardArea.add(center, BorderLayout.CENTER);
        add(boardArea, BorderLayout.CENTER);
        // Default: the history bar; the full log column is one click (or Tab) away.
        setStackColumnCollapsed(true);

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

    /**
     * Tab (default key) shows or hides the full stack list; works even though Swing normally uses
     * Tab for focus. While playing: Space = the prompt's main button (Pass, Attack, ...), Esc = cancel,
     * Enter = pass the turn, Shift+Enter = auto-pass all (see {@link #PASS_TURN_KEY}).
     */
    @Override
    public void addNotify() {
        super.addNotify();
        tabDispatcher = e -> {
            if (e.getID() != KeyEvent.KEY_PRESSED
                || SwingUtilities.getWindowAncestor(this) != KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow()) {
                return false;
            }
            if (input != null && input.request() != null) {
                if (matches(e, PASS_TURN_KEY)) {
                    return input.passHotkey(com.planesnomad.gui.engine.HumanBridge.AutoPass.UNTIL_EVENT);
                }
                if (matches(e, AUTO_PASS_ALL_KEY)) {
                    return input.passHotkey(com.planesnomad.gui.engine.HumanBridge.AutoPass.END_OF_TURN);
                }
            }
            if (e.getModifiersEx() != 0) {
                return false;
            }
            if (e.getKeyCode() == STACK_DETAILS_KEY) {
                toggleStackDetails();
                return true;
            }
            if (input != null && input.request() != null) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    input.primary();
                    return true;
                }
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && !preview.isLargeShowing()) {
                    input.cancel();
                    return true;
                }
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

    /**
     * A person plays the bottom player: the engine's questions arrive through the bridge; cards,
     * portraits, relics and stack items become clickable, and the prompt bar shows what is asked.
     */
    public void setHumanBridge(com.planesnomad.gui.engine.HumanBridge bridge) {
        if (bridge == null) {
            // Watching AI vs AI: nothing is clickable, no prompt.
            input = null;
            for (CardRow row : board.allRows()) {
                row.setInteraction(null);
            }
            enemyPanel.setPortraitInteraction(null, null);
            youPanel.setPortraitInteraction(null, null);
            topBar.setRelicClick(null);
            stackPopup.setItemInteraction(null, null);
            promptBar.show(null, java.util.List.of());
            choicePanel.setModel(null);
            return;
        }
        input = new PlayInput(bridge, this, this::refreshInput);
        bridge.setListener(r -> SwingUtilities.invokeLater(() -> input.onRequest(r)));
        for (CardRow row : board.allRows()) {
            row.setInteraction(input);
        }
        enemyPanel.setPortraitInteraction(input::playerMark, input::playerClick);
        youPanel.setPortraitInteraction(input::playerMark, input::playerClick);
        topBar.setRelicClick(input::relicClick);
        stackPopup.setItemInteraction(input::stackMark, input::stackClick);
    }

    /**
     * Where stops are kept: the phase strips on the player panels show and change them, and the
     * board's right-click menu offers them too.
     */
    public void setStops(PhaseColumn.Stops stops, Runnable onChange) {
        this.stops = stops;
        phaseColumn.setStops(stops, onChange);
    }

    private PhaseColumn.Stops stops;

    /** Right-click on empty board space: playing options (auto-tap, auto-pass) and stops. */
    private void boardMenu(java.awt.event.MouseEvent e) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        if (input != null) {
            var bridge = input.bridge();
            javax.swing.JCheckBoxMenuItem tap = new javax.swing.JCheckBoxMenuItem("Tap lands automatically to pay mana", bridge.autoTap());
            tap.setToolTipText("Off: when you cast something, you click the lands to tap");
            tap.addActionListener(a -> bridge.setAutoTap(tap.isSelected()));
            menu.add(tap);
            menu.addSeparator();
            javax.swing.JMenuItem untilEvent = new javax.swing.JMenuItem("Pass turn");
            untilEvent.setAccelerator(PASS_TURN_KEY);
            untilEvent.setToolTipText("Pass until the enemy casts or activates something, or this turn ends");
            untilEvent.addActionListener(a -> input.autoPass(com.planesnomad.gui.engine.HumanBridge.AutoPass.UNTIL_EVENT));
            menu.add(untilEvent);
            javax.swing.JMenuItem endTurn = new javax.swing.JMenuItem("Auto-pass all");
            endTurn.setAccelerator(AUTO_PASS_ALL_KEY);
            endTurn.setToolTipText("Pass everything until this turn ends (you still choose blockers)");
            endTurn.addActionListener(a -> input.autoPass(com.planesnomad.gui.engine.HumanBridge.AutoPass.END_OF_TURN));
            menu.add(endTurn);
            if (bridge.autoPass() != com.planesnomad.gui.engine.HumanBridge.AutoPass.NONE) {
                javax.swing.JMenuItem stop = new javax.swing.JMenuItem("Stop passing");
                stop.addActionListener(a -> input.autoPass(com.planesnomad.gui.engine.HumanBridge.AutoPass.NONE));
                menu.add(stop);
            }
        }
        if (stops != null) {
            if (menu.getComponentCount() > 0) {
                menu.addSeparator();
            }
            for (boolean yours : new boolean[] {true, false}) {
                javax.swing.JMenu side = new javax.swing.JMenu(yours ? "Stops on your turn" : "Stops on the enemy's turn");
                for (forge.game.phase.PhaseType ph : PhaseColumn.PHASES) {
                    javax.swing.JCheckBoxMenuItem it = new javax.swing.JCheckBoxMenuItem(ph.nameForUi, stops.isStop(yours, ph));
                    it.addActionListener(a -> {
                        stops.setStop(yours, ph, it.isSelected());
                        phaseColumn.repaint();
                    });
                    side.add(it);
                }
                menu.add(side);
            }
        }
        if (menu.getComponentCount() > 0) {
            menu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    public PlayInput playInput() {
        return input;
    }

    /** The question or the choices changed: prompt bar and highlights. */
    /** Blocks shown on the board: the engine's, plus the ones you're choosing right now. */
    private void applyBlocks() {
        if (last == null) {
            return;
        }
        java.util.Map<Integer, java.util.List<Integer>> all = new java.util.HashMap<>(last.blocks());
        if (input != null) {
            input.pendingBlocks().forEach((blocker, attacker) -> all.put(blocker, java.util.List.of(attacker)));
        }
        board.setBlocks(all);
    }

    private void refreshInput() {
        applyBlocks();
        choicePanel.setModel(input.choiceModel());
        promptBar.show(input.prompt(), input.actions());
        boardLayer.doLayout();
        board.allRows().forEach(java.awt.Component::repaint);
        enemyPanel.repaint();
        youPanel.repaint();
        stackPopup.repaint();
        promptBar.repaint();
    }

    public HistoryBar historyBar() {
        return historyBar;
    }

    /**
     * Collapse the stack & log column to the history bar (the default), or show the full log again.
     */
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

    /**
     * Use a theme (colors, fonts, frames, icons) and redraw everything. Safe to call any time on
     * the Swing thread, e.g. from the Settings menu or after a designer edited the theme's files.
     */
    public void applyTheme(com.planesnomad.gui.ui.theme.ThemePack theme) {
        Theme.apply(theme);
        setBackground(Theme.TABLE);
        pinnedCanvas.setBackground(Theme.PANEL);
        pinnedHint.setForeground(Theme.TEXT_DIM);
        collapsedStrip.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.PANEL_EDGE));
        split.setBackground(Theme.PANEL_EDGE);
        collapseButton.setForeground(Theme.TEXT_DIM);
        expandButton.setForeground(Theme.TEXT_DIM);
        rescale();                       // fonts and sizes, in every panel
        if (last != null) {
            update(last);
        }
        repaint();
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
        // The phase strips: the current phase is lit on the panel of the player whose turn it is.
        phaseColumn.setCurrent(s.phase(), s.activePlayer().equals(s.you().name()));
        if (input != null) {
            refreshInput();
        }
        topBar.update(s, runLives);
        enemyPanel.update(s.enemy());
        youPanel.update(s.you());
        board.update(s);
        applyBlocks();
        // What the stack aims at: marked on the cards and portraits, with arrows from the stack.
        java.util.Map<Integer, java.awt.Color> aimed = TargetMarks.cardTargets(s.stack());
        for (CardRow row : board.allRows()) {
            row.setTargetMarks(aimed);
        }
        java.util.Map<String, java.awt.Color> aimedPlayers = TargetMarks.playerTargets(s.stack());
        enemyPanel.setTargetMark(aimedPlayers.get(s.enemy().name()));
        youPanel.setTargetMark(aimedPlayers.get(s.you().name()));
        targetArrows.setItems(s.stack());
        stackAndLog.update(s);
        historyBar.setEntries(s.history());
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

    /** The enemy says this line in a speech bubble by its portrait (null hides it). */
    public void showBark(String line) {
        bubble.say(line);
        placeBubble();
        boardLayer.repaint();
    }

    public SpeechBubble bubble() {
        return bubble;
    }

    /** At the left edge of the board, level with the enemy's portrait (its tail points at it). */
    private void placeBubble() {
        if (!bubble.isVisible()) {
            return;
        }
        java.awt.Dimension d = bubble.getPreferredSize();
        javax.swing.JComponent face = enemyPanel.portraitComponent();
        int y = Theme.px(10);
        if (face.getHeight() > 0) {
            java.awt.Point p = SwingUtilities.convertPoint(face, 0, face.getHeight() / 3, boardLayer);
            y = p.y - Theme.px(18);
        }
        y = Math.max(Theme.px(4), Math.min(y, Math.max(Theme.px(4), boardLayer.getHeight() - d.height)));
        bubble.setBounds(Theme.px(2), y, d.width, d.height);
    }

    /** Shows the reward screen over the board (after a won fight). */
    public void showRewards(String title, String subtitle, java.util.List<RewardScreen.Reward> rewards,
                            java.util.function.Consumer<RewardScreen.Outcome> onContinue) {
        rewardScreen.setBounds(0, 0, boardLayer.getWidth(), boardLayer.getHeight());
        rewardScreen.show(title, subtitle, rewards, onContinue);
    }

    public void hideRewards() {
        rewardScreen.setVisible(false);
    }

    public RewardScreen rewardScreen() {
        return rewardScreen;
    }

    public TopBar topBar() {
        return topBar;
    }
}

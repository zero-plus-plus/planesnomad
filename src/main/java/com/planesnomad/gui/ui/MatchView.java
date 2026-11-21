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
        add(boardLayer, BorderLayout.CENTER);
        add(stackAndLog, BorderLayout.EAST);

        installKeys();
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                rescale();
            }
        });
    }

    private void installKeys() {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke(LARGE_PREVIEW_KEY, 0), "largePreview");
        getActionMap().put("largePreview", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                preview.toggleLarge(SwingUtilities.getWindowAncestor(MatchView.this));
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

    /** Right-click on the stack pop-up, or Tab: show/hide the full text list in the right column. */
    public void toggleStackDetails() {
        stackAndLog.setStackExpanded(!stackAndLog.isStackExpanded());
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

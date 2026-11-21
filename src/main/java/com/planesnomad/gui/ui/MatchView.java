package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.MatchSnapshot;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Font;
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

    /** Left column width at the reference resolution. */
    private static final int LEFT_WIDTH = 220;

    private final CardPreview preview = new CardPreview();
    private final TopBar topBar;
    private final PlayerPanel enemyPanel = new PlayerPanel(true);
    private final PlayerPanel youPanel = new PlayerPanel(false);
    private final JPanel pinnedPreview = new JPanel(new BorderLayout());
    private final CardPreview.CardCanvas pinnedCanvas = new CardPreview.CardCanvas();
    private final JLabel pinnedHint = new JLabel("Hover a card", JLabel.CENTER);
    private final BoardPanel board = new BoardPanel(preview);
    private final StackAndLogPanel stackAndLog = new StackAndLogPanel(preview);
    private final JPanel left = new JPanel(new WeightedColumn());

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
        add(board, BorderLayout.CENTER);
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

    public void setRunLives(int lives) {
        runLives = lives;
    }

    public TopBar topBar() {
        return topBar;
    }
}

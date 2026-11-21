package com.planesnomad.gui;

import com.planesnomad.ForgeEngine;
import com.planesnomad.gui.engine.MatchRunner;
import com.planesnomad.gui.images.CardImages;
import com.planesnomad.gui.images.ImageSetup;
import com.planesnomad.gui.ui.MatchView;
import com.planesnomad.gui.ui.Theme;
import com.planesnomad.gui.ui.TopBar;
import forge.card.CardDb;
import forge.game.Match;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import java.awt.Dimension;

/**
 * Desktop window that plays an AI-vs-AI test match and shows it on the match screen.
 * The bottom player ("you") gets the example relics and consumable.
 *
 * Run from the repository root:
 *   java -cp "target\classes;<classpath>" com.planesnomad.gui.MatchViewerApp [unlimited|mixed]
 * Card pictures: add -Dplanesnomad.cardImages=<folder> (Forge's picture cache is found by itself).
 * "unlimited" / "mixed" play decks built from printings that match the test pictures
 * (Unlimited only, or Torment + Shadowmoor + Future Sight + Khans of Tarkir).
 */
public final class MatchViewerApp {

    private final CardDb db;
    private final JFrame frame = new JFrame("Planesnomad - match viewer (AI vs AI test)");
    private final MatchView view;
    private MatchRunner runner;
    private boolean paused;
    private double speed = 1.0;
    private String testDecks = "";
    private CardImages images;
    private boolean revealEnemyHand;

    private MatchViewerApp(CardDb db) {
        this.db = db;
        this.view = new MatchView(new TopBar.Controls() {
            @Override
            public void setPaused(boolean value) {
                paused = value;
                runner.setPaused(value);
            }

            @Override
            public void step() {
                runner.step();
            }

            @Override
            public void setSpeed(double value) {
                speed = value;
                runner.setSpeed(value);
            }

            @Override
            public void setRevealEnemyHand(boolean value) {
                revealEnemyHand = value;
                runner.setRevealEnemyHand(value);
            }

            @Override
            public void newMatch() {
                startMatch();
            }
        });
    }

    public static void main(String[] args) {
        CardDb db = ForgeEngine.initialize();
        String which = args.length > 0 ? args[0].toLowerCase() : "";
        SwingUtilities.invokeLater(() -> {
            MatchViewerApp app = new MatchViewerApp(db);
            app.testDecks = which;
            app.show();
        });
    }

    private void show() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // default look and feel is fine
        }
        ToolTipManager.sharedInstance().setInitialDelay(250);
        images = ImageSetup.create();
        images.addListener(() -> SwingUtilities.invokeLater(view::repaint));
        System.out.println("Card picture folders: " + images.folders());

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setJMenuBar(menu());
        frame.setContentPane(view);
        frame.setMinimumSize(new Dimension(1024, 600));
        frame.setSize(Theme.REF_WIDTH, Theme.REF_HEIGHT + 40);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        startMatch();
    }

    private JMenuBar menu() {
        JMenuBar bar = new JMenuBar();
        JMenu settings = new JMenu("Settings");

        JCheckBoxMenuItem hover = new JCheckBoxMenuItem("Card preview next to cursor", true);
        hover.addActionListener(e -> view.setHoverPreview(hover.isSelected()));
        JCheckBoxMenuItem pinned = new JCheckBoxMenuItem("Pinned card preview panel", false);
        pinned.addActionListener(e -> view.setPinnedPreview(pinned.isSelected()));
        JCheckBoxMenuItem collapse = new JCheckBoxMenuItem("Collapse top bar", false);
        collapse.addActionListener(e -> view.topBar().setCollapsed(collapse.isSelected()));
        view.topBar().onCollapseChanged(() -> {
            collapse.setSelected(view.topBar().isCollapsed());
            view.revalidate();
        });

        JCheckBoxMenuItem stackPos = new JCheckBoxMenuItem("Stack pop-up remembers where I move it", false);
        stackPos.addActionListener(e -> view.setRememberStackPosition(stackPos.isSelected()));

        JCheckBoxMenuItem downloads = new JCheckBoxMenuItem("Download missing pictures and art crops from Scryfall",
            images.scryfall() != null && images.scryfall().isEnabled());
        downloads.addActionListener(e -> {
            if (images.scryfall() != null) {
                images.scryfall().setEnabled(downloads.isSelected());
            }
        });

        JCheckBoxMenuItem playmats = new JCheckBoxMenuItem("Playmat backgrounds", true);
        playmats.addActionListener(e -> view.setPlaymats(playmats.isSelected()));

        settings.add(hover);
        settings.add(pinned);
        settings.add(collapse);
        settings.add(stackPos);
        settings.add(playmats);
        JCheckBoxMenuItem column = new JCheckBoxMenuItem("Show stack & log column", true);
        column.addActionListener(e -> view.setStackColumnCollapsed(!column.isSelected()));
        view.onStackColumnChanged(() -> column.setSelected(!view.isStackColumnCollapsed()));
        settings.add(column);
        settings.add(downloads);
        settings.addSeparator();
        JMenuItem hint = new JMenuItem("Large card view: point at a card and press Q (Esc closes)");
        hint.setEnabled(false);
        settings.add(hint);
        JMenuItem stackHint = new JMenuItem("Stack details: right-click the stack pop-up or press Tab");
        stackHint.setEnabled(false);
        settings.add(stackHint);
        bar.add(settings);
        return bar;
    }

    private void startMatch() {
        Match match = TestMatches.create(testDecks, db);
        var looks = TestMatches.looks(testDecks);
        runner = new MatchRunner(match, TestMatches.YOU, TestMatches.ENEMY, looks[0], looks[1],
            snapshot -> SwingUtilities.invokeLater(() -> view.update(snapshot)));
        runner.setPaused(paused);
        runner.setSpeed(speed);
        runner.setRevealEnemyHand(revealEnemyHand);
        runner.start();
    }
}

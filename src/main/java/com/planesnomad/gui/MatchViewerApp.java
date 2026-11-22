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
 * "play" (or "play-unlimited", "play-mixed"): you play the bottom deck yourself against the AI.
 * "boards" shows the stress-test boards instead of a match (e.g. Krenko with 200 goblins),
 * frozen, to try piles and the right-click menu; the "Test boards" menu switches between them.
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
    private String currentBoard;
    /** Phase stops and minimum enemy-spell time, kept across matches. */
    private final java.util.Set<String> stops = new java.util.HashSet<>(MatchRunner.defaultStops());
    private long minEnemySpellMs = MatchRunner.DEFAULT_MIN_ENEMY_SPELL_MS;

    private MatchViewerApp(CardDb db) {
        this.db = db;
        this.view = new MatchView(new TopBar.Controls() {
            @Override
            public void setPaused(boolean value) {
                paused = value;
                if (runner != null) runner.setPaused(value);
            }

            @Override
            public void step() {
                if (runner != null) runner.step();
            }

            @Override
            public void setSpeed(double value) {
                speed = value;
                if (runner != null) runner.setSpeed(value);
            }

            @Override
            public void setRevealEnemyHand(boolean value) {
                revealEnemyHand = value;
                if (testDecks.equals("boards")) {
                    showBoard(currentBoard);   // frozen board: rebuild it with the hand face up/down
                } else if (runner != null) {
                    runner.setRevealEnemyHand(value);
                }
            }

            @Override
            public void newMatch() {
                if (testDecks.equals("boards")) {
                    showBoard(currentBoard);   // test boards: rebuild the same board
                } else {
                    startMatch();
                }
            }
        });
    }

    public static void main(String[] args) {
        CardDb db = ForgeEngine.initialize();
        com.planesnomad.gui.ui.Theme.apply(com.planesnomad.gui.ui.theme.ThemePack.fromSystemProperty());
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
        view.setStops(stopsModel, this::syncStopViews);
        frame.setContentPane(view);
        frame.setMinimumSize(new Dimension(1024, 600));
        frame.setSize(Theme.REF_WIDTH, Theme.REF_HEIGHT + 40);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        if (testDecks.equals("boards")) {
            frame.setTitle("Planesnomad - test boards (frozen, no play)");
            java.util.List<String> names = StressTest.boardNames();
            showBoard(names.get(names.size() - 1));
        } else {
            startMatch();
        }
    }

    /** Builds a test board off the Swing thread (it takes a moment), then shows it. */
    private void showBoard(String name) {
        currentBoard = name;
        frame.setTitle("Planesnomad - test board: " + name + " (building...)");
        Thread t = new Thread(() -> {
            try {
                var snap = StressTest.board(db, images, name, revealEnemyHand);
                SwingUtilities.invokeLater(() -> {
                    view.update(snap);
                    frame.setTitle("Planesnomad - test board: " + name + " (frozen, no play)");
                });
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> frame.setTitle("Planesnomad - test board failed: " + e));
            }
        }, "test-board");
        t.setDaemon(true);
        t.start();
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
        JCheckBoxMenuItem lands = new JCheckBoxMenuItem("Stack identical lands beyond 3 per pile", false);
        lands.addActionListener(e -> view.setStackLandsFully(lands.isSelected()));
        settings.add(lands);
        JCheckBoxMenuItem arts = new JCheckBoxMenuItem("Keep different arts of a card in separate piles", false);
        arts.addActionListener(e -> view.setSeparateArts(arts.isSelected()));
        settings.add(arts);
        JCheckBoxMenuItem column = new JCheckBoxMenuItem("Show full game log column (instead of the history bar)",
            !view.isStackColumnCollapsed());
        column.addActionListener(e -> view.setStackColumnCollapsed(!column.isSelected()));
        view.onStackColumnChanged(() -> column.setSelected(!view.isStackColumnCollapsed()));
        settings.add(column);
        settings.add(downloads);
        settings.addSeparator();
        JCheckBoxMenuItem qToggle = new JCheckBoxMenuItem("Q large view: press to open, press again to close (instead of hold)", false);
        qToggle.addActionListener(e -> view.setHoldForLargeView(!qToggle.isSelected()));
        settings.add(qToggle);
        settings.addSeparator();
        JMenuItem hint = new JMenuItem("Large card view: point at a card and hold Q (Esc also closes)");
        hint.setEnabled(false);
        settings.add(hint);
        JMenuItem stackHint = new JMenuItem("Stack details: right-click the stack pop-up or press Tab");
        stackHint.setEnabled(false);
        settings.add(stackHint);
        JMenuItem pileHint = new JMenuItem("Piles of identical cards: right-click to select how many");
        pileHint.setEnabled(false);
        settings.add(pileHint);
        bar.add(settings);
        bar.add(themeMenu());
        if (!testDecks.equals("boards")) {
            bar.add(devMenu());
        }
        bar.add(stopsMenu());
        if (testDecks.equals("boards")) {
            JMenu boards = new JMenu("Test boards");
            for (String name : StressTest.boardNames()) {
                JMenuItem it = new JMenuItem(name);
                it.addActionListener(e -> showBoard(name));
                boards.add(it);
            }
            bar.add(boards);
        }
        return bar;
    }

    /**
     * Theme menu: pick a theme from the themes folder, or reload the current one after its files
     * were edited (F5), so a designer sees changes without restarting.
     */
    private JMenu themeMenu() {
        JMenu menu = new JMenu("Theme");
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        String current = com.planesnomad.gui.ui.Theme.pack().name();
        for (String name : com.planesnomad.gui.ui.theme.ThemePack.available()) {
            com.planesnomad.gui.ui.theme.ThemePack preview = com.planesnomad.gui.ui.theme.ThemePack.load(name);
            javax.swing.JRadioButtonMenuItem item = new javax.swing.JRadioButtonMenuItem(
                preview.displayName(), name.equals(current));
            item.addActionListener(e -> useTheme(name));
            group.add(item);
            menu.add(item);
        }
        menu.addSeparator();
        JMenuItem reload = new JMenuItem("Reload theme files");
        reload.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0));
        reload.addActionListener(e -> useTheme(com.planesnomad.gui.ui.Theme.pack().name()));
        menu.add(reload);
        JMenuItem folder = new JMenuItem("Themes folder: " + com.planesnomad.gui.ui.theme.ThemePack.themesRoot().toAbsolutePath().normalize());
        folder.setEnabled(false);
        menu.add(folder);
        return menu;
    }

    /** Loads a theme from its files and redraws the screen; lists any problems found in them. */
    private void useTheme(String name) {
        var theme = com.planesnomad.gui.ui.theme.ThemePack.load(name);
        view.applyTheme(theme);
        frame.getJMenuBar().repaint();
        if (!theme.problems().isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(frame,
                "Theme '" + theme.displayName() + "' loaded with problems:\n- " + String.join("\n- ", theme.problems()),
                "Theme", javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

    private final java.util.Map<String, JCheckBoxMenuItem> stopItems = new java.util.HashMap<>();

    /** Stops as shown on the player panels' phase strips, the board menu and the Stops menu. */
    private final com.planesnomad.gui.ui.PhaseColumn.Stops stopsModel = new com.planesnomad.gui.ui.PhaseColumn.Stops() {
        @Override
        public boolean isStop(boolean yourTurn, forge.game.phase.PhaseType phase) {
            return stops.contains(MatchRunner.stopKey(yourTurn, phase));
        }

        @Override
        public void setStop(boolean yourTurn, forge.game.phase.PhaseType phase, boolean on) {
            String key = MatchRunner.stopKey(yourTurn, phase);
            if (on) stops.add(key); else stops.remove(key);
            if (runner != null) runner.setStop(yourTurn, phase, on);
            syncStopViews();
        }
    };

    /** Keeps the Stops menu's check marks and the phase strips in step. */
    private void syncStopViews() {
        stopItems.forEach((key, item) -> item.setSelected(stops.contains(key)));
        view.repaint();
    }

    /** Forge-style phase stops: play pauses at these phase changes; other phases go by quickly. */
    private JMenu stopsMenu() {
        JMenu menu = new JMenu("Stops");
        forge.game.phase.PhaseType[] phases = {
            forge.game.phase.PhaseType.UPKEEP, forge.game.phase.PhaseType.DRAW, forge.game.phase.PhaseType.MAIN1,
            forge.game.phase.PhaseType.COMBAT_BEGIN, forge.game.phase.PhaseType.COMBAT_DECLARE_ATTACKERS,
            forge.game.phase.PhaseType.COMBAT_DECLARE_BLOCKERS, forge.game.phase.PhaseType.COMBAT_DAMAGE,
            forge.game.phase.PhaseType.COMBAT_END, forge.game.phase.PhaseType.MAIN2,
            forge.game.phase.PhaseType.END_OF_TURN};
        for (boolean yours : new boolean[] {true, false}) {
            JMenu side = new JMenu(yours ? "Your turn" : "Enemy's turn");
            for (forge.game.phase.PhaseType ph : phases) {
                String key = MatchRunner.stopKey(yours, ph);
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(ph.nameForUi, stops.contains(key));
                item.addActionListener(e -> stopsModel.setStop(yours, ph, item.isSelected()));
                side.add(item);
                stopItems.put(key, item);
            }
            menu.add(side);
        }
        menu.addSeparator();
        JMenu minShow = new JMenu("Enemy spells stay visible at least");
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        for (long ms : new long[] {0, 500, 1000, 1500, 2000, 3000}) {
            javax.swing.JRadioButtonMenuItem it = new javax.swing.JRadioButtonMenuItem(
                ms == 0 ? "No minimum" : (ms / 1000.0 + " s").replace(".0 s", " s"), ms == minEnemySpellMs);
            it.addActionListener(e -> {
                minEnemySpellMs = ms;
                if (runner != null) runner.setMinEnemySpellMs(ms);
            });
            group.add(it);
            minShow.add(it);
        }
        menu.add(minShow);
        menu.addSeparator();
        JMenuItem hint = new JMenuItem("Play pauses at checked phases; others pass quickly");
        hint.setEnabled(false);
        menu.add(hint);
        return menu;
    }

    /** The match to play: set by the start mode, then by the Dev menu's match setup. */
    private com.planesnomad.match.MatchSetup setup;
    private com.planesnomad.match.DeckLibrary decks;

    private com.planesnomad.match.MatchSetup initialSetup() {
        // "play", "play-unlimited", "play-mixed": you play the bottom side; otherwise AI vs AI.
        boolean person = testDecks.startsWith("play");
        String which = person ? testDecks.replaceFirst("^play-?", "") : testDecks;
        return com.planesnomad.match.Presets.testMatch(which, person);
    }

    /** Starts a match from the current setup (ending the one before, if any). */
    private void startMatch() {
        if (decks == null) {
            decks = new com.planesnomad.match.DeckLibrary(db);
        }
        if (setup == null) {
            setup = initialSetup();
        }
        if (runner != null) {
            runner.stop();   // the old match ends; its snapshots no longer reach the screen
        }
        boolean person = setup.you().controller() == com.planesnomad.match.MatchSetup.Controller.PERSON;
        com.planesnomad.gui.engine.HumanBridge bridge = person ? new com.planesnomad.gui.engine.HumanBridge() : null;
        MatchRunner[] self = new MatchRunner[1];
        Match match;
        try {
            match = com.planesnomad.match.MatchFactory.create(setup, db, decks, bridge,
                (yours, phase) -> self[0] != null && self[0].isStop(yours, phase));
        } catch (IllegalArgumentException e) {
            javax.swing.JOptionPane.showMessageDialog(frame, e.getMessage(), "Can't start the match",
                javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        view.setHumanBridge(bridge);
        frame.setTitle("Planesnomad - " + setup.you().name() + " vs " + setup.enemy().name()
            + (person ? "" : " (AI vs AI)"));
        self[0] = new MatchRunner(match, setup.you().name(), setup.enemy().name(), setup.you().look(), setup.enemy().look(),
            snapshot -> SwingUtilities.invokeLater(() -> {
                if (runner == self[0]) {
                    view.update(snapshot);
                }
            }));
        runner = self[0];
        if (bridge != null) {
            runner.setHumanBridge(bridge);
        }
        runner.setPaused(paused);
        runner.setSpeed(speed);
        runner.setRevealEnemyHand(revealEnemyHand);
        runner.setMinEnemySpellMs(minEnemySpellMs);
        for (String key : MatchRunner.defaultStops()) {
            if (!stops.contains(key)) {
                runner.setStop(key.startsWith("you:"), forge.game.phase.PhaseType.valueOf(key.substring(key.indexOf(':') + 1)), false);
            }
        }
        for (String key : stops) {
            runner.setStop(key.startsWith("you:"), forge.game.phase.PhaseType.valueOf(key.substring(key.indexOf(':') + 1)), true);
        }
        runner.start();
    }

    /** Developer controls (testing only; in the game these come from the run and the encounter). */
    private JMenu devMenu() {
        JMenu menu = new JMenu("Dev");
        JMenuItem edit = new JMenuItem("Match setup... (decks, who plays, life, relics, looks)");
        edit.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_M, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        edit.addActionListener(e -> {
            if (decks == null) {
                decks = new com.planesnomad.match.DeckLibrary(db);
            }
            var dialog = new MatchSetupDialog(frame, setup == null ? initialSetup() : setup, decks,
                com.planesnomad.match.MatchFactory.commandCardChoices(db), name -> db.getCard(name) != null);
            var chosen = dialog.ask();
            if (chosen != null) {
                setup = chosen;
                startMatch();
            }
        });
        menu.add(edit);
        JMenuItem restart = new JMenuItem("Restart match (same setup)");
        restart.addActionListener(e -> startMatch());
        menu.add(restart);
        menu.addSeparator();
        for (String[] p : new String[][] {{"Test match: you vs AI", "play"}, {"Test match: AI vs AI", ""},
                {"Unlimited decks: you vs AI", "play-unlimited"}, {"Mixed eras: you vs AI", "play-mixed"}}) {
            JMenuItem it = new JMenuItem(p[0]);
            it.addActionListener(e -> {
                String which = p[1].replaceFirst("^play-?", "");
                setup = com.planesnomad.match.Presets.testMatch(which, p[1].startsWith("play"));
                startMatch();
            });
            menu.add(it);
        }
        return menu;
    }
}

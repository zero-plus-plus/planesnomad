package com.planesnomad.gui;

import com.planesnomad.ForgeEngine;
import com.planesnomad.RelicTest;
import com.planesnomad.gui.engine.MatchRunner;
import com.planesnomad.gui.images.CardImages;
import com.planesnomad.gui.images.ImageSetup;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.RelicSnapshot;
import com.planesnomad.gui.ui.MatchView;
import com.planesnomad.gui.ui.TopBar;
import forge.card.CardDb;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Renders the match screen to PNG files without opening a window (works headless), for
 * reviewing the layout. Plays one viewer match without delays and saves a few moments.
 *
 * Usage: java -Djava.awt.headless=true [-Dplanesnomad.cardImages=<folder>] ...
 *        com.planesnomad.gui.ScreenshotTool [outputDir] [unlimited|mixed]
 */
public final class ScreenshotTool {

    private static final int[][] SIZES = {{1280, 720}, {1920, 1080}};

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "screenshots");
        out.mkdirs();
        String which = args.length > 1 ? args[1].toLowerCase() : "";
        boolean unlimited = which.equals("unlimited");
        boolean mixed = which.equals("mixed");
        CardDb db = ForgeEngine.initialize();
        images = ImageSetup.create();
        System.out.println("Card picture folders: " + images.folders());
        matchFactory = mixed ? TestMatches::mixedErasWithRelics
            : unlimited ? TestMatches::unlimitedWithRelics : TestMatches::redVsBlueWithRelics;
        youSubtitle = mixed ? "Red-green aggro (KTK/SHM/FUT/TOR)"
            : unlimited ? "Black-red knights and dragons" : "Red goblins and burn";
        enemySubtitle = mixed ? "Blue-black tempo (SHM/FUT/TOR/KTK)"
            : unlimited ? "Blue-green merfolk and faeries" : "Blue tempo: counters and bounce";

        // Replay (up to 5 matches) until one has two or more items on the stack at once.
        List<MatchSnapshot> snaps = List.of();
        for (int attempt = 1; attempt <= 5; attempt++) {
            snaps = playOnce(db);
            if (snaps.stream().anyMatch(s -> s.stack().size() >= 2)) {
                break;
            }
            System.out.println("  (match " + attempt + " had no 2-item stack, replaying)");
        }

        save(out, "1-midgame-stack", pick(snaps, s -> !s.stack().isEmpty() && s.turn() >= 5));
        save(out, "2-combat", pick(snaps, s -> s.turn() >= 5 && (anyAttacking(s))));
        save(out, "3-relic-ready", pick(snaps, s -> state(s, RelicTest.ACTIVATED) == RelicSnapshot.State.READY));
        save(out, "4-relic-used", pick(snaps, s -> state(s, RelicTest.ACTIVATED) == RelicSnapshot.State.USED));
        save(out, "5-final", snaps.get(snaps.size() - 1));
        MatchSnapshot deep = snaps.stream().filter(s -> s.stack().size() >= 2).findFirst()
            .orElse(pick(snaps, s -> !s.stack().isEmpty()));
        save(out, "6-stack-cascade", deep);
        saveExpanded(out, "7-stack-details", deep);
        System.out.println("Screenshots written to " + out.getAbsolutePath());
    }

    private static List<MatchSnapshot> playOnce(CardDb db) throws InterruptedException {
        List<MatchSnapshot> snaps = Collections.synchronizedList(new ArrayList<>());
        MatchRunner runner = new MatchRunner(matchFactory.apply(db),
            TestMatches.YOU, TestMatches.ENEMY, youSubtitle, enemySubtitle, snaps::add);
        runner.setNoDelay(true);
        runner.start();
        runner.join();
        return snaps;
    }

    private static CardImages images;
    private static String youSubtitle;
    private static String enemySubtitle;
    private static java.util.function.Function<CardDb, forge.game.Match> matchFactory;

    private static List<com.planesnomad.gui.model.CardSnapshot> allCards(MatchSnapshot s) {
        List<com.planesnomad.gui.model.CardSnapshot> all = new ArrayList<>();
        for (var p : List.of(s.you(), s.enemy())) {
            all.addAll(p.hand());
            all.addAll(p.battlefield());
            all.addAll(p.graveyard());
            all.addAll(p.exile());
        }
        s.stack().forEach(i -> {
            if (i.source() != null) {
                all.add(i.source());
            }
        });
        return all;
    }

    private static boolean anyAttacking(MatchSnapshot s) {
        return s.you().battlefield().stream().anyMatch(c -> c.attacking())
            || s.enemy().battlefield().stream().anyMatch(c -> c.attacking());
    }

    private static RelicSnapshot.State state(MatchSnapshot s, String relic) {
        return s.you().relics().stream().filter(r -> r.name().equals(relic))
            .map(RelicSnapshot::state).findFirst().orElse(null);
    }

    private static MatchSnapshot pick(List<MatchSnapshot> snaps, Predicate<MatchSnapshot> test) {
        return snaps.stream().filter(test).findFirst().orElse(snaps.get(snaps.size() / 2));
    }

    private static void save(File dir, String name, MatchSnapshot snapshot) throws Exception {
        render(dir, name, snapshot, false);
    }

    private static void saveExpanded(File dir, String name, MatchSnapshot snapshot) throws Exception {
        render(dir, name, snapshot, true);
    }

    private static void render(File dir, String name, MatchSnapshot snapshot, boolean stackDetails) throws Exception {
        images.preloadNow(allCards(snapshot));   // pictures load in the background normally
        for (int[] size : SIZES) {
            BufferedImage img = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
            // Swing components must only be touched on the Swing thread.
            SwingUtilities.invokeAndWait(() -> {
                MatchView view = new MatchView(new NoControls());
                view.setSize(size[0], size[1]);
                view.rescale();
                view.update(snapshot);
                if (stackDetails) {
                    view.toggleStackDetails();
                }
                layoutTree(view);
                layoutTree(view);   // second pass: wrapped text settles its height
                Graphics2D g = img.createGraphics();
                view.printAll(g);
                g.dispose();
            });
            // Let resize events queued by setSize run, then confirm nothing changed the result.
            SwingUtilities.invokeAndWait(() -> { });
            File file = new File(dir, name + "-" + size[0] + "x" + size[1] + ".png");
            ImageIO.write(img, "png", file);
            System.out.println("  " + file.getName() + "  (turn " + snapshot.turn() + ", " + snapshot.phase()
                + ", stack " + snapshot.stack().size() + ")");
        }
    }

    /** Without a window, layouts must be run by hand, top-down. */
    private static void layoutTree(Component c) {
        if (c instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    private static final class NoControls implements TopBar.Controls {
        @Override public void setPaused(boolean paused) { }
        @Override public void step() { }
        @Override public void setSpeed(double speed) { }
        @Override public void setRevealEnemyHand(boolean reveal) { }
        @Override public void newMatch() { }
    }
}

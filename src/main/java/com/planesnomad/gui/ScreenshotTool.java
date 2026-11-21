package com.planesnomad.gui;

import com.planesnomad.ForgeEngine;
import com.planesnomad.RelicTest;
import com.planesnomad.gui.engine.MatchRunner;
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
 * Usage: java -Djava.awt.headless=true ... com.planesnomad.gui.ScreenshotTool [outputDir]
 */
public final class ScreenshotTool {

    private static final int[][] SIZES = {{1280, 720}, {1920, 1080}};

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "screenshots");
        out.mkdirs();
        CardDb db = ForgeEngine.initialize();

        List<MatchSnapshot> snaps = Collections.synchronizedList(new ArrayList<>());
        MatchRunner runner = new MatchRunner(TestMatches.redVsBlueWithRelics(db),
            TestMatches.YOU, TestMatches.ENEMY, "Red goblins and burn", "Blue tempo: counters and bounce", snaps::add);
        runner.setNoDelay(true);
        runner.start();
        runner.join();

        save(out, "1-midgame-stack", pick(snaps, s -> !s.stack().isEmpty() && s.turn() >= 5));
        save(out, "2-combat", pick(snaps, s -> s.turn() >= 5 && (anyAttacking(s))));
        save(out, "3-relic-ready", pick(snaps, s -> state(s, RelicTest.ACTIVATED) == RelicSnapshot.State.READY));
        save(out, "4-relic-used", pick(snaps, s -> state(s, RelicTest.ACTIVATED) == RelicSnapshot.State.USED));
        save(out, "5-final", snaps.get(snaps.size() - 1));
        System.out.println("Screenshots written to " + out.getAbsolutePath());
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
        for (int[] size : SIZES) {
            BufferedImage img = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
            // Swing components must only be touched on the Swing thread.
            SwingUtilities.invokeAndWait(() -> {
                MatchView view = new MatchView(new NoControls());
                view.setSize(size[0], size[1]);
                view.rescale();
                view.update(snapshot);
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
            System.out.println("  " + file.getName() + "  (turn " + snapshot.turn() + ", " + snapshot.phase() + ")");
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

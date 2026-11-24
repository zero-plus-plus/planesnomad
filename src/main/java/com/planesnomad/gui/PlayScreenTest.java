package com.planesnomad.gui;

import com.planesnomad.ForgeEngine;
import com.planesnomad.gui.engine.HumanBridge;
import com.planesnomad.gui.engine.HumanLobbyPlayer;
import com.planesnomad.gui.engine.MatchRunner;
import com.planesnomad.gui.images.ImageSetup;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.ui.CardGroups.Pile;
import com.planesnomad.gui.ui.CardRow;
import com.planesnomad.gui.ui.MatchView;
import com.planesnomad.gui.ui.PlayInput;
import com.planesnomad.gui.ui.TopBar;
import forge.card.CardDb;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A whole match played through the match screen's own input handling, without a window: each
 * question from the engine is answered by "clicking" highlighted cards, portraits and buttons
 * (PlayInput), as a person would. Saves a screenshot of the first question of each kind.
 */
public final class PlayScreenTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "play-screens");
        out.mkdirs();
        CardDb db = ForgeEngine.initialize();
        var images = ImageSetup.create();
        HumanBridge bridge = new HumanBridge();
        MatchRunner[] runner = new MatchRunner[1];
        HumanLobbyPlayer you = new HumanLobbyPlayer("You", bridge,
            (yours, phase) -> runner[0] != null && runner[0].isStop(yours, phase));
        AtomicReference<MatchView> viewRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MatchView v = new MatchView(new TopBar.Controls() {
                public void setPaused(boolean p) { }
                public void step() { }
                public void setSpeed(double s) { }
                public void setRevealEnemyHand(boolean r) { }
                public void newMatch() { }
            });
            v.setSize(1280, 720);
            v.rescale();
            v.setHumanBridge(bridge);
            v.setStops(new com.planesnomad.gui.ui.PhaseColumn.Stops() {
                public boolean isStop(boolean yours, forge.game.phase.PhaseType ph) {
                    return runner[0] != null ? runner[0].isStop(yours, ph)
                        : MatchRunner.defaultStops().contains(MatchRunner.stopKey(yours, ph));
                }

                public void setStop(boolean yours, forge.game.phase.PhaseType ph, boolean on) {
                    runner[0].setStop(yours, ph, on);
                }
            }, null);
            viewRef.set(v);
        });
        MatchView view = viewRef.get();
        PlayInput input = view.playInput();
        Map<String, Integer> handled = Collections.synchronizedMap(new LinkedHashMap<>());
        List<String> problems = Collections.synchronizedList(new ArrayList<>());
        boolean[] highlighted = new boolean[4];

        // Replace the window's listener: show the question, take a picture, then "click".
        bridge.setListener(r -> SwingUtilities.invokeLater(() -> {
            input.onRequest(r);
            if (r == null) {
                return;
            }
            String kind = r.getClass().getSimpleName();
            int n = handled.merge(kind, 1, Integer::sum);
            layout(view);
            BufferedImage img = render(view);   // also fills the rows' hit boxes for the clicks
            if (n == 1) {
                try {
                    ImageIO.write(img, "png", new File(out, "play-" + kind.toLowerCase() + ".png"));
                } catch (java.io.IOException e) {
                    problems.add(e.toString());
                }
            }
            try {
                act(view, input, r, highlighted);
            } catch (RuntimeException e) {
                problems.add(kind + ": " + e);
                bridge.answer(null);
            }
        }));
        List<MatchSnapshot> snaps = Collections.synchronizedList(new ArrayList<>());
        var looks = TestMatches.looks("");
        runner[0] = new MatchRunner(TestMatches.create("", db, you), "You", TestMatches.ENEMY, looks[0], looks[1], s -> {
            snaps.add(s);
            SwingUtilities.invokeLater(() -> view.update(s));
        });
        runner[0].setHumanBridge(bridge);
        bridge.setAutoTap(false);   // pay by clicking lands
        runner[0].setNoDelay(true);
        runner[0].start();
        runner[0].join();
        Thread.sleep(300);

        MatchSnapshot last = snaps.get(snaps.size() - 1);
        System.out.println("Questions answered through the screen: " + handled);
        System.out.println("Result: " + last.result() + " (turn " + last.turn() + ")");
        problems.forEach(p -> System.out.println("  problem: " + p));
        check("The match finishes", last.finished());
        check("No errors while clicking", problems.isEmpty());
        check("Priority questions shown and answered", handled.getOrDefault("Priority", 0) > 0);
        check("Playable cards were highlighted (green) at some point", highlighted[0]);
        check("Targets were chosen by clicking", handled.getOrDefault("Targets", 0) > 0 && highlighted[1]);
        check("Attackers were chosen by clicking", handled.getOrDefault("Attackers", 0) > 0 && highlighted[2]);
        check("Lands were tapped by clicking them (auto-tap off)", handled.getOrDefault("ManaPayment", 0) > 0 && highlighted[3]);
        check("You cast spells (your history entries)", last.history().stream().anyMatch(e -> e.byYou() && e.title().startsWith("You cast ")));
        System.out.println("Screenshots: " + out.getAbsolutePath());
        System.out.println(failures == 0 ? "ALL PLAY-SCREEN CHECKS PASSED" : failures + " PLAY-SCREEN CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** One "person" decision, through clicks on highlighted things. */
    private static void act(MatchView view, PlayInput input, HumanBridge.Request r, boolean[] seen) {
        if (r instanceof HumanBridge.Priority) {
            // Click the first highlighted card; nothing highlighted (only relics): pass.
            for (CardRow row : view.board().allRows()) {
                for (int i = 0; i < row.piles().size(); i++) {
                    Pile p = row.piles().get(i);
                    if (input.mark(p.top()) != null) {
                        seen[0] = true;
                        click(input, row, p);
                        return;
                    }
                }
            }
            input.primary();
        } else if (r instanceof HumanBridge.Targets t) {
            seen[1] = true;
            if (t.candidates().contains("player:" + TestMatches.ENEMY)) {
                input.playerClick(TestMatches.ENEMY);   // burn the enemy
                return;
            }
            for (CardRow row : view.board().allRows()) {
                for (Pile p : row.piles()) {
                    if (input.mark(p.top()) != null) {
                        click(input, row, p);
                        return;
                    }
                }
            }
            input.cancel();
        } else if (r instanceof HumanBridge.Attackers) {
            for (CardRow row : view.board().yourBattlefieldRows()) {
                for (Pile p : row.piles()) {
                    if (input.mark(p.top()) != null) {
                        seen[2] = true;
                        click(input, row, p);
                    }
                }
            }
            input.primary();
        } else if (r instanceof HumanBridge.ArrangeCards) {
            var model = input.choiceModel();
            model.toggle(model.cards().get(0));   // scry: first card to the bottom
            input.primary();
        } else if (r instanceof HumanBridge.ManaPayment) {
            for (CardRow row : view.board().yourBattlefieldRows()) {
                for (Pile p : row.piles()) {
                    if (input.mark(p.top()) != null) {
                        seen[3] = true;
                        click(input, row, p);   // tap this land
                        return;
                    }
                }
            }
            input.primary();   // nothing highlighted: auto-pay
        } else if (r instanceof HumanBridge.Blockers) {
            input.primary();   // no blocks (keeps the race simple)
        } else {
            input.primary();
        }
    }

    private static void click(PlayInput input, CardRow row, Pile pile) {
        MouseEvent e = new MouseEvent(row, MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false, MouseEvent.BUTTON1);
        input.click(row, pile, e);
    }

    private static BufferedImage render(MatchView view) {
        BufferedImage img = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        view.printAll(g);
        g.dispose();
        return img;
    }

    private static void layout(Component c) {
        if (c instanceof Container k) {
            k.invalidate();   // as a shown window would: drop cached sizes (e.g. after text changed)
            k.doLayout();
            for (Component x : k.getComponents()) {
                layout(x);
            }
        }
    }

    private static void check(String description, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + description);
        if (!ok) {
            failures++;
        }
    }

    private PlayScreenTest() { }
}

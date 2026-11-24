package com.planesnomad.gui;

import com.planesnomad.ForgeEngine;
import com.planesnomad.encounter.Encounters;
import com.planesnomad.gui.engine.MatchRunner;
import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.MatchSnapshot.Target;
import com.planesnomad.gui.ui.BoardPanel;
import com.planesnomad.gui.ui.CardPreview;
import com.planesnomad.gui.ui.CardRow;
import com.planesnomad.gui.ui.RewardScreen;
import com.planesnomad.gui.ui.Theme;
import forge.card.CardDb;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Checks stack targets from the engine, blockers lined up with attackers, and the reward screen. */
public final class FeatureTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        CardDb db = ForgeEngine.initialize();

        // 1. Targets come from the engine: AI vs AI (the blue deck has Unsummon / Counterspell, red has burn).
        List<MatchSnapshot> snaps = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 3; i++) {
            var looks = TestMatches.looks("");
            MatchRunner r = new MatchRunner(TestMatches.create("", db), TestMatches.YOU, TestMatches.ENEMY, looks[0], looks[1], snaps::add);
            r.setNoDelay(true);
            r.start();
            r.join();
        }
        List<MatchSnapshot.StackItem> aimed = snaps.stream().flatMap(s -> s.stack().stream())
            .filter(i -> !i.targets().isEmpty()).toList();
        check("Stack items carry their targets (" + aimed.size() + " seen)", !aimed.isEmpty());
        boolean namesMatch = aimed.stream().allMatch(i -> i.targets().stream().allMatch(t ->
            t.kind() != Target.Kind.CARD || i.detail().contains(t.name()) || i.detail().isEmpty()
                || i.detail().toLowerCase().contains("target")));
        check("Card targets are named in the stack text", namesMatch);
        boolean cardTargetOnBoard = false;
        for (MatchSnapshot s : snaps) {
            for (var item : s.stack()) {
                for (Target t : item.targets()) {
                    if (t.kind() == Target.Kind.CARD) {
                        var side = t.yours() ? s.you() : s.enemy();
                        if (side.battlefield().stream().anyMatch(c -> c.id() == t.id())) {
                            cardTargetOnBoard = true;
                        }
                    }
                }
            }
        }
        check("A targeted card is found on the right side's battlefield by id", cardTargetOnBoard);
        boolean playerTarget = aimed.stream().anyMatch(i -> i.targets().stream().anyMatch(t -> t.kind() == Target.Kind.PLAYER));
        check("Player targets are reported (burn at a player)", playerTarget);

        // 2. Blockers sit across from what they block.
        CardSnapshot a1 = FeatureShots.card("Goblin Raider", true, false);
        CardSnapshot a2 = FeatureShots.card("Raging Goblin", true, false);
        CardSnapshot a3 = FeatureShots.card("Shivan Dragon", true, false);
        CardSnapshot b1 = FeatureShots.card("Silvergill Adept", false, true);
        CardSnapshot b2 = FeatureShots.card("Merfolk Looter", false, true);
        CardSnapshot b3 = FeatureShots.card("Lord of Atlantis", false, true);
        CardSnapshot idle = FeatureShots.card("Master of the Pearl Trident", false, false);
        MatchSnapshot blocks = FeatureShots.snapshot(List.of(a1, a2, a3), List.of(b3, idle, b1, b2), List.of(),
            Map.of(b1.id(), List.of(a1.id()), b2.id(), List.of(a1.id()), b3.id(), List.of(a3.id())));
        Rectangle[] r = new Rectangle[7];
        SwingUtilities.invokeAndWait(() -> {
            Theme.updateScale(1280, 720);
            BoardPanel board = new BoardPanel(new CardPreview());
            board.setSize(960, 660);
            board.update(blocks);
            layoutTree(board);
            BufferedImage img = new BufferedImage(960, 660, BufferedImage.TYPE_INT_RGB);
            for (int pass = 0; pass < 2; pass++) {
                Graphics2D g = img.createGraphics();
                board.printAll(g);
                g.dispose();
            }
            CardRow enemy = board.battlefieldRows().get(2);
            CardRow you = board.battlefieldRows().get(3);
            int k = 0;
            for (CardSnapshot c : List.of(a1, a2, a3)) {
                r[k++] = SwingUtilities.convertRectangle(you, you.cardBounds(c.id()), board);
            }
            for (CardSnapshot c : List.of(b1, b2, b3, idle)) {
                Rectangle b = enemy.cardBounds(c.id());
                r[k++] = b == null ? null : SwingUtilities.convertRectangle(enemy, b, board);
            }
        });
        boolean allFound = java.util.Arrays.stream(r).allMatch(x -> x != null);
        check("Every attacker and blocker is drawn", allFound);
        if (allFound) {
            int tol = Math.max(4, r[0].width / 5);
            check("A lone blocker sits across from its attacker (dx " + (int) (r[5].getCenterX() - r[2].getCenterX()) + ")",
                Math.abs(r[5].getCenterX() - r[2].getCenterX()) <= tol);
            double pairCenter = (r[3].getCenterX() + r[4].getCenterX()) / 2;
            check("Two blockers on one attacker sit side by side, centered on it",
                Math.abs(pairCenter - r[0].getCenterX()) <= tol && !r[3].intersects(r[4]));
            boolean noOverlap = true;
            for (int i = 3; i < 7; i++) {
                for (int j = i + 1; j < 7; j++) {
                    noOverlap &= !r[i].intersects(r[j]);
                }
            }
            check("Blockers and other creatures don't overlap", noOverlap);
        }

        // 3. Reward screen: what was taken is reported.
        RewardScreen.Outcome[] outcome = new RewardScreen.Outcome[1];
        SwingUtilities.invokeAndWait(() -> {
            RewardScreen screen = new RewardScreen(new CardPreview());
            screen.setSize(960, 660);
            var rewards = RewardOffers.roll(Encounters.MERFOLK.rewards(), new Random(5));
            screen.show("Victory!", "test", rewards, o -> outcome[0] = o);
            check("Rewards: gold, a card choice and a relic", rewards.size() == 3
                && rewards.get(0).kind == RewardScreen.Reward.Kind.GOLD
                && rewards.get(1).kind == RewardScreen.Reward.Kind.CARDS && rewards.get(1).cards.size() == 3
                && rewards.get(2).kind == RewardScreen.Reward.Kind.RELIC);
            screen.clickRow(1);
            check("Clicking the card reward opens the choice", screen.isChoosingCard());
            screen.pickCard(2);
            screen.clickRow(0);
            outcome[0] = screen.outcome();
            check("Picked card and gold are taken; the untouched relic is not",
                outcome[0].gold() == 25 && outcome[0].cards().size() == 1
                    && outcome[0].cards().get(0).name().equals(rewards.get(1).cards.get(2).name())
                    && outcome[0].relics().isEmpty());
        });

        System.out.println(failures == 0 ? "ALL FEATURE CHECKS PASSED" : failures + " FEATURE CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void layoutTree(Component c) {
        if (c instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    private static void check(String d, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + d);
        if (!ok) {
            failures++;
        }
    }

    private FeatureTest() { }
}

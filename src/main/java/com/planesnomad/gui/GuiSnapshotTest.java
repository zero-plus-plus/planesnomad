package com.planesnomad.gui;

import com.planesnomad.ForgeEngine;
import com.planesnomad.RelicTest;
import com.planesnomad.gui.engine.MatchRunner;
import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.RelicSnapshot;
import com.planesnomad.gui.model.RelicSnapshot.State;
import forge.card.CardDb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checks what the match screen is given to draw, without drawing it: runs a full viewer
 * match (no delays) and inspects every snapshot. Needs no display.
 *
 * Run from the repository root. Exits with status 1 if any check fails.
 */
public final class GuiSnapshotTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        CardDb db = ForgeEngine.initialize();

        List<MatchSnapshot> snaps = Collections.synchronizedList(new ArrayList<>());
        var looks = TestMatches.looks("");
        MatchRunner runner = new MatchRunner(TestMatches.redVsBlueWithRelics(db),
            TestMatches.YOU, TestMatches.ENEMY, looks[0], looks[1], snaps::add);
        runner.setNoDelay(true);
        runner.start();
        runner.join();

        System.out.println("Snapshots taken: " + snaps.size());
        MatchSnapshot first = snaps.get(0);
        MatchSnapshot last = snaps.get(snaps.size() - 1);

        check("Many snapshots were published (" + snaps.size() + ")", snaps.size() > 50);
        check("Final snapshot has a result: " + last.result(), last.finished());
        check("Bottom player is " + TestMatches.YOU, first.you().name().equals(TestMatches.YOU));
        check("Top player is " + TestMatches.ENEMY, first.enemy().name().equals(TestMatches.ENEMY));

        boolean enemyHandAlwaysHidden = snaps.stream()
            .allMatch(s -> s.enemy().hand().stream().allMatch(CardSnapshot::isHidden));
        boolean yourHandAlwaysVisible = snaps.stream()
            .allMatch(s -> s.you().hand().stream().noneMatch(CardSnapshot::isHidden));
        boolean enemyHandEverNonEmpty = snaps.stream().anyMatch(s -> !s.enemy().hand().isEmpty());
        check("Enemy hand is always hidden (count only)", enemyHandAlwaysHidden && enemyHandEverNonEmpty);
        check("Your hand is always visible", yourHandAlwaysVisible);

        // "Show enemy hand" after the match has ended (no engine thread left to ask): still works.
        int before = snaps.size();
        runner.setRevealEnemyHand(true);
        MatchSnapshot afterEnd = snaps.size() > before ? snaps.get(snaps.size() - 1) : null;
        check("'Show enemy hand' after the match ended publishes a snapshot with the hand face up",
            afterEnd != null && afterEnd.enemy().hand().stream().noneMatch(CardSnapshot::isHidden));
        runner.setRevealEnemyHand(false);
        MatchSnapshot hiddenAgain = snaps.get(snaps.size() - 1);
        check("  ... and unchecking hides it again",
            hiddenAgain.enemy().hand().stream().allMatch(CardSnapshot::isHidden));

        check("Relics are shown as relics, not as cards on the battlefield",
            snaps.stream().allMatch(s -> s.you().battlefield().stream()
                .noneMatch(c -> c.name().equals(RelicTest.PASSIVE) || c.name().equals(RelicTest.ACTIVATED))));
        // The very first snapshot is taken before the engine sets up the command zone.
        MatchSnapshot turnOne = snaps.stream().filter(s -> s.turn() >= 1).findFirst().orElse(first);
        check("All four relics/consumables are in the bar once turn 1 starts",
            names(turnOne.you().relics()).containsAll(List.of(
                RelicTest.PASSIVE, RelicTest.TRIGGERED, RelicTest.ACTIVATED, RelicTest.CONSUMABLE)));
        check("Enemy's effects are its own (only " + TestMatches.ENEMY_EFFECT + "), never in your relic bar",
            snaps.stream().filter(s -> s.turn() >= 1).allMatch(s -> s.enemy().relics().stream().map(RelicSnapshot::name)
                .toList().equals(List.of(TestMatches.ENEMY_EFFECT))
                && s.you().relics().stream().noneMatch(r -> r.name().equals(TestMatches.ENEMY_EFFECT))));

        check("Passive relic is never READY or USED (no glow, no grey)",
            snaps.stream().allMatch(s -> state(s, RelicTest.PASSIVE) == null || state(s, RelicTest.PASSIVE) == State.IDLE));
        boolean lensReady = snaps.stream().anyMatch(s -> state(s, RelicTest.ACTIVATED) == State.READY);
        boolean lensUsed = snaps.stream().anyMatch(s -> state(s, RelicTest.ACTIVATED) == State.USED);
        boolean lensIdle = snaps.stream().anyMatch(s -> state(s, RelicTest.ACTIVATED) == State.IDLE);
        System.out.println("  Tinker's Lens states seen: ready=" + lensReady + " used=" + lensUsed + " idle=" + lensIdle);
        check("Activated relic is READY (glow) at some point", lensReady);
        check("Activated relic is IDLE when it can't be paid for (e.g. early turns)", lensIdle);
        if (countLensUses(last) > 0) {
            check("Activated relic shows USED (greyed out) after use", lensUsed);
            // The once-per-turn limit resets each turn: a used relic must not still be greyed
            // out at the start of a turn (untap/upkeep), whatever happens later in the match.
            check("USED resets at the start of the next turn (untap/upkeep: USED only if used this turn)",
                snaps.stream().filter(s -> s.phase().startsWith("Untap") || s.phase().startsWith("Upkeep"))
                    .filter(s -> state(s, RelicTest.ACTIVATED) == State.USED)
                    .allMatch(GuiSnapshotTest::lensUsedThisTurn));
            check("USED is followed by READY again on a later turn (when the match goes on)",
                usedThenReadyLater(snaps));
        }

        boolean flaskEverReady = snaps.stream().anyMatch(s -> state(s, RelicTest.CONSUMABLE) == State.READY);
        boolean flaskUsed = last.log().stream().anyMatch(l -> l.text().startsWith(TestMatches.YOU + " activated " + RelicTest.CONSUMABLE));
        check("Consumable is READY (glow) while available", flaskEverReady);
        if (flaskUsed) {
            check("Consumable disappears from the bar after use",
                !names(last.you().relics()).contains(RelicTest.CONSUMABLE));
        }

        boolean stackSeen = snaps.stream().anyMatch(s -> !s.stack().isEmpty());
        check("Stack contents are shown in plain language", stackSeen && snaps.stream()
            .flatMap(s -> s.stack().stream())
            .allMatch(i -> i.text().matches(".+ (casts|activates|triggers) .+")));
        check("Stack shows both players' items", snaps.stream().flatMap(s -> s.stack().stream()).anyMatch(i -> i.byYou())
            && snaps.stream().flatMap(s -> s.stack().stream()).anyMatch(i -> !i.byYou()));

        check("Stack items know whether they are a spell, ability or trigger",
            snaps.stream().flatMap(s -> s.stack().stream()).allMatch(i -> i.kind() != null));
        check("Your own triggers appear on the stack (e.g. " + RelicTest.TRIGGERED + ")",
            snaps.stream().flatMap(s -> s.stack().stream()).anyMatch(i -> i.byYou()
                && i.kind() == MatchSnapshot.StackItem.Kind.TRIGGER && i.text().contains(RelicTest.TRIGGERED)));
        long resolvingSeen = snaps.stream().flatMap(s -> s.stack().stream()).filter(MatchSnapshot.StackItem::resolving).count();
        System.out.println("  (items seen while resolving: " + resolvingSeen + ")");

        // Screen behaviour of the stack pop-up (no window needed).
        MatchSnapshot withStack = snaps.stream().filter(s -> !s.stack().isEmpty()).findFirst().orElseThrow();
        MatchSnapshot withoutStack = snaps.stream().filter(s -> s.stack().isEmpty() && s.turn() >= 1).findFirst().orElseThrow();
        boolean[] ui = new boolean[5];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var view = new com.planesnomad.gui.ui.MatchView(null);
            view.setSize(1280, 720);
            view.rescale();
            view.doLayout();
            view.update(withoutStack);
            ui[0] = !view.isStackPopupShown();
            view.update(withStack);
            ui[1] = view.isStackPopupShown();
            ui[2] = !view.isStackDetailsShown();
            view.toggleStackDetails();
            ui[3] = view.isStackDetailsShown();
            view.toggleStackDetails();
            view.update(withoutStack);
            ui[4] = !view.isStackPopupShown() && !view.isStackDetailsShown();
        });
        // Resizable, collapsible stack & log column.
        int[] widths = new int[6];
        boolean[] col = new boolean[3];
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            var view = new com.planesnomad.gui.ui.MatchView(null);
            view.setSize(1280, 720);
            view.rescale();
            view.update(withStack);
            layoutTree(view);
            layoutTree(view);
            widths[0] = view.stackColumnWidth();
            int boardBefore = boardWidth(view);
            view.setStackColumnCollapsed(true);
            layoutTree(view);
            widths[1] = view.stackColumnWidth();
            widths[2] = boardWidth(view) - boardBefore;
            view.toggleStackDetails();                 // Tab while collapsed
            layoutTree(view);
            layoutTree(view);
            col[0] = !view.isStackColumnCollapsed() && view.isStackDetailsShown();
            view.setStackColumnWidthRef(420);
            layoutTree(view);
            layoutTree(view);
            widths[3] = view.stackColumnWidth();
            view.setStackColumnCollapsed(true);
            layoutTree(view);
            view.setStackColumnCollapsed(false);
            layoutTree(view);
            layoutTree(view);
            widths[4] = view.stackColumnWidth();
        });
        check("Stack & log column starts at its default width (" + widths[0] + " px)", Math.abs(widths[0] - 260) <= 8);
        check("Collapsing hides the column and gives the board the space (+" + widths[2] + " px)",
            widths[1] == 0 && widths[2] > 200);
        check("Tab while collapsed opens the column with the stack list", col[0]);
        check("A set column width is applied (" + widths[3] + " px for 420)", Math.abs(widths[3] - 420) <= 8);
        check("The width is kept after collapsing and expanding again (" + widths[4] + " px)", Math.abs(widths[4] - 420) <= 8);

        check("Stack pop-up is hidden when the stack is empty", ui[0]);
        check("Stack pop-up appears when something is on the stack", ui[1]);
        check("Full stack list in the right column is hidden by default", ui[2]);
        check("Right-click / Tab shows the full stack list", ui[3]);
        check("Pop-up disappears again when the stack empties", ui[4]);

        var stackItems = snaps.stream().flatMap(s -> s.stack().stream()).toList();
        check("Abilities and triggers on the stack are ability cards with only the ability's text",
            stackItems.stream().filter(i -> i.kind() != MatchSnapshot.StackItem.Kind.SPELL && i.source() != null)
                .allMatch(i -> i.source().ability() && i.source().rulesText().equals(i.detail())
                    && i.source().typeLine().endsWith("ability")));
        check("e.g. the Wayfarer's Compass trigger shows only 'scry 1' text, no card frame text",
            stackItems.stream().filter(i -> i.text().contains(RelicTest.TRIGGERED)).anyMatch(i ->
                i.source().rulesText().toLowerCase().contains("scry 1") && i.source().power() == null));
        check("No engine labels like [Zone Changer: X] in stack text",
            stackItems.stream().noneMatch(i -> i.detail().matches(".*\\[[A-Z][A-Za-z ]*: [^\\]]*]\\s*$")));
        check("Spells on the stack stay full cards", stackItems.stream()
            .filter(i -> i.kind() == MatchSnapshot.StackItem.Kind.SPELL && i.source() != null)
            .noneMatch(i -> i.source().ability()));

        check("Portrait and playmat cards are set for both players (from the match's looks)",
            first.you().portrait() != null && first.you().portrait().name().equals(looks[0].portraitCard())
                && first.enemy().portrait() != null && first.enemy().portrait().name().equals(looks[1].portraitCard())
                && first.you().playmat() != null && first.enemy().playmat() != null);
        check("Enemy's guaranteed cards are listed for its effects bar",
            first.enemy().featured().stream().map(CardSnapshot::name).toList().equals(looks[1].featuredCards()));
        check("Portrait/playmat/featured cards carry image keys (art crops)",
            first.enemy().portrait().imageKey().contains("/") && first.you().playmat().imageKey().contains("/"));
        check("No trigger labels like [Phase: ...] or [Zone Changer: ...] in the log",
            last.log().stream().noneMatch(l -> l.text().matches(".*\\[[A-Z][A-Za-z ]*: [^\\]]*]\\s*$")));

        check("Attacking creatures are marked during combat",
            snaps.stream().anyMatch(s -> s.you().battlefield().stream().anyMatch(CardSnapshot::attacking)
                || s.enemy().battlefield().stream().anyMatch(CardSnapshot::attacking)));
        check("Tapped lands are marked", snaps.stream().anyMatch(s -> s.you().battlefield().stream()
            .anyMatch(c -> c.land() && c.tapped())));
        check("Game log grows and starts with turn 1", last.log().size() > 20 && last.log().get(0).turnHeader());
        check("Cards carry Forge's image key (picture file lookup)",
            snaps.stream().flatMap(s -> s.you().battlefield().stream())
                .filter(c -> !c.token()).allMatch(c -> c.imageKey().contains("/")));
        check("Relic engine cards never appear in graveyard or exile (used consumables stay hidden)",
            snaps.stream().allMatch(s -> s.you().exile().stream().noneMatch(c -> c.name().equals(RelicTest.CONSUMABLE))));
        check("Relics on the stack are shown as relics",
            snaps.stream().flatMap(s -> s.stack().stream()).filter(i -> i.source() != null
                && (i.source().name().equals(RelicTest.ACTIVATED) || i.source().name().equals(RelicTest.CONSUMABLE)))
                .allMatch(i -> i.relic() != null && !i.source().typeLine().contains("Artifact")));
        check("Cards carry set code and collector number (for image downloads)",
            snaps.stream().flatMap(s -> s.you().battlefield().stream())
                .filter(c -> !c.token()).allMatch(c -> !c.setCode().isEmpty() && !c.collectorNumber().isEmpty()));

        // Debug option: reveal the enemy hand.
        List<MatchSnapshot> revealed = Collections.synchronizedList(new ArrayList<>());
        MatchRunner r2 = new MatchRunner(TestMatches.redVsBlueWithRelics(db),
            TestMatches.YOU, TestMatches.ENEMY, "you", "enemy", revealed::add);
        r2.setNoDelay(true);
        r2.setRevealEnemyHand(true);
        r2.start();
        r2.join();
        check("'Show enemy hand' reveals the enemy's cards", revealed.stream()
            .anyMatch(s -> !s.enemy().hand().isEmpty() && s.enemy().hand().stream().noneMatch(CardSnapshot::isHidden)));

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL GUI SNAPSHOT CHECKS PASSED");
        } else {
            System.out.println(failures + " GUI SNAPSHOT CHECK(S) FAILED");
            System.exit(1);
        }
    }

    private static void layoutTree(java.awt.Component c) {
        if (c instanceof java.awt.Container k) {
            k.doLayout();
            for (java.awt.Component ch : k.getComponents()) {
                layoutTree(ch);
            }
        }
    }

    /** Width of the board area (the layered pane holding the board). */
    private static int boardWidth(java.awt.Container root) {
        if (root instanceof com.planesnomad.gui.ui.BoardPanel) {
            return root.getWidth();
        }
        for (java.awt.Component ch : root.getComponents()) {
            if (ch instanceof java.awt.Container k) {
                int w = boardWidth(k);
                if (w >= 0) {
                    return w;
                }
            }
        }
        return -1;
    }

    private static List<String> names(List<RelicSnapshot> relics) {
        return relics.stream().map(RelicSnapshot::name).toList();
    }

    private static State state(MatchSnapshot s, String relic) {
        return s.you().relics().stream().filter(r -> r.name().equals(relic))
            .map(RelicSnapshot::state).findFirst().orElse(null);
    }

    /** True if the log shows Tinker's Lens activated since the latest turn header. */
    private static boolean lensUsedThisTurn(MatchSnapshot s) {
        for (int i = s.log().size() - 1; i >= 0; i--) {
            MatchSnapshot.LogLine line = s.log().get(i);
            if (line.turnHeader()) {
                return false;
            }
            if (line.text().startsWith(TestMatches.YOU + " activated " + RelicTest.ACTIVATED)) {
                return true;
            }
        }
        return false;
    }

    private static long countLensUses(MatchSnapshot s) {
        return s.log().stream().filter(l -> l.text().startsWith(TestMatches.YOU + " activated " + RelicTest.ACTIVATED)).count();
    }

    /**
     * True if, after the first time the relic shows USED, it later shows READY; or if the match
     * ended before the player had another chance (no later turn of theirs with the relic idle).
     */
    private static boolean usedThenReadyLater(List<MatchSnapshot> snaps) {
        int lastTurn = snaps.get(snaps.size() - 1).turn();
        int usedTurn = -1;
        for (MatchSnapshot s : snaps) {
            State st = state(s, RelicTest.ACTIVATED);
            if (st == State.USED && usedTurn < 0) {
                usedTurn = s.turn();
            } else if (st == State.READY && usedTurn >= 0 && s.turn() > usedTurn) {
                return true;
            }
        }
        // Never used, or used so late that no later turn of "you" came (turns alternate).
        return usedTurn < 0 || lastTurn <= usedTurn + 1;
    }

    private static void check(String description, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + description);
        if (!ok) {
            failures++;
        }
    }
}

package com.planesnomad.gui;

import com.planesnomad.ForgeEngine;
import com.planesnomad.gui.engine.HumanBridge;
import com.planesnomad.gui.engine.HumanLobbyPlayer;
import com.planesnomad.gui.engine.MatchRunner;
import com.planesnomad.gui.model.MatchSnapshot;
import forge.card.CardDb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plays a match where the bottom player is "a person" - here a script answering the window's
 * questions (play the first option, first targets, attack with everything, block when possible)
 * - to check the human controller end to end without a window.
 */
public final class HumanPlayTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        CardDb db = ForgeEngine.initialize();
        HumanBridge bridge = new HumanBridge();
        MatchRunner[] runner = new MatchRunner[1];
        HumanLobbyPlayer you = new HumanLobbyPlayer("You", bridge,
            (yours, phase) -> runner[0] != null && runner[0].isStop(yours, phase));
        Map<String, AtomicInteger> asked = Collections.synchronizedMap(new LinkedHashMap<>());
        List<String> played = Collections.synchronizedList(new ArrayList<>());
        List<MatchSnapshot> snaps = Collections.synchronizedList(new ArrayList<>());
        boolean[] creatureTargetOffered = {false};
        int[] endTurnUsedOn = {-1};
        List<Integer> priorityTurns = Collections.synchronizedList(new ArrayList<>());
        int[] untilEventStartedOn = {-1};
        List<String> emptyCombatStops = Collections.synchronizedList(new ArrayList<>());
        boolean[] untilEventEnded = {false};
        int[] untilEventEndedOn = {-1};
        bridge.setAutoTap(false);   // first: tap lands yourself
        bridge.setListener(req -> {
            if (req == null) {
                return;
            }
            asked.computeIfAbsent(req.getClass().getSimpleName(), k -> new AtomicInteger()).incrementAndGet();
            int turn = snaps.isEmpty() ? 0 : snaps.get(snaps.size() - 1).turn();
            if (req instanceof HumanBridge.Targets t && t.candidates().stream().anyMatch(k -> k.startsWith("card:"))) {
                creatureTargetOffered[0] = true;
            }
            if (req instanceof HumanBridge.Priority) {
                priorityTurns.add(turn);
                MatchSnapshot now = snaps.isEmpty() ? null : snaps.get(snaps.size() - 1);
                if (now != null && now.stack().isEmpty() && isLateCombat(now.phase())
                    && now.you().battlefield().stream().noneMatch(c -> c.attacking())
                    && now.enemy().battlefield().stream().noneMatch(c -> c.attacking())) {
                    emptyCombatStops.add(now.phase());
                }
                if (untilEventStartedOn[0] >= 0 && !untilEventEnded[0]) {
                    untilEventEnded[0] = true;   // asked again: "until something happens" stopped
                    untilEventEndedOn[0] = turn;
                }
                // From turn 6: try "End turn"; from turn 9 (a later turn): "until something happens".
                if (turn >= 6 && endTurnUsedOn[0] < 0) {
                    endTurnUsedOn[0] = turn;
                    new Thread(() -> bridge.setAutoPass(HumanBridge.AutoPass.END_OF_TURN)).start();
                    return;
                }
                if (turn >= 9 && endTurnUsedOn[0] >= 0 && turn > endTurnUsedOn[0] && untilEventStartedOn[0] < 0) {
                    untilEventStartedOn[0] = turn;
                    new Thread(() -> bridge.setAutoPass(HumanBridge.AutoPass.UNTIL_EVENT)).start();
                    return;
                }
                if (turn >= 4) {
                    bridge.setAutoTap(true);   // later: automatic tapping again
                }
            }
            // Answer from another thread, as the window would (the engine thread is waiting).
            new Thread(() -> bridge.answer(answer(req, played))).start();
        });
        var looks = TestMatches.looks("");
        runner[0] = new MatchRunner(TestMatches.create("", db, you), "You", TestMatches.ENEMY, looks[0], looks[1], snaps::add);
        runner[0].setHumanBridge(bridge);
        runner[0].setNoDelay(true);
        runner[0].start();
        runner[0].join();

        MatchSnapshot last = snaps.get(snaps.size() - 1);
        System.out.println("Questions asked: " + asked);
        System.out.println("Played by 'you': " + played.size() + " " + played.stream().limit(12).toList());
        System.out.println("Result: " + last.result() + " after turn " + last.turn());
        check("The match finishes with a result", last.finished());
        check("You were given priority", asked.containsKey("Priority"));
        check("You played lands", played.stream().anyMatch(p -> p.startsWith("Play ")));
        check("You cast spells", played.stream().anyMatch(p -> p.startsWith("Cast ")));
        check("You chose targets (burn spells)", asked.containsKey("Targets"));
        // Asked to attack whenever a creature could (the scripted player sometimes never keeps one).
        boolean couldAttack = snaps.stream().anyMatch(x -> x.activePlayer().equals("You") && x.turn() != endTurnUsedOn[0]
            && x.phase().toLowerCase().contains("attackers")
            && x.you().battlefield().stream().anyMatch(c -> c.creature() && !c.tapped() && !c.summoningSick()));
        check("You declared attackers when a creature could attack (" + (couldAttack ? "could" : "none could") + ")",
            !couldAttack || asked.containsKey("Attackers"));
        check("The bottom player in the snapshots is you", last.you().name().equals("You"));
        check("History shows your actions", last.history().stream().anyMatch(e -> e.byYou() && e.actor().equals("You")));
        check("Burn spells can target creatures, not only players", creatureTargetOffered[0]);
        check("Your upkeep scry (Wayfarer's Compass) is your choice", asked.containsKey("ArrangeCards"));
        check("No stops in combat steps without attackers " + emptyCombatStops, emptyCombatStops.isEmpty());
        check("With auto-tap off you were asked to tap lands, and spells were still cast",
            asked.containsKey("ManaPayment") && played.stream().anyMatch(p -> p.startsWith("Cast ")));
        long sameTurn = endTurnUsedOn[0] < 0 ? -1 : priorityTurns.stream().filter(t -> t == endTurnUsedOn[0]).count();
        check("'Auto-pass all' passed the rest of that turn (asked once on turn " + endTurnUsedOn[0] + ")", sameTurn == 1);
        check("'Pass turn' passed, then asked again later", untilEventStartedOn[0] < 0 || untilEventEnded[0]);
        check("Auto-passes last for the current turn only ('Pass turn' on turn " + untilEventStartedOn[0]
                + ", asked again on turn " + untilEventEndedOn[0] + ")",
            untilEventStartedOn[0] < 0 || untilEventEndedOn[0] < 0 || untilEventEndedOn[0] <= untilEventStartedOn[0] + 1);
        long nextTurn = endTurnUsedOn[0] < 0 ? -1 : priorityTurns.stream().filter(t -> t == endTurnUsedOn[0] + 1).count();
        check("After 'Auto-pass all', you get priority again on the next turn", endTurnUsedOn[0] < 0 || nextTurn > 0
            || last.turn() <= endTurnUsedOn[0] + 1);

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL HUMAN-PLAY CHECKS PASSED");
        } else {
            System.out.println(failures + " HUMAN-PLAY CHECK(S) FAILED");
            System.exit(1);
        }
        System.exit(0);
    }

    private static boolean isLateCombat(String phase) {
        String p = phase.toLowerCase();
        return p.contains("blockers") || p.contains("damage");   // skipped without attackers (CR 508.8)
    }

    /** A simple "person": first option, first legal targets, attack with all, block the first attacker. */
    private static Object answer(HumanBridge.Request req, List<String> played) {
        if (req instanceof HumanBridge.Priority p) {
            HumanBridge.Option o = p.options().get(0);
            played.add(o.label());
            return o.key();
        }
        if (req instanceof HumanBridge.Targets t) {
            // Prefer the enemy as target (burn), else the first candidate.
            List<String> pick = new ArrayList<>();
            t.candidates().stream().filter(k -> k.equals("player:" + TestMatches.ENEMY)).findFirst().ifPresent(pick::add);
            for (String k : t.candidates()) {
                if (pick.size() >= Math.max(1, t.min())) break;
                if (!pick.contains(k)) pick.add(k);
            }
            return pick;
        }
        if (req instanceof HumanBridge.Attackers a) {
            return new ArrayList<>(a.candidates());
        }
        if (req instanceof HumanBridge.ArrangeCards a) {
            return List.of(a.cards().get(0).id());   // scry: the first card to the bottom
        }
        if (req instanceof HumanBridge.ManaPayment m) {
            return m.sources().isEmpty() ? "auto" : m.sources().get(0).key();   // tap the first land
        }
        if (req instanceof HumanBridge.Blockers b) {
            Map<Integer, Integer> pairs = new LinkedHashMap<>();
            b.legal().forEach((blocker, attackers) -> pairs.putIfAbsent(blocker, attackers.get(0)));
            return pairs;
        }
        return null;
    }

    private static void check(String description, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + description);
        if (!ok) {
            failures++;
        }
    }

    private HumanPlayTest() { }
}

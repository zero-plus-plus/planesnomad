package com.planesnomad.encounter;

import com.google.common.eventbus.Subscribe;
import com.planesnomad.ForgeEngine;
import com.planesnomad.gui.engine.MatchRunner;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.match.DeckLibrary;
import com.planesnomad.match.MatchFactory;
import com.planesnomad.match.MatchSetup;
import com.planesnomad.match.Presets;
import forge.card.CardRules;
import forge.card.mana.ManaCost;
import forge.deck.Deck;
import forge.game.event.GameEvent;
import forge.game.event.GameEventTurnPhase;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.card.CardDb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Checks the Merfolk test encounter: its deck rules, the guaranteed turn-2 Lord, its rewards. */
public final class EncounterTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        CardDb db = ForgeEngine.initialize();
        DeckLibrary decks = new DeckLibrary(db);
        Encounter enc = Encounters.MERFOLK;

        // Deck rules asked for: 60 cards, blue only, nonlands cost 1 or more, no free alternative costs,
        // 4 Counterspell, lands only basic Islands, Merfolk lords.
        Deck deck = decks.get(enc.enemy().deck());
        check("Encounter deck loads", deck != null);
        int total = 0;
        boolean blueOnly = true;
        boolean costOk = true;
        boolean landsOk = true;
        boolean noFree = true;
        int counterspells = 0;
        List<String> bad = new ArrayList<>();
        for (Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            PaperCard pc = e.getKey();
            CardRules r = pc.getRules();
            total += e.getValue();
            if (r.getType().isLand()) {
                if (!pc.getName().equals("Island")) {
                    landsOk = false;
                    bad.add(pc.getName());
                }
                continue;
            }
            if (!r.getColor().hasBlue() || r.getColor().countColors() != 1) {
                blueOnly = false;
                bad.add(pc.getName() + " (color)");
            }
            ManaCost mc = r.getManaCost();
            if (mc == null || mc.getCMC() < 1) {
                costOk = false;
                bad.add(pc.getName() + " (cost)");
            }
            String text = r.getOracleText().toLowerCase();
            if (text.contains("without paying") || text.contains("rather than pay")) {
                noFree = false;
                bad.add(pc.getName() + " (free)");
            }
            if (pc.getName().equals("Counterspell")) {
                counterspells += e.getValue();
            }
        }
        check("60 cards (" + total + ")", total == 60);
        check("Blue cards only " + bad, blueOnly);
        check("Every spell costs 1 or more", costOk);
        check("No spell castable for free", noFree);
        check("Lands are basic Islands only", landsOk);
        check("4 Counterspell", counterspells == 4);
        check("Has Lord of Atlantis", deck.getMain().countByName("Lord of Atlantis") == 4);

        // Guaranteed draw: in several AI-vs-AI games, the enemy has a Lord drawn on its own turn 2.
        int runs = 6;
        int ok = 0;
        for (int i = 0; i < runs; i++) {
            MatchSetup setup = enc.setup(Presets.testMatch("", false).you());
            boolean[] drawnByTurn2 = {false};
            GuaranteedDraws[] hook = new GuaranteedDraws[1];
            List<MatchSnapshot> snaps = Collections.synchronizedList(new ArrayList<>());
            MatchRunner r = new MatchRunner(MatchFactory.create(setup, db, decks, null, (y, p) -> false),
                setup.you().name(), setup.enemy().name(), setup.you().look(), setup.enemy().look(), snaps::add);
            r.setNoDelay(true);
            r.setBeforeStart(game -> {
                hook[0] = GuaranteedDraws.install(game, setup.enemy().name(), enc.guarantees());
                Player enemy = game.getRegisteredPlayers().stream()
                    .filter(p -> p.getName().equals(setup.enemy().name())).findFirst().orElseThrow();
                game.subscribeToEvents(new Object() {
                    int turns;

                    @Subscribe
                    public void on(GameEvent ev) {
                        if (ev instanceof GameEventTurnPhase tp && tp.playerTurn() != null
                            && tp.playerTurn().getId() == enemy.getId()) {
                            if (tp.phase() == PhaseType.UPKEEP) {
                                turns++;
                            }
                            if (turns == 2 && tp.phase() == PhaseType.MAIN1 && !hook[0].placedIds().isEmpty()) {
                                int id = hook[0].placedIds().get(0);
                                drawnByTurn2[0] = enemy.getCardsIn(ZoneType.Library).stream().noneMatch(c -> c.getId() == id)
                                    && enemy.getCardsIn(ZoneType.Hand).stream().anyMatch(c -> c.getId() == id);
                            }
                        }
                    }
                });
            });
            r.start();
            r.join();
            System.out.println("    game " + (i + 1) + ": " + hook[0].done() + ", in hand at turn-2 main: " + drawnByTurn2[0]
                + ", result " + snaps.get(snaps.size() - 1).result());
            boolean logged = snaps.get(snaps.size() - 1).log().stream()
                .anyMatch(l -> l.text().startsWith("Guaranteed: " + setup.enemy().name() + " draws Lord of Atlantis"));
            if (drawnByTurn2[0] && logged) {
                ok++;
            }
        }
        check("Lord of Atlantis drawn on the enemy's turn 2 and logged (" + ok + "/" + runs + ")", ok == runs);

        // Rewards
        List<String> offer = enc.rewards().rollCards(new Random(1));
        check("Card reward offers 3 different cards " + offer, offer.size() == 3 && offer.stream().distinct().count() == 3);
        check("Reward cards exist", offer.stream().allMatch(n -> db.getCard(n) != null));
        check("Test rewards include gold and a relic", enc.rewards().gold() > 0 && !enc.rewards().relics().isEmpty());

        System.out.println(failures == 0 ? "ALL ENCOUNTER CHECKS PASSED" : failures + " ENCOUNTER CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(String d, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + d);
        if (!ok) {
            failures++;
        }
    }

    private EncounterTest() { }
}

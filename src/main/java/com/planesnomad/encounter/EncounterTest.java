package com.planesnomad.encounter;

import com.google.common.eventbus.Subscribe;
import com.planesnomad.ForgeEngine;
import com.planesnomad.gui.BarkWatcher;
import com.planesnomad.gui.engine.MatchRunner;
import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.PlayerSnapshot;
import com.planesnomad.match.DeckLibrary;
import com.planesnomad.match.MatchFactory;
import com.planesnomad.match.MatchSetup;
import com.planesnomad.match.Presets;
import com.planesnomad.match.StartRules;
import forge.card.CardDb;
import forge.card.CardRules;
import forge.deck.Deck;
import forge.game.event.GameEvent;
import forge.game.event.GameEventTurnPhase;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Checks encounter files and the Lorwyn Merfolk enemy: the files load without problems, mistakes
 * in a file are reported, the enemy's deck rules, its start (player first, preset 6-card hand
 * taken from the deck, two Merfolk tokens), the turn-2 Lord, barks, and the red starter deck.
 */
public final class EncounterTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        CardDb db = ForgeEngine.initialize();

        // 1. The encounter files load cleanly.
        EncounterLibrary lib = EncounterLibrary.load(db);
        check("Encounter files load without problems " + lib.problems(), lib.problems().isEmpty());
        Encounter enc = lib.get(Presets.FIRST_ENCOUNTER);
        check("The Lorwyn easy Merfolk enemy is there", enc != null);
        if (enc == null) {
            finish();
        }
        check("Plane and tier come from the folder", enc.plane().equals("lorwyn") && enc.tier() == Encounter.Tier.EASY);
        check("Player goes first", enc.firstPlayer() == MatchSetup.FirstPlayer.YOU);
        check("Preset hand: 2 Island, 2 Cursecatcher, 1 Merfolk of the Pearl Trident, 1 Counterspell",
            sorted(enc.enemy().start().hand()).equals(sorted(List.of("Island", "Island", "Cursecatcher", "Cursecatcher",
                "Merfolk of the Pearl Trident", "Counterspell"))));
        check("Two bonus Merfolk tokens", enc.enemy().start().battlefield().equals(List.of("token:u_1_1_merfolk", "token:u_1_1_merfolk")));
        check("A bark below half life", enc.barks().stream().anyMatch(b -> b.when() == Bark.When.LIFE_BELOW_HALF));
        check("Icon is a card's art, background a picture file",
            "Lord of Atlantis".equals(enc.enemy().look().portraitCard())
                && enc.enemy().look().playmatCard() != null && enc.enemy().look().playmatCard().startsWith("file:"));
        check("Drops listed (not used yet)", !enc.drops().isEmpty());

        // 2. Enemy deck rules: 60 cards, blue only, cost 1+, nothing free, basic Islands, 4 Counterspell.
        DeckLibrary decks = new DeckLibrary(db);
        Deck deck = decks.get(enc.enemy().deck());
        check("The enemy deck is in the deck library as \"" + enc.enemy().deck() + "\"", deck != null);
        List<String> bad = new ArrayList<>();
        int total = 0;
        int counterspells = 0;
        for (Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            CardRules r = e.getKey().getRules();
            total += e.getValue();
            String n = e.getKey().getName();
            if (r.getType().isLand()) {
                if (!n.equals("Island")) bad.add(n + " (land)");
                continue;
            }
            if (!r.getColor().hasBlue() || r.getColor().countColors() != 1) bad.add(n + " (color)");
            if (r.getManaCost() == null || r.getManaCost().getCMC() < 1) bad.add(n + " (cost)");
            String t = r.getOracleText().toLowerCase(Locale.ROOT);
            if (t.contains("without paying") || t.contains("rather than pay")) bad.add(n + " (free)");
            if (n.equals("Counterspell")) counterspells += e.getValue();
        }
        check("Enemy deck: 60 cards, blue, cost 1+, nothing free, Islands only " + bad, total == 60 && bad.isEmpty());
        check("Enemy deck: 4 Counterspell, Merfolk of the Pearl Trident instead of Master",
            counterspells == 4 && deck.getMain().countByName("Merfolk of the Pearl Trident") == 4
                && deck.getMain().countByName("Master of the Pearl Trident") == 0);

        // 3. The player's red starter deck.
        Deck red = decks.get(Presets.STARTER_DECK);
        check("Red starter deck loads", red != null);
        Set<String> keywords = Set.of("haste", "trample", "first strike", "flying", "vigilance", "reach", "menace",
            "intimidate", "deathtouch", "lifelink", "double strike", "defender");
        List<String> redBad = new ArrayList<>();
        int redTotal = 0;
        for (Map.Entry<PaperCard, Integer> e : red.getMain()) {
            CardRules r = e.getKey().getRules();
            String n = e.getKey().getName();
            redTotal += e.getValue();
            if (r.getType().isLand()) {
                if (!n.equals("Mountain")) redBad.add(n);
                continue;
            }
            if (!r.getColor().hasRed() || r.getColor().countColors() != 1) redBad.add(n + " (color)");
            if (r.getType().isCreature()) {
                if (r.getManaCost().getCMC() > 4) redBad.add(n + " (costs more than 4)");
                String text = r.getOracleText().replaceAll("\\([^)]*\\)", "").trim();
                int kws = 0;
                for (String part : text.isEmpty() ? new String[0] : text.split("[,\\n]+")) {
                    String k = part.trim().toLowerCase(Locale.ROOT);
                    if (k.isEmpty()) continue;
                    kws++;
                    if (!keywords.contains(k)) redBad.add(n + " (has \"" + part.trim() + "\")");
                }
                if (kws > 2) redBad.add(n + " (" + kws + " keywords)");
            }
        }
        check("Red deck: 60 cards, red only, creatures cost 4 or less with 0-2 keywords and nothing else " + redBad,
            redTotal == 60 && redBad.isEmpty());
        check("Red deck: Shocks, no Bolts, one Pillage, one Blaze",
            red.getMain().countByName("Shock") == 4 && red.getMain().countByName("Lightning Bolt") == 0
                && red.getMain().countByName("Pillage") == 1 && red.getMain().countByName("Blaze") == 1);

        // 4. Mistakes in a file are reported (field by field), and the file still loads when it can.
        Path tmp = Files.createTempDirectory("enc");
        Path dir = Files.createDirectories(tmp.resolve("lorwyn/easy"));
        Files.writeString(dir.resolve("broken.json"), """
            // comments are fine
            {
              "version": 1, "name": "Broken", "lfe": 20,
              "images": {"icon": "card:No Such Card", "background": "file:data/nope.jpg"},
              "deck": ["4 Island", "2 Notacard", "4 Cursecatcher|XYZ"],
              "start": {"enemy": {"hand": ["Counterspell"], "battlefield": ["token:u_9_9_nothing"]}},
              "barks": [{"when": "onSunday", "text": "hi"}],
              "rewards": {"relics": ["Not A Relic"]},
              "firstPlayer": "me"
            }""");
        Files.createDirectories(tmp.resolve("time spiral/easy"));
        Files.writeString(tmp.resolve("time spiral/easy/x.json"), "{\"name\": \"X\", \"deck\": [\"60 Island\"]}");
        EncounterLibrary badLib = EncounterLibrary.load(tmp, db);
        String all = String.join("\n", badLib.problems());
        System.out.println("    reported:\n      " + String.join("\n      ", badLib.problems()));
        for (String expect : List.of("[lfe]: unknown setting", "[images.icon]", "[images.background]", "Notacard",
                "no printing in set XYZ", "isn't in the deck", "u_9_9_nothing", "onSunday", "Not A Relic",
                "[firstPlayer]", "no spaces")) {
            check("Reported: " + expect, all.contains(expect));
        }
        check("A file with mistakes still loads when it can", badLib.get("lorwyn/easy/broken") != null);

        // 5. Barks: said when the condition becomes true, once.
        Encounter forBarks = enc;
        BarkWatcher w = new BarkWatcher(forBarks.barks());
        String first = w.check(snap(1, 20, 20));
        String again = w.check(snap(2, 20, 20));
        String low = w.check(snap(3, 9, 20));
        String lowAgain = w.check(snap(4, 8, 20));
        w.check(snap(5, 12, 20));
        String lowThird = w.check(snap(6, 7, 20));
        check("Start bark at the first turn, once", first != null && again == null);
        check("Below-half bark when life drops under 10, only once", low != null && low.contains("tide")
            && lowAgain == null && lowThird == null);

        // 6. The match: player first, preset hand from the deck, tokens, Lord on the enemy's turn 2.
        int runs = 4;
        int ok = 0;
        for (int i = 0; i < runs; i++) {
            MatchSetup setup = enc.setup(Presets.encounterPlayer().withController(MatchSetup.Controller.AI));
            StartRules rules = new StartRules(setup);
            List<MatchSnapshot> snaps = Collections.synchronizedList(new ArrayList<>());
            boolean[] lordOnTurn2 = {false};
            MatchRunner r = new MatchRunner(MatchFactory.create(setup, db, decks, null, (y, p) -> false, rules),
                setup.you().name(), setup.enemy().name(), setup.you().look(), setup.enemy().look(), snaps::add);
            r.setNoDelay(true);
            r.setRevealEnemyHand(true);
            r.setBeforeStart(game -> {
                rules.install(game);
                Player enemy = game.getRegisteredPlayers().stream()
                    .filter(p -> p.getName().equals(setup.enemy().name())).findFirst().orElseThrow();
                game.subscribeToEvents(new Object() {
                    int turns;

                    @Subscribe
                    public void on(GameEvent ev) {
                        if (ev instanceof GameEventTurnPhase tp && tp.playerTurn() != null && tp.playerTurn().getId() == enemy.getId()) {
                            if (tp.phase() == PhaseType.UPKEEP) turns++;
                            if (turns == 2 && tp.phase() == PhaseType.MAIN1 && !rules.guaranteedDraws().isEmpty()
                                && !rules.guaranteedDraws().get(0).placedIds().isEmpty()) {
                                int id = rules.guaranteedDraws().get(0).placedIds().get(0);
                                lordOnTurn2[0] = enemy.getCardsIn(ZoneType.Hand).stream().anyMatch(c -> c.getId() == id);
                            }
                        }
                    }
                });
            });
            r.start();
            r.join();
            MatchSnapshot t1 = snaps.stream().filter(s -> s.turn() == 1).findFirst().orElse(null);
            boolean firstOk = t1 != null && t1.activePlayer().equals(setup.you().name());
            PlayerSnapshot foe = t1 == null ? null : t1.enemy();
            List<String> hand = foe == null ? List.of() : sorted(foe.hand().stream().map(CardSnapshot::name).toList());
            boolean handOk = hand.equals(sorted(enc.enemy().start().hand()));
            boolean libOk = foe != null && foe.librarySize() == 54;
            long tokens = foe == null ? 0 : foe.battlefield().stream().filter(c -> c.token() && c.name().contains("Merfolk")).count();
            String result = snaps.get(snaps.size() - 1).result();
            System.out.println("    game " + (i + 1) + ": first " + (t1 == null ? "?" : t1.activePlayer()) + ", enemy hand " + hand
                + ", library " + (foe == null ? "?" : foe.librarySize()) + ", tokens " + tokens + ", Lord turn 2 " + lordOnTurn2[0]
                + ", problems " + rules.problems() + ", " + result);
            if (firstOk && handOk && libOk && tokens == 2 && lordOnTurn2[0] && rules.problems().isEmpty()) {
                ok++;
            }
        }
        check("Every game: player first, preset 6-card hand from the deck (54 left), 2 tokens, Lord on turn 2 ("
            + ok + "/" + runs + ")", ok == runs);

        // 7. Rewards still roll.
        List<String> offer = enc.rewards().rollCards(new Random(1));
        check("Card reward offers 3 different cards " + offer, offer.size() == 3 && offer.stream().distinct().count() == 3);
        finish();
    }

    private static MatchSnapshot snap(int turn, int enemyLife, int start) {
        PlayerSnapshot you = new PlayerSnapshot("You", "", 20, 20, 40, List.of(), List.of(), List.of(), List.of(), List.of(),
            true, false, null, null, List.of());
        PlayerSnapshot foe = new PlayerSnapshot("Foe", "", enemyLife, start, 40, List.of(), List.of(), List.of(), List.of(), List.of(),
            false, false, null, null, List.of());
        return new MatchSnapshot(turn, "You", "Main", you, foe, List.of(), List.of(), null);
    }

    private static List<String> sorted(List<String> l) {
        List<String> c = new ArrayList<>(l);
        Collections.sort(c);
        return c;
    }

    private static void finish() {
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

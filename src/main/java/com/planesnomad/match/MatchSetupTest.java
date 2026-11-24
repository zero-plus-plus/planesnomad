package com.planesnomad.match;

import com.planesnomad.ForgeEngine;
import com.planesnomad.RelicTest;
import com.planesnomad.gui.engine.MatchRunner;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.PlayerLook;
import com.planesnomad.gui.model.RelicSnapshot;
import forge.card.CardDb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Checks match setups: deck library (incl. .dck files), setups reaching the match, abandoning a match. */
public final class MatchSetupTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        CardDb db = ForgeEngine.initialize();

        // Decks: the test decks, plus .dck files from the decks folder.
        Path folder = Files.createTempDirectory("decks");
        System.setProperty("planesnomad.decks", folder.toString());
        DeckLibrary decks = new DeckLibrary(db);
        check("Six built-in test decks", decks.names().stream().filter(n -> n.startsWith("Test: ")).count() == 6);
        DeckLibrary.save(decks.get("Test: Blue tempo"), "My blue deck");
        decks.reload();
        check("A .dck file in the decks folder is listed and loads", decks.names().contains("My blue deck")
            && decks.get("My blue deck") != null
            && decks.get("My blue deck").getMain().countAll() == decks.get("Test: Blue tempo").getMain().countAll());
        check("Unknown deck -> null (reported when starting)", decks.get("No such deck") == null);

        List<String> choices = MatchFactory.commandCardChoices(db);
        check("Relic / effect choices are our own cards " + choices, choices.containsAll(
            List.of(RelicTest.PASSIVE, RelicTest.ACTIVATED, RelicTest.CONSUMABLE, "Undertow Ward")));

        // A custom setup reaches the match: names, life, chosen relics, no enemy effects, looks, a .dck deck.
        MatchSetup base = Presets.testMatch("", false);
        MatchSetup custom = new MatchSetup(
            new MatchSetup.Side("Tester", MatchSetup.Controller.AI, "Test: Red goblins and burn", 30,
                List.of(RelicTest.PASSIVE, RelicTest.CONSUMABLE), new PlayerLook("Custom theme", "Shivan Dragon", "Mountain", List.of())),
            new MatchSetup.Side("Dummy", MatchSetup.Controller.AI, "My blue deck", 7, List.of(), base.enemy().look()));
        List<MatchSnapshot> snaps = Collections.synchronizedList(new ArrayList<>());
        MatchRunner r = new MatchRunner(MatchFactory.create(custom, db, decks, null, (y, p) -> false),
            "Tester", "Dummy", custom.you().look(), custom.enemy().look(), snaps::add);
        r.setNoDelay(true);
        r.start();
        r.join();
        MatchSnapshot early = snaps.stream().filter(s -> s.turn() >= 1).findFirst().orElseThrow();
        check("Names from the setup", early.you().name().equals("Tester") && early.enemy().name().equals("Dummy"));
        check("Starting life from the setup (30 / 7)", early.you().startingLife() == 30 && early.enemy().startingLife() == 7);
        check("Only the chosen relics are in your bar", early.you().relics().stream().map(RelicSnapshot::name).toList()
            .equals(List.of(RelicTest.PASSIVE, RelicTest.CONSUMABLE)));
        check("No enemy effects when none chosen", early.enemy().relics().isEmpty());
        check("Looks from the setup (theme text, portrait)", early.you().subtitle().equals("Custom theme")
            && early.you().portrait() != null && early.you().portrait().name().equals("Shivan Dragon"));
        check("The match finishes", snaps.get(snaps.size() - 1).finished());

        // Abandoning a match (Dev: new setup / restart) ends it at once.
        MatchRunner slow = new MatchRunner(MatchFactory.create(base, db, decks, null, (y, p) -> true),
            "Red AI", "Blue AI", base.you().look(), base.enemy().look(), s -> { });
        slow.start();   // normal pacing: would take minutes
        Thread.sleep(1500);
        long t0 = System.currentTimeMillis();
        slow.stop();
        slow.join();
        check("A stopped match ends quickly (" + (System.currentTimeMillis() - t0) + " ms)", System.currentTimeMillis() - t0 < 10000);

        // Same names on both sides are refused.
        boolean refused = false;
        try {
            MatchFactory.create(new MatchSetup(base.you(), base.you()), db, decks, null, (y, p) -> false);
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        check("Two players with the same name are refused", refused);

        System.out.println(failures == 0 ? "ALL MATCH-SETUP CHECKS PASSED" : failures + " MATCH-SETUP CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(String d, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + d);
        if (!ok) {
            failures++;
        }
    }

    private MatchSetupTest() { }
}

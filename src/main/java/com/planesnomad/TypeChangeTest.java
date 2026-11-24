package com.planesnomad;

import forge.ai.LobbyPlayerAi;
import forge.card.CardDb;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Checks that card types, and effects that add or replace types, work in the engine.
 * Places cards straight onto the battlefield (no AI decisions involved) and checks
 * the resulting types, mana abilities and power/toughness.
 *
 * Run from the repository root:
 *   java -cp "target/classes;<classpath>" com.planesnomad.TypeChangeTest
 * Exits with status 1 if any check fails.
 */
public class TypeChangeTest {

    private static CardDb db;
    private static int failures = 0;

    public static void main(String[] args) {
        db = ForgeEngine.initialize();

        // --- Intrinsic types -------------------------------------------------
        scenario("Basic lands tap for their own color", (game, me) -> {
            expectMana(put(game, me, "Mountain"), "R");
            expectMana(put(game, me, "Island"), "U");
        });

        scenario("Dual land with two basic types taps for both", (game, me) -> {
            expectMana(put(game, me, "Tropical Island"), "G", "U");
        });

        scenario("Creature types are recognized", (game, me) -> {
            Card raider = put(game, me, "Goblin Raider");
            check("Goblin Raider is a Goblin", raider.getType().hasCreatureType("Goblin"));
            Card merfolk = put(game, me, "Coral Merfolk");
            check("Coral Merfolk is a Merfolk", merfolk.getType().hasCreatureType("Merfolk"));
        });

        // --- Effects that ADD types --------------------------------------------
        scenario("Urborg adds Swamp to every land (keeps original types)", (game, me) -> {
            Card mountain = put(game, me, "Mountain");
            Card urborg = put(game, me, "Urborg, Tomb of Yawgmoth");
            expectMana(mountain, "B", "R");
            expectMana(urborg, "B");
            check("Mountain is still a Mountain", mountain.getType().hasSubtype("Mountain"));
            check("Mountain is now also a Swamp", mountain.getType().hasSubtype("Swamp"));
        });

        scenario("Changeling counts as every creature type (tribal lord applies)", (game, me) -> {
            put(game, me, "Goblin King");
            Card changeling = put(game, me, "Woodland Changeling");   // printed 2/2
            check("Woodland Changeling is a Goblin", changeling.getType().hasCreatureType("Goblin"));
            expectPT(changeling, 3, 3);
        });

        scenario("Tribal lord buffs its type only", (game, me) -> {
            put(game, me, "Lord of Atlantis");
            Card merfolk = put(game, me, "Merfolk of the Pearl Trident");   // printed 1/1
            Card goblin = put(game, me, "Goblin Raider");                  // printed 2/2
            expectPT(merfolk, 2, 2);
            expectPT(goblin, 2, 2);
        });

        // --- Effects that REPLACE types ----------------------------------------
        scenario("Blood Moon replaces nonbasic land types with Mountain", (game, me) -> {
            Card trop = put(game, me, "Tropical Island");
            Card island = put(game, me, "Island");
            put(game, me, "Blood Moon");
            expectMana(trop, "R");
            check("Tropical Island lost Forest", !trop.getType().hasSubtype("Forest"));
            check("Tropical Island lost Island", !trop.getType().hasSubtype("Island"));
            expectMana(island, "U");   // basic, unaffected
        });

        // --- Interaction: replace vs add (layer/dependency rules) --------------
        scenario("Blood Moon turns off Urborg (Urborg loses its ability)", (game, me) -> {
            Card island = put(game, me, "Island");
            Card urborg = put(game, me, "Urborg, Tomb of Yawgmoth");
            put(game, me, "Blood Moon");
            expectMana(urborg, "R");
            expectMana(island, "U");   // no longer a Swamp
        });

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL TYPE CHECKS PASSED");
        } else {
            System.out.println(failures + " TYPE CHECK(S) FAILED");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------------

    private interface Body {
        void run(Game game, Player me);
    }

    private static void scenario(String name, Body body) {
        System.out.println("\n== " + name);
        Game game = newGame();
        body.run(game, game.getPlayers().get(0));
    }

    private static Game newGame() {
        Deck deck = new Deck("filler");
        deck.getMain().add(db.getCard("Mountain"), 20);
        RegisteredPlayer a = new RegisteredPlayer(deck);
        RegisteredPlayer b = new RegisteredPlayer(deck);
        a.setPlayer(new LobbyPlayerAi("A", Set.of()));
        b.setPlayer(new LobbyPlayerAi("B", Set.of()));
        Match match = new Match(new GameRules(GameType.Constructed), List.of(a, b), "TypeChangeTest");
        return match.createGame();
    }

    private static Card put(Game game, Player owner, String cardName) {
        var paper = db.getCard(cardName);
        if (paper == null) {
            throw new IllegalArgumentException("Unknown card: " + cardName);
        }
        Card card = Card.fromPaperCard(paper, owner);
        Card onField = game.getAction().moveToPlay(card, owner, null, null);
        game.getAction().checkStaticAbilities();
        return onField;
    }

    private static void expectMana(Card card, String... colors) {
        Set<String> expected = new TreeSet<>(List.of(colors));
        Set<String> actual = new TreeSet<>();
        for (SpellAbility sa : card.getManaAbilities()) {
            for (String c : sa.getManaPart().getOrigProduced().split(" ")) {
                actual.add(c);
            }
        }
        check(card.getName() + " taps for " + expected + " (got " + actual + ")", expected.equals(actual));
    }

    private static void expectPT(Card card, int power, int toughness) {
        String got = card.getNetPower() + "/" + card.getNetToughness();
        check(card.getName() + " is " + power + "/" + toughness + " (got " + got + ")",
            card.getNetPower() == power && card.getNetToughness() == toughness);
    }

    private static void check(String description, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + description);
        if (!ok) {
            failures++;
        }
    }
}

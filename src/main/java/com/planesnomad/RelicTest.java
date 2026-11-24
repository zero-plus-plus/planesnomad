package com.planesnomad;

import forge.ai.LobbyPlayerAi;
import forge.card.CardDb;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameLogEntry;
import forge.game.GameLogEntryType;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proof of concept: relics and consumables as command-zone cards (the back end only;
 * the UI is meant to show them as normal relics, never as cards).
 *
 * Example cards (original, in forge-engine/resources/custom/cards, set "PNO"):
 *   Ember Charm         passive relic    - creatures you control get +1/+0
 *   Wayfarer's Compass  triggered relic  - at the beginning of your upkeep, scry 1
 *   Tinker's Lens       activated relic  - {2}: draw a card, once each turn
 *   Flask of Embers     consumable       - free, instant speed: 3 damage to any target, then exiles itself
 *
 * Part A places cards directly (no AI decisions). Part B plays AI vs AI matches where
 * Red starts with all four in its command zone via RegisteredPlayer.addExtraCardsInCommandZone,
 * the same hook Forge's Adventure mode uses for its items.
 *
 * Run from the repository root. Exits with status 1 if any check fails.
 */
public class RelicTest {

    public static final String PASSIVE = "Ember Charm";
    public static final String TRIGGERED = "Wayfarer's Compass";
    public static final String ACTIVATED = "Tinker's Lens";
    public static final String CONSUMABLE = "Flask of Embers";

    private static final int MATCHES = 3;

    private static CardDb db;
    private static int failures = 0;

    public static void main(String[] args) {
        db = ForgeEngine.initialize();

        System.out.println("\n=== Part A: direct checks ===");
        partA();

        System.out.println("\n=== Part B: AI vs AI matches, Red starts with all four ===");
        for (int i = 1; i <= MATCHES; i++) {
            partB(i);
        }

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL RELIC CHECKS PASSED");
        } else {
            System.out.println(failures + " RELIC CHECK(S) FAILED");
            System.exit(1);
        }
    }

    // ---------------------------------------------------------------- Part A

    private static void partA() {
        Game game = newMatch(false).createGame();
        Player me = game.getPlayers().get(0);

        Card raider = put(game, me, "Goblin Raider", ZoneType.Battlefield);   // printed 2/2
        expectPT("Goblin Raider without relics", raider, 2, 2);

        Card charm = put(game, me, PASSIVE, ZoneType.Command);
        expectPT("Goblin Raider with " + PASSIVE + " in the command zone", raider, 3, 2);
        check(PASSIVE + " is in the command zone, not on the battlefield",
            me.getCardsIn(ZoneType.Command).contains(charm)
                && !me.getCardsIn(ZoneType.Battlefield).contains(charm));

        // An enemy "destroying" a relic: remove it from the command zone.
        game.getAction().moveTo(ZoneType.Exile, charm, null, null);
        game.getAction().checkStaticAbilities();
        expectPT("Goblin Raider after the relic is removed (enemy disables it)", raider, 2, 2);

        Card lens = put(game, me, ACTIVATED, ZoneType.Command);
        SpellAbility lensAbility = onlyAbility(lens);
        check(ACTIVATED + " ability is activated from the command zone",
            lensAbility != null && "Command".equals(lensAbility.getParam("ActivationZone")));
        check(ACTIVATED + " ability is limited to once each turn",
            lensAbility != null && "1".equals(lensAbility.getParam("ActivationLimit")));

        Card flask = put(game, me, CONSUMABLE, ZoneType.Command);
        SpellAbility flaskAbility = onlyAbility(flask);
        check(CONSUMABLE + " costs nothing",
            flaskAbility != null && flaskAbility.getPayCosts().getTotalMana().isZero()
                && !flaskAbility.getPayCosts().hasTapCost());
        check(CONSUMABLE + " exiles itself after use",
            flaskAbility != null && flaskAbility.getSubAbility() != null
                && "ChangeZone".equals(flaskAbility.getSubAbility().getApi().toString()));
    }

    // ---------------------------------------------------------------- Part B

    private static final Pattern TURN = Pattern.compile("^Turn (\\d+) \\((.+)\\)");

    private static void partB(int index) {
        Match match = newMatch(true);
        Game game = match.createGame();
        match.startGame(game);

        Player red = player(game, "Red AI");
        List<GameLogEntry> log = new ArrayList<>(game.getGameLog().getAllEntries());
        Collections.reverse(log);   // the engine stores newest first

        int redTurns = 0;
        int compassTriggers = 0;
        int lensUses = 0;
        int flaskUses = 0;
        int boostedHits = 0;
        int unboostedHits = 0;
        int maxLensPerTurn = 0;
        String currentTurn = "";
        Map<String, Integer> lensPerTurn = new HashMap<>();
        Pattern goblinHit = Pattern.compile("^Goblin Raider \\(\\d+\\) deals (\\d+) combat damage");

        for (GameLogEntry entry : log) {
            String msg = entry.message();
            Matcher turn = TURN.matcher(msg);
            if (entry.type() == GameLogEntryType.TURN && turn.find()) {
                currentTurn = turn.group(1);
                if (turn.group(2).equals("Red AI")) {
                    redTurns++;
                }
            }
            if (msg.equals("Red AI triggered " + TRIGGERED)) {
                compassTriggers++;
            }
            if (msg.startsWith("Red AI activated " + ACTIVATED)) {
                lensUses++;
                int n = lensPerTurn.merge(currentTurn, 1, Integer::sum);
                maxLensPerTurn = Math.max(maxLensPerTurn, n);
            }
            if (msg.startsWith("Red AI activated " + CONSUMABLE)) {
                flaskUses++;
            }
            Matcher hit = goblinHit.matcher(msg);
            if (hit.find()) {
                if (hit.group(1).equals("3")) {
                    boostedHits++;
                } else {
                    unboostedHits++;
                }
            }
        }

        boolean flaskInCommand = containsNamed(red.getCardsIn(ZoneType.Command), CONSUMABLE);
        boolean flaskInExile = containsNamed(red.getCardsIn(ZoneType.Exile), CONSUMABLE);
        String winner = game.getOutcome().getWinningPlayer() == null
            ? "none" : game.getOutcome().getWinningPlayer().getPlayer().getName();

        System.out.printf("%nMatch %d: %d turns, winner %s | compass %d/%d upkeeps | lens %d uses (max %d/turn) "
                + "| flask %d use(s) | goblin raider hits: %d for 3, %d other%n",
            index, game.getOutcome().getLastTurnNumber(), winner, compassTriggers, redTurns,
            lensUses, maxLensPerTurn, flaskUses, boostedHits, unboostedHits);

        check("Relics stay in Red's command zone",
            containsNamed(red.getCardsIn(ZoneType.Command), PASSIVE)
                && containsNamed(red.getCardsIn(ZoneType.Command), TRIGGERED)
                && containsNamed(red.getCardsIn(ZoneType.Command), ACTIVATED));
        check("Relics never reach the battlefield",
            !containsNamed(red.getCardsIn(ZoneType.Battlefield), PASSIVE)
                && !containsNamed(red.getCardsIn(ZoneType.Battlefield), ACTIVATED));
        check(TRIGGERED + " triggered on every Red upkeep (" + compassTriggers + "/" + redTurns + ")",
            compassTriggers == redTurns && redTurns > 0);
        check(ACTIVATED + " used at most once per turn", maxLensPerTurn <= 1);
        check(CONSUMABLE + " used at most once", flaskUses <= 1);
        check(CONSUMABLE + " location matches use (used -> exile, unused -> command zone)",
            flaskUses == 1 ? (flaskInExile && !flaskInCommand) : (flaskInCommand && !flaskInExile));
        check(PASSIVE + ": unblocked Goblin Raiders hit for 3 (2 + 1)", unboostedHits == 0);

        // What a run layer would read back after the fight.
        System.out.println("  Run-layer handback: consumable " + CONSUMABLE + " "
            + (flaskInExile ? "USED (remove from inventory)" : "unused (keep)"));
    }

    // ---------------------------------------------------------------- helpers

    private static Match newMatch(boolean withRelics) {
        Deck redDeck = PlanesnomadGame.buildRedDeck(db);
        Deck blueDeck = PlanesnomadGame.buildBlueDeck(db);
        RegisteredPlayer red = new RegisteredPlayer(redDeck);
        RegisteredPlayer blue = new RegisteredPlayer(blueDeck);
        red.setPlayer(PlanesnomadGame.createAi("Red AI"));
        blue.setPlayer(PlanesnomadGame.createAi("Blue AI"));
        if (withRelics) {
            red.addExtraCardsInCommandZone(List.of(
                card(PASSIVE), card(TRIGGERED), card(ACTIVATED), card(CONSUMABLE)));
        }
        return new Match(new GameRules(GameType.Constructed), List.of(red, blue), "RelicTest");
    }

    private static PaperCard card(String name) {
        PaperCard card = db.getCard(name);
        if (card == null) {
            throw new IllegalStateException("Missing card script: " + name);
        }
        return card;
    }

    private static Card put(Game game, Player owner, String name, ZoneType zone) {
        Card card = Card.fromPaperCard(card(name), owner);
        Card placed = zone == ZoneType.Battlefield
            ? game.getAction().moveToPlay(card, owner, null, null)
            : game.getAction().moveTo(zone, card, null, null);
        game.getAction().checkStaticAbilities();
        return placed;
    }

    /** The card's single activated ability (ignores the "cast this card" spell every artifact has). */
    private static SpellAbility onlyAbility(Card card) {
        List<SpellAbility> abilities = new ArrayList<>();
        for (SpellAbility sa : card.getSpellAbilities()) {
            if (sa.isActivatedAbility()) {
                abilities.add(sa);
            }
        }
        return abilities.size() == 1 ? abilities.get(0) : null;
    }

    private static Player player(Game game, String name) {
        for (Player p : game.getRegisteredPlayers()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        throw new IllegalStateException("No player " + name);
    }

    private static boolean containsNamed(Iterable<Card> cards, String name) {
        for (Card c : cards) {
            if (c.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static void expectPT(String what, Card card, int power, int toughness) {
        String got = card.getNetPower() + "/" + card.getNetToughness();
        check(what + " is " + power + "/" + toughness + " (got " + got + ")",
            card.getNetPower() == power && card.getNetToughness() == toughness);
    }

    private static void check(String description, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + description);
        if (!ok) {
            failures++;
        }
    }
}

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
import forge.game.player.RegisteredPlayer;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class PlanesnomadGame {

    public static void main(String[] args) {
        CardDb database = ForgeEngine.initialize();

        var mountain = database.getCard("Mountain");
        System.out.println("Mountain rules: "
            + mountain.getRules().getMainPart().getOracleText());

        var island = database.getCard("Island");
        System.out.println("Island rules: "
            + island.getRules().getMainPart().getOracleText());

        Deck redDeck = buildRedDeck(database);
        Deck blueDeck = buildBlueDeck(database);

        System.out.println("Red deck: " + redDeck.getMain().countAll() + " cards");
        System.out.println("Blue deck: " + blueDeck.getMain().countAll() + " cards");

        RegisteredPlayer human = new RegisteredPlayer(redDeck);
        RegisteredPlayer opponent = new RegisteredPlayer(blueDeck);

        // Standard Forge AI. AIOption.USE_HYBRID_SIMULATION (Forge's experimental
        // simulation AI) repeatedly tried to cast spells it could not pay for.
        human.setPlayer(createAi("Red AI"));
        opponent.setPlayer(createAi("Blue AI"));

        GameRules rules = new GameRules(GameType.Constructed);
        Match match = new Match(rules, List.of(human, opponent), "Planesnomad Test");

        System.out.println("Creating Forge game...");
        Game game = match.createGame();

        System.out.println("Starting AI vs AI match...");
        match.startGame(game);

        printGameLog(game);

        System.out.println("\n=== PLAYERS STILL IN GAME ===");

        for (forge.game.player.Player player : game.getPlayers()) {
            System.out.println(player.getName());

            for (forge.game.card.Card card :
                    player.getCardsIn(forge.game.zone.ZoneType.Battlefield)) {
                System.out.println("  " + card.getName()
                + " | Mana abilities: " + card.getManaAbilities().size()
                + " | Tapped: " + card.isTapped());
            }
        }

        System.out.println("\n=== ELIMINATED PLAYERS ===");

        for (forge.game.player.Player player : game.getLostPlayers()) {
            System.out.println(player.getName());

            for (forge.game.card.Card card :
                    player.getCardsIn(forge.game.zone.ZoneType.Battlefield)) {
                System.out.println("  " + card.getName()
                + " | Mana abilities: " + card.getManaAbilities().size()
                + " | Tapped: " + card.isTapped());
            }
        }

        System.out.println("Game stage: " + game.getPhaseHandler().getPhase());

        System.out.println("Match finished.");
        var outcome = game.getOutcome();

        System.out.println("End reason: " + outcome.getWinCondition());
        System.out.println("Is draw: " + outcome.isDraw());
        System.out.println("Winning player: " +
            (outcome.getWinningPlayer() == null
                ? "None"
                : outcome.getWinningPlayer().getPlayer().getName()));
        System.out.println("Turns played: " + outcome.getLastTurnNumber());

        System.out.println("Player outcomes:");
        for (String result : outcome.getOutcomeStrings()) {
            System.out.println("  " + result);
        }
    }

    /** Prints the engine's game log oldest-first: turns, spells, lands, combat, damage, life. */
    private static void printGameLog(Game game) {
        Set<GameLogEntryType> shown = EnumSet.of(
            GameLogEntryType.TURN, GameLogEntryType.LAND, GameLogEntryType.STACK_ADD,
            GameLogEntryType.STACK_RESOLVE, GameLogEntryType.COMBAT, GameLogEntryType.DAMAGE,
            GameLogEntryType.LIFE, GameLogEntryType.GAME_OUTCOME);
        List<GameLogEntry> entries = new ArrayList<>(game.getGameLog().getLogEntriesForTypes(shown));
        Collections.reverse(entries); // the engine stores newest first

        System.out.println("\n=== GAME LOG ===");
        for (GameLogEntry entry : entries) {
            String indent = entry.type() == GameLogEntryType.TURN ? "" : "  ";
            System.out.println(indent + entry.message().replace("\n", "\n" + indent));
        }
    }

    /** Test deck: red burn and goblins. */
    static Deck buildRedDeck(CardDb database) {
        Deck deck = new Deck("Planesnomad - Red");
        addCards(deck, database, "Shock", "10E", "232", 4);
        addCards(deck, database, "Lightning Bolt", "M10", "146", 4);
        addCards(deck, database, "Goblin Raider", "10E", "207", 4);
        addCards(deck, database, "Mons's Goblin Raiders", "10E", "211", 4);
        addBasicLands(deck, database, "Mountain", "M10", 12);
        return deck;
    }

    /** Test deck: blue counters, bounce and merfolk. */
    static Deck buildBlueDeck(CardDb database) {
        Deck deck = new Deck("Planesnomad - Blue");
        addCards(deck, database, "Counterspell", "7ED", "68", 4);
        addCards(deck, database, "Unsummon", "10E", "117", 4);
        addCards(deck, database, "Merfolk of the Pearl Trident", "M13", "57", 4);
        addCards(deck, database, "Coral Merfolk", "10E", "79", 4);
        addBasicLands(deck, database, "Island", "M10", 12);
        return deck;
    }

    static LobbyPlayerAi createAi(String name) {
        LobbyPlayerAi ai = new LobbyPlayerAi(name, Set.of());
        ai.setAiProfile(ForgeEngine.DEFAULT_AI_PROFILE);
        return ai;
    }

    private static void addCards(
        Deck deck, CardDb database,
        String name, String set, String collectorNumber, int quantity
    ) {
        PaperCard card = database.getCard(name, set, collectorNumber);

        if (card == null) {
            throw new IllegalArgumentException(
                "Missing exact printing: " + name + " (" + set + ", " + collectorNumber + ")"
            );
        }

        deck.getMain().add(card, quantity);
        System.out.println("Added " + quantity + "x " + name
            + " [" + set + " #" + collectorNumber + "]");
    }

    private static void addBasicLands(
        Deck deck, CardDb database,
        String name, String set, int quantity
    ) {
        PaperCard card = database.getCard(name, set);

        if (card == null) {
            throw new IllegalArgumentException(
                "Missing basic land: " + name + " (" + set + ")"
            );
        }

    deck.getMain().add(card, quantity);
    System.out.println("Added " + quantity + "x " + name);
}
}
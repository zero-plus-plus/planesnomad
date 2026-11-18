package com.planesnomad;

import forge.CardStorageReader;
import forge.StaticData;
import forge.ImageKeys;
import forge.ai.AIOption;
import forge.ai.LobbyPlayerAi;
import forge.card.CardDb;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.player.RegisteredPlayer;
import forge.item.PaperCard;
import forge.util.Localizer;
import forge.util.Lang;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class PlanesnomadGame {

    public static void main(String[] args) {
        Path resources = Path.of("forge-engine", "resources");

        Localizer localizer = Localizer.getInstance();
        localizer.setEnglish(true);
        localizer.initialize("en-US", resources.resolve("languages").toString());
        Lang.createInstance("en-US");

        ImageKeys.initializeDirs(
            resources.resolve("card-pictures").toString(),
            new HashMap<>(),
            resources.resolve("token-pictures").toString(),
            resources.resolve("icons").toString(),
            resources.resolve("boosters").toString(),
            resources.resolve("fatpacks").toString(),
            resources.resolve("boosterboxes").toString(),
            resources.resolve("precons").toString(),
            resources.resolve("tournamentpacks").toString()
        );

        System.out.println("Loading Forge card database...");

        CardStorageReader cards = new CardStorageReader(
            resources.resolve("cardsfolder").toString(), null, false);
        CardStorageReader tokens = new CardStorageReader(
            resources.resolve("tokenscripts").toString(), null, false);
        CardStorageReader customCards = new CardStorageReader(
            resources.resolve("custom/cards").toString(), null, false);
        CardStorageReader customTokens = new CardStorageReader(
            resources.resolve("custom/tokens").toString(), null, false);

        new StaticData(
            cards, tokens, customCards, customTokens,
            resources.resolve("editions").toString(),
            resources.resolve("custom/editions").toString(),
            resources.resolve("blockdata").toString(),
            resources.resolve("setlookup").toString(),
            "ORIGINAL_ART_ALL_EDITIONS",
            false, true, false, false, null
        );

        CardDb database = StaticData.instance().getCommonCards();
        System.out.println("Card database loaded.");

        var mountain = database.getCard("Mountain");
        System.out.println("Mountain rules: "
            + mountain.getRules().getMainPart().getOracleText());

        var island = database.getCard("Island");
        System.out.println("Island rules: "
            + island.getRules().getMainPart().getOracleText());

        Deck redDeck = new Deck("Planesnomad - Red");
        Deck blueDeck = new Deck("Planesnomad - Blue");

        addCards(redDeck, database, "Shock", "10E", "232", 4);
        addCards(redDeck, database, "Lightning Bolt", "M10", "146", 4);
        addCards(redDeck, database, "Goblin Raider", "10E", "207", 4);
        addCards(redDeck, database, "Mons's Goblin Raiders", "10E", "211", 4);
        addBasicLands(redDeck, database, "Mountain", "M10", 12);


        addCards(blueDeck, database, "Counterspell", "7ED", "68", 4);
        addCards(blueDeck, database, "Unsummon", "10E", "117", 4);
        addCards(blueDeck, database, "Merfolk of the Pearl Trident", "M13", "57", 4);
        addCards(blueDeck, database, "Coral Merfolk", "10E", "79", 4);
        addBasicLands(blueDeck, database, "Island", "M10", 12);

        System.out.println("Red deck: " + redDeck.getMain().countAll() + " cards");
        System.out.println("Blue deck: " + blueDeck.getMain().countAll() + " cards");

        RegisteredPlayer human = new RegisteredPlayer(redDeck);
        RegisteredPlayer opponent = new RegisteredPlayer(blueDeck);

        human.setPlayer(new LobbyPlayerAi(
            "Red AI", Set.of(AIOption.USE_HYBRID_SIMULATION)));
        opponent.setPlayer(new LobbyPlayerAi(
            "Blue AI", Set.of(AIOption.USE_HYBRID_SIMULATION)));

        GameRules rules = new GameRules(GameType.Constructed);
        Match match = new Match(rules, List.of(human, opponent), "Planesnomad Test");

        System.out.println("Creating Forge game...");
        Game game = match.createGame();

        System.out.println("Starting AI vs AI match...");
        match.startGame(game);

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
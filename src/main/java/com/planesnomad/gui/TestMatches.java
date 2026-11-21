package com.planesnomad.gui;

import com.planesnomad.PlanesnomadGame;
import com.planesnomad.RelicTest;
import forge.card.CardDb;
import forge.deck.Deck;
import forge.item.PaperCard;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.player.RegisteredPlayer;

import java.util.List;

/** Test matches for the viewer: the same Red and Blue decks as PlanesnomadGame. */
public final class TestMatches {

    public static final String YOU = "Red AI";
    public static final String ENEMY = "Blue AI";

    private TestMatches() { }

    /**
     * Same relics, but both decks use Unlimited Edition (2ED) printings, matching the test
     * pictures. Swamps have no test picture on purpose (they show the text frame).
     */
    public static Match unlimitedWithRelics(CardDb db) {
        Deck you = new Deck("Unlimited - Black/Red");
        add(you, db, "Black Knight", 4, 1);
        add(you, db, "Terror", 3, 1);
        add(you, db, "Dark Ritual", 3, 1);
        add(you, db, "Unholy Strength", 2, 1);
        add(you, db, "Shivan Dragon", 2, 1);
        add(you, db, "Animate Dead", 2, 1);
        add(you, db, "Obsianus Golem", 2, 1);
        add(you, db, "Bad Moon", 2, 1);
        add(you, db, "Swamp", 8, 1);
        add(you, db, "Mountain", 4, 1);
        add(you, db, "Mountain", 4, 2);

        Deck enemy = new Deck("Unlimited - Blue/Green");
        add(enemy, db, "Lord of Atlantis", 3, 1);
        add(enemy, db, "Merfolk of the Pearl Trident", 4, 1);
        add(enemy, db, "Scryb Sprites", 4, 1);
        add(enemy, db, "Invisibility", 2, 1);
        add(enemy, db, "Natural Selection", 2, 1);
        add(enemy, db, "Island", 8, 3);
        add(enemy, db, "Forest", 4, 2);
        add(enemy, db, "Forest", 4, 3);

        RegisteredPlayer red = new RegisteredPlayer(you);
        RegisteredPlayer blue = new RegisteredPlayer(enemy);
        red.setPlayer(PlanesnomadGame.createAi(YOU));
        blue.setPlayer(PlanesnomadGame.createAi(ENEMY));
        red.addExtraCardsInCommandZone(List.of(
            db.getCard(RelicTest.PASSIVE), db.getCard(RelicTest.TRIGGERED),
            db.getCard(RelicTest.ACTIVATED), db.getCard(RelicTest.CONSUMABLE)));
        return new Match(new GameRules(GameType.Constructed), List.of(red, blue), "Planesnomad viewer (Unlimited)");
    }

    /**
     * Decks mixing four frame eras, built from the printings in the test picture folders:
     * Torment (old frame), Shadowmoor and Future Sight (2003-2014 frame), Khans of Tarkir
     * (current frame). Swamps have no test picture on purpose (they show the text frame).
     */
    public static Match mixedErasWithRelics(CardDb db) {
        Deck you = new Deck("Mixed eras - Red/Green");
        add(you, db, "Monastery Swiftspear", "KTK", 3, 1);
        add(you, db, "Heir of the Wilds", "KTK", 3, 1);
        add(you, db, "Hooded Hydra", "KTK", 1, 1);
        add(you, db, "Meandering Towershell", "KTK", 1, 1);
        add(you, db, "Boartusk Liege", "SHM", 2, 1);
        add(you, db, "Boggart Ram-Gang", "SHM", 3, 1);
        add(you, db, "Tattermunge Maniac", "SHM", 3, 1);
        add(you, db, "Bogardan Lancer", "FUT", 3, 1);
        add(you, db, "Baru, Fist of Krosa", "FUT", 1, 1);
        add(you, db, "Grim Lavamancer", "TOR", 2, 1);
        add(you, db, "Rugged Highlands", "KTK", 4, 1);
        add(you, db, "Forest", "KTK", 4, 2);
        add(you, db, "Forest", "SHM", 3, 1);
        add(you, db, "Mountain", "SHM", 7, 1);

        Deck enemy = new Deck("Mixed eras - Blue/Black");
        add(enemy, db, "Cursecatcher", "SHM", 3, 1);
        add(enemy, db, "Hollowsage", "SHM", 2, 1);
        add(enemy, db, "Sygg, River Cutthroat", "SHM", 1, 1);
        add(enemy, db, "Faerie Swarm", "SHM", 1, 1);
        add(enemy, db, "Cloudseeder", "FUT", 2, 1);
        add(enemy, db, "Augur of Skulls", "FUT", 2, 1);
        add(enemy, db, "Whip-Spine Drake", "FUT", 2, 1);
        add(enemy, db, "Carrion Rats", "TOR", 2, 1);
        add(enemy, db, "Mesmeric Fiend", "TOR", 2, 1);
        add(enemy, db, "Chainer's Edict", "TOR", 3, 1);
        add(enemy, db, "Faceless Butcher", "TOR", 2, 1);
        add(enemy, db, "Dismal Backwater", "KTK", 4, 1);
        add(enemy, db, "Island", "KTK", 7, 1);
        add(enemy, db, "Swamp", "M10", 7, 1);

        RegisteredPlayer red = new RegisteredPlayer(you);
        RegisteredPlayer blue = new RegisteredPlayer(enemy);
        red.setPlayer(PlanesnomadGame.createAi(YOU));
        blue.setPlayer(PlanesnomadGame.createAi(ENEMY));
        red.addExtraCardsInCommandZone(List.of(
            db.getCard(RelicTest.PASSIVE), db.getCard(RelicTest.TRIGGERED),
            db.getCard(RelicTest.ACTIVATED), db.getCard(RelicTest.CONSUMABLE)));
        return new Match(new GameRules(GameType.Constructed), List.of(red, blue), "Planesnomad viewer (mixed eras)");
    }

    /** Adds an exact printing (art index matters for basic lands). */
    private static void add(Deck deck, CardDb db, String name, String set, int count, int artIndex) {
        PaperCard card = db.getCard(name, set, artIndex);
        if (card == null) {
            throw new IllegalArgumentException("No printing: " + name + " (" + set + ", art " + artIndex + ")");
        }
        deck.getMain().add(card, count);
    }

    /** Adds an exact Unlimited Edition printing (art index matters for basic lands). */
    private static void add(Deck deck, CardDb db, String name, int count, int artIndex) {
        PaperCard card = db.getCard(name, "2ED", artIndex);
        if (card == null) {
            throw new IllegalArgumentException("No Unlimited printing: " + name + " art " + artIndex);
        }
        deck.getMain().add(card, count);
    }

    /** Red (bottom, "you") starts with the four example relics/consumables. */
    public static Match redVsBlueWithRelics(CardDb db) {
        RegisteredPlayer red = new RegisteredPlayer(PlanesnomadGame.buildRedDeck(db));
        RegisteredPlayer blue = new RegisteredPlayer(PlanesnomadGame.buildBlueDeck(db));
        red.setPlayer(PlanesnomadGame.createAi(YOU));
        blue.setPlayer(PlanesnomadGame.createAi(ENEMY));
        red.addExtraCardsInCommandZone(List.of(
            db.getCard(RelicTest.PASSIVE), db.getCard(RelicTest.TRIGGERED),
            db.getCard(RelicTest.ACTIVATED), db.getCard(RelicTest.CONSUMABLE)));
        return new Match(new GameRules(GameType.Constructed), List.of(red, blue), "Planesnomad viewer");
    }
}

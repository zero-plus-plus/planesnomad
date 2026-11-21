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

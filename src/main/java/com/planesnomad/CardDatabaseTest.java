package com.planesnomad;

import forge.card.CardDb;
import forge.item.PaperCard;

public class CardDatabaseTest {

    public static void main(String[] args) {

        CardDb database = ForgeEngine.initialize();

        System.out.println("Unique cards: " + database.getUniqueCards().size());
        System.out.println("Available printings: " + database.getAllCards().size());

        PaperCard shock = database.getCard("Shock", "10E");

        System.out.println("Shock 10E found: " + (shock != null));

        if (shock != null) {
            System.out.println("Set: " + shock.getEdition());
            System.out.println("Collector number: " + shock.getCollectorNumber());
            System.out.println("Artist: " + shock.getArtist());
        }

        System.out.println("Shock found: " + (shock != null));

        System.out.println("Planesnomad card database test complete.");
    }
}
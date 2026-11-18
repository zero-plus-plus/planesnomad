package com.planesnomad;

import forge.CardStorageReader;
import forge.StaticData;
import forge.card.CardDb;
import forge.item.PaperCard;
import forge.util.Localizer;
import forge.util.Lang;
import forge.ImageKeys;
import java.util.HashMap;

import java.nio.file.Path;

public class CardDatabaseTest {

    public static void main(String[] args) {

        Path resources = Path.of("forge-engine", "resources");

        Localizer localizer = Localizer.getInstance();
        localizer.setEnglish(true);
        localizer.initialize(
                "en-US",
                resources.resolve("languages").toString()
        );

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

        StaticData data = new StaticData(
                cards,
                tokens,
                customCards,
                customTokens,
                resources.resolve("editions").toString(),
                resources.resolve("custom/editions").toString(),
                resources.resolve("blockdata").toString(),
                resources.resolve("setlookup").toString(),
                "ORIGINAL_ART_ALL_EDITIONS",
                false,
                true,
                false,
                false,
                null
        );

        CardDb database = data.getCommonCards();

        System.out.println("Card database loaded!");
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
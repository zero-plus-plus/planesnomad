package com.planesnomad;

import forge.CardStorageReader;
import forge.ImageKeys;
import forge.StaticData;
import forge.ai.AiProfileUtil;
import forge.card.CardDb;
import forge.card.CardType;
import forge.game.card.CardUtil;
import forge.util.FileSection;
import forge.util.FileUtil;
import forge.util.Lang;
import forge.util.Localizer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Starts the extracted Forge engine the same way Forge's own app (FModel.initialize)
 * does for the parts the engine needs, without Forge's GUI layer.
 *
 * Order matters: type lists must be loaded BEFORE card scripts are read. Without them
 * the engine does not recognise subtypes such as "Mountain" or "Goblin" and strips them,
 * which leaves basic lands with no mana abilities and breaks every type-based effect
 * (Urborg, Blood Moon, Changeling, tribal lords, landwalk, ...).
 */
public final class ForgeEngine {

    /** Default AI profile name, matching resources/ai/Default.ai. */
    public static final String DEFAULT_AI_PROFILE = "Default";

    private static final Path RESOURCES = Path.of("forge-engine", "resources");

    private static CardDb cards;

    private ForgeEngine() { }

    public static Path resources() {
        return RESOURCES;
    }

    /** Initialise the engine once and return the card database. Safe to call repeatedly. */
    public static synchronized CardDb initialize() {
        if (cards != null) {
            return cards;
        }

        Localizer localizer = Localizer.getInstance();
        localizer.setEnglish(true);
        localizer.initialize("en-US", RESOURCES.resolve("languages").toString());
        Lang.createInstance("en-US");

        // Empty folders are not stored in git, so create the ones the engine expects.
        for (String dir : List.of("custom/cards", "custom/tokens", "custom/editions",
                "card-pictures", "token-pictures")) {
            createDirectory(RESOURCES.resolve(dir));
        }

        ImageKeys.initializeDirs(
            RESOURCES.resolve("card-pictures").toString(),
            new HashMap<>(),
            RESOURCES.resolve("token-pictures").toString(),
            RESOURCES.resolve("icons").toString(),
            RESOURCES.resolve("boosters").toString(),
            RESOURCES.resolve("fatpacks").toString(),
            RESOURCES.resolve("boosterboxes").toString(),
            RESOURCES.resolve("precons").toString(),
            RESOURCES.resolve("tournamentpacks").toString()
        );

        // Must happen before the card database is built (see class comment).
        loadTypeLists();
        loadNonStackingKeywords();

        System.out.println("Loading Forge card database...");
        CardStorageReader cardReader = new CardStorageReader(
            RESOURCES.resolve("cardsfolder").toString(), null, false);
        CardStorageReader tokenReader = new CardStorageReader(
            RESOURCES.resolve("tokenscripts").toString(), null, false);
        CardStorageReader customCardReader = new CardStorageReader(
            RESOURCES.resolve("custom/cards").toString(), null, false);
        CardStorageReader customTokenReader = new CardStorageReader(
            RESOURCES.resolve("custom/tokens").toString(), null, false);

        new StaticData(
            cardReader, tokenReader, customCardReader, customTokenReader,
            RESOURCES.resolve("editions").toString(),
            RESOURCES.resolve("custom/editions").toString(),
            RESOURCES.resolve("blockdata").toString(),
            RESOURCES.resolve("setlookup").toString(),
            "ORIGINAL_ART_ALL_EDITIONS",
            false, true, false, false, null
        );

        AiProfileUtil.loadAllProfiles(RESOURCES.resolve("ai").toString());

        cards = StaticData.instance().getCommonCards();
        System.out.println("Card database loaded.");
        return cards;
    }

    /** Mirrors FModel.loadDynamicGamedata(): fills CardType.Constant from TypeLists.txt. */
    private static void loadTypeLists() {
        if (CardType.Constant.LOADED.isSet()) {
            return;
        }
        Path file = requireFile(RESOURCES.resolve("lists/TypeLists.txt"));
        Map<String, List<String>> sections = FileSection.parseSections(FileUtil.readFile(file.toString()));
        for (Map.Entry<String, List<String>> section : sections.entrySet()) {
            CardType.Helper.parseTypes(section.getKey(), section.getValue());
        }
        CardType.Constant.LOADED.set();
    }

    /** Mirrors FModel.loadDynamicGamedata(): keywords that don't stack (e.g. multiple Flying). */
    private static void loadNonStackingKeywords() {
        if (!CardUtil.NON_STACKING_LIST.isEmpty()) {
            return;
        }
        Path file = requireFile(RESOURCES.resolve("lists/NonStackingKWList.txt"));
        for (String line : FileUtil.readFile(file.toString())) {
            if (line.length() > 1) {
                CardUtil.NON_STACKING_LIST.add(line);
            }
        }
    }

    private static Path requireFile(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Missing Forge data file: " + file.toAbsolutePath()
                + " (run from the repository root)");
        }
        return file;
    }

    private static void createDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + dir, e);
        }
    }
}

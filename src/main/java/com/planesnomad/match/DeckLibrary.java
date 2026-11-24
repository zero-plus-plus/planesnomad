package com.planesnomad.match;

import com.planesnomad.gui.TestMatches;
import forge.card.CardDb;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The decks a match can use, by name: the built-in test decks, and Forge deck files (.dck) in
 * the {@code decks/} folder (or {@code -Dplanesnomad.decks=<folder>}). Later the player's deck
 * comes from the run and the enemy's from the encounter.
 */
public final class DeckLibrary {

    private final CardDb db;
    private final Map<String, Supplier<Deck>> decks = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    public DeckLibrary(CardDb db) {
        this.db = db;
        reload();
    }

    public static Path folder() {
        String p = System.getProperty("planesnomad.decks");
        return Paths.get(p != null && !p.isBlank() ? p : "decks");
    }

    /** Reads the decks again (e.g. after adding .dck files to the folder). */
    public void reload() {
        decks.clear();
        problems.clear();
        // Built-in test decks (the same as the test matches).
        decks.put("Test: Red goblins and burn", () -> testDeck("", 0));
        decks.put("Test: Blue tempo", () -> testDeck("", 1));
        decks.put("Test: Unlimited black-red", () -> testDeck("unlimited", 0));
        decks.put("Test: Unlimited blue-green", () -> testDeck("unlimited", 1));
        decks.put("Test: Mixed eras red-green", () -> testDeck("mixed", 0));
        decks.put("Test: Mixed eras blue-black", () -> testDeck("mixed", 1));
        // Each encounter's deck, as "Enemy: <name>".
        try {
            var enc = com.planesnomad.encounter.EncounterLibrary.load(db);
            for (var e : enc.all()) {
                decks.put(e.enemy().deck(), e::deck);
            }
        } catch (RuntimeException e) {
            problems.add("Encounters: " + e.getMessage());
        }
        Path dir = folder();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".dck")).sorted().forEach(f -> {
                    String name = f.getFileName().toString().replaceFirst("(?i)\\.dck$", "");
                    decks.put(name, () -> DeckSerializer.fromFile(f.toFile()));
                });
            } catch (java.io.IOException e) {
                problems.add("Could not list " + dir + ": " + e.getMessage());
            }
        }
    }

    private Deck testDeck(String which, int side) {
        return TestMatches.create(which, db).getPlayers().get(side).getDeck();
    }

    public List<String> names() {
        return List.copyOf(decks.keySet());
    }

    /** The deck by name, or null if unknown or unreadable. */
    public Deck get(String name) {
        Supplier<Deck> s = decks.get(name);
        if (s == null) {
            return null;
        }
        try {
            Deck d = s.get();
            return d == null || d.getMain().isEmpty() ? null : d;
        } catch (RuntimeException e) {
            problems.add(name + ": " + e.getMessage());
            return null;
        }
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }

    /** Writes a deck as a .dck file into the decks folder (e.g. to start your own from a test deck). */
    public static File save(Deck deck, String name) throws java.io.IOException {
        Files.createDirectories(folder());
        File f = folder().resolve(name + ".dck").toFile();
        DeckSerializer.writeDeck(deck, f);
        return f;
    }
}

package com.planesnomad.encounter;

import forge.card.CardDb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * All encounters: every {@code .json} file under {@code data/encounters} (or
 * {@code -Dplanesnomad.encounters=<folder>}), checked as they are read. Problems are collected
 * (and printed by the viewer) instead of stopping the game; a file that can't be used is left out.
 */
public final class EncounterLibrary {

    private final Map<String, Encounter> encounters = new LinkedHashMap<>();
    private final List<String> problems = new ArrayList<>();

    public static Path folder() {
        String p = System.getProperty("planesnomad.encounters");
        return Paths.get(p != null && !p.isBlank() ? p : "data/encounters");
    }

    public static EncounterLibrary load(CardDb db) {
        return load(folder(), db);
    }

    public static EncounterLibrary load(Path root, CardDb db) {
        EncounterLibrary lib = new EncounterLibrary();
        if (!Files.isDirectory(root)) {
            lib.problems.add("Encounters folder not found: " + root.toAbsolutePath());
            return lib;
        }
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(f -> Files.isRegularFile(f) && f.getFileName().toString().toLowerCase().endsWith(".json"))
                .sorted()
                .forEach(f -> {
                    EncounterFile.Result r = EncounterFile.read(root, f, db);
                    lib.problems.addAll(r.problems());
                    if (r.encounter() != null) {
                        Encounter e = r.encounter();
                        if (lib.encounters.values().stream().anyMatch(x -> x.name().equals(e.name()))) {
                            lib.problems.add(e.id() + ": another encounter already uses the name \"" + e.name() + "\"");
                        }
                        lib.encounters.put(e.id(), e);
                    }
                });
        } catch (IOException e) {
            lib.problems.add("Can't read " + root + ": " + e.getMessage());
        }
        return lib;
    }

    /** By id, e.g. "lorwyn/easy/merfolk-tidecaller"; null if there's none. */
    public Encounter get(String id) {
        return encounters.get(id);
    }

    public List<Encounter> all() {
        return List.copyOf(encounters.values());
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }
}

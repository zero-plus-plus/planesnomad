package com.planesnomad.tools;

import com.planesnomad.ForgeEngine;
import com.planesnomad.gui.engine.SnapshotBuilder;
import com.planesnomad.gui.images.ImageSetup;
import com.planesnomad.gui.images.ScryfallDownloader;
import com.planesnomad.gui.model.CardSnapshot;
import forge.StaticData;
import forge.card.CardDb;
import forge.card.CardEdition;
import forge.item.PaperCard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Downloads the pictures of only the cards listed in CSV files (instead of a whole picture
 * library), into the same cache the game reads, so a first launch has what it needs.
 *
 * CSV columns: {@code name,set,collector_number} (a header row is optional). The number may be
 * left out (that set's basic printing is used), and the set too (the card's default printing).
 * Our own cards (custom set) need no download and are just listed. Set codes may be Scryfall's (e.g. "ody") or
 * Forge's. Names with commas go in quotes: {@code "Krenko, Mob Boss",m13,}
 *
 * For each card it fetches the full picture and the art crop (portraits, history, inspection),
 * skipping what is already cached, at Scryfall's polite request rate.
 *
 * Usage: java ... com.planesnomad.tools.CardImageDownloader cards.csv [more.csv ...]
 *        [--no-art] (full pictures only)  [--check] (only check the list, download nothing)
 * The cache is the game's (Windows: %LOCALAPPDATA%\Planesnomad\cache\scryfall), or
 * -Dplanesnomad.cache=<folder>.
 */
public final class CardImageDownloader {

    /** One CSV row. */
    record Entry(String name, String set, String number, String source) { }

    public static void main(String[] args) throws Exception {
        List<Path> files = new ArrayList<>();
        boolean art = true;
        boolean checkOnly = false;
        for (String a : args) {
            switch (a) {
                case "--no-art" -> art = false;
                case "--check" -> checkOnly = true;
                default -> files.add(Path.of(a));
            }
        }
        if (files.isEmpty()) {
            System.out.println("Usage: CardImageDownloader <cards.csv> [more.csv ...] [--no-art] [--check]");
            System.exit(2);
        }
        List<Entry> entries = new ArrayList<>();
        for (Path f : files) {
            entries.addAll(read(f));
        }
        CardDb db = ForgeEngine.initialize();
        ScryfallDownloader scryfall = new ScryfallDownloader(ScryfallDownloader.defaultCache(), ImageSetup::scryfallLink);
        System.out.println();
        System.out.println(entries.size() + " cards listed; cache: " + scryfall.cacheRoot().toAbsolutePath());

        int downloaded = 0;
        int cached = 0;
        List<String> unknown = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        List<String> own = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int i = 0;
        for (Entry e : entries) {
            i++;
            PaperCard pc = resolve(db, e);
            if (pc == null) {
                unknown.add(e.source() + ": " + e.name() + (e.set().isEmpty() ? "" : " (" + e.set() + " " + e.number() + ")"));
                continue;
            }
            if (isOwnCard(pc)) {
                own.add(pc.getName());
                System.out.printf("  %3d/%d  %-32s %-5s  our own card: nothing to download%n", i, entries.size(), pc.getName(), pc.getEdition());
                continue;
            }
            CardSnapshot card = SnapshotBuilder.fromPaper(pc);
            String key = pc.getEdition() + "/" + pc.getCollectorNumber() + "/" + pc.getName();
            if (!seen.add(key)) {
                continue;   // listed twice
            }
            List<ScryfallDownloader.Version> versions = art
                ? List.of(ScryfallDownloader.Version.NORMAL, ScryfallDownloader.Version.ART_CROP)
                : List.of(ScryfallDownloader.Version.NORMAL);
            StringBuilder status = new StringBuilder();
            for (ScryfallDownloader.Version v : versions) {
                if (scryfall.cached(card, v) != null) {
                    cached++;
                    status.append(" ").append(v.name().toLowerCase(Locale.ROOT)).append(":cached");
                    continue;
                }
                if (checkOnly) {
                    status.append(" ").append(v.name().toLowerCase(Locale.ROOT)).append(":missing");
                    continue;
                }
                Path got = scryfall.fetchNow(card, v);
                if (got != null) {
                    downloaded++;
                    status.append(" ").append(v.name().toLowerCase(Locale.ROOT)).append(":downloaded");
                } else {
                    failed.add(pc.getName() + " [" + pc.getEdition() + " #" + pc.getCollectorNumber() + "] " + v);
                    status.append(" ").append(v.name().toLowerCase(Locale.ROOT)).append(":FAILED");
                }
            }
            System.out.printf("  %3d/%d  %-32s %-5s #%-5s%s%n", i, entries.size(), pc.getName(), pc.getEdition(),
                pc.getCollectorNumber(), status);
        }

        System.out.println();
        System.out.println((checkOnly ? "Checked" : "Done") + ": " + downloaded + " downloaded, " + cached + " already cached"
            + (own.isEmpty() ? "" : ", " + own.size() + " our own")
            + (unknown.isEmpty() ? "" : ", " + unknown.size() + " not known to the game")
            + (failed.isEmpty() ? "" : ", " + failed.size() + " failed"));
        unknown.forEach(u -> System.out.println("  not known (check the name / set / number): " + u));
        failed.forEach(f -> System.out.println("  failed: " + f));
        System.exit(unknown.isEmpty() && failed.isEmpty() ? 0 : 1);
    }

    /** The printing named by the row: by set (Scryfall or Forge code) and collector number if given. */
    static PaperCard resolve(CardDb db, Entry e) {
        PaperCard any = db.getCard(e.name());
        String name = any != null ? any.getName() : findName(db, e.name());
        if (name == null) {
            return null;
        }
        if (e.set().isEmpty()) {
            return db.getCard(name);
        }
        CardEdition edition = edition(e.set());
        if (edition == null) {
            return null;
        }
        PaperCard basic = null;
        for (PaperCard pc : db.getAllCards(name)) {
            if (!pc.getEdition().equalsIgnoreCase(edition.getCode())) {
                continue;
            }
            if (!e.number().isEmpty() && e.number().equalsIgnoreCase(pc.getCollectorNumber())) {
                return pc;
            }
            // No number: the set's basic (regular) printing - art 1, lowest collector number.
            if (basic == null || pc.getArtIndex() < basic.getArtIndex()
                || pc.getArtIndex() == basic.getArtIndex() && numberOrder(pc) < numberOrder(basic)) {
                basic = pc;
            }
        }
        return e.number().isEmpty() ? basic : null;
    }

    private static int numberOrder(PaperCard pc) {
        String digits = pc.getCollectorNumber() == null ? "" : pc.getCollectorNumber().replaceAll("\\D", "");
        try {
            return digits.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(digits);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    /** Our own cards (a custom set): Scryfall doesn't have them; their pictures come with the game. */
    static boolean isOwnCard(PaperCard pc) {
        CardEdition ed = StaticData.instance().getEditions().get(pc.getEdition());
        return ed != null && ed.getType() == CardEdition.Type.CUSTOM_SET;
    }

    /** Case-insensitive name lookup (CSV lists are often lower case). */
    private static String findName(CardDb db, String name) {
        for (PaperCard pc : db.getUniqueCards()) {
            if (pc.getName().equalsIgnoreCase(name)) {
                return pc.getName();
            }
        }
        return null;
    }

    private static CardEdition edition(String code) {
        for (CardEdition ed : StaticData.instance().getEditions()) {
            if (code.equalsIgnoreCase(ed.getScryfallCode()) || code.equalsIgnoreCase(ed.getCode())
                || code.equalsIgnoreCase(ed.getCode2())) {
                return ed;
            }
        }
        return null;
    }

    /** Reads name,set,collector_number rows (quotes allowed; header and blank lines skipped). */
    static List<Entry> read(Path file) throws IOException {
        List<Entry> out = new ArrayList<>();
        int lineNo = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            lineNo++;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            List<String> cols = split(line);
            String name = cols.isEmpty() ? "" : cols.get(0).trim();
            if (lineNo == 1 && name.equalsIgnoreCase("name")) {
                continue;
            }
            if (name.isEmpty()) {
                continue;
            }
            out.add(new Entry(name, cols.size() > 1 ? cols.get(1).trim() : "", cols.size() > 2 ? cols.get(2).trim() : "",
                file.getFileName() + ":" + lineNo));
        }
        return out;
    }

    private static List<String> split(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cols.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString());
        return cols;
    }

    private CardImageDownloader() { }
}

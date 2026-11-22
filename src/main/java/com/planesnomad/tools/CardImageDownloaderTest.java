package com.planesnomad.tools;

import com.planesnomad.ForgeEngine;
import forge.card.CardDb;
import forge.item.PaperCard;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Checks reading card lists and finding the exact printings (no downloads). */
public final class CardImageDownloaderTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        Path csv = Files.createTempFile("cards", ".csv");
        Files.writeString(csv, String.join("\n",
            "name,set,collector_number",
            "forest,ody,349",
            "\"Krenko, Mob Boss\",m13,",
            "Lightning Bolt,,",
            "Shock,STH,98",
            "",
            "# a comment",
            "No Such Card,ody,1",
            "Forest,ody,9999",
            "Mountain,ody,",
            "Ember Charm,,"));
        List<CardImageDownloader.Entry> rows = CardImageDownloader.read(csv);
        check("Header, blank and comment lines skipped: 8 rows", rows.size() == 8);
        check("A quoted name with a comma is one name", rows.get(1).name().equals("Krenko, Mob Boss"));

        CardDb db = ForgeEngine.initialize();
        PaperCard forest = CardImageDownloader.resolve(db, rows.get(0));
        check("Lower-case name + Scryfall set code + number -> Forest ODY #349",
            forest != null && forest.getName().equals("Forest") && forest.getCollectorNumber().equals("349"));
        PaperCard krenko = CardImageDownloader.resolve(db, rows.get(1));
        check("Set without number -> that set's printing (Krenko, M13)", krenko != null && krenko.getEdition().equals("M13"));
        check("No set -> the default printing (Lightning Bolt)", CardImageDownloader.resolve(db, rows.get(2)) != null);
        check("Forge-style set code works too (Shock, STH #98)", CardImageDownloader.resolve(db, rows.get(3)) != null);
        check("Unknown card -> reported, not guessed", CardImageDownloader.resolve(db, rows.get(4)) == null);
        check("Wrong collector number -> reported, not guessed", CardImageDownloader.resolve(db, rows.get(5)) == null);
        PaperCard mountain = CardImageDownloader.resolve(db, rows.get(6));
        check("Set without number -> the set's basic printing (Mountain ODY, art 1: #" + (mountain == null ? "?" : mountain.getCollectorNumber()) + ")",
            mountain != null && mountain.getEdition().equals("ODY") && mountain.getArtIndex() == 1);
        PaperCard own = CardImageDownloader.resolve(db, rows.get(7));
        check("Our own card without set/number is found and marked as ours (Ember Charm)",
            own != null && CardImageDownloader.isOwnCard(own));
        Files.deleteIfExists(csv);

        System.out.println(failures == 0 ? "ALL DOWNLOADER CHECKS PASSED" : failures + " DOWNLOADER CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(String d, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + d);
        if (!ok) {
            failures++;
        }
    }

    private CardImageDownloaderTest() { }
}

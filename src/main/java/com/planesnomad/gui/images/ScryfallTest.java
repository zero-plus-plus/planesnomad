package com.planesnomad.gui.images;

import com.planesnomad.ForgeEngine;
import com.planesnomad.gui.engine.SnapshotBuilder;
import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.ui.CardPainter;
import forge.card.CardDb;
import forge.item.PaperCard;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks Scryfall downloads (NEEDS NETWORK ACCESS to api.scryfall.com and cards.scryfall.io).
 * Uses a fresh cache folder, so it really downloads.
 *
 * Usage (repo root): java ... com.planesnomad.gui.images.ScryfallTest <localPicturesFolder> [sheet.png]
 */
public final class ScryfallTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        Path pictures = Path.of(args.length > 0 ? args[0] : "../test-files/pics/cards");
        CardDb db = ForgeEngine.initialize();
        Path cache = Files.createTempDirectory("scryfall-test");

        // 1. Links from Forge's builder
        String shivan = ImageSetup.scryfallLink(snap(db, "Shivan Dragon", "2ED"), ScryfallDownloader.Version.NORMAL);
        check("Link for Shivan Dragon (2ED): " + shivan, "2ed/175/en?format=image&version=normal".equalsIgnoreCase(shivan));
        String conflux = ImageSetup.scryfallLink(snap(db, "Progenitus", "CFX"), ScryfallDownloader.Version.ART_CROP);
        check("Conflux uses Scryfall's set code CON: " + conflux, conflux != null && conflux.toUpperCase().startsWith("CON/"));
        check("Our own cards (Ember Charm, PNO) get no link",
            ImageSetup.scryfallLink(snap(db, "Ember Charm", "PNO"), ScryfallDownloader.Version.NORMAL) == null);

        // 2. Downloads, cache, rate limit
        ScryfallDownloader sf = new ScryfallDownloader(cache, ImageSetup::scryfallLink);
        CardSnapshot vivien = snap(db, "Vivien Reid", "M19");
        Path normal = sf.fetchNow(vivien, ScryfallDownloader.Version.NORMAL);
        BufferedImage normalImg = normal == null ? null : ImageIO.read(normal.toFile());
        check("Full picture downloaded and readable (" + size(normalImg) + ")",
            normalImg != null && normalImg.getHeight() > normalImg.getWidth());
        Path art = sf.fetchNow(vivien, ScryfallDownloader.Version.ART_CROP);
        BufferedImage artImg = art == null ? null : ImageIO.read(art.toFile());
        check("Art crop downloaded and readable (" + size(artImg) + ")",
            artImg != null && artImg.getWidth() > artImg.getHeight());
        check("Cached under the cache folder, by set and number (" + (art == null ? "-" : cache.relativize(art)) + ")",
            art != null && art.startsWith(cache.resolve("art_crop").resolve("M19")));
        int before = sf.downloads();
        sf.fetchNow(vivien, ScryfallDownloader.Version.ART_CROP);
        check("A second request uses the cache (no new download)", sf.downloads() == before);

        String[] names = {"Lightning Bolt", "Counterspell", "Goblin King", "Lord of Atlantis"};
        long t0 = System.currentTimeMillis();
        int fetched = 0;
        for (String n : names) {
            if (sf.fetchNow(snap(db, n, null), ScryfallDownloader.Version.ART_CROP) != null) {
                fetched++;
            }
        }
        long elapsed = System.currentTimeMillis() - t0;
        check("Requests are spaced at least " + ScryfallDownloader.MIN_INTERVAL_MS + " ms apart (" + fetched
                + " downloads in " + elapsed + " ms)",
            fetched == names.length && elapsed >= (names.length - 1) * ScryfallDownloader.MIN_INTERVAL_MS);

        // 3. Missing pictures are remembered; disabled downloads fetch nothing
        ScryfallDownloader bogus = new ScryfallDownloader(cache, (c, v) -> "2ED/99999/en?format=image&version=normal");
        check("A picture Scryfall doesn't have returns nothing", bogus.fetchNow(vivien, ScryfallDownloader.Version.NORMAL) == null);
        long t1 = System.currentTimeMillis();
        boolean stillNull = bogus.fetchNow(vivien, ScryfallDownloader.Version.NORMAL) == null;
        check("...and isn't asked for again this session (answered in " + (System.currentTimeMillis() - t1) + " ms)",
            stillNull && System.currentTimeMillis() - t1 < 50);
        ScryfallDownloader off = new ScryfallDownloader(Files.createTempDirectory("scryfall-off"), ImageSetup::scryfallLink);
        off.setEnabled(false);
        check("Downloads switched off: nothing is fetched", off.fetchNow(vivien, ScryfallDownloader.Version.NORMAL) == null
            && off.downloads() == 0);

        // 4. End to end in the image library: no local picture -> downloaded; exact art crops for
        //    layouts our crop boxes don't handle well.
        CardImages images = new CardImages(List.of(Files.createTempDirectory("no-local-pictures")),
            ArtCrops.load(Path.of("data", "art-crops.txt")));
        images.setScryfall(sf);
        CardSnapshot split = snap(db, "Fire // Ice", "APC");
        CardSnapshot saga = snap(db, "The Eldest Reborn", "DOM");
        CardSnapshot battle = snap(db, "Invasion of Ikoria", "MOM");
        List<CardSnapshot> special = new ArrayList<>(List.of(vivien, saga, split));
        if (battle != null) {
            special.add(battle);
        }
        images.preloadWithDownloads(special);
        for (CardSnapshot c : special) {
            check(c.name() + " (" + c.setCode() + ", " + c.layout() + "): picture and exact art crop from Scryfall",
                images.hasImage(c) && images.art(c, 160, 110) != null);
        }

        if (args.length > 1) {
            writeSheet(new File(args[1]), db, pictures, sf);
        }

        System.out.println();
        System.out.println("Cache used: " + cache + " (" + sf.downloads() + " downloads)");
        if (failures == 0) {
            System.out.println("ALL SCRYFALL CHECKS PASSED");
        } else {
            System.out.println(failures + " SCRYFALL CHECK(S) FAILED");
            System.exit(1);
        }
    }

    /** Our crop box (left) vs Scryfall's exact art crop (right), for cards with tricky layouts. */
    private static void writeSheet(File out, CardDb db, Path pictures, ScryfallDownloader sf) throws Exception {
        String[][] cards = {
            {"Vivien Reid", "M19"}, {"Lukka, Bound to Ruin", "ONE"}, {"Jace, the Mind Sculptor", "WWK"},
            {"History of Benalia", "DOM"}, {"Shivan Dragon", "2ED"}, {"Hooded Hydra", "KTK"},
        };
        ArtCrops crops = ArtCrops.load(Path.of("data", "art-crops.txt"));
        CardImages boxOnly = new CardImages(List.of(pictures), crops);
        CardImages withScryfall = new CardImages(List.of(pictures), crops);
        withScryfall.setScryfall(sf);
        int tw = 260, th = 180, pad = 14, labelW = 260;
        BufferedImage img = new BufferedImage(labelW + 2 * (tw + pad) + pad, pad + cards.length * (th + pad) + 24, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x15181D));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        g.setColor(Color.WHITE);
        g.drawString("Our crop box", labelW + pad, 18);
        g.drawString("Scryfall art crop", labelW + 2 * pad + tw, 18);
        int y = 24 + pad;
        for (String[] c : cards) {
            CardSnapshot card = snap(db, c[0], c[1]);
            boxOnly.preloadNow(List.of(card));
            withScryfall.preloadWithDownloads(List.of(card));
            Image a = boxOnly.art(card, tw, th);
            Image b = withScryfall.art(card, tw, th);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g.drawString(c[0] + " (" + c[1] + ")", pad, y + th / 2);
            if (a != null) {
                g.drawImage(a, labelW + pad, y, null);
            } else {
                g.setColor(Color.DARK_GRAY);
                g.drawString("(no crop)", labelW + pad + 8, y + th / 2);
            }
            if (b != null) {
                g.drawImage(b, labelW + 2 * pad + tw, y, null);
            }
            y += th + pad;
        }
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("Sheet: " + out.getAbsolutePath());
        CardPainter.setImageSource(null);
    }

    private static CardSnapshot snap(CardDb db, String name, String set) {
        PaperCard pc = set == null ? db.getCard(name) : db.getCard(name, set);
        return pc == null ? null : SnapshotBuilder.fromPaper(pc);
    }

    private static String size(BufferedImage img) {
        return img == null ? "none" : img.getWidth() + "x" + img.getHeight();
    }

    private static void check(String description, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + description);
        if (!ok) {
            failures++;
        }
    }
}

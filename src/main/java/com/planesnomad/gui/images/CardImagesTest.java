package com.planesnomad.gui.images;

import com.planesnomad.ForgeEngine;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks card picture lookup and art crops against a folder of test pictures in Forge's naming
 * (set folders such as U/, TO/, SHM/, FUT/, KTK/, plus flat files), and writes a contact sheet
 * of art thumbnails for reviewing the crop boxes.
 *
 * Usage (repo root): java ... com.planesnomad.gui.images.CardImagesTest <picturesFolder> [sheet.png]
 * Exits with status 1 if any check fails.
 */
public final class CardImagesTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        Path pictures = Path.of(args.length > 0 ? args[0] : "../test-files/card-images");
        CardDb db = ForgeEngine.initialize();
        ArtCrops crops = ArtCrops.load(Path.of("data", "art-crops.txt"));
        CardImages images = new CardImages(List.of(pictures), crops);
        CardPainter.setImageSource(new CardPainter.ImageSource() {
            @Override
            public Image full(CardSnapshot card, int w, int h) {
                return images.full(card, w, h);
            }

            @Override
            public Image art(CardSnapshot card, int w, int h) {
                return images.art(card, w, h);
            }
        });

        check("Crop file lists sets (" + crops.size() + " codes)", crops.size() >= 7);

        // Printings whose pictures are in the test folder: {name, set, art index}
        String[][] present = {
            {"Shivan Dragon", "2ED", "1"}, {"Mountain", "2ED", "2"}, {"Island", "2ED", "3"},
            {"Cabal Surgeon", "TOR", "1"}, {"Cursecatcher", "SHM", "1"},
            {"Cloudseeder", "FUT", "1"}, {"Hooded Hydra", "KTK", "1"},
        };
        List<CardSnapshot> sheet = new ArrayList<>();
        for (String[] p : present) {
            CardSnapshot card = snap(db, p[0], p[1], Integer.parseInt(p[2]));
            if (card == null) {
                check("Printing exists: " + p[0] + " (" + p[1] + ")", false);
                continue;
            }
            check(p[0] + " (" + p[1] + ", key " + card.imageKey() + ") has a picture", images.hasImage(card));
            Image art = images.art(card, 120, 88);
            check(p[0] + " (" + p[1] + ") has an art crop", art != null);
            Image full = images.full(card, 63, 88);
            check(p[0] + " full picture scales to 63x88", full != null && full.getWidth(null) == 63 && full.getHeight(null) == 88);
            sheet.add(card);
        }

        // Basic lands with several arts must find the right file (Mountain2, not Mountain1).
        CardSnapshot mountain1 = snap(db, "Mountain", "2ED", 1);
        CardSnapshot mountain2 = snap(db, "Mountain", "2ED", 2);
        check("Land art index is part of the image key (" + mountain2.imageKey() + ")",
            mountain2.imageKey().contains("Mountain2") && mountain1.imageKey().contains("Mountain1"));

        // A printing with no picture in the folder: text frame (null), no crash.
        CardSnapshot shock = snap(db, "Shock", "10E", 1);
        check("No picture for Shock (10E): falls back to the text frame", !images.hasImage(shock)
            && images.full(shock, 63, 88) == null && images.art(shock, 40, 30) == null);

        // A set with pictures but no crop box yet: full picture yes, art crop no.
        check("Crop boxes only for checked sets (none for 10E)", crops.find("10E", "10E").isEmpty());

        // Hidden cards never get a picture.
        check("Hidden cards have no picture", images.full(CardSnapshot.hidden(1), 63, 88) == null);

        if (args.length > 1) {
            writeSheet(new File(args[1]), sheet, images);
        }

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CARD IMAGE CHECKS PASSED");
        } else {
            System.out.println(failures + " CARD IMAGE CHECK(S) FAILED");
            System.exit(1);
        }
    }

    /** A card snapshot for a printing, as the screen would see it (no game needed). */
    private static CardSnapshot snap(CardDb db, String name, String set, int artIndex) {
        PaperCard pc = db.getCard(name, set, artIndex);
        if (pc == null) {
            return null;
        }
        return new CardSnapshot(0, pc.getName(), pc.getEdition(), pc.getCollectorNumber(), "", "",
            "", "", null, null, 0, false, false, false, false, false, false, false, false,
            pc.getCardImageKey());
    }

    /** Full card next to its art thumbnail, one row per card. */
    private static void writeSheet(File out, List<CardSnapshot> cards, CardImages images) throws Exception {
        int fw = 140, fh = 196, aw = 180, ah = 132, pad = 12;
        BufferedImage img = new BufferedImage(pad * 4 + fw + aw + 260, pad + cards.size() * (fh + pad), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x15181D));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        int y = pad;
        for (CardSnapshot c : cards) {
            CardPainter.paint(g, c, pad, y, fw, fh);
            CardPainter.paintArt(g, c, pad * 2 + fw, y + (fh - ah) / 2, aw, ah);
            CardPainter.paintArt(g, c, pad * 3 + fw + aw, y + (fh - 30) / 2, 40, 30);   // stack-list size
            g.setColor(Color.WHITE);
            g.drawString(c.name() + " (" + c.setCode() + ")", pad * 4 + fw + aw + 40, y + fh / 2 + 5);
            y += fh + pad;
        }
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("Contact sheet: " + out.getAbsolutePath());
    }

    private static void check(String description, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + description);
        if (!ok) {
            failures++;
        }
    }
}

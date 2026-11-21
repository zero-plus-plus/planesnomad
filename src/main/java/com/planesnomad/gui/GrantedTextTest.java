package com.planesnomad.gui;

import com.planesnomad.ForgeEngine;
import com.planesnomad.gui.engine.SnapshotBuilder;
import com.planesnomad.gui.images.CardImages;
import com.planesnomad.gui.images.ImageSetup;
import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.ui.CardPainter;
import forge.ai.LobbyPlayerAi;
import forge.card.CardDb;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.item.PaperCard;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Checks that the screen shows what a card is NOW, not only what is printed on it:
 * abilities granted by other cards and changed power/toughness (which a card picture can't show
 * on its own), and that granted-ability detection has no false positives.
 *
 * Usage (repo root): java ... com.planesnomad.gui.GrantedTextTest [sheet.png]
 * With -Dplanesnomad.cardImages=<folder>, the sheet uses real pictures.
 */
public final class GrantedTextTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        CardDb db = ForgeEngine.initialize();

        // 1. No false positives: cards alone on the battlefield have nothing granted.
        List<String> names = new ArrayList<>(List.of("Shock", "Lightning Bolt", "Goblin Raider",
            "Mons's Goblin Raiders", "Counterspell", "Unsummon", "Merfolk of the Pearl Trident",
            "Coral Merfolk", "Goblin King", "Lord of Atlantis", "Shivan Dragon", "Black Knight",
            "Boggart Ram-Gang", "Hooded Hydra", "Vivien Reid", "History of Benalia", "Kitchen Finks"));
        List<PaperCard> pool = new ArrayList<>();
        for (PaperCard pc : db.getAllCards()) {
            if (pc.getArtIndex() == 1 && !pc.getRules().getType().isLand()) {
                pool.add(pc);
            }
        }
        Random random = new Random(20261121);
        for (int i = 0; i < 400; i++) {
            names.add(pool.get(random.nextInt(pool.size())).getName());
        }
        List<String> falsePositives = new ArrayList<>();
        int checked = 0;
        for (String name : names) {
            Game game = newGame(db);
            Player me = game.getPlayers().get(0);
            PaperCard pc = db.getCard(name);
            if (pc == null) {
                continue;
            }
            Card c;
            try {
                c = put(game, me, pc);
            } catch (RuntimeException e) {
                continue;   // a few cards can't simply be put into play outside a real game
            }
            if (c == null || !c.isInPlay()) {
                continue;
            }
            checked++;
            String granted = SnapshotBuilder.granted(c);
            if (!granted.isEmpty()) {
                falsePositives.add(name + " -> [" + granted.replace("\n", " | ") + "]");
            }
        }
        System.out.println("Cards checked alone on the battlefield: " + checked);
        falsePositives.stream().limit(15).forEach(f -> System.out.println("    false positive: " + f));
        check("No false 'granted' abilities on " + checked + " cards alone on the battlefield ("
            + falsePositives.size() + " found)", falsePositives.isEmpty() && checked > 300);

        // 2. Real grants are detected, and power/toughness changes are flagged.
        Game game = newGame(db);
        Player me = game.getPlayers().get(0);
        Card merfolk = put(game, me, db.getCard("Merfolk of the Pearl Trident"));
        Card raider = put(game, me, db.getCard("Goblin Raider"));
        Card krenko = put(game, me, db.getCard("Krenko, Mob Boss"));
        Card lord = put(game, me, db.getCard("Lord of Atlantis"));
        put(game, me, db.getCard("Goblin King"));
        CardSnapshot merfolkSnap = snapshot(merfolk);
        CardSnapshot raiderSnap = snapshot(raider);
        check("Merfolk under Lord of Atlantis: islandwalk shown as granted (" + merfolkSnap.granted() + ")",
            merfolkSnap.granted().toLowerCase().contains("islandwalk"));
        check("Merfolk under Lord of Atlantis: 2/2 flagged as changed from printed 1/1",
            merfolkSnap.statsChanged() && merfolkSnap.power() == 2 && merfolkSnap.printedPower() == 1);
        check("Goblin Raider (has printed text) under Goblin King: mountainwalk granted, printed text not repeated ("
                + raiderSnap.granted().replace("\n", " | ") + ")",
            raiderSnap.granted().toLowerCase().contains("mountainwalk")
                && !raiderSnap.granted().toLowerCase().contains("can't block"));
        check("Goblin Raider's current text includes both its printed and granted abilities",
            raiderSnap.rulesText().toLowerCase().contains("can't block")
                && raiderSnap.rulesText().toLowerCase().contains("mountainwalk"));

        // 3. Picture sheet: board size and preview size (needs -Dplanesnomad.cardImages for pictures).
        if (args.length > 0) {
            CardImages images = ImageSetup.create();
            List<CardSnapshot> cards = List.of(merfolkSnap, raiderSnap);
            images.preloadNow(cards);
            int bw = 63 * 2, bh = 88 * 2, pw = 300, ph = 419, pad = 14;
            BufferedImage img = new BufferedImage(pad + cards.size() * (bw + pw + 2 * pad), ph + 2 * pad, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(0x15181D));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            int x = pad;
            for (CardSnapshot c : cards) {
                CardPainter.paint(g, c, x, pad, bw, bh);
                CardPainter.paint(g, c, x + bw + pad, pad, pw, ph);
                x += bw + pw + 2 * pad;
            }
            g.dispose();
            ImageIO.write(img, "png", new File(args[0]));
            System.out.println("Sheet: " + new File(args[0]).getAbsolutePath());
        }

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL GRANTED-TEXT CHECKS PASSED");
        } else {
            System.out.println(failures + " GRANTED-TEXT CHECK(S) FAILED");
            System.exit(1);
        }
    }

    /** The snapshot the screen would get for this card. */
    private static CardSnapshot snapshot(Card c) {
        return SnapshotBuilder.card(c);
    }

    private static Game newGame(CardDb db) {
        Deck deck = new Deck("filler");
        deck.getMain().add(db.getCard("Island"), 20);
        RegisteredPlayer a = new RegisteredPlayer(deck);
        RegisteredPlayer b = new RegisteredPlayer(deck);
        a.setPlayer(new LobbyPlayerAi("A", Set.of()));
        b.setPlayer(new LobbyPlayerAi("B", Set.of()));
        return new Match(new GameRules(GameType.Constructed), List.of(a, b), "GrantedTextTest").createGame();
    }

    private static Card put(Game game, Player owner, PaperCard pc) {
        Card placed = game.getAction().moveToPlay(Card.fromPaperCard(pc, owner), owner, null, null);
        game.getAction().checkStaticAbilities();
        return placed;
    }

    private static void check(String description, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + description);
        if (!ok) {
            failures++;
        }
    }
}

package com.planesnomad.gui;

import com.google.common.eventbus.Subscribe;
import com.planesnomad.ForgeEngine;
import com.planesnomad.gui.engine.HistoryRecorder;
import com.planesnomad.gui.engine.MatchRunner;
import com.planesnomad.gui.engine.SnapshotBuilder;
import com.planesnomad.gui.images.CardImages;
import com.planesnomad.gui.images.ImageSetup;
import com.planesnomad.gui.model.HistoryEntry;
import com.planesnomad.gui.model.HistoryEntry.Kind;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.ui.HistoryBar;
import com.planesnomad.gui.ui.HistoryIcons;
import com.planesnomad.gui.ui.HistoryPopup;
import com.planesnomad.gui.ui.Theme;
import forge.StaticData;
import forge.card.CardDb;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.event.GameEvent;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Checks the match history (icon bar): what a real match records, merging of mass events,
 * and that the bar and its hover pop-up draw. With an output folder argument, also saves
 * screenshots of the bar, the pop-ups and all symbols.
 */
public final class HistoryTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        CardDb db = ForgeEngine.initialize();
        com.planesnomad.gui.ui.Theme.apply(com.planesnomad.gui.ui.theme.ThemePack.fromSystemProperty());

        // 1. A real AI-vs-AI match.
        List<MatchSnapshot> snaps = Collections.synchronizedList(new ArrayList<>());
        var looks = TestMatches.looks("");
        MatchRunner runner = new MatchRunner(TestMatches.create("", db), TestMatches.YOU, TestMatches.ENEMY,
            looks[0], looks[1], snaps::add);
        runner.setNoDelay(true);
        runner.start();
        runner.join();
        List<HistoryEntry> h = snaps.get(snaps.size() - 1).history();
        System.out.println("History entries: " + h.size());
        check("A match records history (" + h.size() + " entries)", h.size() > 15);
        check("Spells cast are recorded, each with its card", has(h, Kind.CAST)
            && h.stream().filter(e -> e.kind() == Kind.CAST).allMatch(e -> e.icon() != null));
        check("Lands played are recorded", has(h, Kind.LAND));
        check("Attacks are recorded with the attackers", h.stream().anyMatch(e -> e.kind() == Kind.ATTACK && !e.cards().isEmpty()));
        check("Damage to players is recorded", has(h, Kind.DAMAGE));
        check("The game's end is the last entry", h.get(h.size() - 1).kind() == Kind.GAME_END);
        check("Entries are in order and numbered", isSorted(h));
        check("Both players appear (yours and the enemy's)",
            h.stream().anyMatch(HistoryEntry::byYou) && h.stream().anyMatch(e -> !e.byYou()));
        check("Relic triggers/uses show as relics", h.stream().anyMatch(e -> e.relic() != null));
        check("No engine ids like \"(33)\" in the text",
            h.stream().allMatch(e -> !e.title().matches(".*\\(\\d+\\).*") && e.details().stream().noneMatch(d -> d.matches(".*\\(\\d+\\).*"))));
        check("Actions list what they led to (e.g. a burn spell's damage)",
            h.stream().anyMatch(e -> !e.effects().isEmpty()));
        check("Combat damage is listed under the attack that caused it",
            h.stream().anyMatch(e -> e.kind() == Kind.ATTACK && e.effects().stream().anyMatch(f -> f.contains("damage"))));
        check("Effect lists stay short (at most 11 lines)", h.stream().allMatch(e -> e.effects().size() <= 11));
        boolean grows = true;
        for (int i = 1; i < snaps.size(); i++) {
            if (snaps.get(i).history().size() + 1 < snaps.get(i - 1).history().size()) {
                grows = false;
            }
        }
        check("History only grows during the match", grows);

        // 2. Mass events merge: 200 goblin tokens enter, then all die at once.
        Game game = TestMatches.create("", db).createGame();
        Player you = game.getPlayers().stream().filter(p -> p.getName().equals(TestMatches.YOU)).findFirst().orElseThrow();
        Player enemy = game.getPlayers().stream().filter(p -> !p.getName().equals(TestMatches.YOU)).findFirst().orElseThrow();
        SnapshotBuilder builder = new SnapshotBuilder(game, you, enemy, looks[0], looks[1]);
        HistoryRecorder rec = builder.recordHistory();
        game.subscribeToEvents(new Object() {
            @Subscribe
            public void on(GameEvent e) {
                rec.record(e);
            }
        });
        var goblin = StaticData.instance().getAllTokens().getToken("r_1_1_goblin");
        List<Card> tokens = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            tokens.add(game.getAction().moveToPlay(CardFactory.getCard(goblin, you, game), you, null, null));
        }
        for (Card t : tokens) {
            game.getAction().moveToGraveyard(t, null, null);
        }
        List<HistoryEntry> mass = rec.entries();
        System.out.println("Mass events: " + mass.stream().map(e -> e.kind() + " x" + e.count()).toList());
        check("200 tokens created at once -> one Tokens entry x200",
            mass.stream().anyMatch(e -> e.kind() == Kind.TOKENS && e.count() == 200));
        check("200 goblins dying at once -> one Died entry x200",
            mass.stream().anyMatch(e -> e.kind() == Kind.DEATH && e.count() == 200));
        check("... its 200 identical lines merged into one (\"Goblin Token \u00d7200\")",
            mass.stream().filter(e -> e.kind() == Kind.DEATH).allMatch(e -> e.details().size() == 1 && e.details().get(0).endsWith("\u00d7200")));
        check("... with its card list capped (no 200 snapshots kept)",
            mass.stream().allMatch(e -> e.cards().size() <= 24 && e.details().size() <= 15));

        // 3. The bar and pop-up draw without errors, at both sizes.
        CardImages images = ImageSetup.create();
        List<HistoryEntry> all = new ArrayList<>(h);
        all.addAll(mass);
        boolean[] drawn = {false};
        SwingUtilities.invokeAndWait(() -> {
            Theme.updateScale(1280, 720);
            HistoryBar bar = new HistoryBar();
            bar.setSize(Theme.px(HistoryBar.WIDTH), 640);
            bar.setEntries(all);
            BufferedImage img = new BufferedImage(bar.getWidth(), 640, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            bar.paint(g);
            g.dispose();
            drawn[0] = bar.maxScroll() > 0;
        });
        check("The bar draws a full match and can scroll back to older entries", drawn[0]);

        if (args.length > 0) {
            File out = new File(args[0]);
            out.mkdirs();
            images.preloadWithDownloads(all.stream().flatMap(e -> e.cards().stream()).toList());
            screenshots(out, all);
        }

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL HISTORY CHECKS PASSED");
        } else {
            System.out.println(failures + " HISTORY CHECK(S) FAILED");
            System.exit(1);
        }
        System.exit(0);
    }

    /** The bar beside a few pop-ups (one of each kind), plus a sheet of all symbols. */
    private static void screenshots(File out, List<HistoryEntry> all) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Theme.updateScale(1280, 720);
            int barW = Theme.px(HistoryBar.WIDTH);
            int height = 700;
            List<HistoryEntry> samples = new ArrayList<>();
            for (Kind k : Kind.values()) {
                all.stream().filter(e -> e.kind() == k).reduce((a, b) -> b.count() > a.count() ? b : a).ifPresent(samples::add);
            }
            int popW = Theme.px(330);
            int cols = 3;
            BufferedImage img = new BufferedImage(barW + 20 + cols * (popW + 12), height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(new Color(0x1E2A24));
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            HistoryBar bar = new HistoryBar();
            bar.setSize(barW, height);
            bar.setEntries(all.subList(0, Math.max(0, all.size() - 2)));   // the match part, newest at top
            bar.paint(g);
            int[] colY = new int[cols];
            for (HistoryEntry e : samples) {
                int c = 0;
                for (int i = 1; i < cols; i++) {
                    if (colY[i] < colY[c]) c = i;
                }
                Dimension d = HistoryPopup.size(e);
                if (colY[c] + d.height > height) {
                    continue;
                }
                Graphics2D pg = (Graphics2D) g.create(barW + 20 + c * (popW + 12), colY[c] + 8, d.width, d.height);
                HistoryPopup.paint(pg, e, d.width, d.height);
                pg.dispose();
                colY[c] += d.height + 12;
            }
            g.dispose();
            try {
                ImageIO.write(img, "png", new File(out, "history-bar-and-popups.png"));
                BufferedImage sheet = new BufferedImage(Kind.values().length * 70 + 10, 80, BufferedImage.TYPE_INT_RGB);
                Graphics2D sg = sheet.createGraphics();
                sg.setColor(new Color(0x15181D));
                sg.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
                for (int i = 0; i < Kind.values().length; i++) {
                    HistoryIcons.paint(sg, Kind.values()[i], 10 + i * 70, 8, 44);
                    sg.setColor(Color.LIGHT_GRAY);
                    sg.setFont(Theme.font(java.awt.Font.PLAIN, 9));
                    sg.drawString(Kind.values()[i].label, 8 + i * 70, 72);
                }
                sg.dispose();
                ImageIO.write(sheet, "png", new File(out, "history-symbols.png"));
            } catch (java.io.IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        System.out.println("Screenshots: " + out.getAbsolutePath());
    }

    private static boolean has(List<HistoryEntry> h, Kind k) {
        return h.stream().anyMatch(e -> e.kind() == k);
    }

    private static boolean isSorted(List<HistoryEntry> h) {
        for (int i = 1; i < h.size(); i++) {
            if (h.get(i).seq() <= h.get(i - 1).seq() || h.get(i).turn() < h.get(i - 1).turn()) {
                return false;
            }
        }
        return true;
    }

    private static void check(String description, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + description);
        if (!ok) {
            failures++;
        }
    }

    private HistoryTest() { }
}

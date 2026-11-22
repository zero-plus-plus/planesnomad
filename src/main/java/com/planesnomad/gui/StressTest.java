package com.planesnomad.gui;

import com.planesnomad.ForgeEngine;
import com.planesnomad.RelicTest;
import com.planesnomad.gui.engine.SnapshotBuilder;
import com.planesnomad.gui.images.CardImages;
import com.planesnomad.gui.images.ImageSetup;
import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.PlayerLook;
import com.planesnomad.gui.ui.CardRow;
import com.planesnomad.gui.ui.MatchView;
import com.planesnomad.gui.ui.StackPopup;
import com.planesnomad.gui.ui.TopBar;
import forge.StaticData;
import forge.card.CardDb;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.combat.Combat;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.item.PaperToken;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Stress test for crowded boards. Builds extreme board states directly in the engine (no AI):
 * dozens of creatures per side, a dozen lands, auras and equipment, tokens, a deep stack.
 * Renders each at 1280x720 and 1920x1080 with the stack & log column open and collapsed, and
 * measures:
 *  - how much of each card stays visible when a row overlaps (and so how wide its hover area is)
 *  - which cards the stack pop-up covers in its default spot
 *  - how long one full repaint takes with every picture loaded
 *
 * Builds the board from cards that have a picture in the local picture folders, so the repaint
 * timing is realistic. Nothing is checked pass/fail: the report is for reviewing the layout.
 *
 * Usage: java -Djava.awt.headless=true -Dplanesnomad.cardImages=<folder>
 *        com.planesnomad.gui.StressTest [outputDir]
 */
public final class StressTest {

    private static final int[][] SIZES = {{1280, 720}, {1920, 1080}};
    private static final long SEED = 20261122L;

    /** One board to build. */
    private record Scenario(String name, int creatures, int tokens, int lands, int auras, int equipment,
                            int hand, int stack, boolean combat, boolean horde) { }

    private static final List<Scenario> SCENARIOS = List.of(
        new Scenario("a-20-creatures", 14, 6, 12, 4, 3, 7, 0, false, false),
        new Scenario("b-30-creatures-combat", 22, 8, 12, 5, 3, 7, 3, true, false),
        new Scenario("c-40-creatures-deep-stack", 30, 10, 14, 6, 4, 10, 9, true, false),
        // Krenko made 200 goblins: piles of identical tokens, split by state.
        new Scenario("d-krenko-200-goblins", 4, 0, 16, 1, 1, 7, 2, true, true)
    );

    private static CardImages images;
    /** Time to build the last scenario's snapshot (done after every engine event in a match). */
    private static double lastBuildMs;
    private static final List<PaperCard> creatures = new ArrayList<>();
    private static final List<PaperCard> auras = new ArrayList<>();
    private static final List<PaperCard> equipment = new ArrayList<>();
    private static final List<PaperCard> lands = new ArrayList<>();
    private static final List<PaperCard> spells = new ArrayList<>();
    private static final List<PaperCard> abilityCreatures = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "stress");
        out.mkdirs();
        CardDb db = ForgeEngine.initialize();
        images = ImageSetup.create();
        System.out.println("Card picture folders: " + images.folders());
        collectPool(db);
        System.out.printf("Pool with pictures: %d creatures (%d with activated abilities), %d auras, %d equipment, "
            + "%d basic lands, %d spells%n", creatures.size(), abilityCreatures.size(), auras.size(),
            equipment.size(), lands.size(), spells.size());

        StringBuilder report = new StringBuilder("Planesnomad stress test report\n\n");
        for (Scenario sc : SCENARIOS) {
            MatchSnapshot snap = build(db, sc);
            report.append(describe(sc, snap));
            report.append(String.format("  snapshot build (engine side, per event): %.1f ms%n", lastBuildMs));
            images.preloadWithDownloads(allCards(snap));
            for (int[] size : SIZES) {
                for (boolean collapsed : new boolean[] {false, true}) {
                    String file = sc.name() + "-" + size[0] + "x" + size[1] + (collapsed ? "-collapsed" : "");
                    report.append(render(out, file, snap, size[0], size[1], collapsed));
                }
            }
            if (sc.stack() > 0) {
                report.append(render(out, sc.name() + "-1280x720-stack-list", snap, 1280, 720, false, true));
            }
            report.append('\n');
        }
        Files.writeString(new File(out, "report.txt").toPath(), report);
        System.out.println(report);
        System.out.println("Written to " + out.getAbsolutePath());
        System.exit(0);
    }

    // ------------------------------------------------------------------ for the viewer

    /** Names of the test boards, for the viewer's "Test boards" menu. */
    public static List<String> boardNames() {
        return SCENARIOS.stream().map(Scenario::name).toList();
    }

    /** Builds one test board (by name) as the screen would get it; the pool is collected once. */
    public static synchronized MatchSnapshot board(CardDb db, CardImages pictures, String name) throws Exception {
        return board(db, pictures, name, false);
    }

    /** @param revealEnemyHand test option: the enemy's hand face up */
    public static synchronized MatchSnapshot board(CardDb db, CardImages pictures, String name,
                                                   boolean revealEnemyHand) throws Exception {
        if (images == null) {
            images = pictures;
            collectPool(db);
            fallbackPool(db);
        }
        Scenario sc = SCENARIOS.stream().filter(x -> x.name().equals(name)).findFirst()
            .orElse(SCENARIOS.get(SCENARIOS.size() - 1));
        return build(db, sc, revealEnemyHand);
    }

    /** Without local pictures the pool would be empty: use a few well-known cards instead. */
    private static void fallbackPool(CardDb db) {
        if (creatures.isEmpty()) {
            for (String n : List.of("Grizzly Bears", "Hill Giant", "Serra Angel", "Goblin King", "Llanowar Elves",
                "Air Elemental", "Prodigal Pyromancer", "Shivan Dragon")) {
                if (db.getCard(n) != null) classify(db.getCard(n));
            }
        }
        if (lands.isEmpty()) {
            for (String n : List.of("Mountain", "Island", "Forest", "Plains", "Swamp")) {
                if (db.getCard(n) != null) classify(db.getCard(n));
            }
        }
        if (auras.isEmpty() && db.getCard("Holy Strength") != null) classify(db.getCard("Holy Strength"));
        if (equipment.isEmpty() && db.getCard("Bonesplitter") != null) classify(db.getCard("Bonesplitter"));
        if (spells.isEmpty() && db.getCard("Divination") != null) classify(db.getCard("Divination"));
    }

    // ------------------------------------------------------------------ card pool

    /** Cards that have a picture in the local folders: "<folder>/<Name>[n].fullborder.jpg". */
    private static void collectPool(CardDb db) throws Exception {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Path folder : images.folders()) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> s = Files.walk(folder, 2)) {
                files = s.filter(p -> p.getFileName().toString().endsWith(".fullborder.jpg")).sorted().toList();
            }
            for (Path f : files) {
                String set = f.getParent().getFileName().toString();
                String name = f.getFileName().toString().replace(".fullborder.jpg", "").replaceAll("\\d+$", "");
                PaperCard pc = db.getCard(name, set);
                if (pc == null || !seen.add(name)) {
                    continue;
                }
                classify(pc);
            }
        }
    }

    private static void classify(PaperCard pc) {
        CardRules r = pc.getRules();
        var type = r.getType();
        String layout = com.planesnomad.gui.images.CardLayouts.of(r);
        if (!"normal".equals(layout) || type.isLegendary()) {
            return;   // legendaries would die to the legend rule; odd layouts aren't the point here
        }
        String text = r.getOracleText() == null ? "" : r.getOracleText();
        if (type.isLand() && type.isBasicLand()) {
            lands.add(pc);
        } else if (type.isCreature()) {
            creatures.add(pc);
            if (text.contains("{T}:") && !text.contains("Add {")) {
                abilityCreatures.add(pc);
            }
        } else if (type.isEnchantment() && type.hasSubtype("Aura") && text.startsWith("Enchant creature")) {
            auras.add(pc);
        } else if (type.isArtifact() && type.hasSubtype("Equipment")) {
            equipment.add(pc);
        } else if ((type.isInstant() || type.isSorcery()) && !text.toLowerCase().contains("target")) {
            spells.add(pc);
        }
    }

    // ------------------------------------------------------------------ building a board

    private static MatchSnapshot build(CardDb db, Scenario sc) {
        return build(db, sc, false);
    }

    private static MatchSnapshot build(CardDb db, Scenario sc, boolean revealEnemyHand) {
        Random rnd = new Random(SEED + sc.name().hashCode());
        Match match = TestMatches.create("", db);
        Game game = match.createGame();
        Player you = player(game, TestMatches.YOU);
        Player enemy = player(game, TestMatches.ENEMY);

        for (String relic : List.of(RelicTest.PASSIVE, RelicTest.TRIGGERED, RelicTest.ACTIVATED, RelicTest.CONSUMABLE)) {
            you.getZone(ZoneType.Command).add(Card.fromPaperCard(db.getCard(relic), you));
        }
        enemy.getZone(ZoneType.Command).add(Card.fromPaperCard(db.getCard(TestMatches.ENEMY_EFFECT), enemy));

        List<Card> youCreatures = fillSide(game, you, sc, rnd);
        List<Card> enemyCreatures = fillSide(game, enemy, sc, rnd);

        PhaseType phase = sc.combat() ? PhaseType.COMBAT_DECLARE_BLOCKERS : PhaseType.MAIN1;
        game.getPhaseHandler().devModeSet(phase, you, 7);
        if (sc.horde()) {
            horde(game, db, you, enemy);
        } else if (sc.combat()) {
            Combat combat = new Combat(you);
            List<Card> attackers = youCreatures.subList(0, youCreatures.size() / 2);
            for (Card a : attackers) {
                a.setSickness(false);
                a.setTapped(true);
                combat.addAttacker(a, enemy);
            }
            for (int i = 0; i < Math.min(attackers.size(), enemyCreatures.size()) / 2; i++) {
                combat.addBlocker(attackers.get(i), enemyCreatures.get(i));
            }
            game.getPhaseHandler().setCombat(combat);
        }
        addStack(game, sc.stack(), you, enemy, rnd);
        game.getAction().checkStaticAbilities();

        PlayerLook[] looks = TestMatches.looks("");
        SnapshotBuilder builder = new SnapshotBuilder(game, you, enemy, looks[0], looks[1]);
        builder.build(false);   // warm-up
        long t0 = System.nanoTime();
        MatchSnapshot snap = builder.build(revealEnemyHand);
        lastBuildMs = (System.nanoTime() - t0) / 1e6;
        return snap;
    }

    /**
     * Krenko and 200 red goblin tokens: 110 untapped, 60 attacking, 10 with a +1/+1 counter,
     * 20 just made (summoning sick); plus 12 black-red goblins (another token, its own pile),
     * and lands of one printing. The enemy has 40 soldier tokens, 6 of them blocking.
     */
    private static void horde(Game game, CardDb db, Player you, Player enemy) {
        var bf = you.getZone(ZoneType.Battlefield);
        Card krenko = Card.fromPaperCard(db.getCard("Krenko, Mob Boss"), you);
        bf.add(krenko);
        krenko.setSickness(false);
        PaperToken goblin = StaticData.instance().getAllTokens().getToken("r_1_1_goblin");
        PaperToken blackRed = StaticData.instance().getAllTokens().getToken("br_1_1_goblin");
        PaperToken soldier = StaticData.instance().getAllTokens().getToken("w_1_1_soldier");
        List<Card> attackers = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            Card t = CardFactory.getCard(goblin, you, game);
            if (i >= 170 && i < 180) {
                t.setCounters(forge.game.card.CounterType.getType("P1P1"), 1);
            }
            bf.add(t);
            t.setSickness(i >= 180);   // after add: entering the battlefield sets it
            if (i >= 110 && i < 170) {
                attackers.add(t);
            }
        }
        for (int i = 0; blackRed != null && i < 12; i++) {
            Card t = CardFactory.getCard(blackRed, you, game);
            bf.add(t);
            t.setSickness(false);
        }
        for (int i = 0; i < 8; i++) {
            Card m = Card.fromPaperCard(db.getCard("Mountain"), you);
            m.setTapped(i < 5);
            bf.add(m);
        }
        List<Card> blockers = new ArrayList<>();
        for (int i = 0; soldier != null && i < 40; i++) {
            Card t = CardFactory.getCard(soldier, enemy, game);
            enemy.getZone(ZoneType.Battlefield).add(t);
            t.setSickness(false);
            if (i < 6) {
                blockers.add(t);
            }
        }
        Combat combat = new Combat(you);
        for (Card a : attackers) {
            a.setTapped(true);
            combat.addAttacker(a, enemy);
        }
        for (int i = 0; i < blockers.size(); i++) {
            combat.addBlocker(attackers.get(i), blockers.get(i));
        }
        game.getPhaseHandler().setCombat(combat);
    }

    /** @return the side's creatures (tokens included), in board order */
    private static List<Card> fillSide(Game game, Player p, Scenario sc, Random rnd) {
        var bf = p.getZone(ZoneType.Battlefield);
        for (int i = 0; i < sc.lands(); i++) {
            Card land = Card.fromPaperCard(pick(lands, rnd), p);
            land.setTapped(i % 3 == 0);
            bf.add(land);
        }
        List<Card> made = new ArrayList<>();
        for (int i = 0; i < sc.creatures(); i++) {
            List<PaperCard> from = i % 5 == 0 && !abilityCreatures.isEmpty() ? abilityCreatures : creatures;
            Card c = Card.fromPaperCard(pick(from, rnd), p);
            c.setSickness(i % 4 == 0);
            c.setTapped(i % 7 == 3);
            bf.add(c);
            made.add(c);
        }
        List<PaperToken> tokenKinds = tokenKinds();
        for (int i = 0; i < sc.tokens() && !tokenKinds.isEmpty(); i++) {
            Card t = CardFactory.getCard(tokenKinds.get(i % tokenKinds.size()), p, game);
            t.setSickness(false);
            bf.add(t);
            made.add(t);
        }
        for (int i = 0; i < sc.auras() && !auras.isEmpty(); i++) {
            Card a = Card.fromPaperCard(pick(auras, rnd), p);
            bf.add(a);
            a.attachToEntity(made.get(rnd.nextInt(made.size())), null);
        }
        for (int i = 0; i < sc.equipment() && !equipment.isEmpty(); i++) {
            Card e = Card.fromPaperCard(pick(equipment, rnd), p);
            bf.add(e);
            if (i % 2 == 0) {
                e.attachToEntity(made.get(rnd.nextInt(made.size())), null);
            }
        }
        // Some damage so badges show up.
        for (int i = 0; i < made.size(); i += 6) {
            if (made.get(i).getNetToughness() > 1) {
                made.get(i).setDamage(1);
            }
        }
        List<PaperCard> handPool = new ArrayList<>(creatures);
        handPool.addAll(spells);
        for (int i = 0; i < sc.hand(); i++) {
            p.getZone(ZoneType.Hand).add(Card.fromPaperCard(pick(handPool, rnd), p));
        }
        for (int i = 0; i < 30; i++) {
            p.getZone(ZoneType.Library).add(Card.fromPaperCard(pick(handPool, rnd), p));
        }
        for (int i = 0; i < 15; i++) {
            p.getZone(ZoneType.Graveyard).add(Card.fromPaperCard(pick(handPool, rnd), p));
        }
        for (int i = 0; i < 4; i++) {
            p.getZone(ZoneType.Exile).add(Card.fromPaperCard(pick(handPool, rnd), p));
        }
        p.setLife(7 + rnd.nextInt(20), null);
        return made;
    }

    private static List<PaperToken> tokenKinds;

    private static List<PaperToken> tokenKinds() {
        if (tokenKinds == null) {
            tokenKinds = new ArrayList<>();
            var db = StaticData.instance().getAllTokens();
            for (String script : List.of("r_1_1_goblin", "w_1_1_soldier", "g_3_3_beast", "u_1_1_bird_flying",
                "b_2_2_zombie", "c_1_1_a_thopter_flying")) {
                try {
                    PaperToken t = db.getToken(script);
                    if (t != null) {
                        tokenKinds.add(t);
                    }
                } catch (RuntimeException ignored) {
                    // token script not in this engine build
                }
            }
        }
        return tokenKinds;
    }

    /** Alternating spells and activated abilities from both players. */
    private static void addStack(Game game, int depth, Player you, Player enemy, Random rnd) {
        int added = 0;
        for (int i = 0; added < depth && i < depth * 4; i++) {
            Player p = i % 2 == 0 ? you : enemy;
            try {
                SpellAbility sa;
                if (i % 3 == 2) {
                    List<Card> sources = p.getCardsIn(ZoneType.Battlefield).stream()
                        .filter(c -> c.getSpellAbilities().stream()
                            .anyMatch(a -> a.isActivatedAbility() && !a.isManaAbility() && !a.usesTargeting()))
                        .toList();
                    if (sources.isEmpty()) {
                        continue;
                    }
                    Card src = sources.get(rnd.nextInt(sources.size()));
                    sa = src.getSpellAbilities().stream()
                        .filter(a -> a.isActivatedAbility() && !a.isManaAbility() && !a.usesTargeting())
                        .findFirst().orElseThrow().copy(src, p, false);
                } else {
                    if (spells.isEmpty()) {
                        continue;
                    }
                    Card c = Card.fromPaperCard(pick(spells, rnd), p);
                    sa = c.getFirstSpellAbility();
                    if (sa == null || sa.usesTargeting()) {
                        continue;
                    }
                    p.getZone(ZoneType.Hand).add(c);   // cast from hand: the stack moves it
                }
                sa.setActivatingPlayer(p);
                game.getStack().add(sa);
                if (sa.isSpell()) {
                    p.getZone(ZoneType.Hand).remove(sa.getHostCard());   // now on the stack only
                }
                added++;
            } catch (RuntimeException e) {
                System.err.println("  (stack item skipped: " + e + ")");
            }
        }
    }

    private static <T> T pick(List<T> list, Random rnd) {
        return list.get(rnd.nextInt(list.size()));
    }

    private static Player player(Game game, String name) {
        return game.getRegisteredPlayers().stream().filter(p -> p.getName().equals(name)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ rendering and measuring

    private static String describe(Scenario sc, MatchSnapshot s) {
        return String.format("== %s: per side %d creatures + %d tokens, %d lands, %d auras, %d equipment; "
                + "you: %d permanents, enemy: %d; hands %d/%d; stack %d; phase %s%n",
            sc.name(), sc.creatures(), sc.tokens(), sc.lands(), sc.auras(), sc.equipment(),
            s.you().battlefield().size(), s.enemy().battlefield().size(),
            s.you().hand().size(), s.enemy().hand().size(), s.stack().size(), s.phase());
    }

    private static String render(File dir, String name, MatchSnapshot snap, int w, int h, boolean collapsed) throws Exception {
        return render(dir, name, snap, w, h, collapsed, false);
    }

    private static String render(File dir, String name, MatchSnapshot snap, int w, int h, boolean collapsed,
                                 boolean stackList) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        StringBuilder rep = new StringBuilder();
        SwingUtilities.invokeAndWait(() -> {
            MatchView view = new MatchView(new NoControls());
            view.setSize(w, h);
            view.rescale();
            view.update(snap);
            if (collapsed) {
                view.setStackColumnCollapsed(true);
            }
            if (stackList) {
                view.toggleStackDetails();
            }
            for (int i = 0; i < 3; i++) {
                layoutTree(view);
            }
            Graphics2D g = img.createGraphics();
            view.printAll(g);
            g.dispose();

            // Repaint timing (everything already loaded by the first paint).
            int runs = 10;
            long t0 = System.nanoTime();
            for (int i = 0; i < runs; i++) {
                Graphics2D g2 = img.createGraphics();
                view.printAll(g2);
                g2.dispose();
            }
            double ms = (System.nanoTime() - t0) / 1e6 / runs;

            rep.append(String.format("  %s  full repaint %.1f ms%n", name, ms));
            rep.append(measureRows(view));
            rep.append(measurePopup(view));
        });
        ImageIO.write(img, "png", new File(dir, name + ".png"));
        return rep.toString();
    }

    private static final String[] ROW_NAMES = {"enemy hand", "enemy lands", "enemy other", "enemy creatures",
        "your creatures", "your other", "your lands", "your hand"};

    /** Per row: card size and the narrowest visible strip (= hover area) of an overlapped card. */
    private static String measureRows(MatchView view) {
        List<CardRow> rows = new ArrayList<>();
        collect(view, CardRow.class, rows);
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            CardRow row = rows.get(r);
            List<Rectangle> boxes = hitBoxes(row);
            if (boxes.isEmpty()) {
                continue;
            }
            int cardH = boxes.get(0).height;
            int cardW = (int) (cardH * com.planesnomad.gui.ui.Theme.CARD_ASPECT);
            int minStrip = Integer.MAX_VALUE;
            for (int i = 0; i + 1 < boxes.size(); i++) {
                minStrip = Math.min(minStrip, boxes.get(i + 1).x - boxes.get(i).x);
            }
            Rectangle last = boxes.get(boxes.size() - 1);
            int overflow = last.x + last.width - row.getWidth();
            String spill = row.maxScroll() > 0 ? String.format("  (pans %d px: drag or wheel)", row.maxScroll())
                : overflow > 0 ? String.format("  << LAST CARD %d px PAST THE EDGE", overflow) : "";
            String strip = boxes.size() == 1 || minStrip >= cardW ? "no overlap"
                : String.format("narrowest visible strip %d px (%d%% of an untapped card)%s", minStrip,
                    Math.round(100.0 * minStrip / cardW), minStrip < 12 ? "  << TOO NARROW TO HOVER" : "");
            var piles = row.piles();
            int cards = piles.stream().mapToInt(p -> p.count()).sum();
            String pileText = piles.size() == cards ? String.format("%3d cards", cards)
                : String.format("%3d cards in %2d piles (largest %d)", cards, piles.size(),
                    piles.stream().mapToInt(p -> p.count()).max().orElse(0));
            sb.append(String.format("    %-16s %s, card %dx%d px, %s%s%n",
                r < ROW_NAMES.length ? ROW_NAMES[r] : "row " + r, pileText, cardW, cardH, strip, spill));
        }
        return sb.toString();
    }

    /** Which battlefield cards the stack pop-up covers at its default spot. */
    private static String measurePopup(MatchView view) {
        List<StackPopup> popups = new ArrayList<>();
        collect(view, StackPopup.class, popups);
        if (popups.isEmpty() || !popups.get(0).isVisible()) {
            return "    stack pop-up: hidden\n";
        }
        StackPopup popup = popups.get(0);
        Rectangle pr = SwingUtilities.convertRectangle(popup.getParent(), popup.getBounds(), view);
        List<CardRow> rows = new ArrayList<>();
        collect(view, CardRow.class, rows);
        StringBuilder sb = new StringBuilder(String.format("    stack pop-up at %d,%d size %dx%d%n",
            pr.x, pr.y, pr.width, pr.height));
        for (int r = 0; r < rows.size(); r++) {
            List<Rectangle> boxes = hitBoxes(rows.get(r));
            int hidden = 0;
            int partly = 0;
            for (int i = 0; i < boxes.size(); i++) {
                Rectangle b = boxes.get(i);
                // The visible strip: up to where the next card starts.
                int right = i + 1 < boxes.size() ? Math.min(b.x + b.width, boxes.get(i + 1).x) : b.x + b.width;
                Rectangle vis = SwingUtilities.convertRectangle(rows.get(r),
                    new Rectangle(b.x, b.y, Math.max(1, right - b.x), b.height), view);
                Rectangle cut = vis.intersection(pr);
                if (cut.isEmpty()) {
                    continue;
                }
                double share = (double) cut.width * cut.height / (vis.width * vis.height);
                if (share > 0.95) {
                    hidden++;
                } else if (share > 0.2) {
                    partly++;
                }
            }
            if (hidden + partly > 0) {
                sb.append(String.format("      covers in %s: %d fully, %d partly%s%n",
                    r < ROW_NAMES.length ? ROW_NAMES[r] : "row " + r, hidden, partly,
                    hidden > 0 ? "  << CARDS HIDDEN" : ""));
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<Rectangle> hitBoxes(CardRow row) {
        try {
            Field f = CardRow.class.getDeclaredField("hitBoxes");
            f.setAccessible(true);
            return new ArrayList<>((List<Rectangle>) f.get(row));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> void collect(Component c, Class<T> type, List<T> out) {
        if (type.isInstance(c)) {
            out.add(type.cast(c));
        }
        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, out);
            }
        }
    }

    private static void layoutTree(Component c) {
        if (c instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    private static List<CardSnapshot> allCards(MatchSnapshot s) {
        List<CardSnapshot> all = new ArrayList<>();
        for (var p : List.of(s.you(), s.enemy())) {
            if (p.portrait() != null) all.add(p.portrait());
            if (p.playmat() != null) all.add(p.playmat());
            all.addAll(p.featured());
            all.addAll(p.hand());
            all.addAll(p.battlefield());
            all.addAll(p.graveyard());
            all.addAll(p.exile());
        }
        s.stack().forEach(i -> {
            if (i.source() != null) all.add(i.source());
        });
        return all;
    }

    private static final class NoControls implements TopBar.Controls {
        @Override public void setPaused(boolean paused) { }
        @Override public void step() { }
        @Override public void setSpeed(double speed) { }
        @Override public void setRevealEnemyHand(boolean reveal) { }
        @Override public void newMatch() { }
    }

    private StressTest() { }
}

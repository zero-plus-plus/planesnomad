package com.planesnomad.gui;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.ui.CardGroups;
import com.planesnomad.gui.ui.CardGroups.Pile;
import com.planesnomad.gui.ui.CardPreview;
import com.planesnomad.gui.ui.CardRow;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks for piling identical cards ({@link CardGroups}) and selecting a number of a pile
 * ({@link CardRow}). No engine needed; runs headless.
 */
public final class CardGroupsTest {

    private static int failures;
    private static int nextId = 1;

    public static void main(String[] args) throws Exception {
        // Nonlands: more than 3 identical -> one pile; up to 3 -> drawn one by one.
        check("200 identical goblin tokens -> one pile of 200",
            sizes(CardGroups.group(goblins(200, false, ""), false, false)).equals(List.of(200)));
        check("3 identical creatures stay separate",
            sizes(CardGroups.group(goblins(3, false, ""), false, false)).equals(List.of(1, 1, 1)));
        check("4 identical creatures -> one pile",
            sizes(CardGroups.group(goblins(4, false, ""), false, false)).equals(List.of(4)));

        // Any difference splits a pile.
        List<CardSnapshot> mixed = new ArrayList<>(goblins(120, false, ""));
        mixed.addAll(goblins(80, true, ""));
        check("120 untapped + 80 tapped -> two piles", sizes(CardGroups.group(mixed, false, false)).equals(List.of(120, 80)));
        List<CardSnapshot> countered = new ArrayList<>(goblins(10, false, ""));
        countered.addAll(goblins(2, false, "P1P1=1"));
        check("goblins with a +1/+1 counter don't join the plain pile (10 pile + 2 singles)",
            sizes(CardGroups.group(countered, false, false)).equals(List.of(10, 1, 1)));
        List<CardSnapshot> attacking = new ArrayList<>(goblins(6, false, ""));
        attacking.addAll(withFlags(goblins(5, true, ""), true, false));
        check("attackers form their own pile", sizes(CardGroups.group(attacking, false, false)).equals(List.of(6, 5)));
        List<CardSnapshot> twoColors = new ArrayList<>(goblins(5, false, ""));
        twoColors.addAll(recolor(goblins(5, false, ""), "B"));
        check("same token in another color is a separate pile", sizes(CardGroups.group(twoColors, false, false)).equals(List.of(5, 5)));
        List<CardSnapshot> faceDown = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            faceDown.add(CardSnapshot.hidden(nextId++));
        }
        check("face-down / hidden cards are never piled", CardGroups.group(faceDown, false, false).size() == 5);

        // Lands: piles of at most 3 by default; the setting piles them all.
        List<CardSnapshot> mountains = lands("Mountain", 12);
        mountains.addAll(lands("Island", 2));
        check("12 Mountains + 2 Islands -> Mountains 3+3+3+3, Islands one pile of 2 (default)",
            sizes(CardGroups.group(mountains, true, false)).equals(List.of(3, 3, 3, 3, 2)));
        check("with 'stack lands beyond 3': Mountains one pile of 12, Islands 2",
            sizes(CardGroups.group(mountains, true, true)).equals(List.of(12, 2)));
        List<CardSnapshot> tappedLands = lands("Mountain", 4);
        tappedLands.addAll(tap(lands("Mountain", 4)));
        check("tapped and untapped lands pile separately (3+1 and 3+1)",
            sizes(CardGroups.group(tappedLands, true, false)).equals(List.of(3, 1, 3, 1)));

        // Different arts of the same card pile together by default; a setting keeps them apart.
        List<CardSnapshot> arts = new ArrayList<>(lands("Mountain", 2));
        arts.addAll(reprint(lands("Mountain", 2), "10E", "380", 2));
        check("Mountains from two sets/arts share piles by default (one pile of 3 + 1)",
            sizes(CardGroups.group(arts, true, false)).equals(List.of(3, 1)));
        check("with 'Keep different arts separate': 2 + 2",
            sizes(CardGroups.group(arts, true, false, true, java.util.Set.of(), java.util.Set.of())).equals(List.of(2, 2)));
        List<CardSnapshot> goblinArts = new ArrayList<>(goblins(3, false, ""));
        goblinArts.addAll(reprint(goblins(3, false, ""), "TEMA", "10", 1));
        check("6 identical goblin tokens with two different arts -> one pile of 6",
            sizes(CardGroups.group(goblinArts, false, false)).equals(List.of(6)));
        List<CardSnapshot> hasteGoblins = new ArrayList<>(goblins(4, false, ""));
        hasteGoblins.addAll(retext(goblins(4, false, ""), "Haste"));
        check("same-name tokens that work differently (one has haste) -> separate piles",
            sizes(CardGroups.group(hasteGoblins, false, false)).equals(List.of(4, 4)));

        // Pile keys are stable while the cards don't change, so a selection survives updates.
        List<CardSnapshot> horde = goblins(200, false, "");
        String key1 = CardGroups.group(horde, false, false).get(0).key();
        String key2 = CardGroups.group(goblins(150, false, ""), false, false).get(0).key();
        check("pile key doesn't depend on card ids or count", key1.equals(key2));

        // Selecting a number of a pile in a row.
        SwingUtilities.invokeAndWait(() -> {
            CardRow row = new CardRow("Creatures", new CardPreview(), false);
            row.setGrouping(true, false);
            row.setSize(900, 120);
            row.setCards(horde);
            Pile pile = row.piles().get(0);
            row.select(pile.key(), 37);
            check("select 37 of 200 -> 37 card ids", row.selectedCardIds().size() == 37);
            row.setCards(goblins(200, false, ""));   // next snapshot: same pile, new card objects
            check("selection kept after an update that doesn't change the pile", row.selectedIn(pile.key()) == 37);
            List<CardSnapshot> fewer = goblins(20, false, "");
            row.setCards(fewer);
            check("pile shrinks to 20 -> selection clamped to 20", row.selectedIn(pile.key()) == 20);
            List<CardSnapshot> nowTapped = tap(goblins(20, false, ""));
            row.setCards(nowTapped);
            check("pile's cards change state (tapped) -> old selection dropped", row.selectedCardIds().isEmpty());
            row.select(row.piles().get(0).key(), 999);
            check("selecting more than the pile has -> all of it", row.selectedCardIds().size() == 20);

            // Right-click: separate a pile back into single cards, combine small groups.
            row.setCards(horde);
            String base = row.piles().get(0).baseKey();
            row.separate(base);
            check("'Separate this pile': 200 goblins drawn one by one", row.piles().size() == 200);
            row.setCards(goblins(200, false, ""));
            check("separated stays separated after an update", row.piles().size() == 200);
            row.combine(base);
            check("'Combine': back to one pile", row.piles().size() == 1 && row.piles().get(0).count() == 200);
            List<CardSnapshot> three = goblins(3, false, "");
            row.setCards(three);
            check("your 'Combine' choice holds while identical cards remain (197 died, 3 left: still a pile)",
                row.piles().size() == 1 && row.piles().get(0).count() == 3);
            row.resetGrouping();
            check("3 identical are separate by default", row.piles().size() == 3);
            row.combine(row.piles().get(0).baseKey());
            check("'Combine' 3 identical -> one pile of 3", row.piles().size() == 1 && row.piles().get(0).count() == 3);
            row.resetGrouping();
            check("'Automatic piles' -> back to 3 singles", row.piles().size() == 3);
            List<CardSnapshot> mixedRow = new ArrayList<>(goblins(6, false, ""));
            mixedRow.addAll(goblins(2, true, ""));
            row.setCards(mixedRow);
            row.separateAll();
            check("'Separate all piles in this row' -> 8 single cards", row.piles().size() == 8);
            row.combineAll();
            check("'Combine identical cards in this row' -> 2 piles (6 untapped, 2 tapped)",
                sizes(row.piles()).equals(List.of(6, 2)));
            row.setCards(goblins(4, false, "P1P1=1"));
            check("a choice is forgotten once those cards are gone (new group uses the default: 1 pile of 4)",
                sizes(row.piles()).equals(List.of(4)));
            CardRow landsRow = new CardRow("Lands", new CardPreview(), false);
            landsRow.setGrouping(true, true);
            landsRow.setCards(lands("Mountain", 7));
            check("lands: 7 Mountains -> 3+3+1", sizes(landsRow.piles()).equals(List.of(3, 3, 1)));
            landsRow.combine(landsRow.piles().get(0).baseKey());
            check("lands: 'Combine' -> one pile of 7", sizes(landsRow.piles()).equals(List.of(7)));

            // Panning: separated, 200 cards don't fit; cards keep a third visible and the row pans.
            CardRow wide = new CardRow("Creatures", new CardPreview(), false);
            wide.setGrouping(true, false);
            wide.setSize(900, 120);
            wide.setCards(horde);
            wide.separate(wide.piles().get(0).baseKey());
            BufferedImage scratch = new BufferedImage(900, 120, BufferedImage.TYPE_INT_RGB);
            Graphics2D sg = scratch.createGraphics();
            wide.paint(sg);
            check("200 separate cards in a 900 px row: the row pans (" + wide.maxScroll() + " px)", wide.maxScroll() > 0);
            List<java.awt.Rectangle> boxes = hitBoxes(wide);
            int strip = boxes.get(1).x - boxes.get(0).x;
            check("  ... each card keeps about a third visible (" + strip + " of " + boxes.get(0).width + " px)",
                strip >= boxes.get(0).width * 0.3);
            wide.scrollTo(400);
            wide.paint(sg);
            check("  ... panning moves the cards (first card now at x=" + hitBoxes(wide).get(0).x + ")",
                wide.scroll() == 400 && hitBoxes(wide).get(0).x < boxes.get(0).x - 350);
            wide.scrollTo(1_000_000);
            wide.paint(sg);
            java.awt.Rectangle last = hitBoxes(wide).get(hitBoxes(wide).size() - 1);
            check("  ... panned to the end, the last card is fully on screen",
                wide.scroll() == wide.maxScroll() && last.x + last.width <= 900);
            wide.combine(wide.piles().get(0).baseKey());
            wide.paint(sg);
            check("  ... combined back into a pile, nothing to pan", wide.maxScroll() == 0 && wide.scroll() == 0);
            sg.dispose();

            // Drawing: a row of 200 goblins is one pile, not 200 slivers.
            row.resetGrouping();
            BufferedImage img = new BufferedImage(900, 120, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            row.setCards(horde);
            row.paint(g);
            row.setCards(mixed);
            row.paint(g);
            g.dispose();
            check("row with 120 untapped + 80 tapped goblins draws 2 piles", row.piles().size() == 2);
        });

        System.out.println();
        if (failures == 0) {
            System.out.println("ALL CARD-GROUP CHECKS PASSED");
        } else {
            System.out.println(failures + " CARD-GROUP CHECK(S) FAILED");
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<java.awt.Rectangle> hitBoxes(CardRow row) {
        try {
            var f = CardRow.class.getDeclaredField("hitBoxes");
            f.setAccessible(true);
            return new ArrayList<>((List<java.awt.Rectangle>) f.get(row));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Integer> sizes(List<Pile> piles) {
        return piles.stream().map(Pile::count).toList();
    }

    private static List<CardSnapshot> goblins(int n, boolean tapped, String counters) {
        List<CardSnapshot> out = new ArrayList<>();
        int p = counters.contains("P1P1") ? 2 : 1;
        for (int i = 0; i < n; i++) {
            out.add(new CardSnapshot(nextId++, "Goblin", "TM10", "", "R", "", "Token Creature - Goblin", "",
                p, p, 0, tapped, true, false, true, false, false, false, false, "", 1, "normal",
                "", 1, 1, false, counters, 0, ""));
        }
        return out;
    }

    private static List<CardSnapshot> lands(String name, int n) {
        List<CardSnapshot> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new CardSnapshot(nextId++, name, "M10", "1", "", "", "Basic Land - " + name, "",
                null, null, 0, false, false, true, false, false, false, false, false, "", 1, "normal"));
        }
        return out;
    }

    private static List<CardSnapshot> tap(List<CardSnapshot> cards) {
        return cards.stream().map(c -> new CardSnapshot(c.id(), c.name(), c.setCode(), c.collectorNumber(),
            c.colors(), c.manaCost(), c.typeLine(), c.rulesText(), c.power(), c.toughness(), c.damage(), true,
            c.creature(), c.land(), c.token(), c.attacking(), c.blocking(), c.summoningSick(), c.faceDown(),
            c.imageKey(), c.artIndex(), c.layout(), c.granted(), c.printedPower(), c.printedToughness(),
            c.ability(), c.counters(), c.attachedTo(), c.attachments())).collect(java.util.stream.Collectors.toList());
    }

    private static List<CardSnapshot> withFlags(List<CardSnapshot> cards, boolean attacking, boolean blocking) {
        return cards.stream().map(c -> new CardSnapshot(c.id(), c.name(), c.setCode(), c.collectorNumber(),
            c.colors(), c.manaCost(), c.typeLine(), c.rulesText(), c.power(), c.toughness(), c.damage(), c.tapped(),
            c.creature(), c.land(), c.token(), attacking, blocking, c.summoningSick(), c.faceDown(),
            c.imageKey(), c.artIndex(), c.layout(), c.granted(), c.printedPower(), c.printedToughness(),
            c.ability(), c.counters(), c.attachedTo(), c.attachments())).toList();
    }

    private static List<CardSnapshot> reprint(List<CardSnapshot> cards, String set, String number, int art) {
        return cards.stream().map(c -> new CardSnapshot(c.id(), c.name(), set, number,
            c.colors(), c.manaCost(), c.typeLine(), c.rulesText(), c.power(), c.toughness(), c.damage(), c.tapped(),
            c.creature(), c.land(), c.token(), c.attacking(), c.blocking(), c.summoningSick(), c.faceDown(),
            set + "/" + c.name() + art + ".full", art, c.layout(), c.granted(), c.printedPower(), c.printedToughness(),
            c.ability(), c.counters(), c.attachedTo(), c.attachments())).toList();
    }

    private static List<CardSnapshot> retext(List<CardSnapshot> cards, String text) {
        return cards.stream().map(c -> new CardSnapshot(c.id(), c.name(), c.setCode(), c.collectorNumber(),
            c.colors(), c.manaCost(), c.typeLine(), text, c.power(), c.toughness(), c.damage(), c.tapped(),
            c.creature(), c.land(), c.token(), c.attacking(), c.blocking(), c.summoningSick(), c.faceDown(),
            c.imageKey(), c.artIndex(), c.layout(), c.granted(), c.printedPower(), c.printedToughness(),
            c.ability(), c.counters(), c.attachedTo(), c.attachments())).toList();
    }

    private static List<CardSnapshot> recolor(List<CardSnapshot> cards, String colors) {
        return cards.stream().map(c -> new CardSnapshot(c.id(), c.name(), c.setCode(), c.collectorNumber(),
            colors, c.manaCost(), c.typeLine(), c.rulesText(), c.power(), c.toughness(), c.damage(), c.tapped(),
            c.creature(), c.land(), c.token(), c.attacking(), c.blocking(), c.summoningSick(), c.faceDown(),
            c.imageKey(), c.artIndex(), c.layout(), c.granted(), c.printedPower(), c.printedToughness(),
            c.ability(), c.counters(), c.attachedTo(), c.attachments())).toList();
    }

    private static void check(String description, boolean ok) {
        System.out.println((ok ? "  PASS  " : "  FAIL  ") + description);
        if (!ok) {
            failures++;
        }
    }

    private CardGroupsTest() { }
}

package com.planesnomad.gui;

import com.planesnomad.ForgeEngine;
import com.planesnomad.encounter.Encounters;
import com.planesnomad.gui.engine.SnapshotBuilder;
import com.planesnomad.gui.images.CardImages;
import com.planesnomad.gui.images.ImageSetup;
import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.MatchSnapshot.StackItem;
import com.planesnomad.gui.model.MatchSnapshot.Target;
import com.planesnomad.gui.model.PlayerSnapshot;
import com.planesnomad.gui.ui.MatchView;
import com.planesnomad.gui.ui.TopBar;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Screenshots of hand-built boards for features that are hard to catch in a real match:
 * an enemy Unsummon aimed at your creature (and your Bolt aimed at the enemy), blockers lined up
 * across from their attackers, and the reward screen (list and card choice).
 *
 * Usage: FeatureShots <output folder>
 */
public final class FeatureShots {

    private static int nextId = 1000;

    public static void main(String[] args) throws Exception {
        ForgeEngine.initialize();
        File out = new File(args.length > 0 ? args[0] : "feature-shots");
        out.mkdirs();
        CardImages images = ImageSetup.create();

        // 1. Targets: enemy Unsummon on your Shivan Dragon; your Lightning Bolt at the enemy.
        CardSnapshot dragon = card("Shivan Dragon", false, false);
        List<CardSnapshot> yourCreatures = List.of(card("Goblin Raider", false, false), card("Goblin Raider", false, false), dragon);
        List<CardSnapshot> enemyCreatures = List.of(card("Lord of Atlantis", false, false), card("Silvergill Adept", false, false));
        CardSnapshot unsummon = card("Unsummon", false, false);
        CardSnapshot bolt = card("Lightning Bolt", false, false);
        MatchSnapshot targets = snapshot(yourCreatures, enemyCreatures, List.of(
            new StackItem("Merfolk Tidecaller", "Merfolk Tidecaller casts Unsummon", "Return Shivan Dragon to its owner's hand.",
                false, StackItem.Kind.SPELL, false, unsummon, null, 2,
                List.of(new Target(Target.Kind.CARD, dragon.id(), dragon.name(), true))),
            new StackItem("You", "You cast Lightning Bolt", "Lightning Bolt deals 3 damage to Merfolk Tidecaller.",
                true, StackItem.Kind.SPELL, false, bolt, null, 1,
                List.of(new Target(Target.Kind.PLAYER, 0, "Merfolk Tidecaller", false)))), Map.of());
        render(out, "targets", targets, images, v -> { });

        // 2. Blocks: you attack with three creatures; the enemy double-blocks the left one and
        //    blocks the right one; the blockers move across from what they block, with arrows.
        CardSnapshot a1 = card("Goblin Raider", true, false);
        CardSnapshot a2 = card("Raging Goblin", true, false);
        CardSnapshot a3 = card("Shivan Dragon", true, false);
        CardSnapshot b1 = card("Silvergill Adept", false, true);
        CardSnapshot b2 = card("Merfolk Looter", false, true);
        CardSnapshot b3 = card("Lord of Atlantis", false, true);
        CardSnapshot idle = card("Master of the Pearl Trident", false, false);
        MatchSnapshot blocks = snapshot(List.of(a1, a2, a3, card("Goblin King", false, false)),
            List.of(b3, idle, b1, b2), List.of(),
            Map.of(b1.id(), List.of(a1.id()), b2.id(), List.of(a1.id()), b3.id(), List.of(a3.id())));
        render(out, "blocks", blocks, images, v -> { });

        // 3. Reward screen: the list, then the card choice.
        var rewards = RewardOffers.roll(Encounters.MERFOLK.rewards(), new Random(3));
        List<CardSnapshot> offered = new ArrayList<>();
        rewards.forEach(r -> offered.addAll(r.cards));
        images.preloadWithDownloads(offered);
        MatchSnapshot end = snapshot(List.of(a1), List.of(), List.of(), Map.of());
        render(out, "rewards", end, images, v -> v.showRewards("Victory!", "Merfolk of the Tide defeated",
            RewardOffers.roll(Encounters.MERFOLK.rewards(), new Random(3)), o -> { }));
        render(out, "rewards-cards", end, images, v -> {
            v.showRewards("Victory!", "Merfolk of the Tide defeated",
                RewardOffers.roll(Encounters.MERFOLK.rewards(), new Random(3)), o -> { });
            v.rewardScreen().clickRow(0);   // took the gold
            v.rewardScreen().openCardChoice();
        });
        render(out, "rewards-taken", end, images, v -> {
            v.showRewards("Victory!", "Merfolk of the Tide defeated",
                RewardOffers.roll(Encounters.MERFOLK.rewards(), new Random(3)), o -> { });
            v.rewardScreen().clickRow(0);
            v.rewardScreen().openCardChoice();
            v.rewardScreen().pickCard(1);
            v.rewardScreen().clickRow(2);
        });
        System.exit(0);
    }

    static CardSnapshot card(String name, boolean attacking, boolean blocking) {
        CardSnapshot c = SnapshotBuilder.named(name);
        if (c == null) {
            throw new IllegalArgumentException("No card " + name);
        }
        return new CardSnapshot(nextId++, c.name(), c.setCode(), c.collectorNumber(), c.colors(), c.manaCost(),
            c.typeLine(), c.rulesText(), c.power(), c.toughness(), 0, attacking, c.creature(), c.land(), false,
            attacking, blocking, false, false, c.imageKey(), c.artIndex(), c.layout(), "",
            c.printedPower(), c.printedToughness(), false);
    }

    static MatchSnapshot snapshot(List<CardSnapshot> yours, List<CardSnapshot> enemy, List<StackItem> stack,
                                          Map<Integer, List<Integer>> blocks) {
        List<CardSnapshot> yourLands = new ArrayList<>();
        List<CardSnapshot> enemyLands = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            yourLands.add(card("Mountain", false, false));
            enemyLands.add(card("Island", false, false));
        }
        List<CardSnapshot> yb = new ArrayList<>(yours);
        yb.addAll(yourLands);
        List<CardSnapshot> eb = new ArrayList<>(enemy);
        eb.addAll(enemyLands);
        PlayerSnapshot you = new PlayerSnapshot("You", "Red goblins and burn", 17, 20, 40,
            List.of(card("Fireball", false, false), card("Mountain", false, false)), yb, List.of(), List.of(), List.of(),
            true, false, SnapshotBuilder.named("Krenko, Mob Boss"), SnapshotBuilder.named("Mountain"), List.of());
        PlayerSnapshot foe = new PlayerSnapshot("Merfolk Tidecaller", "Merfolk lords and counterspells", 14, 20, 40,
            List.of(CardSnapshot.hidden(1), CardSnapshot.hidden(2), CardSnapshot.hidden(3)), eb, List.of(), List.of(), List.of(),
            false, false, SnapshotBuilder.named("Lord of Atlantis"), SnapshotBuilder.named("Island"),
            List.of(SnapshotBuilder.named("Lord of Atlantis")), Map.of("Lord of Atlantis", "Turn 2"));
        return new MatchSnapshot(4, "You", "Declare Blockers", you, foe, stack, List.of(), null, List.of(), blocks);
    }

    private static void render(File dir, String name, MatchSnapshot s, CardImages images, Consumer<MatchView> extra) throws Exception {
        List<CardSnapshot> all = new ArrayList<>();
        all.addAll(s.you().battlefield());
        all.addAll(s.enemy().battlefield());
        all.addAll(s.you().hand());
        s.stack().forEach(i -> all.add(i.source()));
        images.preloadWithDownloads(all);
        for (int[] size : new int[][] {{1280, 720}, {1920, 1080}}) {
            BufferedImage img = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
            SwingUtilities.invokeAndWait(() -> {
                MatchView view = new MatchView(new NoControls());
                view.setSize(size[0], size[1]);
                view.rescale();
                view.update(s);
                layoutTree(view);
                extra.accept(view);
                layoutTree(view);
                // Painted twice: in the first pass the rows learn where the attackers are, in the
                // second the blockers line up across from them.
                for (int pass = 0; pass < 2; pass++) {
                    Graphics2D g = img.createGraphics();
                    view.printAll(g);
                    g.dispose();
                }
            });
            File f = new File(dir, name + "-" + size[0] + "x" + size[1] + ".png");
            ImageIO.write(img, "png", f);
            System.out.println("  " + f.getName());
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

    private static final class NoControls implements TopBar.Controls {
        @Override public void setPaused(boolean value) { }
        @Override public void step() { }
        @Override public void setSpeed(double value) { }
        @Override public void setRevealEnemyHand(boolean value) { }
        @Override public void newMatch() { }
    }

    private FeatureShots() { }
}

package com.planesnomad.gui.ui;

import com.planesnomad.gui.model.CardSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Groups identical battlefield cards into piles, so a board with e.g. 200 goblin tokens stays
 * readable. Only mechanically identical cards share a pile: same name, same controller (each row
 * belongs to one player), same colors, types, text and current state (tapped, power/toughness,
 * damage, counters, granted abilities, attachments, summoning sickness, combat role, ...).
 * Different arts of the same card (e.g. Mountains from several sets) pile together by default;
 * a setting keeps each art separate. Tokens with the same name that work differently (e.g. a 1/1
 * Goblin and a 2/1 haste Goblin) differ in their text or stats, so they never share a pile.
 * Any difference splits the pile, e.g. tapping 80 of 200 goblins gives a pile of 120 and one of 80.
 *
 * <ul>
 *   <li>Nonlands: more than {@value #NONLAND_THRESHOLD} identical cards form one pile (drawn as
 *       three cards with a count); up to {@value #NONLAND_THRESHOLD} are drawn one by one.</li>
 *   <li>Lands: identical lands are piled in groups of at most {@value #LAND_PILE_MAX} by default;
 *       a setting lets them pile without limit.</li>
 * </ul>
 * A pile keeps the board position of its first card.
 */
public final class CardGroups {

    /** Nonlands pile only when there are more than this many identical cards. */
    public static final int NONLAND_THRESHOLD = 3;
    /** Default largest land pile. */
    public static final int LAND_PILE_MAX = 3;
    /** How many cards of a pile are drawn (the rest is shown by the count). */
    public static final int DRAWN = 3;

    /**
     * One pile, or a single card (a pile of one).
     * @param key     this pile's identity (for selections); stable while its cards' state is unchanged
     * @param baseKey identity shared by all cards identical to this pile's (for combine/separate)
     */
    public record Pile(String key, String baseKey, List<CardSnapshot> cards) {
        public CardSnapshot top() {
            return cards.get(cards.size() - 1);
        }

        public int count() {
            return cards.size();
        }

        public boolean isPile() {
            return cards.size() > 1;
        }
    }

    /**
     * @param lands             true for a land row
     * @param stackLandsFully   setting: pile identical lands without the limit of 3
     */
    public static List<Pile> group(List<CardSnapshot> cards, boolean lands, boolean stackLandsFully) {
        return group(cards, lands, stackLandsFully, false, Set.of(), Set.of());
    }

    /**
     * With the player's own choices (from the right-click menu), by base key:
     * @param separated groups to show card by card, even if large
     * @param combined  groups to show as one pile, even if small (2+ cards), lands without a limit
     * @param byArt     setting: different arts/printings of the same card in separate piles
     */
    public static List<Pile> group(List<CardSnapshot> cards, boolean lands, boolean stackLandsFully,
                                   boolean byArt, Set<String> separated, Set<String> combined) {
        Map<String, List<CardSnapshot>> byKey = new LinkedHashMap<>();
        int unique = 0;
        for (CardSnapshot c : cards) {
            // Hidden or face-down cards are never grouped: the viewer can't know they are the same.
            String key = c.faceDown() || c.isHidden() ? "#unique" + (unique++) + "#" + c.id() : key(c, byArt);
            byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }
        List<Pile> out = new ArrayList<>();
        for (Map.Entry<String, List<CardSnapshot>> e : byKey.entrySet()) {
            String base = e.getKey();
            List<CardSnapshot> same = e.getValue();
            boolean landGroup = lands && same.get(0).land();
            if (separated.contains(base)) {
                singles(out, base, same);
            } else if (combined.contains(base) && same.size() > 1) {
                out.add(new Pile(base, base, List.copyOf(same)));
            } else if (landGroup && !stackLandsFully) {
                for (int i = 0, n = 0; i < same.size(); i += LAND_PILE_MAX, n++) {
                    out.add(new Pile(base + "#" + n, base, List.copyOf(same.subList(i, Math.min(same.size(), i + LAND_PILE_MAX)))));
                }
            } else if (landGroup || same.size() > NONLAND_THRESHOLD) {
                out.add(new Pile(base, base, List.copyOf(same)));
            } else {
                singles(out, base, same);
            }
        }
        return out;
    }

    private static void singles(List<Pile> out, String base, List<CardSnapshot> same) {
        for (int i = 0; i < same.size(); i++) {
            out.add(new Pile(base + "#" + i, base, List.of(same.get(i))));
        }
    }

    /**
     * Everything about a card that matters to the rules, except its id; with {@code byArt} also
     * its printing (set, number, picture).
     */
    static String key(CardSnapshot c, boolean byArt) {
        String printing = byArt
            ? String.join("/", c.setCode(), c.collectorNumber(), c.imageKey(), String.valueOf(c.artIndex()))
            : "";
        return String.join("\u0001",
            c.name(), printing, c.colors(), c.manaCost(), c.typeLine(), c.rulesText(), c.granted(),
            Objects.toString(c.power()), Objects.toString(c.toughness()), String.valueOf(c.damage()),
            flags(c), c.counters(), c.attachments(), String.valueOf(c.attachedTo()));
    }

    private static String flags(CardSnapshot c) {
        return (c.tapped() ? "T" : "t") + (c.token() ? "K" : "k") + (c.attacking() ? "A" : "a")
            + (c.blocking() ? "B" : "b") + (c.summoningSick() ? "S" : "s");
    }

    private CardGroups() { }
}

package com.planesnomad.gui.model;

import java.util.List;

/**
 * One moment in the match history (the icon bar beside the board): a spell cast, an ability or
 * trigger, a land, an attack, blocks, deaths, damage to a player, ... Several of the same kind in
 * a row (e.g. 37 goblins dying to one sweeper) are merged into one entry with a count.
 *
 * @param seq     running number (stable id within one match)
 * @param turn    turn number it happened in
 * @param kind    what happened (sets the icon)
 * @param byYou   true if the bottom player did it (or controlled the card it happened to)
 * @param actor   name of that player
 * @param title   one line, e.g. "Blue AI casts Counterspell"
 * @param details more lines, e.g. targets, attackers and blockers, damage per source
 * @param cards   the cards involved (the first one is shown as the icon); may be capped
 * @param count   how many things the entry stands for (e.g. 37 deaths), at least 1
 * @param relic   set if the source is a relic or consumable: shown as the relic, not a card
 * @param effects what this action led to, in order (e.g. "Coral Merfolk takes 3 damage",
 *                "Coral Merfolk dies", "Wayfarer's Compass triggers"); capped, repeats merged ("×5")
 */
public record HistoryEntry(int seq, int turn, Kind kind, boolean byYou, String actor, String title,
                           List<String> details, List<CardSnapshot> cards, int count, RelicSnapshot relic,
                           List<String> effects) {

    public enum Kind {
        CAST("Spell"), ABILITY("Ability"), TRIGGER("Trigger"), LAND("Land"),
        ATTACK("Attack"), BLOCK("Blocks"), DEATH("Died"), EXILE("Exiled"), BOUNCE("Returned"),
        TOKENS("Tokens"), DAMAGE("Damage"), GAME_END("Game over");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    public HistoryEntry {
        details = List.copyOf(details);
        cards = List.copyOf(cards);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    /** Without effects. */
    public HistoryEntry(int seq, int turn, Kind kind, boolean byYou, String actor, String title,
                        List<String> details, List<CardSnapshot> cards, int count, RelicSnapshot relic) {
        this(seq, turn, kind, byYou, actor, title, details, cards, count, relic, List.of());
    }

    public HistoryEntry withEffects(List<String> newEffects) {
        return new HistoryEntry(seq, turn, kind, byYou, actor, title, details, cards, count, relic, newEffects);
    }

    /** The card shown as this entry's icon, or null. */
    public CardSnapshot icon() {
        return cards.isEmpty() ? null : cards.get(0);
    }

    /** Spells are shown larger, as small cards; everything else as a small icon. */
    public boolean large() {
        return kind == Kind.CAST;
    }
}

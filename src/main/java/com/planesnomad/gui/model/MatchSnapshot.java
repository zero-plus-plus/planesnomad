package com.planesnomad.gui.model;

import java.util.List;

/**
 * Immutable picture of a match at one moment, built on the engine thread and handed to
 * the Swing thread. The GUI only ever reads these, never live engine objects, which keeps
 * the two threads apart and lets the same screen be driven by a human-vs-AI match later.
 *
 * @param turn         turn number (1-based)
 * @param activePlayer name of the player whose turn it is
 * @param phase        phase name for display, e.g. "Declare Attackers"
 * @param you          the bottom player (the one who owns relics in a run)
 * @param enemy        the top player
 * @param stack        stack items, top of the stack first
 * @param log          plain-language game log lines, oldest first
 * @param result       null while the match is running; otherwise the end-of-match text
 */
public record MatchSnapshot(
    int turn,
    String activePlayer,
    String phase,
    PlayerSnapshot you,
    PlayerSnapshot enemy,
    List<StackItem> stack,
    List<LogLine> log,
    String result
) {
    public boolean finished() {
        return result != null;
    }

    /**
     * One stack entry in plain language.
     *
     * @param text   headline, e.g. "Blue AI casts Counterspell"
     * @param detail what it will do, e.g. "Counter Goblin Raider."
     * @param byYou  true if the bottom player controls it (drawn on that side's color)
     * @param kind   spell, activated ability or trigger (abilities are shown with their source card)
     * @param resolving true while this item is resolving, e.g. waiting for a player's choice
     * @param relic  set if the source is a relic or consumable: show it as a relic, not a card
     */
    public record StackItem(String controller, String text, String detail, boolean byYou,
                            Kind kind, boolean resolving, CardSnapshot source, RelicSnapshot relic) {
        public enum Kind { SPELL, ABILITY, TRIGGER }
    }

    /** One game log line. Turn headers are shown differently. */
    public record LogLine(String text, boolean turnHeader) { }
}

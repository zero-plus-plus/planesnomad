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
 * @param history      what happened so far, oldest first (the history icon bar)
 * @param blocks       in combat: blocker card id -> the attackers it blocks (ids)
 */
public record MatchSnapshot(
    int turn,
    String activePlayer,
    String phase,
    PlayerSnapshot you,
    PlayerSnapshot enemy,
    List<StackItem> stack,
    List<LogLine> log,
    String result,
    List<HistoryEntry> history,
    java.util.Map<Integer, List<Integer>> blocks
) {
    public MatchSnapshot {
        history = history == null ? List.of() : history;
        blocks = blocks == null ? java.util.Map.of() : java.util.Map.copyOf(blocks);
    }

    /** Without blocks. */
    public MatchSnapshot(int turn, String activePlayer, String phase, PlayerSnapshot you, PlayerSnapshot enemy,
                         List<StackItem> stack, List<LogLine> log, String result, List<HistoryEntry> history) {
        this(turn, activePlayer, phase, you, enemy, stack, log, result, history, java.util.Map.of());
    }

    /** Without history (e.g. a board built directly for a test). */
    public MatchSnapshot(int turn, String activePlayer, String phase, PlayerSnapshot you, PlayerSnapshot enemy,
                         List<StackItem> stack, List<LogLine> log, String result) {
        this(turn, activePlayer, phase, you, enemy, stack, log, result, List.of(), java.util.Map.of());
    }

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
     * @param id     the engine's id of the stack entry (for choosing it as a target)
     * @param targets what it targets (cards, players, other stack items), shown on the board
     */
    public record StackItem(String controller, String text, String detail, boolean byYou,
                            Kind kind, boolean resolving, CardSnapshot source, RelicSnapshot relic, int id,
                            List<Target> targets) {
        public enum Kind { SPELL, ABILITY, TRIGGER }

        public StackItem {
            targets = targets == null ? List.of() : List.copyOf(targets);
        }

        /** Without targets. */
        public StackItem(String controller, String text, String detail, boolean byYou,
                         Kind kind, boolean resolving, CardSnapshot source, RelicSnapshot relic, int id) {
            this(controller, text, detail, byYou, kind, resolving, source, relic, id, List.of());
        }

        /** Without an id (e.g. built by a test). */
        public StackItem(String controller, String text, String detail, boolean byYou,
                         Kind kind, boolean resolving, CardSnapshot source, RelicSnapshot relic) {
            this(controller, text, detail, byYou, kind, resolving, source, relic, 0);
        }
    }

    /**
     * Something a stack item targets.
     * @param kind  a card (on the battlefield, in a hand or graveyard), a player, or another stack item
     * @param id    card id, or stack entry id; 0 for a player
     * @param name  card or player name, for text ("-> Grizzly Bears")
     * @param yours true if it is the bottom player, or a card the bottom player controls
     */
    public record Target(Kind kind, int id, String name, boolean yours) {
        public enum Kind { CARD, PLAYER, STACK }
    }

    /** One game log line. Turn headers are shown differently. */
    public record LogLine(String text, boolean turnHeader) { }
}

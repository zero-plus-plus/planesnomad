package com.planesnomad.encounter;

/**
 * A card an enemy is sure to get, shown face up in its effects bar ("T2" badge).
 * Today one kind: drawn on the enemy's own Nth turn (its draw for that turn is this card). Planned
 * kinds (see the design notes): by turn N, in the opening hand, starting in play, hidden or random.
 *
 * @param card name of the card (must be in the enemy's deck)
 * @param turn the enemy's own turn number on which it draws the card (1 = its first turn; an
 *             enemy that goes first skips that draw, so use 2 or later)
 */
public record Guarantee(String card, int turn) {

    /** Short note for the effects bar and hover text, e.g. "Turn 2". */
    public String note() {
        return "Turn " + turn;
    }
}

package com.planesnomad.gui.model;

/**
 * A relic or consumable as the player sees it: an icon with a tooltip, never a card.
 * The engine back end (a command-zone card) stays hidden.
 *
 * @param kind   what sort of relic this is (affects how the icon is drawn)
 * @param state  usable now (glow), used (greyed out until usable again), or idle
 * @param color  placeholder color until relic art exists, as 0xRRGGBB
 * @param glyph  short placeholder symbol drawn on the icon
 */
public record RelicSnapshot(
    String name,
    String description,
    Kind kind,
    State state,
    int color,
    String glyph
) {
    public enum Kind {
        /** Always-on effect. */
        PASSIVE,
        /** Fires on its own when something happens. */
        TRIGGERED,
        /** Has an ability the player activates. */
        ACTIVATED,
        /** One-use item, gone after use (Slay the Spire's potions). */
        CONSUMABLE
    }

    public enum State {
        /** Nothing to do: passive/triggered relics, or an ability that can't be paid for right now. */
        IDLE,
        /** Can be activated right now: drawn with a small glowing border. */
        READY,
        /** Already used this turn (or otherwise spent): greyed out until it can be activated again. */
        USED
    }
}

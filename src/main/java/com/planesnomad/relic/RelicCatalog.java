package com.planesnomad.relic;

import com.planesnomad.gui.model.RelicSnapshot.Kind;

import java.util.Map;
import java.util.Optional;

/**
 * Planesnomad's own list of relics and consumables, keyed by the name of the command-zone
 * card that implements each one in the engine.
 *
 * The card is only the back end: the player sees the name, description, kind and icon from
 * here. Icons are placeholder emoji pictures in data/relic-icons (Twemoji, see CREDITS.txt there)
 * until relic art exists; the color and letter are the fallback if an icon file is missing.
 *
 * TODO: move to a data file (the design doc keeps relics as versioned JSON) once the run layer exists.
 */
public final class RelicCatalog {

    /**
     * How one relic is presented. Description defaults to the card's rules text when null.
     * @param icon file name in data/relic-icons
     */
    public record RelicInfo(Kind kind, int color, String glyph, String icon, String description) { }

    private static final Map<String, RelicInfo> RELICS = Map.of(
        // Spare icons for future test relics: chess-pawn, apple, key, feather, mushroom, meat.
        "Ember Charm",        new RelicInfo(Kind.PASSIVE,    0xD9572B, "E", "fire.png", null),
        "Wayfarer's Compass", new RelicInfo(Kind.TRIGGERED,  0x3E8E7E, "C", "compass.png", null),
        "Tinker's Lens",      new RelicInfo(Kind.ACTIVATED,  0x8A6BBE, "L", "magnifying-glass.png", null),
        "Flask of Embers",    new RelicInfo(Kind.CONSUMABLE, 0xE0782E, "F", "test-tube.png", null),
        // Enemy effect (test): a passive shown in the enemy's effects bar.
        "Undertow Ward",      new RelicInfo(Kind.PASSIVE,    0x3A7BC8, "W", "feather.png", null)
    );

    private RelicCatalog() { }

    public static Optional<RelicInfo> find(String cardName) {
        return Optional.ofNullable(RELICS.get(cardName));
    }
}

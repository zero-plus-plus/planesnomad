package com.planesnomad.relic;

import com.planesnomad.gui.model.RelicSnapshot.Kind;

import java.util.Map;
import java.util.Optional;

/**
 * Planesnomad's own list of relics and consumables, keyed by the name of the command-zone
 * card that implements each one in the engine.
 *
 * The card is only the back end: the player sees the name, description, kind and icon from
 * here. Placeholder colors and glyphs stand in until relic art exists.
 *
 * TODO: move to a data file (the design doc keeps relics as versioned JSON) once the run layer exists.
 */
public final class RelicCatalog {

    /** How one relic is presented. Description defaults to the card's rules text when null. */
    public record RelicInfo(Kind kind, int color, String glyph, String description) { }

    private static final Map<String, RelicInfo> RELICS = Map.of(
        // Glyphs are plain letters so they render with any font until real icons exist.
        "Ember Charm",        new RelicInfo(Kind.PASSIVE,    0xD9572B, "E", null),
        "Wayfarer's Compass", new RelicInfo(Kind.TRIGGERED,  0x3E8E7E, "C", null),
        "Tinker's Lens",      new RelicInfo(Kind.ACTIVATED,  0x8A6BBE, "L", null),
        "Flask of Embers",    new RelicInfo(Kind.CONSUMABLE, 0xE0782E, "F", null)
    );

    private RelicCatalog() { }

    public static Optional<RelicInfo> find(String cardName) {
        return Optional.ofNullable(RELICS.get(cardName));
    }
}

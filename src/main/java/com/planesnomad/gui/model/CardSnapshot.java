package com.planesnomad.gui.model;

/**
 * What the GUI needs to draw one card.
 *
 * @param id               engine card id (stable for the card's life in one zone)
 * @param setCode          printing's set code, used later to fetch the image
 * @param collectorNumber  printing's collector number, used later to fetch the image
 * @param colors           color letters W U B R G, empty for colorless
 * @param manaCost         mana cost with symbols in braces, e.g. "{1}{R/G}"; empty for lands
 * @param power            current power, or null if not a creature
 * @param toughness        current toughness, or null if not a creature
 * @param attacking        attacking in the current combat
 * @param blocking         blocking in the current combat
 * @param imageKey         Forge's image key for this printing, e.g. "U/Shivan Dragon.full"
 *                         (set folder + file name, as in Forge's picture cache); empty if none
 * @param artIndex         which of the set's printings of this card (1 = the regular one)
 * @param layout           card layout for art cropping: normal, planeswalker, saga, split, ...
 */
public record CardSnapshot(
    int id,
    String name,
    String setCode,
    String collectorNumber,
    String colors,
    String manaCost,
    String typeLine,
    String rulesText,
    Integer power,
    Integer toughness,
    int damage,
    boolean tapped,
    boolean creature,
    boolean land,
    boolean token,
    boolean attacking,
    boolean blocking,
    boolean summoningSick,
    boolean faceDown,
    String imageKey,
    int artIndex,
    String layout
) {
    /** Placeholder for a card whose face the viewer may not see (e.g. the enemy's hand). */
    public static CardSnapshot hidden(int id) {
        return new CardSnapshot(id, "", "", "", "", "", "", "", null, null, 0,
            false, false, false, false, false, false, false, true, "", 1, "normal");
    }

    public boolean isHidden() {
        return faceDown && name.isEmpty();
    }

    /** True if the card is neither a creature nor a land (artifacts, enchantments, ...). */
    public boolean other() {
        return !creature && !land;
    }
}

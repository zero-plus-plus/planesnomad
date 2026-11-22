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
 * @param granted          abilities the card has now that its printed text doesn't (one per line),
 *                         e.g. "Islandwalk" from a lord; empty if none. Shown over pictures.
 * @param printedPower     printed power (null if not a creature), to show changes over pictures
 * @param printedToughness printed toughness (null if not a creature)
 * @param ability          true for an "ability card": an activated or triggered ability on the
 *                         stack, drawn with its source's name and art but only the ability's text
 * @param counters         counters on the card, sorted, e.g. "CHARGE=1,P1P1=2"; empty if none
 * @param attachedTo       id of the card this aura/equipment is attached to, 0 if none
 * @param attachments      names of the cards attached to this one, sorted, comma-separated
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
    String layout,
    String granted,
    Integer printedPower,
    Integer printedToughness,
    boolean ability,
    String counters,
    int attachedTo,
    String attachments
) {
    public CardSnapshot {
        counters = counters == null ? "" : counters;
        attachments = attachments == null ? "" : attachments;
    }

    /** Without counters or attachments. */
    public CardSnapshot(int id, String name, String setCode, String collectorNumber, String colors,
                        String manaCost, String typeLine, String rulesText, Integer power, Integer toughness,
                        int damage, boolean tapped, boolean creature, boolean land, boolean token,
                        boolean attacking, boolean blocking, boolean summoningSick, boolean faceDown,
                        String imageKey, int artIndex, String layout, String granted,
                        Integer printedPower, Integer printedToughness, boolean ability) {
        this(id, name, setCode, collectorNumber, colors, manaCost, typeLine, rulesText, power, toughness,
            damage, tapped, creature, land, token, attacking, blocking, summoningSick, faceDown,
            imageKey, artIndex, layout, granted, printedPower, printedToughness, ability, "", 0, "");
    }

    /** A card as printed: nothing granted, printed power/toughness equal to the current ones. */
    public CardSnapshot(int id, String name, String setCode, String collectorNumber, String colors,
                        String manaCost, String typeLine, String rulesText, Integer power, Integer toughness,
                        int damage, boolean tapped, boolean creature, boolean land, boolean token,
                        boolean attacking, boolean blocking, boolean summoningSick, boolean faceDown,
                        String imageKey, int artIndex, String layout) {
        this(id, name, setCode, collectorNumber, colors, manaCost, typeLine, rulesText, power, toughness,
            damage, tapped, creature, land, token, attacking, blocking, summoningSick, faceDown,
            imageKey, artIndex, layout, "", power, toughness, false);
    }

    /**
     * An ability card for the stack: this card's name, colors and art, but only the ability's text.
     * @param kindLine e.g. "Triggered ability"
     */
    public CardSnapshot asAbility(String kindLine, String abilityText) {
        return new CardSnapshot(id, name, setCode, collectorNumber, colors, "", kindLine, abilityText,
            null, null, 0, false, false, false, token, false, false, false, false,
            imageKey, artIndex, layout, "", null, null, true, "", 0, "");
    }

    /** True if current power or toughness differs from the printed values. */
    public boolean statsChanged() {
        return power != null && printedPower != null && toughness != null && printedToughness != null
            && (!power.equals(printedPower) || !toughness.equals(printedToughness));
    }
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

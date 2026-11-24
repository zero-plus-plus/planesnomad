package com.planesnomad.gui.images;

import forge.card.CardRules;
import forge.card.CardSplitType;
import forge.card.CardType;

/**
 * Names a printing's card layout, because the art window sits elsewhere on some layouts
 * (sagas, split cards, battles, ...). Used to pick, or skip, an art crop box.
 */
public final class CardLayouts {

    public static final String NORMAL = "normal";

    private CardLayouts() { }

    /**
     * @return one of: normal, planeswalker, saga, split, flip, battle, class, case.
     *         Adventure/omen cards and double-faced fronts count as normal (art where usual).
     */
    public static String of(CardRules rules) {
        if (rules == null) {
            return NORMAL;
        }
        CardSplitType split = rules.getSplitType();
        if (split == CardSplitType.Split) {
            return "split";
        }
        if (split == CardSplitType.Flip) {
            return "flip";
        }
        CardType type = rules.getType();
        if (type == null) {
            return NORMAL;
        }
        if (type.isPlaneswalker()) {
            return "planeswalker";
        }
        if (type.isBattle()) {
            return "battle";
        }
        if (type.hasSubtype("Saga")) {
            return "saga";
        }
        if (type.hasSubtype("Class")) {
            return "class";
        }
        if (type.hasSubtype("Case")) {
            return "case";
        }
        return NORMAL;
    }
}

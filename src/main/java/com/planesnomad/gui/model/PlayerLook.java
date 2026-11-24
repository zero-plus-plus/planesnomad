package com.planesnomad.gui.model;

import java.util.List;

/**
 * How a player is presented on the match screen, set by the match (later: by each encounter).
 * Cards are named; their art crops are used for the portrait and playmat.
 *
 * @param subtitle      short line under the name (e.g. the enemy's theme)
 * @param portraitCard  card whose art is the player's portrait; null = placeholder
 * @param playmatCard   card whose art is the player's half of the table; null = fallback picture
 * @param featuredCards cards shown in the enemy's effects bar ("guaranteed" cards), may be empty
 * @param featuredNotes per featured card (by name), a short note such as "Turn 2" (when it is
 *                      guaranteed); shown as a badge on the thumbnail and in the hover pop-up
 */
public record PlayerLook(String subtitle, String portraitCard, String playmatCard, List<String> featuredCards,
                         java.util.Map<String, String> featuredNotes) {

    public PlayerLook {
        featuredCards = featuredCards == null ? List.of() : List.copyOf(featuredCards);
        featuredNotes = featuredNotes == null ? java.util.Map.of() : java.util.Map.copyOf(featuredNotes);
    }

    public PlayerLook(String subtitle, String portraitCard, String playmatCard, List<String> featuredCards) {
        this(subtitle, portraitCard, playmatCard, featuredCards, java.util.Map.of());
    }

    public static PlayerLook plain(String subtitle) {
        return new PlayerLook(subtitle, null, null, List.of(), java.util.Map.of());
    }
}

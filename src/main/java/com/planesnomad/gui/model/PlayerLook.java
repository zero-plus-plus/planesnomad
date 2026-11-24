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
 */
public record PlayerLook(String subtitle, String portraitCard, String playmatCard, List<String> featuredCards) {

    public PlayerLook {
        featuredCards = featuredCards == null ? List.of() : List.copyOf(featuredCards);
    }

    public static PlayerLook plain(String subtitle) {
        return new PlayerLook(subtitle, null, null, List.of());
    }
}

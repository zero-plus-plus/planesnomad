package com.planesnomad.gui.model;

import java.util.List;

/**
 * One player's visible state.
 *
 * @param hand      hand cards. For a hidden hand the list holds {@link CardSnapshot#hidden()}
 *                  placeholders, so the count is still known but not the cards.
 * @param relics    relics and consumables, already translated from command-zone cards
 * @param subtitle  short description shown under the name (enemy theme, for now the deck name)
 * @param portrait  card whose art is the portrait, or null (placeholder)
 * @param playmat   card whose art is this player's half of the table, or null (fallback)
 * @param featured  "guaranteed" cards shown in the enemy's effects bar
 */
public record PlayerSnapshot(
    String name,
    String subtitle,
    int life,
    int startingLife,
    int librarySize,
    List<CardSnapshot> hand,
    List<CardSnapshot> battlefield,
    List<CardSnapshot> graveyard,
    List<CardSnapshot> exile,
    List<RelicSnapshot> relics,
    boolean activeTurn,
    boolean lost,
    CardSnapshot portrait,
    CardSnapshot playmat,
    List<CardSnapshot> featured
) { }

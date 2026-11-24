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
 * @param featuredNotes per featured card name, e.g. "Turn 2" (see {@link PlayerLook#featuredNotes()})
 * @param portraitFile  a picture file used as the portrait instead of a card's art (null if none)
 * @param playmatFile   a picture file used as this player's half of the table (null if none)
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
    List<CardSnapshot> featured,
    java.util.Map<String, String> featuredNotes,
    String portraitFile,
    String playmatFile
) {
    public PlayerSnapshot {
        featuredNotes = featuredNotes == null ? java.util.Map.of() : java.util.Map.copyOf(featuredNotes);
    }

    /** Without picture files. */
    public PlayerSnapshot(String name, String subtitle, int life, int startingLife, int librarySize,
                          List<CardSnapshot> hand, List<CardSnapshot> battlefield, List<CardSnapshot> graveyard,
                          List<CardSnapshot> exile, List<RelicSnapshot> relics, boolean activeTurn, boolean lost,
                          CardSnapshot portrait, CardSnapshot playmat, List<CardSnapshot> featured,
                          java.util.Map<String, String> featuredNotes) {
        this(name, subtitle, life, startingLife, librarySize, hand, battlefield, graveyard, exile, relics,
            activeTurn, lost, portrait, playmat, featured, featuredNotes, null, null);
    }

    /** Without notes on the featured cards. */
    public PlayerSnapshot(String name, String subtitle, int life, int startingLife, int librarySize,
                          List<CardSnapshot> hand, List<CardSnapshot> battlefield, List<CardSnapshot> graveyard,
                          List<CardSnapshot> exile, List<RelicSnapshot> relics, boolean activeTurn, boolean lost,
                          CardSnapshot portrait, CardSnapshot playmat, List<CardSnapshot> featured) {
        this(name, subtitle, life, startingLife, librarySize, hand, battlefield, graveyard, exile, relics,
            activeTurn, lost, portrait, playmat, featured, java.util.Map.of(), null, null);
    }
}

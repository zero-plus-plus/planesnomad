package com.planesnomad.encounter;

import com.planesnomad.match.MatchSetup;
import forge.deck.CardPool;
import forge.deck.Deck;

import java.util.ArrayList;
import java.util.List;

/**
 * One enemy to fight, read from an encounter file ({@code data/encounters/<plane>/<tier>/<id>.json},
 * see {@link EncounterFile}): who it is and how it looks, its deck and how both sides start, what
 * it says, and what winning gives.
 *
 * @param id          file path inside the encounters folder without ".json", e.g. "lorwyn/easy/merfolk-tidecaller"
 * @param plane       from the folder, e.g. "lorwyn"
 * @param tier        from the folder: easy, hard, elite or boss
 * @param enemy       the enemy's side of the table (deck name "Enemy: <name>", life, effects, look, start)
 * @param deckLines   the enemy's deck, one "count name|SET" per line
 * @param firstPlayer who takes the first turn
 * @param playerStart how the player's side starts in this fight (added to the player's own setup)
 * @param barks       what the enemy says, and when
 * @param rewards     gold, card choice, relics for winning
 * @param drops       cards this enemy drops (to be added to the card rewards; not used yet)
 */
public record Encounter(String id, String plane, Tier tier, MatchSetup.Side enemy, List<String> deckLines,
                        MatchSetup.FirstPlayer firstPlayer, MatchSetup.Start playerStart, List<Bark> barks,
                        Rewards rewards, List<String> drops) {

    public enum Tier { EASY, HARD, ELITE, BOSS }

    public Encounter {
        deckLines = List.copyOf(deckLines);
        barks = barks == null ? List.of() : List.copyOf(barks);
        drops = drops == null ? List.of() : List.copyOf(drops);
        playerStart = playerStart == null ? MatchSetup.Start.NORMAL : playerStart;
    }

    public String name() {
        return enemy.name();
    }

    /** The enemy's deck. */
    public Deck deck() {
        Deck d = new Deck(enemy.deck());
        d.getMain().addAll(CardPool.fromCardList(deckLines));
        return d;
    }

    /**
     * The match against this enemy with {@code you} on the bottom: the encounter's player start is
     * added to yours (preset hand replaces yours if set; bonus permanents and guarantees add up).
     */
    public MatchSetup setup(MatchSetup.Side you) {
        MatchSetup.Start mine = you.start();
        List<String> board = new ArrayList<>(mine.battlefield());
        board.addAll(playerStart.battlefield());
        List<com.planesnomad.match.Guarantee> sure = new ArrayList<>(mine.guarantees());
        sure.addAll(playerStart.guarantees());
        MatchSetup.Start merged = new MatchSetup.Start(
            playerStart.presetHand() ? playerStart.hand() : mine.hand(), board, sure);
        return new MatchSetup(you.withStart(merged), enemy, firstPlayer);
    }
}

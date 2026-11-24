package com.planesnomad.encounter;

import com.planesnomad.match.MatchSetup;

import java.util.List;

/**
 * One fight on the map: the enemy (deck, life, effects, look), its guaranteed cards, and the
 * rewards for winning. The run layer will pick encounters; today the Dev menu starts them.
 *
 * @param name        shown on the reward screen ("Merfolk of the Tide defeated")
 * @param kind        normal, elite or boss (bosses and elites will give relics)
 * @param enemy       the enemy's side of the table
 * @param guarantees  cards the enemy is sure to draw
 * @param rewards     what winning gives
 */
public record Encounter(String name, Kind kind, MatchSetup.Side enemy, List<Guarantee> guarantees, Rewards rewards) {

    public enum Kind { NORMAL, ELITE, BOSS }

    public Encounter {
        guarantees = guarantees == null ? List.of() : List.copyOf(guarantees);
    }

    /** The match against this encounter, with {@code you} on the bottom. */
    public MatchSetup setup(MatchSetup.Side you) {
        return new MatchSetup(you, enemy);
    }
}

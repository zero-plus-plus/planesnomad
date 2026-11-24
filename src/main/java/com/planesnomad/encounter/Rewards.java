package com.planesnomad.encounter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * What winning an encounter gives. Every part is optional (0 / empty = not offered).
 * Normal fights will usually give gold and a card choice; elites and bosses also relics.
 *
 * @param gold         currency
 * @param cardChoices  how many cards are offered (pick one, or skip)
 * @param cardPool     cards the offer is drawn from
 * @param relics       relics given (names of our relic cards, see RelicCatalog)
 */
public record Rewards(int gold, int cardChoices, List<String> cardPool, List<String> relics) {

    public Rewards {
        cardPool = cardPool == null ? List.of() : List.copyOf(cardPool);
        relics = relics == null ? List.of() : List.copyOf(relics);
    }

    /** The cards offered this time: {@code cardChoices} different cards from the pool. */
    public List<String> rollCards(Random random) {
        List<String> pool = new ArrayList<>(new java.util.LinkedHashSet<>(cardPool));
        Collections.shuffle(pool, random);
        return List.copyOf(pool.subList(0, Math.min(cardChoices, pool.size())));
    }
}

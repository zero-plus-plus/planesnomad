package com.planesnomad.gui;

import com.planesnomad.encounter.Bark;
import com.planesnomad.gui.model.MatchSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides when an enemy says its barks, from the snapshots of a match: each snapshot is checked
 * against every bark's condition; a condition fires when it becomes true (not again while it stays
 * true), and "once" barks only ever fire one time per fight.
 */
public final class BarkWatcher {

    private final List<Bark> barks;
    private final Set<Bark> said = new HashSet<>();
    private final Set<Bark> active = new HashSet<>();

    public BarkWatcher(List<Bark> barks) {
        this.barks = List.copyOf(barks);
    }

    /** The line to say now, or null. Call with every new snapshot. */
    public String check(MatchSnapshot s) {
        String out = null;
        for (Bark b : barks) {
            boolean now = holds(b.when(), s);
            boolean wasTrue = active.contains(b);
            if (now) {
                active.add(b);
            } else {
                active.remove(b);
            }
            if (now && !wasTrue && !(b.once() && said.contains(b)) && out == null) {
                said.add(b);
                out = b.text();
            }
        }
        return out;
    }

    private static boolean holds(Bark.When w, MatchSnapshot s) {
        return switch (w) {
            case START -> s.turn() >= 1;
            case LIFE_BELOW_HALF -> s.enemy().life() * 2 < s.enemy().startingLife();
            case PLAYER_LIFE_BELOW_HALF -> s.you().life() * 2 < s.you().startingLife();
        };
    }
}

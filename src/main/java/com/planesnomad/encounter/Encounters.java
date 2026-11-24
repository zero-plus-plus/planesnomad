package com.planesnomad.encounter;

import com.planesnomad.gui.model.PlayerLook;
import com.planesnomad.match.MatchSetup;

import java.util.List;
import java.util.Map;

/** The encounters that exist so far (test content until the run layer and its data files exist). */
public final class Encounters {

    /**
     * Test encounter: a mono-blue Merfolk deck (lords, Merfolk, 4 Counterspell; only blue cards
     * costing 1 or more, nothing castable for free; basic Islands only) that always draws Lord of
     * Atlantis on its own second turn. Its rewards include everything (gold, a card, a relic) so the
     * reward screen's layout can be seen, although a normal fight won't give relics.
     */
    public static final Encounter MERFOLK = new Encounter(
        "Merfolk of the Tide",
        Encounter.Kind.NORMAL,
        new MatchSetup.Side("Merfolk Tidecaller", MatchSetup.Controller.AI, "Encounter - Merfolk of the Tide", 20,
            List.of(),
            new PlayerLook("Merfolk lords and counterspells", "Lord of Atlantis", "Island",
                List.of("Lord of Atlantis"), Map.of("Lord of Atlantis", "Turn 2"))),
        List.of(new Guarantee("Lord of Atlantis", 2)),
        new Rewards(25, 3,
            List.of("Lightning Bolt", "Fireball", "Goblin King", "Shivan Dragon", "Incinerate",
                "Raging Goblin", "Mogg Fanatic", "Shock", "Goblin Piledriver", "Lava Axe"),
            List.of("Wayfarer's Compass")));

    public static List<Encounter> all() {
        return List.of(MERFOLK);
    }

    private Encounters() { }
}

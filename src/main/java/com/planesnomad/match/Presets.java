package com.planesnomad.match;

import com.planesnomad.RelicTest;
import com.planesnomad.gui.TestMatches;
import com.planesnomad.gui.model.PlayerLook;

import java.util.List;

/** Ready-made setups: the test matches, with you (a person) or the AI on the bottom. */
public final class Presets {

    public static final String PERSON_NAME = "You";

    /** The encounter the "Fight" launchers start: data/encounters/lorwyn/easy/merfolk-tidecaller.json. */
    public static final String FIRST_ENCOUNTER = "lorwyn/easy/merfolk-tidecaller";

    /** The player's deck for encounters until the run layer gives one (decks/Starter - Red.dck). */
    public static final String STARTER_DECK = "Starter - Red";

    /**
     * You, for encounters: the red starter deck (Shocks, simple red creatures, a Pillage and a
     * Blaze), 20 life and the test relics. Later the run supplies the deck and relics.
     */
    public static MatchSetup.Side encounterPlayer() {
        return new MatchSetup.Side(PERSON_NAME, MatchSetup.Controller.PERSON, STARTER_DECK, 20,
            List.of(RelicTest.PASSIVE, RelicTest.TRIGGERED, RelicTest.ACTIVATED, RelicTest.CONSUMABLE),
            new PlayerLook("Red starter deck", "Krenko, Mob Boss", "Mountain", List.of()));
    }

    /** @param which "", "unlimited" or "mixed"; @param person true: you play the bottom side */
    public static MatchSetup testMatch(String which, boolean person) {
        String w = which == null ? "" : which;
        PlayerLook[] looks = TestMatches.looks(w);
        String[] decks = switch (w) {
            case "unlimited" -> new String[] {"Test: Unlimited black-red", "Test: Unlimited blue-green"};
            case "mixed" -> new String[] {"Test: Mixed eras red-green", "Test: Mixed eras blue-black"};
            default -> new String[] {"Test: Red goblins and burn", "Test: Blue tempo"};
        };
        MatchSetup.Side you = new MatchSetup.Side(person ? PERSON_NAME : TestMatches.YOU,
            person ? MatchSetup.Controller.PERSON : MatchSetup.Controller.AI, decks[0], 20,
            List.of(RelicTest.PASSIVE, RelicTest.TRIGGERED, RelicTest.ACTIVATED, RelicTest.CONSUMABLE), looks[0]);
        MatchSetup.Side enemy = new MatchSetup.Side(TestMatches.ENEMY, MatchSetup.Controller.AI, decks[1], 20,
            List.of(TestMatches.ENEMY_EFFECT), looks[1]);
        return new MatchSetup(you, enemy);
    }

    private Presets() { }
}

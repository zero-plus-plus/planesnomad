package com.planesnomad.match;

import com.planesnomad.gui.model.PlayerLook;

import java.util.List;

/**
 * Everything that defines a match, as the game will set it up: for each side who plays it, the
 * deck, starting life, the cards in its command zone (your relics and consumables, an enemy's
 * effects) and how it looks. Today the developer controls edit it; later the run layer builds it
 * from the game state (the player's deck and relics, the encounter's enemy).
 */
public record MatchSetup(Side you, Side enemy) {

    /** Who makes a side's decisions. */
    public enum Controller { PERSON, AI }

    /**
     * One side of the table.
     * @param name          player name (shown on screen and in the log)
     * @param controller    a person (through the window) or the AI
     * @param deck          deck name in the {@link DeckLibrary}
     * @param startingLife  life at the start
     * @param commandCards  relics and consumables (yours) or effects (an enemy's): card names
     * @param look          theme text, portrait, playmat, featured cards
     */
    public record Side(String name, Controller controller, String deck, int startingLife,
                       List<String> commandCards, PlayerLook look) {
        public Side {
            commandCards = commandCards == null ? List.of() : List.copyOf(commandCards);
        }

        public Side withController(Controller c) {
            return new Side(name, c, deck, startingLife, commandCards, look);
        }
    }

    public MatchSetup withYou(Side s) {
        return new MatchSetup(s, enemy);
    }

    public MatchSetup withEnemy(Side s) {
        return new MatchSetup(you, s);
    }
}

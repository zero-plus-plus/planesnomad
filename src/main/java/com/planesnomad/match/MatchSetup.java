package com.planesnomad.match;

import com.planesnomad.gui.model.PlayerLook;

import java.util.List;

/**
 * Everything that defines a match, as the game will set it up: for each side who plays it, the
 * deck, starting life, the cards in its command zone (your relics and consumables, an enemy's
 * effects), how it looks and how it starts (preset hand, bonus permanents, guaranteed draws); and
 * who goes first. Today the developer controls or an encounter file build it; later the run layer
 * builds it from the game state (the player's deck and relics, the encounter's enemy).
 */
public record MatchSetup(Side you, Side enemy, FirstPlayer firstPlayer) {

    /** Who makes a side's decisions. */
    public enum Controller { PERSON, AI }

    /** Who takes the first turn: a coin flip (Forge's default), or fixed. */
    public enum FirstPlayer { RANDOM, YOU, ENEMY }

    public MatchSetup {
        firstPlayer = firstPlayer == null ? FirstPlayer.RANDOM : firstPlayer;
    }

    /** A coin flip decides who goes first. */
    public MatchSetup(Side you, Side enemy) {
        this(you, enemy, FirstPlayer.RANDOM);
    }

    /**
     * How one side starts, beyond a normal opening hand.
     *
     * @param hand        preset opening hand (card names, taken out of the deck); empty = draw 7
     *                    and mulligan as usual. A side with a preset hand never mulligans.
     * @param battlefield bonus permanents on the battlefield from the start, NOT taken from the
     *                    deck: card names, or {@code token:<Forge token script>} (e.g.
     *                    {@code token:u_1_1_merfolk})
     * @param guarantees  cards this side is sure to draw on a given turn of its own
     */
    public record Start(List<String> hand, List<String> battlefield, List<Guarantee> guarantees) {
        public static final Start NORMAL = new Start(List.of(), List.of(), List.of());

        public Start {
            hand = hand == null ? List.of() : List.copyOf(hand);
            battlefield = battlefield == null ? List.of() : List.copyOf(battlefield);
            guarantees = guarantees == null ? List.of() : List.copyOf(guarantees);
        }

        public boolean presetHand() {
            return !hand.isEmpty();
        }
    }

    /**
     * One side of the table.
     * @param name          player name (shown on screen and in the log)
     * @param controller    a person (through the window) or the AI
     * @param deck          deck name in the {@link DeckLibrary}
     * @param startingLife  life at the start
     * @param commandCards  relics and consumables (yours) or effects (an enemy's): card names
     * @param look          theme text, portrait, playmat, featured cards
     * @param start         preset hand, bonus permanents, guaranteed draws
     */
    public record Side(String name, Controller controller, String deck, int startingLife,
                       List<String> commandCards, PlayerLook look, Start start) {
        public Side {
            commandCards = commandCards == null ? List.of() : List.copyOf(commandCards);
            start = start == null ? Start.NORMAL : start;
        }

        /** A side that starts normally (7 cards, mulligans, nothing extra). */
        public Side(String name, Controller controller, String deck, int startingLife,
                    List<String> commandCards, PlayerLook look) {
            this(name, controller, deck, startingLife, commandCards, look, Start.NORMAL);
        }

        public Side withController(Controller c) {
            return new Side(name, c, deck, startingLife, commandCards, look, start);
        }

        public Side withStart(Start s) {
            return new Side(name, controller, deck, startingLife, commandCards, look, s);
        }
    }

    public MatchSetup withYou(Side s) {
        return new MatchSetup(s, enemy, firstPlayer);
    }

    public MatchSetup withEnemy(Side s) {
        return new MatchSetup(you, s, firstPlayer);
    }
}

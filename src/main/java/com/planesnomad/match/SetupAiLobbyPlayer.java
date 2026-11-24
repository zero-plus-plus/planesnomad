package com.planesnomad.match;

import forge.ai.LobbyPlayerAi;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.player.Player;

import java.util.Set;

/**
 * An AI player that follows the match's {@link StartRules}: it lets the setup decide who goes
 * first and keeps a preset opening hand. Everything else is Forge's AI as usual.
 */
public final class SetupAiLobbyPlayer extends LobbyPlayerAi {

    private final StartRules rules;

    public SetupAiLobbyPlayer(String name, StartRules rules) {
        super(name, Set.of());
        setAiProfile(com.planesnomad.ForgeEngine.DEFAULT_AI_PROFILE);
        this.rules = rules;
    }

    @Override
    public Player createIngamePlayer(Game game, int id) {
        Player p = new Player(getName(), game, id);
        p.setFirstController(new Controller(game, p, this, rules));
        return p;
    }

    /** Forge's AI controller with the setup's start choices. */
    public static final class Controller extends PlayerControllerAi {
        private final StartRules rules;

        Controller(Game game, Player p, LobbyPlayerAi lobby, StartRules rules) {
            super(game, p, lobby);
            this.rules = rules;
        }

        @Override
        public Player chooseStartingPlayer(boolean isFirstGame) {
            Player fixed = rules == null ? null : rules.firstPlayer(getGame());
            return fixed != null ? fixed : super.chooseStartingPlayer(isFirstGame);
        }

        @Override
        public boolean mulliganKeepHand(Player firstPlayer, int cardsToReturn) {
            Boolean keep = rules == null ? null : rules.keepHand(getPlayer());
            return keep != null ? keep : super.mulliganKeepHand(firstPlayer, cardsToReturn);
        }
    }
}

package com.planesnomad.gui.engine;

import forge.ai.LobbyPlayerAi;
import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;

import java.util.Set;
import java.util.function.BiPredicate;

/** The lobby entry for a person playing through the match window: their player gets a {@link HumanController}. */
public final class HumanLobbyPlayer extends LobbyPlayerAi {

    private final HumanBridge bridge;
    private final BiPredicate<Boolean, PhaseType> stops;

    public HumanLobbyPlayer(String name, HumanBridge bridge, BiPredicate<Boolean, PhaseType> stops) {
        super(name, Set.of());
        setAiProfile(com.planesnomad.ForgeEngine.DEFAULT_AI_PROFILE);
        this.bridge = bridge;
        this.stops = stops;
    }

    @Override
    public Player createIngamePlayer(Game game, int id) {
        Player p = new Player(getName(), game, id);
        p.setFirstController(new HumanController(game, p, this, bridge, stops));
        return p;
    }
}

package com.planesnomad.encounter;

import com.google.common.eventbus.Subscribe;
import com.planesnomad.gui.engine.SnapshotBuilder;
import forge.game.Game;
import forge.game.GameLogEntryType;
import forge.game.card.Card;
import forge.game.event.GameEvent;
import forge.game.event.GameEventTurnPhase;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.PlayerZone;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes an enemy's guaranteed draws happen: at the start of the enemy's Nth turn's upkeep, one copy
 * of the card is taken from its library and put on top, so the turn's normal draw is that card.
 * The library is reordered directly (no zone change, so nothing triggers), and a line
 * "Guaranteed: ..." goes into the game log. If no copy is left in the library, nothing happens
 * (it was already drawn, or all copies are elsewhere).
 */
public final class GuaranteedDraws {

    private final Game game;
    private final Player player;
    private final List<Guarantee> guarantees;
    private int ownTurns;
    /** What was done, for tests: "turn 2: Lord of Atlantis (id 57)". */
    private final List<String> done = new ArrayList<>();
    private final List<Integer> placedIds = new ArrayList<>();

    private GuaranteedDraws(Game game, Player player, List<Guarantee> guarantees) {
        this.game = game;
        this.player = player;
        this.guarantees = List.copyOf(guarantees);
    }

    /** Hooks the guarantees into a game that hasn't started yet. Returns null if there are none. */
    public static GuaranteedDraws install(Game game, String playerName, List<Guarantee> guarantees) {
        if (guarantees == null || guarantees.isEmpty()) {
            return null;
        }
        Player p = game.getRegisteredPlayers().stream().filter(x -> x.getName().equals(playerName))
            .findFirst().orElseThrow(() -> new IllegalArgumentException("No player named " + playerName));
        GuaranteedDraws g = new GuaranteedDraws(game, p, guarantees);
        game.subscribeToEvents(g);
        return g;
    }

    @Subscribe
    public void onEvent(GameEvent event) {
        if (!(event instanceof GameEventTurnPhase tp) || tp.phase() != PhaseType.UPKEEP) {
            return;
        }
        if (tp.playerTurn() == null || tp.playerTurn().getId() != player.getId()) {
            return;
        }
        ownTurns++;
        for (Guarantee gu : guarantees) {
            if (gu.turn() == ownTurns) {
                placeOnTop(gu);
            }
        }
    }

    private void placeOnTop(Guarantee gu) {
        PlayerZone library = player.getZone(ZoneType.Library);
        Card copy = null;
        for (Card c : library.getCards()) {
            if (c.getName().equals(gu.card())) {
                copy = c;
                break;
            }
        }
        if (copy == null) {
            done.add("turn " + ownTurns + ": no " + gu.card() + " left in the library");
            return;
        }
        library.remove(copy);
        library.add(copy, 0);
        placedIds.add(copy.getId());
        done.add("turn " + ownTurns + ": " + gu.card() + " (id " + copy.getId() + ")");
        game.getGameLog().add(GameLogEntryType.INFORMATION,
            SnapshotBuilder.GUARANTEED_PREFIX + player.getName() + " draws " + gu.card() + " this turn");
    }

    public List<String> done() {
        return List.copyOf(done);
    }

    /** Engine ids of the copies put on top, in order. */
    public List<Integer> placedIds() {
        return List.copyOf(placedIds);
    }
}

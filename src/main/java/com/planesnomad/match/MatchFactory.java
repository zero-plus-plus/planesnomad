package com.planesnomad.match;

import com.planesnomad.gui.engine.HumanBridge;
import com.planesnomad.gui.engine.HumanLobbyPlayer;
import forge.LobbyPlayer;
import forge.card.CardDb;
import forge.deck.Deck;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.phase.PhaseType;
import forge.game.player.RegisteredPlayer;
import forge.item.IPaperCard;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/** Turns a {@link MatchSetup} into an engine match. */
public final class MatchFactory {

    /**
     * @param bridge for a side played by a person: where its questions go (null if none)
     * @param stops  for a person: where play stops ((your turn?, phase) -> stop)
     * @throws IllegalArgumentException if a deck can't be found or read
     */
    public static Match create(MatchSetup setup, CardDb db, DeckLibrary decks, HumanBridge bridge,
                               BiPredicate<Boolean, PhaseType> stops) {
        return create(setup, db, decks, bridge, stops, new StartRules(setup));
    }

    /**
     * As {@link #create(MatchSetup, CardDb, DeckLibrary, HumanBridge, BiPredicate)}, with the start
     * rules the players follow (first player, preset hands). Call {@link StartRules#install} on the
     * game before it starts (e.g. {@code runner.setBeforeStart(rules::install)}) for bonus
     * permanents and guaranteed draws.
     */
    public static Match create(MatchSetup setup, CardDb db, DeckLibrary decks, HumanBridge bridge,
                               BiPredicate<Boolean, PhaseType> stops, StartRules rules) {
        if (setup.you().name().equals(setup.enemy().name())) {
            throw new IllegalArgumentException("The two players need different names");
        }
        RegisteredPlayer you = side(setup.you(), db, decks, bridge, stops, rules);
        RegisteredPlayer enemy = side(setup.enemy(), db, decks, bridge, stops, rules);
        return new Match(new GameRules(GameType.Constructed), List.of(you, enemy), "Planesnomad");
    }

    private static RegisteredPlayer side(MatchSetup.Side s, CardDb db, DeckLibrary decks, HumanBridge bridge,
                                         BiPredicate<Boolean, PhaseType> stops, StartRules rules) {
        Deck deck = decks.get(s.deck());
        if (deck == null) {
            throw new IllegalArgumentException("Deck not found or unreadable: " + s.deck());
        }
        RegisteredPlayer rp = new RegisteredPlayer(deck);
        rp.setStartingLife(s.startingLife());
        LobbyPlayer player = s.controller() == MatchSetup.Controller.PERSON && bridge != null
            ? new HumanLobbyPlayer(s.name(), bridge, stops).withStartRules(rules)
            : new SetupAiLobbyPlayer(s.name(), rules);
        rp.setPlayer(player);
        List<IPaperCard> command = new ArrayList<>();
        for (String name : s.commandCards()) {
            PaperCard pc = db.getCard(name);
            if (pc == null) {
                throw new IllegalArgumentException("Relic / effect card not found: " + name);
            }
            command.add(pc);
        }
        if (!command.isEmpty()) {
            rp.addExtraCardsInCommandZone(command);
        }
        return rp;
    }

    /** Our own cards (relics, consumables, enemy effects) that can go in a command zone. */
    public static List<String> commandCardChoices(CardDb db) {
        List<String> out = new ArrayList<>();
        for (PaperCard pc : db.getUniqueCards()) {
            var ed = forge.StaticData.instance().getEditions().get(pc.getEdition());
            if (ed != null && ed.getType() == forge.card.CardEdition.Type.CUSTOM_SET) {
                out.add(pc.getName());
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    private MatchFactory() { }
}

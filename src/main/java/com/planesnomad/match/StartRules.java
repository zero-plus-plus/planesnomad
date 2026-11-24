package com.planesnomad.match;

import com.google.common.eventbus.Subscribe;
import com.planesnomad.gui.engine.SnapshotBuilder;
import forge.StaticData;
import forge.game.Game;
import forge.game.GameLogEntryType;
import forge.game.card.Card;
import forge.game.card.CardFactory;
import forge.game.event.GameEvent;
import forge.game.event.GameEventTurnBegan;
import forge.game.event.GameEventTurnPhase;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;
import forge.item.PaperToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How a match starts, from its {@link MatchSetup}: who goes first, preset opening hands (no
 * mulligan), bonus permanents and guaranteed draws. The rules of the game stay Forge's: this only
 * decides the choices Forge leaves to the players (who plays first, keeping a hand) and arranges
 * cards before the first turn.
 *
 * - First player: the controllers ask {@link #firstPlayer(Game)} when Forge lets the coin-flip
 *   winner choose who starts.
 * - Preset hand: when Forge asks whether to keep the opening hand, the drawn hand goes back to the
 *   library, the preset cards are taken out of it into the hand, the library is shuffled, and the
 *   hand is kept ({@link #keepHand(Player)}).
 * - Bonus permanents: put onto the battlefield as the first turn begins (before anyone gets
 *   priority), not taken from the deck. Cards by name, or {@code token:<script>}.
 * - Guaranteed draws: {@link GuaranteedDraws}.
 */
public final class StartRules {

    private final MatchSetup setup;
    private final Set<Integer> handsDone = ConcurrentHashMap.newKeySet();
    private boolean boardDone;
    private final List<String> problems = new ArrayList<>();
    private final List<GuaranteedDraws> draws = new ArrayList<>();

    public StartRules(MatchSetup setup) {
        this.setup = setup;
    }

    public MatchSetup setup() {
        return setup;
    }

    private Game game;

    /** Hooks the start-of-game parts (bonus permanents, guaranteed draws) into a game not started yet. */
    public void install(Game game) {
        this.game = game;
        game.subscribeToEvents(this);
        for (MatchSetup.Side s : List.of(setup.you(), setup.enemy())) {
            GuaranteedDraws g = GuaranteedDraws.install(game, s.name(), s.start().guarantees());
            if (g != null) {
                draws.add(g);
            }
        }
    }

    /** The fixed first player, or null when a coin flip decides (the controller chooses as usual). */
    public Player firstPlayer(Game game) {
        String name = switch (setup.firstPlayer()) {
            case YOU -> setup.you().name();
            case ENEMY -> setup.enemy().name();
            case RANDOM -> null;
        };
        return name == null ? null : player(game, name);
    }

    /**
     * Asked instead of "keep this hand?": for a side with a preset hand, puts that hand in place
     * (once) and keeps it. Null for a side without one (decide as usual).
     */
    public Boolean keepHand(Player p) {
        MatchSetup.Side side = side(p);
        if (side == null || !side.start().presetHand()) {
            return null;
        }
        if (handsDone.add(p.getId())) {
            presetHand(p, side.start().hand());
        }
        return true;
    }

    private void presetHand(Player p, List<String> names) {
        Game game = p.getGame();
        for (Card c : new ArrayList<>(p.getCardsIn(ZoneType.Hand))) {
            game.getAction().moveToLibrary(c, null);
        }
        for (String name : names) {
            Card found = null;
            for (Card c : p.getCardsIn(ZoneType.Library)) {
                if (c.getName().equalsIgnoreCase(name)) {
                    found = c;
                    break;
                }
            }
            if (found == null) {
                problems.add(p.getName() + ": preset hand card not in the deck: " + name);
                continue;
            }
            game.getAction().moveToHand(found, null);
        }
        p.shuffle(null);
    }

    @Subscribe
    public void onEvent(GameEvent e) {
        if (boardDone || !(e instanceof GameEventTurnBegan || e instanceof GameEventTurnPhase)) {
            return;
        }
        boardDone = true;
        for (MatchSetup.Side s : List.of(setup.you(), setup.enemy())) {
            if (s.start().battlefield().isEmpty()) {
                continue;
            }
            Player p = player(game, s.name());
            if (p == null) {
                continue;
            }
            for (String entry : s.start().battlefield()) {
                Card c = create(entry, p, game);
                if (c == null) {
                    problems.add(s.name() + ": can't create starting permanent " + entry);
                    continue;
                }
                game.getAction().moveToPlay(c, p, null, null);
            }
            game.getGameLog().add(GameLogEntryType.INFORMATION, SnapshotBuilder.START_PREFIX + s.name()
                + " starts with " + String.join(", ", s.start().battlefield().stream().map(StartRules::label).toList()));
        }
    }

    /** "token:u_1_1_merfolk" -> "u_1_1_merfolk token" for the log. */
    private static String label(String entry) {
        return entry.startsWith("token:") ? entry.substring(6) + " token" : entry;
    }

    /** A card or token for a starting battlefield entry, owned by p; null if unknown. */
    public static Card create(String entry, Player p, Game game) {
        if (entry.startsWith("token:")) {
            PaperToken t = token(entry.substring(6));
            return t == null ? null : CardFactory.getCard(t, p, game);
        }
        PaperCard pc = StaticData.instance().getCommonCards().getCard(entry);
        return pc == null ? null : Card.fromPaperCard(pc, p);
    }

    /** A Forge token script by name (e.g. u_1_1_merfolk), or null. */
    public static PaperToken token(String script) {
        try {
            return StaticData.instance().getAllTokens().getToken(script);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }

    public List<GuaranteedDraws> guaranteedDraws() {
        return List.copyOf(draws);
    }

    private MatchSetup.Side side(Player p) {
        if (p.getName().equals(setup.you().name())) {
            return setup.you();
        }
        if (p.getName().equals(setup.enemy().name())) {
            return setup.enemy();
        }
        return null;
    }

    private static Player player(Game game, String name) {
        for (Player p : game.getRegisteredPlayers()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }
}

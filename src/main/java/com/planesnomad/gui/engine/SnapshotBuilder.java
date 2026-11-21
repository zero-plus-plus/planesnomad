package com.planesnomad.gui.engine;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.MatchSnapshot.LogLine;
import com.planesnomad.gui.model.MatchSnapshot.StackItem;
import com.planesnomad.gui.model.PlayerSnapshot;
import com.planesnomad.gui.model.RelicSnapshot;
import com.planesnomad.gui.model.RelicSnapshot.Kind;
import com.planesnomad.gui.model.RelicSnapshot.State;
import com.planesnomad.relic.RelicCatalog;
import forge.ai.ComputerUtilMana;
import forge.card.ColorSet;
import forge.game.Game;
import forge.game.GameLogEntry;
import forge.game.GameLogEntryType;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.zone.ZoneType;
import forge.item.IPaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Builds {@link MatchSnapshot}s from a live engine game. Must be called on the engine
 * thread (inside an engine event handler), where reading game state is safe.
 */
public final class SnapshotBuilder {

    private static final Set<GameLogEntryType> LOG_TYPES = EnumSet.of(
        GameLogEntryType.TURN, GameLogEntryType.LAND, GameLogEntryType.STACK_ADD,
        GameLogEntryType.STACK_RESOLVE, GameLogEntryType.COMBAT, GameLogEntryType.DAMAGE,
        GameLogEntryType.LIFE, GameLogEntryType.GAME_OUTCOME);

    private final Game game;
    private final Player you;
    private final Player enemy;
    private final String youSubtitle;
    private final String enemySubtitle;

    public SnapshotBuilder(Game game, Player you, Player enemy, String youSubtitle, String enemySubtitle) {
        this.game = game;
        this.you = you;
        this.enemy = enemy;
        this.youSubtitle = youSubtitle;
        this.enemySubtitle = enemySubtitle;
    }

    /**
     * @param revealEnemyHand debug option: show the enemy's hand face up
     */
    public MatchSnapshot build(boolean revealEnemyHand) {
        var ph = game.getPhaseHandler();
        Player active = ph.getPlayerTurn();
        String phase = ph.getPhase() == null ? "Starting" : ph.getPhase().nameForUi;

        String result = null;
        if (game.isGameOver() && game.getOutcome() != null) {
            var outcome = game.getOutcome();
            result = outcome.isDraw() ? "Draw"
                : outcome.getWinningPlayer().getPlayer().getName() + " wins";
        }

        return new MatchSnapshot(
            ph.getTurn(),
            active == null ? "" : active.getName(),
            phase,
            player(you, youSubtitle, true),
            player(enemy, enemySubtitle, revealEnemyHand),
            stack(),
            log(),
            result
        );
    }

    // ------------------------------------------------------------------ players

    private PlayerSnapshot player(Player p, String subtitle, boolean handVisible) {
        List<CardSnapshot> hand = new ArrayList<>();
        for (Card c : p.getCardsIn(ZoneType.Hand)) {
            hand.add(handVisible ? card(c) : CardSnapshot.hidden(c.getId()));
        }
        return new PlayerSnapshot(
            p.getName(),
            subtitle,
            p.getLife(),
            p.getStartingLife(),
            p.getCardsIn(ZoneType.Library).size(),
            hand,
            cards(p, ZoneType.Battlefield),
            cards(p, ZoneType.Graveyard),
            cards(p, ZoneType.Exile),
            relics(p),
            p == game.getPhaseHandler().getPlayerTurn(),
            p.hasLost()
        );
    }

    private List<CardSnapshot> cards(Player p, ZoneType zone) {
        List<CardSnapshot> out = new ArrayList<>();
        for (Card c : p.getCardsIn(zone)) {
            out.add(card(c));
        }
        return out;
    }

    static CardSnapshot card(Card c) {
        Combat combat = c.getGame().getCombat();
        IPaperCard paper = c.getPaperCard();
        boolean creature = c.isCreature();
        return new CardSnapshot(
            c.getId(),
            c.getName(),
            paper == null ? c.getSetCode() : paper.getEdition(),
            paper == null ? "" : paper.getCollectorNumber(),
            colors(c.getColor()),
            c.getManaCost() == null || c.isLand() ? "" : shortCost(c.getManaCost().toString()),
            c.getType().toString(),
            rulesText(c),
            creature ? c.getNetPower() : null,
            creature ? c.getNetToughness() : null,
            c.getDamage(),
            c.isTapped(),
            creature,
            c.isLand(),
            c.isToken(),
            combat != null && combat.isAttacking(c),
            combat != null && combat.isBlocking(c),
            creature && c.isSick(),
            c.isFaceDown()
        );
    }

    /** "{1}{U}" or "1 {U}" -> "1U" (until we draw our own mana symbols). */
    static String shortCost(String cost) {
        if (cost == null || cost.equalsIgnoreCase("no cost")) {
            return "";
        }
        return cost.replaceAll("[{}\\s]", "");
    }

    private static String rulesText(Card c) {
        String text = c.getOracleText();
        if (text == null || text.isBlank()) {
            text = c.getAbilityText();
        }
        return text == null ? "" : text.replace("\\n", "\n").trim();
    }

    private static String colors(ColorSet set) {
        StringBuilder sb = new StringBuilder();
        if (set.hasWhite()) sb.append('W');
        if (set.hasBlue()) sb.append('U');
        if (set.hasBlack()) sb.append('B');
        if (set.hasRed()) sb.append('R');
        if (set.hasGreen()) sb.append('G');
        return sb.toString();
    }

    // ------------------------------------------------------------------ relics

    private List<RelicSnapshot> relics(Player p) {
        List<RelicSnapshot> out = new ArrayList<>();
        for (Card c : p.getCardsIn(ZoneType.Command)) {
            RelicCatalog.find(c.getName()).ifPresent(info -> {
                String description = info.description() != null ? info.description() : rulesText(c);
                out.add(new RelicSnapshot(c.getName(), description, info.kind(),
                    relicState(c, p, info.kind()), info.color(), info.glyph()));
            });
        }
        return out;
    }

    /**
     * READY  = its ability can be activated right now (restrictions met and affordable).
     * USED   = it hit its activation limit this turn; greyed out until the limit resets.
     * IDLE   = passive/triggered, or an ability that can't be used at this moment.
     */
    private State relicState(Card c, Player owner, Kind kind) {
        if (kind != Kind.ACTIVATED && kind != Kind.CONSUMABLE) {
            return State.IDLE;
        }
        for (SpellAbility sa : c.getSpellAbilities()) {
            if (!sa.isActivatedAbility()) {
                continue;
            }
            String limit = sa.getParam("ActivationLimit");
            if (limit != null) {
                try {
                    if (c.getAbilityActivatedThisTurn(sa) >= Integer.parseInt(limit)) {
                        return State.USED;
                    }
                } catch (NumberFormatException ignored) {
                    // non-numeric limits (rare) fall through to the canPlay check
                }
            }
            try {
                if (sa.getActivatingPlayer() == null) {
                    sa.setActivatingPlayer(owner);
                }
                if (sa.canPlay() && ComputerUtilMana.canPayManaCost(sa, owner, 0, false)) {
                    return State.READY;
                }
            } catch (RuntimeException e) {
                // A display check must never break the match; treat as not usable now.
            }
        }
        return State.IDLE;
    }

    // ------------------------------------------------------------------ stack and log

    private List<StackItem> stack() {
        List<StackItem> out = new ArrayList<>();
        // The engine resolves an item while it is still on the stack (it is removed afterwards),
        // so an item waiting for a choice (e.g. Duress) stays visible; mark it as resolving.
        SpellAbility resolvingNow = game.getStack().isResolving() ? game.getStack().getResolvingAbility() : null;
        for (SpellAbilityStackInstance si : game.getStack()) {
            SpellAbility sa = si.getSpellAbility();
            Card source = si.getSourceCard();
            Player who = si.getActivatingPlayer();
            StackItem.Kind kind = sa != null && sa.isSpell() ? StackItem.Kind.SPELL
                : sa != null && sa.isTrigger() ? StackItem.Kind.TRIGGER : StackItem.Kind.ABILITY;
            String verb = switch (kind) {
                case SPELL -> "casts";
                case TRIGGER -> "triggers";
                case ABILITY -> "activates";
            };
            String name = source == null ? "?" : source.getName();
            String detail = si.getStackDescription();
            if (detail != null && detail.startsWith(name)) {
                detail = detail.substring(name.length()).replaceFirst("^\\s*\\(\\d+\\)\\s*-\\s*", "");
            }
            out.add(new StackItem(
                who == null ? "" : who.getName(),
                (who == null ? "" : who.getName() + " ") + verb + " " + name,
                detail == null ? "" : CARD_ID.matcher(detail).replaceAll("").trim(),
                who == you,
                kind,
                sa != null && sa == resolvingNow,
                source == null ? null : card(source)));
        }
        return out;
    }

    private static final java.util.regex.Pattern CARD_ID = java.util.regex.Pattern.compile(" \\(\\d+\\)");

    /**
     * Makes engine log lines readable: drops card id numbers, keeps only the headline of
     * cast/trigger entries (the engine repeats the card text underneath), and turns
     * resolution entries ("Counterspell - Counter Goblin Raider.") into "Counterspell resolves: ...".
     * Damage resolutions are dropped because the damage entry already says it.
     * Returns null for lines that add nothing.
     */
    static String cleanLogLine(GameLogEntry e) {
        String msg = e.message();
        if (msg == null || msg.isBlank()) {
            return null;
        }
        String first = CARD_ID.matcher(msg.split("\n")[0].trim()).replaceAll("");
        if (first.contains("[Phase:")) {
            return null;   // a trigger's own text; the "X triggered Y" line already says it
        }
        if (e.type() == GameLogEntryType.STACK_RESOLVE) {
            int dash = first.indexOf(" - ");
            if (dash < 0) {
                return first;
            }
            String source = first.substring(0, dash);
            String effect = first.substring(dash + 3).trim();
            if (effect.contains(" deals ") || effect.isEmpty()
                || effect.matches("^(Legendary |Artifact |Enchantment )*(Creature|Artifact|Enchantment|Planeswalker|Land|Battle)\\b.*")) {
                return null;
            }
            return source + " resolves: " + effect;
        }
        if (e.type() == GameLogEntryType.STACK_ADD) {
            return first.replace("[", "").replace("]", "");
        }
        return first;
    }

    private List<LogLine> log() {
        List<GameLogEntry> entries = new ArrayList<>(game.getGameLog().getLogEntriesForTypes(LOG_TYPES));
        Collections.reverse(entries); // the engine stores newest first
        List<LogLine> out = new ArrayList<>(entries.size());
        for (GameLogEntry e : entries) {
            String line = cleanLogLine(e);
            if (line != null) {
                out.add(new LogLine(line, e.type() == GameLogEntryType.TURN));
            }
        }
        return out;
    }
}

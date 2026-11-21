package com.planesnomad.gui.engine;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.MatchSnapshot.LogLine;
import com.planesnomad.gui.model.MatchSnapshot.StackItem;
import com.planesnomad.gui.model.PlayerLook;
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
    private final PlayerLook youLook;
    private final PlayerLook enemyLook;
    private final CardSnapshot youPortrait;
    private final CardSnapshot youPlaymat;
    private final List<CardSnapshot> youFeatured;
    private final CardSnapshot enemyPortrait;
    private final CardSnapshot enemyPlaymat;
    private final List<CardSnapshot> enemyFeatured;

    public SnapshotBuilder(Game game, Player you, Player enemy, PlayerLook youLook, PlayerLook enemyLook) {
        this.game = game;
        this.you = you;
        this.enemy = enemy;
        this.youLook = youLook;
        this.enemyLook = enemyLook;
        this.youPortrait = named(youLook.portraitCard());
        this.youPlaymat = named(youLook.playmatCard());
        this.youFeatured = named(youLook.featuredCards());
        this.enemyPortrait = named(enemyLook.portraitCard());
        this.enemyPlaymat = named(enemyLook.playmatCard());
        this.enemyFeatured = named(enemyLook.featuredCards());
    }

    /** A snapshot of a card by name (its default printing), for portraits, playmats, featured cards. */
    static CardSnapshot named(String name) {
        if (name == null || name.isBlank() || forge.StaticData.instance() == null) {
            return null;
        }
        forge.item.PaperCard pc = forge.StaticData.instance().getCommonCards().getCard(name);
        return pc == null ? null : fromPaper(pc);
    }

    private static List<CardSnapshot> named(List<String> names) {
        List<CardSnapshot> out = new ArrayList<>();
        for (String n : names) {
            CardSnapshot c = named(n);
            if (c != null) {
                out.add(c);
            }
        }
        return List.copyOf(out);
    }

    /** A card outside the game (no engine card): built from its printing's rules. */
    public static CardSnapshot fromPaper(forge.item.PaperCard pc) {
        var r = pc.getRules();
        var type = r.getType();
        String cost = r.getManaCost() == null ? "" : braceCost(r.getManaCost().toString());
        StringBuilder colors = new StringBuilder();
        if (r.getColor().hasWhite()) colors.append('W');
        if (r.getColor().hasBlue()) colors.append('U');
        if (r.getColor().hasBlack()) colors.append('B');
        if (r.getColor().hasRed()) colors.append('R');
        if (r.getColor().hasGreen()) colors.append('G');
        String oracle = r.getOracleText() == null ? "" : r.getOracleText().replace("\\n", "\n");
        boolean creature = type.isCreature();
        return new CardSnapshot(-Math.abs(pc.getName().hashCode()) - 1, pc.getName(), pc.getEdition(),
            pc.getCollectorNumber(), colors.toString(), type.isLand() ? "" : cost, type.toString(), oracle,
            creature ? r.getIntPower() : null, creature ? r.getIntToughness() : null, 0,
            false, creature, type.isLand(), false, false, false, false, false,
            pc.getCardImageKey(), pc.getArtIndex(), com.planesnomad.gui.images.CardLayouts.of(r));
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
            player(you, youLook.subtitle(), true, youPortrait, youPlaymat, youFeatured),
            player(enemy, enemyLook.subtitle(), revealEnemyHand, enemyPortrait, enemyPlaymat, enemyFeatured),
            stack(),
            log(),
            result
        );
    }

    // ------------------------------------------------------------------ players

    private PlayerSnapshot player(Player p, String subtitle, boolean handVisible,
                                  CardSnapshot portrait, CardSnapshot playmat, List<CardSnapshot> featured) {
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
            p.hasLost(),
            portrait,
            playmat,
            featured
        );
    }

    private List<CardSnapshot> cards(Player p, ZoneType zone) {
        List<CardSnapshot> out = new ArrayList<>();
        for (Card c : p.getCardsIn(zone)) {
            // Relic/consumable engine cards stay hidden (e.g. a used consumable goes to exile).
            if (RelicCatalog.find(c.getName()).isEmpty()) {
                out.add(card(c));
            }
        }
        return out;
    }

    static CardSnapshot card(Card c) {
        Combat combat = c.getGame().getCombat();
        IPaperCard paper = c.getPaperCard();
        boolean creature = c.isCreature();
        var relicInfo = RelicCatalog.find(c.getName());
        if (relicInfo.isPresent()) {
            // A relic's engine card is hidden: present it as a relic, with no card picture.
            return new CardSnapshot(c.getId(), c.getName(), "", "", "", "",
                relicTypeLine(relicInfo.get().kind()), rulesText(c), null, null, 0,
                false, false, false, false, false, false, false, false, "", 1, "normal");
        }
        return new CardSnapshot(
            c.getId(),
            c.getName(),
            paper == null ? c.getSetCode() : paper.getEdition(),
            paper == null ? "" : paper.getCollectorNumber(),
            colors(c.getColor()),
            c.getManaCost() == null || c.isLand() ? "" : braceCost(c.getManaCost().toString()),
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
            c.isFaceDown(),
            imageKey(paper),
            paper instanceof forge.item.PaperCard pc ? pc.getArtIndex() : 1,
            paper instanceof forge.item.PaperCard pc2 ? com.planesnomad.gui.images.CardLayouts.of(pc2.getRules()) : "normal"
        );
    }

    /** The engine's cost text, e.g. "{1}{R/G}"; empty for "no cost". */
    static String braceCost(String cost) {
        if (cost == null || cost.equalsIgnoreCase("no cost")) {
            return "";
        }
        return cost.trim();
    }

    static String relicTypeLine(Kind kind) {
        return switch (kind) {
            case PASSIVE, TRIGGERED -> "Relic";
            case ACTIVATED -> "Relic - activated";
            case CONSUMABLE -> "Consumable";
        };
    }

    private static String imageKey(IPaperCard paper) {
        if (paper instanceof forge.item.PaperCard pc) {
            try {
                String key = pc.getCardImageKey();
                return key == null ? "" : key;
            } catch (RuntimeException e) {
                return "";
            }
        }
        return "";
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

    private RelicSnapshot relicOf(Card c) {
        return RelicCatalog.find(c.getName()).map(info -> new RelicSnapshot(c.getName(),
            info.description() != null ? info.description() : rulesText(c), info.kind(),
            State.IDLE, info.color(), info.glyph(), info.icon())).orElse(null);
    }

    private List<RelicSnapshot> relics(Player p) {
        List<RelicSnapshot> out = new ArrayList<>();
        for (Card c : p.getCardsIn(ZoneType.Command)) {
            RelicCatalog.find(c.getName()).ifPresent(info -> {
                String description = info.description() != null ? info.description() : rulesText(c);
                out.add(new RelicSnapshot(c.getName(), description, info.kind(),
                    relicState(c, p, info.kind()), info.color(), info.glyph(), info.icon()));
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
                detail == null ? "" : CARD_ID.matcher(detail).replaceAll("").trim().replaceFirst("^-\\s*", ""),
                who == you,
                kind,
                sa != null && sa == resolvingNow,
                source == null ? null : card(source),
                source == null ? null : relicOf(source)));
        }
        return out;
    }

    private static final java.util.regex.Pattern CARD_ID = java.util.regex.Pattern.compile(" \\(\\d+\\)");
    /** The engine's label at the end of a trigger's text, e.g. "[Phase: Red AI]", "[Zone Changer: X]". */
    private static final java.util.regex.Pattern TRIGGER_LABEL = java.util.regex.Pattern.compile("\\[[A-Z][A-Za-z ]*: [^\\]]*]\\s*$");

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
        if (TRIGGER_LABEL.matcher(first).find()) {
            return null;   // a trigger's own text, e.g. "... [Phase: Red AI]" or "... [Zone Changer: X]";
                           // the "X triggered Y" line already says it
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

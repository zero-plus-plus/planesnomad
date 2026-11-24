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
        GameLogEntryType.LIFE, GameLogEntryType.GAME_OUTCOME, GameLogEntryType.INFORMATION);

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
    public static CardSnapshot named(String name) {
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
            String winner = outcome.isDraw() ? null : outcome.getWinningPlayer().getPlayer().getName();
            result = winner == null ? "Draw" : winner.equals("You") ? "You win" : winner + " wins";
        }

        return new MatchSnapshot(
            ph.getTurn(),
            active == null ? "" : active.getName(),
            phase,
            player(you, youLook.subtitle(), true, youPortrait, youPlaymat, youFeatured, youLook.featuredNotes()),
            player(enemy, enemyLook.subtitle(), revealEnemyHand, enemyPortrait, enemyPlaymat, enemyFeatured,
                enemyLook.featuredNotes()),
            stack(),
            log(),
            result,
            history == null ? List.of() : history.entries(),
            blocks()
        );
    }

    /**
     * True while a player is making a choice for the item that is resolving (human input sets
     * this; AI choices are instant): the item then stays on the stack, tagged "Resolving".
     */
    private volatile boolean choicePending;

    public void setChoicePending(boolean pending) {
        choicePending = pending;
    }

    /** History for the icon bar; filled from engine events by {@link MatchRunner}. */
    private HistoryRecorder history;

    /** Starts recording history (call once, before the match starts). */
    public HistoryRecorder recordHistory() {
        if (history == null) {
            history = new HistoryRecorder(game, you.getName(), this::relicOf);
        }
        return history;
    }

    // ------------------------------------------------------------------ players

    private PlayerSnapshot player(Player p, String subtitle, boolean handVisible,
                                  CardSnapshot portrait, CardSnapshot playmat, List<CardSnapshot> featured,
                                  java.util.Map<String, String> featuredNotes) {
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
            featured,
            featuredNotes
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

    public static CardSnapshot card(Card c) {
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
            paper instanceof forge.item.PaperCard pc2 ? com.planesnomad.gui.images.CardLayouts.of(pc2.getRules()) : "normal",
            granted(c),
            creature ? printedPower(c) : null,
            creature ? printedToughness(c) : null,
            false,
            counters(c),
            c.getEntityAttachedTo() instanceof Card host ? host.getId() : 0,
            attachments(c)
        );
    }

    /** Counters on the card, sorted, e.g. "CHARGE=1,P1P1=2"; empty if none. */
    static String counters(Card c) {
        try {
            var map = c.getCounters();
            if (map == null || map.isEmpty()) {
                return "";
            }
            java.util.TreeSet<String> parts = new java.util.TreeSet<>();
            for (var e : map.entrySet()) {
                if (e.getCount() > 0) {
                    parts.add(e.getElement() + "=" + e.getCount());
                }
            }
            return String.join(",", parts);
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Names of the auras/equipment attached to the card, sorted; empty if none. */
    static String attachments(Card c) {
        try {
            if (!c.hasCardAttachments()) {
                return "";
            }
            List<String> names = new ArrayList<>();
            for (Card a : c.getAttachedCards()) {
                names.add(a.getName());
            }
            Collections.sort(names);
            return String.join(",", names);
        } catch (RuntimeException e) {
            return "";
        }
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
        if (paper instanceof forge.item.PaperToken pt) {
            // Tokens: "t:<script>|<set>|<number>|<art>", e.g. "t:r_1_1_goblin|10E|4|1".
            try {
                String key = pt.getCardImageKey();
                return key == null ? "" : key;
            } catch (RuntimeException e) {
                return "";
            }
        }
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

    /**
     * The card's text as it is now, including abilities granted by other cards (e.g. a lord's
     * islandwalk). Falls back to the printed text if the engine gives none.
     */
    private static String rulesText(Card c) {
        String text = c.getAbilityText();
        if (text == null || text.isBlank()) {
            text = c.getOracleText();
        }
        return text == null ? "" : text.replace("\\n", "\n").trim();
    }

    /**
     * Abilities the card has now that aren't printed on it (granted by other cards, e.g. a lord's
     * islandwalk), one per line. Uses the engine's own record of which keywords, abilities,
     * triggers and static effects are intrinsic, not a comparison of texts (texts are worded
     * differently, which gave false positives).
     */
    public static String granted(Card c) {
        if (c.isFaceDown()) {
            return "";
        }
        java.util.LinkedHashSet<String> lines = new java.util.LinkedHashSet<>();
        try {
            // Skip grants that come from the card's own printed effects (e.g. a Sliver that gives
            // all Slivers first strike, or "as long as this is untapped, it has protection ..."):
            // they are already in its printed text.
            for (forge.game.keyword.KeywordInterface kw : c.getUnhiddenKeywords()) {
                if (!kw.isIntrinsic() && !fromSelf(c, kw.getStatic() != null ? kw.getStatic().getHostCard() : null)) {
                    lines.add(kw.getTitle());
                }
            }
            for (SpellAbility sa : c.getSpellAbilities()) {
                if (!sa.isIntrinsic() && sa.getKeyword() == null && !sa.isSpell() && !sa.isLandAbility()
                    && !fromSelf(c, sa.getOriginalHost())) {
                    add(lines, sa.getDescription());
                }
            }
            for (forge.game.trigger.Trigger t : c.getTriggers()) {
                if (!t.isIntrinsic() && t.getKeyword() == null && !t.isSecondary()
                    && !fromSelf(c, t.getOriginalHost())) {
                    add(lines, t.toString());
                }
            }
            for (forge.game.staticability.StaticAbility st : c.getStaticAbilities()) {
                if (!st.isIntrinsic() && st.getKeyword() == null && !st.isSecondary()
                    && !fromSelf(c, st.getOriginalHost())) {
                    add(lines, st.toString());
                }
            }
        } catch (RuntimeException e) {
            return "";   // a display detail must never break the match
        }
        return String.join("\n", lines);
    }

    /** True if a non-intrinsic trait came from the card itself (a self-grant), or its origin is unknown. */
    private static boolean fromSelf(Card c, Card source) {
        return source == null || source.equals(c);
    }

    private static void add(java.util.Set<String> lines, String text) {
        if (text == null) {
            return;
        }
        String clean = TRIGGER_LABEL.matcher(text.replaceAll("\\s*\\([^)]*\\)", "")).replaceAll("").trim();
        if (!clean.isEmpty()) {
            lines.add(clean);
        }
    }

    /** Printed power, or null if it isn't a fixed number (e.g. star power) or unknown. */
    private static Integer printedPower(Card c) {
        try {
            int v = c.getRules() == null ? c.getBasePower() : c.getRules().getIntPower();
            return v < 0 ? null : v;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Integer printedToughness(Card c) {
        try {
            int v = c.getRules() == null ? c.getBaseToughness() : c.getRules().getIntToughness();
            return v < 0 ? null : v;
        } catch (RuntimeException e) {
            return null;
        }
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

    /** Who blocks whom in the current combat: blocker id -> attacker ids. */
    private java.util.Map<Integer, List<Integer>> blocks() {
        java.util.Map<Integer, List<Integer>> out = new java.util.HashMap<>();
        try {
            var combat = game.getCombat();
            if (combat == null) {
                return out;
            }
            for (Card attacker : combat.getAttackers()) {
                for (Card blocker : combat.getBlockers(attacker)) {
                    out.computeIfAbsent(blocker.getId(), k -> new ArrayList<>()).add(attacker.getId());
                }
            }
        } catch (RuntimeException e) {
            // display only
        }
        return out;
    }

    private List<StackItem> stack() {
        List<StackItem> out = new ArrayList<>();
        // The engine resolves an item while it is still on the stack (it is removed afterwards).
        // A simple resolution (a creature entering, a Bolt's damage) should not linger there, so the
        // resolving item is hidden - unless a player is making a choice for it (e.g. scrying for
        // Opt, discarding to Duress): then it stays, tagged "Resolving".
        SpellAbility resolvingNow = game.getStack().isResolving() ? game.getStack().getResolvingAbility() : null;
        for (SpellAbilityStackInstance si : game.getStack()) {
            SpellAbility sa = si.getSpellAbility();
            if (sa != null && sa == resolvingNow && !choicePending) {
                continue;
            }
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
                cleanDetail(detail),
                who == you,
                kind,
                sa != null && sa == resolvingNow,
                source == null ? null : stackCard(source, kind, detail),
                source == null ? null : relicOf(source),
                si.getId(),
                targets(sa)));
        }
        return out;
    }

    /** What a stack item targets, its sub-abilities included (e.g. "Counter target spell. Draw..."). */
    private List<MatchSnapshot.Target> targets(SpellAbility sa) {
        List<MatchSnapshot.Target> out = new ArrayList<>();
        if (sa == null) {
            return out;
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        try {
            for (forge.game.spellability.TargetChoices tc : sa.getAllTargetChoices()) {
                if (tc == null) {
                    continue;
                }
                for (Card c : tc.getTargetCards()) {
                    if (seen.add("c" + c.getId())) {
                        Player ctrl = c.getController();
                        out.add(new MatchSnapshot.Target(MatchSnapshot.Target.Kind.CARD, c.getId(), c.getName(), ctrl == you));
                    }
                }
                for (Player p : tc.getTargetPlayers()) {
                    if (seen.add("p" + p.getName())) {
                        out.add(new MatchSnapshot.Target(MatchSnapshot.Target.Kind.PLAYER, 0, p.getName(), p == you));
                    }
                }
                for (SpellAbility target : tc.getTargetSpells()) {
                    SpellAbilityStackInstance si = game.getStack().getInstanceMatchingSpellAbilityID(target);
                    int id = si == null ? 0 : si.getId();
                    Card host = target.getHostCard();
                    if (seen.add("s" + id)) {
                        out.add(new MatchSnapshot.Target(MatchSnapshot.Target.Kind.STACK, id,
                            host == null ? "a spell" : host.getName(), target.getActivatingPlayer() == you));
                    }
                }
            }
        } catch (RuntimeException e) {
            // Showing targets must never break the match.
        }
        return out;
    }

    /** Log lines written by our own match rules (guaranteed draws), shown although engine notes aren't. */
    public static final String GUARANTEED_PREFIX = "Guaranteed: ";

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
        if (e.type() == GameLogEntryType.INFORMATION && !msg.startsWith(GUARANTEED_PREFIX)) {
            return null;   // engine notes; only our own "Guaranteed: ..." lines are shown
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

    /**
     * What the stack pop-up draws: spells as their card; abilities and triggers as an ability card
     * with the source's name and art but only the ability's own text (the card may have gained it
     * from elsewhere, so its printed text isn't the right thing to show).
     */
    private static CardSnapshot stackCard(Card source, StackItem.Kind kind, String detail) {
        CardSnapshot card = card(source);
        if (kind == StackItem.Kind.SPELL) {
            return card;
        }
        String text = cleanDetail(detail);
        return card.asAbility(kind == StackItem.Kind.TRIGGER ? "Triggered ability" : "Activated ability", text);
    }

    /** Stack text without card ids, leading dashes or engine labels like "[Zone Changer: X]". */
    private static String cleanDetail(String detail) {
        if (detail == null) {
            return "";
        }
        String text = CARD_ID.matcher(detail).replaceAll("").trim().replaceFirst("^-\\s*", "");
        return TRIGGER_LABEL.matcher(text).replaceAll("").trim();
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

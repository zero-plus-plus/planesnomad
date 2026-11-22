package com.planesnomad.gui.engine;

import com.planesnomad.gui.engine.HumanBridge.Attackers;
import com.planesnomad.gui.engine.HumanBridge.Blockers;
import com.planesnomad.gui.engine.HumanBridge.Option;
import com.planesnomad.gui.engine.HumanBridge.Priority;
import com.planesnomad.gui.engine.HumanBridge.Targets;
import com.planesnomad.relic.RelicCatalog;
import forge.LobbyPlayer;
import forge.ai.ComputerUtilMana;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.spellability.TargetRestrictions;
import forge.game.zone.ZoneType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * A player controlled by a person through the match window. Built on the AI controller: the
 * person decides priority (play a land, cast a spell, activate an ability, or pass), targets,
 * attackers and blockers; mana is paid automatically; every other decision (mulligan,
 * discarding to hand size, choices inside a resolving effect, triggered abilities' targets, ...)
 * is still made by the AI for now.
 *
 * Priority follows the stops: with an empty stack, play only pauses at a stop phase; when the
 * enemy has something on the stack, you are asked if you can respond; with nothing you could
 * do, priority passes by itself.
 */
public final class HumanController extends PlayerControllerAi {

    private final HumanBridge bridge;
    /** (your turn?, phase) -> is this a stop. */
    private final BiPredicate<Boolean, PhaseType> stops;

    public HumanController(Game game, Player player, LobbyPlayer lobby, HumanBridge bridge,
                           BiPredicate<Boolean, PhaseType> stops) {
        super(game, player, lobby);
        this.bridge = bridge;
        this.stops = stops;
    }

    // ------------------------------------------------------------------ priority

    @Override
    public List<SpellAbility> chooseSpellAbilityToPlay() {
        Player me = getPlayer();
        Game game = me.getGame();
        if (game.isGameOver()) {
            return null;
        }
        var ph = game.getPhaseHandler();
        boolean myTurn = ph.isPlayerTurn(me);
        boolean stackEmpty = game.getStack().isEmpty();
        if (autoPassing(game, myTurn, stackEmpty)) {
            return null;
        }
        List<SpellAbility> options = playable();
        if (options.isEmpty()) {
            return null;   // nothing you could do: pass by yourself
        }
        if (stackEmpty && (!stops.test(myTurn, ph.getPhase()) || MatchRunner.combatWithoutAttack(game, ph.getPhase()))) {
            return null;   // not a stop (or a combat step with no attack in it): play goes on
        }
        if (!stackEmpty) {
            SpellAbilityStackInstance top = game.getStack().peek();
            if (top != null && top.getActivatingPlayer() == me) {
                return null;   // your own spell on top: let it resolve (the enemy may still respond)
            }
        }
        List<Option> list = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            SpellAbility sa = options.get(i);
            Card c = sa.getHostCard();
            list.add(new Option("play:" + i, c.getId(), c.getName(), label(sa), RelicCatalog.find(c.getName()).isPresent()));
        }
        String prompt;
        if (stackEmpty) {
            prompt = (myTurn ? "Your turn" : ph.getPlayerTurn().getName() + "'s turn") + " · "
                + ph.getPhase().nameForUi + ": play something, or pass";
        } else {
            SpellAbilityStackInstance top = game.getStack().peek();
            String who = top == null || top.getActivatingPlayer() == null ? "" : top.getActivatingPlayer().getName() + ": ";
            prompt = "Respond to " + who + (top == null ? "the stack" : top.getSourceCard().getName()) + ", or pass";
        }
        Object answer = bridge.ask(new Priority(prompt, list, !stackEmpty));
        if (answer instanceof String key && key.startsWith("play:")) {
            int i = Integer.parseInt(key.substring(5));
            if (i >= 0 && i < options.size()) {
                return List.of(options.get(i));
            }
        }
        return null;
    }

    private int autoPassTurn = -1;
    private int autoPassStartTurnOwner = -1;

    /**
     * "Pass until end of turn": passes everything until the turn number changes.
     * "Pass until something happens": passes until an enemy spell or ability is on the stack, or
     * your next turn begins.
     */
    private boolean autoPassing(Game game, boolean myTurn, boolean stackEmpty) {
        HumanBridge.AutoPass mode = bridge.autoPass();
        if (mode == HumanBridge.AutoPass.NONE) {
            autoPassTurn = -1;
            return false;
        }
        int turn = game.getPhaseHandler().getTurn();
        if (autoPassTurn < 0) {
            autoPassTurn = turn;
        }
        if (mode == HumanBridge.AutoPass.END_OF_TURN) {
            if (turn == autoPassTurn) {
                return true;
            }
            bridge.setAutoPass(HumanBridge.AutoPass.NONE);
            autoPassTurn = -1;
            return false;
        }
        // Until something happens.
        SpellAbilityStackInstance top = stackEmpty ? null : game.getStack().peek();
        boolean enemyActs = top != null && top.getActivatingPlayer() != getPlayer();
        boolean yourNewTurn = myTurn && turn != autoPassTurn;
        if (enemyActs || yourNewTurn) {
            bridge.setAutoPass(HumanBridge.AutoPass.NONE);
            autoPassTurn = -1;
            return false;
        }
        return true;
    }

    /** What the person can do now (mana abilities are left out: mana is paid automatically). */
    List<SpellAbility> playable() {
        Player me = getPlayer();
        List<SpellAbility> out = new ArrayList<>();
        for (ZoneType zone : List.of(ZoneType.Hand, ZoneType.Battlefield, ZoneType.Command, ZoneType.Graveyard, ZoneType.Exile)) {
            for (Card c : me.getCardsIn(zone)) {
                List<SpellAbility> abilities;
                try {
                    abilities = c.getAllPossibleAbilities(me, true);
                } catch (RuntimeException e) {
                    continue;
                }
                for (SpellAbility sa : abilities) {
                    try {
                        if (sa.isManaAbility()) {
                            continue;
                        }
                        sa.setActivatingPlayer(me);
                        if (!sa.canPlay()) {
                            continue;
                        }
                        if (!sa.isLandAbility() && !ComputerUtilMana.canPayManaCost(sa, me, 0, false)) {
                            continue;
                        }
                        out.add(sa);
                    } catch (RuntimeException e) {
                        // an ability that can't be checked is left out
                    }
                }
            }
        }
        return out;
    }

    private static String label(SpellAbility sa) {
        Card c = sa.getHostCard();
        if (sa.isLandAbility()) {
            return "Play " + c.getName();
        }
        if (sa.isSpell()) {
            return "Cast " + c.getName();
        }
        String d = sa.getDescription() == null ? "" : sa.getDescription().replaceAll("\\s*\\(\\d+\\)", "")
            .replace("CARDNAME", c.getName()).replace("NICKNAME", c.getName()).trim();
        if (d.startsWith(c.getName())) {
            d = d.substring(c.getName().length()).replaceFirst("^\\s*-\\s*", "");
        }
        return "Activate " + c.getName() + (d.isEmpty() ? "" : ": " + d);
    }

    @Override
    public boolean playChosenSpellAbility(SpellAbility sa) {
        if (sa.isLandAbility()) {
            return super.playChosenSpellAbility(sa);
        }
        // Mana is paid automatically; targets are chosen by the person, after costs are known.
        boolean[] cancelled = {false};
        Consumer<SpellAbility> chooseTargets = s -> {
            for (SpellAbility a = s; a != null && !cancelled[0]; a = a.getSubAbility()) {
                if (a.usesTargeting() && !chooseTargets(a)) {
                    cancelled[0] = true;
                    s.resetTargets();
                }
            }
        };
        // Auto-tap off: you tap lands first (the mana goes to your pool, which pays first);
        // cancelling here casts nothing (mana already made stays in the pool until the step ends).
        if (!bridge.autoTap() && !payManually(sa)) {
            return true;
        }
        playWithRollback(sa, chooseTargets);
        return true;
    }

    /**
     * Manual mana: lists your untapped mana sources; each tap adds mana to your pool. Ends when
     * the pool holds enough mana (by amount), or "Auto" (pay the rest automatically). False = cancel.
     */
    private boolean payManually(SpellAbility sa) {
        Player me = getPlayer();
        Game game = me.getGame();
        forge.card.mana.ManaCost cost = sa.getPayCosts() == null || sa.getPayCosts().getTotalMana() == null
            ? null : sa.getPayCosts().getTotalMana();
        if (cost == null || cost.getCMC() == 0) {
            return true;
        }
        for (int round = 0; round < 40; round++) {
            if (me.getManaPool().totalMana() >= cost.getCMC()) {
                return true;   // enough in the pool: it pays from there
            }
            List<SpellAbility> sources = new ArrayList<>();
            List<Option> options = new ArrayList<>();
            for (Card c : me.getCardsIn(ZoneType.Battlefield)) {
                if (c.isTapped()) {
                    continue;
                }
                for (SpellAbility m : c.getManaAbilities()) {
                    m.setActivatingPlayer(me);
                    if (m.canPlay() && m.getPayCosts() != null && m.getPayCosts().hasTapCost()) {
                        String produces = m.getManaPart() == null ? "" : m.getManaPart().getOrigProduced();
                        options.add(new Option("tap:" + sources.size(), c.getId(), c.getName(),
                            "Tap " + c.getName() + " for " + manaText(produces), false));
                        sources.add(m);
                    }
                }
            }
            if (sources.isEmpty()) {
                return true;   // nothing left to tap: let the automatic payment try
            }
            String pool = poolText(me);
            Object answer = bridge.ask(new HumanBridge.ManaPayment("Pay " + cost + " for " + sa.getHostCard().getName()
                + ": click lands to tap them", cost.toString(), pool == null ? "" : pool, options));
            if (answer == null) {
                return false;
            }
            if ("auto".equals(answer)) {
                return true;
            }
            if (answer instanceof String key && key.startsWith("tap:")) {
                int i = Integer.parseInt(key.substring(4));
                if (i >= 0 && i < sources.size()) {
                    SpellAbility m = sources.get(i);
                    forge.ai.ComputerUtil.playNoStack(me, m, game, true);
                }
            }
        }
        return true;
    }

    /** Mana in the pool as symbols, e.g. "{R}{R}{C}". */
    static String poolText(Player p) {
        StringBuilder sb = new StringBuilder();
        var pool = p.getManaPool();
        byte[] colors = {forge.card.MagicColor.WHITE, forge.card.MagicColor.BLUE, forge.card.MagicColor.BLACK,
            forge.card.MagicColor.RED, forge.card.MagicColor.GREEN, forge.card.MagicColor.COLORLESS};
        String[] letters = {"W", "U", "B", "R", "G", "C"};
        for (int i = 0; i < colors.length; i++) {
            for (int n = pool.getAmountOfColor(colors[i]); n > 0; n--) {
                sb.append('{').append(letters[i]).append('}');
            }
        }
        return sb.toString();
    }

    private static String manaText(String produced) {
        if (produced == null || produced.isBlank()) {
            return "mana";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : produced.trim().split("\\s+")) {
            sb.append(part.startsWith("Combo") ? "any color" : "{" + part + "}");
        }
        return sb.toString();
    }

    /** Asks the person for one ability's targets; false if they cancelled or nothing can be targeted. */
    private boolean chooseTargets(SpellAbility a) {
        Game game = getPlayer().getGame();
        TargetRestrictions tr = a.getTargetRestrictions();
        Card host = a.getHostCard();
        int min = tr.getMinTargets(host, a);
        int max = tr.getMaxTargets(host, a);
        Map<String, Object> byKey = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        // All legal targets: players, creatures, planeswalkers, ... as the ability allows ("any target").
        for (GameEntity e : tr.getAllCandidates(a, false)) {
            if (!a.canTarget(e)) {
                continue;
            }
            if (e instanceof Card c) {
                byKey.put("card:" + c.getId(), c);
                labels.put("card:" + c.getId(), c.getName());
            } else if (e instanceof Player p) {
                byKey.put("player:" + p.getName(), p);
                labels.put("player:" + p.getName(), p.getName());
            }
        }
        if (tr.getZone() != null && tr.getZone().contains(ZoneType.Stack)) {
            for (SpellAbilityStackInstance si : game.getStack()) {
                SpellAbility target = si.getSpellAbility();
                if (target != null && target != a && a.canTarget(target)) {
                    byKey.put("stack:" + si.getId(), target);
                    labels.put("stack:" + si.getId(), si.getSourceCard().getName());
                }
            }
        }
        if (byKey.isEmpty()) {
            return min == 0;
        }
        String what = a.getDescription() == null ? host.getName() : a.getDescription().replaceAll("\\s*\\(\\d+\\)", "")
            .replace("CARDNAME", host.getName()).replace("NICKNAME", host.getName());
        String prompt = "Choose " + (max > 1 ? "up to " + max + " targets" : "a target") + " for " + host.getName()
            + (what.length() < 90 ? " (" + what + ")" : "");
        Object answer = bridge.ask(new Targets(prompt, List.copyOf(byKey.keySet()), labels, min, Math.max(min, max)));
        if (!(answer instanceof List<?> keys)) {
            return false;   // cancelled
        }
        a.resetTargets();
        for (Object k : keys) {
            Object t = byKey.get(String.valueOf(k));
            if (t instanceof GameEntity ge) {
                a.getTargets().add(ge);
            } else if (t instanceof SpellAbility s) {
                a.getTargets().add(s);
            }
        }
        return a.isTargetNumberValid();
    }

    // ------------------------------------------------------------------ looking at your library

    @Override
    public org.apache.commons.lang3.tuple.ImmutablePair<forge.game.card.CardCollection, forge.game.card.CardCollection>
            arrangeForScry(forge.game.card.CardCollection topN) {
        List<Card> away = askArrange(topN, "Scry " + topN.size() + ": click a card to put it on the bottom of your library", "Bottom");
        return split(topN, away);
    }

    @Override
    public org.apache.commons.lang3.tuple.ImmutablePair<forge.game.card.CardCollection, forge.game.card.CardCollection>
            arrangeForSurveil(forge.game.card.CardCollection topN) {
        List<Card> away = askArrange(topN, "Surveil " + topN.size() + ": click a card to put it into your graveyard", "Graveyard");
        return split(topN, away);
    }

    @Override
    public boolean willPutCardOnTop(Card c) {
        forge.game.card.CardCollection one = new forge.game.card.CardCollection();
        one.add(c);
        return askArrange(one, c.getName() + ": keep it on top of your library? Click it to put it on the bottom", "Bottom").isEmpty();
    }

    /** Shows the cards and returns those the person sends away (the rest stay on top, in order). */
    private List<Card> askArrange(forge.game.card.CardCollection cards, String prompt, String awayTo) {
        List<com.planesnomad.gui.model.CardSnapshot> shown = new ArrayList<>();
        for (Card c : cards) {
            shown.add(SnapshotBuilder.card(c));
        }
        Object answer = bridge.ask(new HumanBridge.ArrangeCards(prompt, shown, awayTo));
        List<Card> away = new ArrayList<>();
        if (answer instanceof List<?> ids) {
            for (Card c : cards) {
                if (ids.contains(c.getId())) {
                    away.add(c);
                }
            }
        }
        return away;
    }

    private static org.apache.commons.lang3.tuple.ImmutablePair<forge.game.card.CardCollection, forge.game.card.CardCollection>
            split(forge.game.card.CardCollection all, List<Card> away) {
        forge.game.card.CardCollection top = new forge.game.card.CardCollection();
        forge.game.card.CardCollection rest = new forge.game.card.CardCollection();
        for (Card c : all) {
            (away.contains(c) ? rest : top).add(c);
        }
        return org.apache.commons.lang3.tuple.ImmutablePair.of(top, rest);
    }

    // ------------------------------------------------------------------ combat

    @Override
    public void declareAttackers(Player attacker, Combat combat) {
        GameEntity defender = combat.getDefenders().isEmpty() ? null : defaultDefender(combat);
        List<Integer> candidates = new ArrayList<>();
        for (Card c : attacker.getCreaturesInPlay()) {
            if (defender != null && CombatUtil.canAttack(c, defender)) {
                candidates.add(c.getId());
            }
        }
        if (candidates.isEmpty() || bridge.autoPass() == HumanBridge.AutoPass.END_OF_TURN) {
            return;   // nothing can attack, or you passed the turn: no attack
        }
        Object answer = bridge.ask(new Attackers("Declare attackers: click creatures to attack "
            + defender.getName() + ", then Attack", candidates, defender.getName()));
        if (!(answer instanceof List<?> ids) || ids.isEmpty()) {
            return;
        }
        Game game = attacker.getGame();
        for (Object id : ids) {
            Card c = game.findById(((Number) id).intValue());
            if (c != null && CombatUtil.canAttack(c, defender)) {
                combat.addAttacker(c, defender);
            }
        }
        if (!CombatUtil.validateAttackers(combat)) {
            // Requirements this screen can't express yet (e.g. "must attack"): the AI declares instead.
            for (Card c : new ArrayList<>(combat.getAttackers())) {
                combat.removeFromCombat(c);
            }
            super.declareAttackers(attacker, combat);
        }
    }

    /** The defending player (planeswalkers and battles as attack targets come later). */
    private GameEntity defaultDefender(Combat combat) {
        for (GameEntity e : combat.getDefenders()) {
            if (e instanceof Player) {
                return e;
            }
        }
        return combat.getDefenders().iterator().next();
    }

    @Override
    public void declareBlockers(Player defender, Combat combat) {
        List<Card> attackers = new ArrayList<>(combat.getAttackers());
        if (attackers.isEmpty()) {
            return;
        }
        Map<Integer, List<Integer>> legal = new LinkedHashMap<>();
        for (Card b : defender.getCreaturesInPlay()) {
            if (!CombatUtil.canBlock(b, combat)) {
                continue;
            }
            List<Integer> canBlock = new ArrayList<>();
            for (Card a : attackers) {
                if (CombatUtil.canBlock(a, b, combat)) {
                    canBlock.add(a.getId());
                }
            }
            if (!canBlock.isEmpty()) {
                legal.put(b.getId(), canBlock);
            }
        }
        if (legal.isEmpty()) {
            return;
        }
        List<Integer> attackerIds = attackers.stream().map(Card::getId).toList();
        Game game = defender.getGame();
        String error = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            Object answer = bridge.ask(new Blockers("Declare blockers: click one of your creatures, then the attacker "
                + "it blocks; then Block", attackerIds, legal, error));
            if (!(answer instanceof Map<?, ?> pairs) || pairs.isEmpty()) {
                return;
            }
            List<Card> placed = new ArrayList<>();
            for (Map.Entry<?, ?> p : pairs.entrySet()) {
                Card blocker = game.findById(((Number) p.getKey()).intValue());
                Card attacker = game.findById(((Number) p.getValue()).intValue());
                if (blocker != null && attacker != null && CombatUtil.canBlock(attacker, blocker, combat)) {
                    combat.addBlocker(attacker, blocker);
                    placed.add(blocker);
                }
            }
            error = CombatUtil.validateBlocks(combat, defender);
            if (error == null) {
                return;
            }
            for (Card b : placed) {
                combat.undoBlockingAssignment(b);
            }
        }
        super.declareBlockers(defender, combat);   // still not legal after three tries: the AI blocks
    }
}

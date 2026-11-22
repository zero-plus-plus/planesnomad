package com.planesnomad.gui.ui;

import com.planesnomad.gui.engine.HumanBridge;
import com.planesnomad.gui.engine.HumanBridge.Attackers;
import com.planesnomad.gui.engine.HumanBridge.Blockers;
import com.planesnomad.gui.engine.HumanBridge.Option;
import com.planesnomad.gui.engine.HumanBridge.Priority;
import com.planesnomad.gui.engine.HumanBridge.Request;
import com.planesnomad.gui.engine.HumanBridge.Targets;
import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.MatchSnapshot.StackItem;
import com.planesnomad.gui.ui.CardGroups.Pile;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import java.awt.Color;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Playing through the window: turns the engine's current question ({@link HumanBridge}) into
 * highlights and click handling, and answers it.
 *
 * - Priority: cards you can play glow green; click one to play it (several ways: a small menu);
 *   relics: click, then confirm. Pass with the button or Space.
 * - Targets: legal targets glow gold (cards, player portraits, stack items); click to choose.
 * - Attackers: click creatures (or piles) to toggle them; Attack confirms (Space).
 * - Blockers: click one of your creatures, then the attacker it blocks; Block confirms.
 */
public final class PlayInput implements CardRow.Interaction {

    /** A button in the prompt bar. */
    public record Action(String label, Runnable run, boolean primary) { }

    private final HumanBridge bridge;
    private final Runnable changed;
    private final java.awt.Component parent;
    private Request request;
    private final Set<String> chosenTargets = new LinkedHashSet<>();
    private final Set<Integer> chosenAttackers = new LinkedHashSet<>();
    private final Map<Integer, Integer> blocks = new LinkedHashMap<>();
    private Integer pendingBlocker;
    /** Scry and similar: the cards the person sends away (bottom / graveyard). */
    private final Set<Integer> sentAway = new LinkedHashSet<>();

    /**
     * @param changed called (on the Swing thread) when the question or the choices change: the
     *                window repaints highlights and the prompt bar
     */
    public PlayInput(HumanBridge bridge, java.awt.Component parent, Runnable changed) {
        this.bridge = bridge;
        this.parent = parent;
        this.changed = changed;
    }

    /** Swing thread: a new question (or null when it was answered). */
    public void onRequest(Request r) {
        request = r;
        chosenTargets.clear();
        chosenAttackers.clear();
        blocks.clear();
        pendingBlocker = null;
        sentAway.clear();
        changed.run();
    }

    public Request request() {
        return request;
    }

    private void answer(Object value) {
        Request r = request;
        request = null;   // no double answers while the engine takes over
        changed.run();
        if (r != null) {
            bridge.answer(value);
        }
    }

    // ------------------------------------------------------------------ prompt bar

    public String prompt() {
        Request r = request;
        if (r == null) {
            return switch (bridge.autoPass()) {
                case UNTIL_EVENT -> "Passing the turn (stops if the enemy acts)\u2026";
                case END_OF_TURN -> "Auto-passing everything this turn\u2026";
                default -> null;
            };
        }
        if (r instanceof HumanBridge.ManaPayment m) {
            return m.prompt() + (m.pool().isEmpty() ? "" : "   (pool: " + m.pool() + ")");
        }
        if (r instanceof HumanBridge.ArrangeCards a) {
            int away = sentAway.size();
            return a.prompt() + "  (" + (a.cards().size() - away) + " on top, " + away + " to " + a.awayTo().toLowerCase() + ")";
        }
        if (r instanceof Targets t && t.max() > 1) {
            return t.prompt() + "  (" + chosenTargets.size() + " chosen)";
        }
        if (r instanceof Attackers) {
            return r.prompt() + "  (" + chosenAttackers.size() + " attacking)";
        }
        if (r instanceof Blockers b) {
            String base = pendingBlocker != null ? "Now click the attacker it blocks" : b.prompt();
            return (b.error() != null ? "Not allowed: " + b.error() + "  -  " : "") + base + "  (" + blocks.size() + " blocks)";
        }
        return r.prompt();
    }

    public List<Action> actions() {
        Request r = request;
        List<Action> out = new ArrayList<>();
        if (r == null && bridge.autoPass() != HumanBridge.AutoPass.NONE) {
            out.add(new Action("Stop", () -> {
                bridge.setAutoPass(HumanBridge.AutoPass.NONE);
                changed.run();
            }, true));
        } else if (r instanceof Priority) {
            out.add(new Action("Pass", () -> answer(null), true));
            out.add(new Action("Pass turn", () -> autoPass(HumanBridge.AutoPass.UNTIL_EVENT), false));
            out.add(new Action("Auto-pass all", () -> autoPass(HumanBridge.AutoPass.END_OF_TURN), false));
        } else if (r instanceof HumanBridge.ArrangeCards) {
            out.add(new Action("Done", () -> answer(new ArrayList<>(sentAway)), true));
        } else if (r instanceof HumanBridge.ManaPayment) {
            out.add(new Action("Auto-pay the rest", () -> answer("auto"), true));
            out.add(new Action("Cancel", () -> answer(null), false));
        } else if (r instanceof Targets t) {
            if (t.max() > 1) {
                boolean ok = chosenTargets.size() >= t.min() && chosenTargets.size() <= t.max();
                out.add(new Action(ok ? "Done" : "Choose " + t.min(), ok ? () -> answer(new ArrayList<>(chosenTargets)) : () -> { }, true));
            }
            out.add(new Action("Cancel", () -> answer(null), false));
        } else if (r instanceof Attackers a) {
            out.add(new Action(chosenAttackers.isEmpty() ? "No attack" : "Attack (" + chosenAttackers.size() + ")",
                () -> answer(new ArrayList<>(chosenAttackers)), true));
            out.add(new Action("All", () -> {
                chosenAttackers.addAll(a.candidates());
                changed.run();
            }, false));
            if (!chosenAttackers.isEmpty()) {
                out.add(new Action("Clear", () -> {
                    chosenAttackers.clear();
                    changed.run();
                }, false));
            }
        } else if (r instanceof Blockers) {
            out.add(new Action(blocks.isEmpty() ? "No blocks" : "Block (" + blocks.size() + ")",
                () -> answer(new LinkedHashMap<>(blocks)), true));
            if (!blocks.isEmpty() || pendingBlocker != null) {
                out.add(new Action("Clear", () -> {
                    blocks.clear();
                    pendingBlocker = null;
                    changed.run();
                }, false));
            }
        }
        return out;
    }

    /**
     * Pass priority automatically: until something happens (an enemy spell or ability, or your
     * next turn), or hard until the end of the turn. Answers the current priority question too.
     */
    public void autoPass(HumanBridge.AutoPass mode) {
        Request r = request;
        if (r instanceof Priority) {
            request = null;
        }
        bridge.setAutoPass(mode);
        changed.run();
    }

    public HumanBridge bridge() {
        return bridge;
    }

    /** Space: the main button (pass, attack, block, done). */
    public void primary() {
        actions().stream().filter(Action::primary).findFirst().ifPresent(a -> a.run().run());
    }

    /** Esc: cancel a target choice, or drop a half-made block. */
    public void cancel() {
        if (request instanceof Targets || request instanceof HumanBridge.ManaPayment) {
            answer(null);
        } else if (request instanceof Blockers && pendingBlocker != null) {
            pendingBlocker = null;
            changed.run();
        }
    }

    // ------------------------------------------------------------------ cards

    @Override
    public Color mark(CardSnapshot c) {
        Request r = request;
        if (r == null || c == null) {
            return null;
        }
        if (r instanceof Priority p) {
            return p.options().stream().anyMatch(o -> o.cardId() == c.id() && !o.relic()) ? Theme.YOU : null;
        }
        if (r instanceof HumanBridge.ManaPayment m) {
            return m.sources().stream().anyMatch(o -> o.cardId() == c.id()) ? Theme.ACCENT : null;
        }
        if (r instanceof Targets t) {
            String key = "card:" + c.id();
            return chosenTargets.contains(key) ? Theme.ACCENT.brighter()
                : t.candidates().contains(key) ? Theme.ACCENT : null;
        }
        if (r instanceof Attackers a) {
            return chosenAttackers.contains(c.id()) ? Theme.ATTACKING
                : a.candidates().contains(c.id()) ? faded(Theme.ATTACKING) : null;
        }
        if (r instanceof Blockers b) {
            if (pendingBlocker != null && pendingBlocker == c.id()) {
                return Theme.BLOCKING.brighter();
            }
            if (blocks.containsKey(c.id())) {
                return Theme.BLOCKING;
            }
            if (pendingBlocker != null && b.legal().getOrDefault(pendingBlocker, List.of()).contains(c.id())) {
                return Theme.ATTACKING;   // can be blocked by the chosen creature: click it
            }
            return b.legal().containsKey(c.id()) ? faded(Theme.BLOCKING) : null;
        }
        return null;
    }

    private static Color faded(Color c) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 150);
    }

    @Override
    public void click(CardRow row, Pile pile, MouseEvent e) {
        Request r = request;
        if (r == null) {
            return;
        }
        if (r instanceof HumanBridge.ManaPayment m) {
            List<Option> opts = new ArrayList<>();
            for (CardSnapshot c : pile.cards()) {
                m.sources().stream().filter(o -> o.cardId() == c.id()).forEach(opts::add);
                if (!opts.isEmpty()) {
                    break;
                }
            }
            choose(opts, row, e);
            return;
        }
        if (r instanceof Priority p) {
            List<Option> opts = new ArrayList<>();
            for (CardSnapshot c : pile.cards()) {
                for (Option o : p.options()) {
                    if (o.cardId() == c.id() && !o.relic()) {
                        opts.add(o);
                    }
                }
                if (!opts.isEmpty()) {
                    break;   // identical cards: the first one that can be played
                }
            }
            choose(opts, row, e);
        } else if (r instanceof Targets t) {
            for (CardSnapshot c : pile.cards()) {
                String key = "card:" + c.id();
                if (t.candidates().contains(key) && !chosenTargets.contains(key)) {
                    pickTarget(t, key);
                    return;
                }
            }
            // clicking a chosen target again un-chooses it
            pile.cards().stream().map(c -> "card:" + c.id()).filter(chosenTargets::contains).findFirst()
                .ifPresent(k -> {
                    chosenTargets.remove(k);
                    changed.run();
                });
        } else if (r instanceof Attackers a) {
            List<Integer> ids = pile.cards().stream().map(CardSnapshot::id).filter(a.candidates()::contains).toList();
            if (ids.isEmpty()) {
                return;
            }
            boolean all = chosenAttackers.containsAll(ids);
            if (all) {
                ids.forEach(chosenAttackers::remove);
            } else {
                chosenAttackers.addAll(ids);
            }
            changed.run();
        } else if (r instanceof Blockers b) {
            // One of your creatures: pick it (or un-assign it). An attacker: assign the picked blocker.
            for (CardSnapshot c : pile.cards()) {
                if (b.legal().containsKey(c.id())) {
                    if (blocks.containsKey(c.id())) {
                        blocks.remove(c.id());
                        pendingBlocker = null;
                    } else {
                        pendingBlocker = c.id();
                    }
                    changed.run();
                    return;
                }
            }
            if (pendingBlocker != null) {
                for (CardSnapshot c : pile.cards()) {
                    if (b.legal().getOrDefault(pendingBlocker, List.of()).contains(c.id())) {
                        blocks.put(pendingBlocker, c.id());
                        pendingBlocker = null;
                        changed.run();
                        return;
                    }
                }
            }
        }
    }

    private void pickTarget(Targets t, String key) {
        if (t.max() <= 1) {
            answer(List.of(key));
            return;
        }
        if (chosenTargets.size() < t.max()) {
            chosenTargets.add(key);
        }
        changed.run();
        if (chosenTargets.size() == t.max()) {
            answer(new ArrayList<>(chosenTargets));
        }
    }

    /** One option: play it. Several (e.g. a spell and an ability on the same card): a small menu. */
    private void choose(List<Option> opts, java.awt.Component where, MouseEvent e) {
        if (opts.isEmpty()) {
            return;
        }
        if (opts.size() == 1 || java.awt.GraphicsEnvironment.isHeadless()) {
            answer(opts.get(0).key());
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        for (Option o : opts) {
            JMenuItem it = new JMenuItem(o.label());
            it.addActionListener(a -> answer(o.key()));
            menu.add(it);
        }
        menu.show(where, e.getX(), e.getY());
    }

    // ------------------------------------------------------------------ players, stack, relics

    public Color playerMark(String name) {
        if (request instanceof Targets t) {
            String key = "player:" + name;
            return chosenTargets.contains(key) ? Theme.ACCENT.brighter() : t.candidates().contains(key) ? Theme.ACCENT : null;
        }
        return null;
    }

    public void playerClick(String name) {
        if (request instanceof Targets t && t.candidates().contains("player:" + name)) {
            pickTarget(t, "player:" + name);
        }
    }

    public Color stackMark(StackItem item) {
        if (request instanceof Targets t && t.candidates().contains("stack:" + item.id())) {
            return Theme.ACCENT;
        }
        return null;
    }

    public void stackClick(StackItem item) {
        if (request instanceof Targets t && t.candidates().contains("stack:" + item.id())) {
            pickTarget(t, "stack:" + item.id());
        }
    }

    /** Relics and consumables: confirm before using (like other roguelikes), then play. */
    public void relicClick(String relicName) {
        if (!(request instanceof Priority p)) {
            return;
        }
        List<Option> opts = p.options().stream().filter(o -> o.relic() && o.cardName().equals(relicName)).toList();
        if (opts.isEmpty()) {
            return;
        }
        Option o = opts.get(0);
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            answer(o.key());
            return;
        }
        int ok = JOptionPane.showConfirmDialog(parent, o.label() + "?", "Use " + relicName,
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (ok == JOptionPane.OK_OPTION && request == p) {
            answer(o.key());
        }
    }

    /** The card panel's contents while looking at library cards (scry, surveil), else null. */
    public CardChoicePanel.Model choiceModel() {
        if (!(request instanceof HumanBridge.ArrangeCards a)) {
            return null;
        }
        return new CardChoicePanel.Model() {
            public List<CardSnapshot> cards() {
                return a.cards();
            }

            public boolean isAway(CardSnapshot c) {
                return sentAway.contains(c.id());
            }

            public String awayLabel() {
                return a.awayTo();
            }

            public void toggle(CardSnapshot c) {
                if (!sentAway.remove(c.id())) {
                    sentAway.add(c.id());
                }
                changed.run();
            }
        };
    }

    /** True if a relic can be used now (its icon can show that). */
    public boolean relicUsable(String relicName) {
        return request instanceof Priority p && p.options().stream().anyMatch(o -> o.relic() && o.cardName().equals(relicName));
    }
}

package com.planesnomad.gui.engine;

import com.planesnomad.gui.model.CardSnapshot;
import com.planesnomad.gui.model.HistoryEntry;
import com.planesnomad.gui.model.HistoryEntry.Kind;
import com.planesnomad.gui.model.RelicSnapshot;
import forge.game.Game;
import forge.game.GameEntityView;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.event.GameEvent;
import forge.game.event.GameEventAttackersDeclared;
import forge.game.event.GameEventBlockersDeclared;
import forge.game.event.GameEventCardChangeZone;
import forge.game.event.GameEventGameOutcome;
import forge.game.event.GameEventLandPlayed;
import forge.game.event.GameEventPlayerDamaged;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.game.zone.ZoneType;
import forge.game.zone.ZoneView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Turns engine events into {@link HistoryEntry}s for the history icon bar. Runs on the engine
 * thread (fed by {@link MatchRunner}), where reading the game is safe.
 *
 * Recorded: spells cast, activated abilities, triggers, lands played, attacks, blocks, permanents
 * dying / exiled / returned to hand, tokens created, damage to players, and the game's end.
 * Several deaths (or exiles, returns, tokens, damage to one player) in a row in the same step are
 * merged into one entry with a count, so a sweeper killing 200 goblins is one icon.
 */
public final class HistoryRecorder {

    /** Oldest entries are dropped beyond this many (the full log keeps everything). */
    static final int MAX_ENTRIES = 400;
    /** At most this many cards / detail lines are kept per entry (the count says how many in all). */
    static final int MAX_CARDS = 24;
    static final int MAX_DETAILS = 14;

    private final Game game;
    private final String youName;
    private final Function<Card, RelicSnapshot> relicOf;
    private final List<HistoryEntry> entries = new ArrayList<>();
    private int seq;
    /** Merge key of the last entry ("" = don't merge into it): kind, step, side, target. */
    private String lastMergeKey = "";

    HistoryRecorder(Game game, String youName, Function<Card, RelicSnapshot> relicOf) {
        this.game = game;
        this.youName = youName;
        this.relicOf = relicOf;
    }

    /** The history so far, oldest first. */
    public List<HistoryEntry> entries() {
        return List.copyOf(entries);
    }

    /** Never throws: the history is a display detail and must not disturb the match. */
    public void record(GameEvent event) {
        try {
            if (event instanceof GameEventSpellAbilityCast e) {
                Integer cause = currentCause();
                lastResolvedCause = null;
                cast(e);
                if (cause != null && e.si() != null && e.si().isTrigger()) {
                    // A trigger that an action caused (e.g. a creature dying triggers something).
                    String src = e.si().getSourceCard() == null ? "?" : e.si().getSourceCard().getName();
                    addEffect(cause, src + " triggers");
                }
            } else if (event instanceof forge.game.event.GameEventSpellResolved e) {
                Integer cause = currentCause();
                if (cause != null && e.hasFizzled()) {
                    addEffect(cause, "Fizzled (no legal targets)");
                }
                lastResolvedCause = cause;   // deaths checked right after resolving still belong to it
            } else if (event instanceof forge.game.event.GameEventCardDamaged e) {
                Integer cause = currentCause();
                if (cause != null && e.card() != null) {
                    String by = e.source() == null ? "" : e.source().getName() + " deals ";
                    addEffect(cause, by.isEmpty() ? e.card().getName() + " takes " + e.amount() + " damage"
                        : by + e.amount() + " damage to " + e.card().getName());
                }
            } else if (event instanceof forge.game.event.GameEventTurnPhase) {
                lastResolvedCause = null;
            } else if (event instanceof GameEventLandPlayed e) {
                land(e);
            } else if (event instanceof GameEventAttackersDeclared e) {
                attack(e);
            } else if (event instanceof GameEventBlockersDeclared e) {
                blocks(e);
            } else if (event instanceof GameEventCardChangeZone e) {
                zoneChange(e);
            } else if (event instanceof GameEventPlayerDamaged e) {
                damage(e);
            } else if (event instanceof GameEventGameOutcome e) {
                String winner = e.winningPlayerName();
                add(Kind.GAME_END, winner != null && winner.equals(youName), winner == null ? "" : winner,
                    winner == null ? "The game is a draw" : winner + " wins", e.outcomeStrings(), List.of(), null, "");
            }
        } catch (RuntimeException ex) {
            // ignore: a missing history icon is better than a stopped match
        }
    }

    // ------------------------------------------------------------------ events

    private void cast(GameEventSpellAbilityCast e) {
        StackItemView si = e.si();
        if (si == null) {
            return;
        }
        Card source = card(si.getSourceCard());
        String who = name(si.getActivatingPlayer());
        String sourceName = source != null ? source.getName() : si.getSourceCard() != null ? si.getSourceCard().getName() : "?";
        Kind kind = si.isTrigger() ? Kind.TRIGGER : si.isAbility() ? Kind.ABILITY : Kind.CAST;
        String title = switch (kind) {
            case TRIGGER -> sourceName + " triggers";
            case ABILITY -> who + " activates " + sourceName;
            default -> who + " casts " + sourceName;
        };
        List<String> details = new ArrayList<>();
        String text = clean(si.getText(), sourceName);
        if (!text.isEmpty()) {
            details.add(text);
        }
        List<CardSnapshot> cards = new ArrayList<>();
        RelicSnapshot relic = null;
        if (source != null) {
            relic = relicOf.apply(source);
            CardSnapshot snap = SnapshotBuilder.card(source);
            cards.add(kind == Kind.CAST || relic != null ? snap
                : snap.asAbility(kind == Kind.TRIGGER ? "Triggered ability" : "Activated ability", text));
        }
        List<String> targets = new ArrayList<>();
        if (si.getTargetPlayers() != null) {
            si.getTargetPlayers().forEach(p -> targets.add(p.getName()));
        }
        if (si.getTargetCards() != null) {
            for (CardView cv : si.getTargetCards()) {
                targets.add(cv.getName());
                Card t = card(cv);
                if (t != null) {
                    cards.add(SnapshotBuilder.card(t));
                }
            }
        }
        if (si.getTargetStackItems() != null) {
            si.getTargetStackItems().forEach(t -> targets.add(t.getSourceCard() == null ? "a spell" : t.getSourceCard().getName()));
        }
        if (!targets.isEmpty() && !text.contains(targets.get(0))) {
            details.add("Target: " + String.join(", ", targets));
        }
        add(kind, who.equals(youName), who, title, details, cards, relic, "");
        causeBySlot.put(si.getId(), seq);
    }

    private void land(GameEventLandPlayed e) {
        Card land = card(e.land());
        String who = name(e.player());
        add(Kind.LAND, who.equals(youName), who, who + " plays " + (land == null ? "a land" : land.getName()),
            List.of(), land == null ? List.of() : List.of(SnapshotBuilder.card(land)), null, "");
    }

    private void attack(GameEventAttackersDeclared e) {
        var map = e.attackersMap();
        if (map == null || map.isEmpty()) {
            return;
        }
        String who = name(e.player());
        List<CardSnapshot> cards = new ArrayList<>();
        List<String> details = new ArrayList<>();
        for (GameEntityView defender : map.keySet()) {
            Collection<CardView> attackers = map.get(defender);
            List<String> names = new ArrayList<>();
            for (CardView cv : attackers) {
                names.add(cv.getName());
                Card c = card(cv);
                if (c != null) {
                    cards.add(SnapshotBuilder.card(c));
                }
            }
            details.add("→ " + defender.getName() + ": " + summarize(names));
        }
        int n = map.size();
        add(Kind.ATTACK, who.equals(youName), who,
            who + " attacks with " + n + (n == 1 ? " creature" : " creatures"), details, cards, null, "");
    }

    private void blocks(GameEventBlockersDeclared e) {
        // Read the blocks from the combat itself (who blocks whom), not from the event's maps.
        var combat = game.getCombat();
        if (combat == null) {
            return;
        }
        String who = name(e.defendingPlayer());
        List<CardSnapshot> cards = new ArrayList<>();
        List<String> details = new ArrayList<>();
        int blockers = 0;
        for (Card attacker : combat.getAttackers()) {
            var blockedBy = combat.getBlockers(attacker);
            if (blockedBy == null || blockedBy.isEmpty()) {
                continue;
            }
            List<String> names = new ArrayList<>();
            for (Card b : blockedBy) {
                names.add(b.getName());
                blockers++;
                cards.add(SnapshotBuilder.card(b));
            }
            details.add(String.join(" + ", names) + " blocks " + attacker.getName());
        }
        if (blockers == 0) {
            return;
        }
        add(Kind.BLOCK, who.equals(youName), who,
            who + " blocks with " + blockers + (blockers == 1 ? " creature" : " creatures"), details, cards, null, "");
    }

    private void zoneChange(GameEventCardChangeZone e) {
        ZoneType from = zone(e.from());
        ZoneType to = zone(e.to());
        CardView cv = e.card();
        if (cv == null || to == null) {
            return;
        }
        Card c = card(cv);
        CardSnapshot snap = c == null ? null : SnapshotBuilder.card(c);
        if (c != null && relicOf.apply(c) != null) {
            return;   // relics and consumables live in the command zone; their use is the ability entry
        }
        String who = cv.getController() != null ? cv.getController().getName() : name(cv.getOwner());
        boolean mine = who.equals(youName);
        String name = cv.getName();
        if (from == null && to == ZoneType.Battlefield && cv.isToken()) {
            merge(Kind.TOKENS, mine, who, "token", snap, name,
                n -> who + (n == 1 ? " creates a " + name : " creates " + n + " tokens"));
            return;
        }
        if (from != ZoneType.Battlefield) {
            return;   // draws, casting, resolving spells, ... are shown by other entries or not at all
        }
        switch (to) {
            case Graveyard -> merge(Kind.DEATH, mine, who, "", snap, name,
                n -> n == 1 ? name + (c != null && c.isCreature() || cv.isToken() ? " dies" : " is put into a graveyard")
                    : n + " permanents die");
            case Exile -> merge(Kind.EXILE, mine, who, "", snap, name,
                n -> n == 1 ? name + " is exiled" : n + " permanents are exiled");
            case Hand -> merge(Kind.BOUNCE, mine, who, "", snap, name,
                n -> n == 1 ? name + " returns to its owner's hand" : n + " permanents return to hand");
            case Library -> merge(Kind.BOUNCE, mine, who, "library", snap, name,
                n -> n == 1 ? name + " is put into its owner's library" : n + " permanents go into libraries");
            default -> { }
        }
    }

    private void damage(GameEventPlayerDamaged e) {
        String target = name(e.target());
        Card source = card(e.source());
        String sourceName = e.source() == null ? "?" : e.source().getName();
        String sourceController = e.source() != null && e.source().getController() != null
            ? e.source().getController().getName() : "";
        // Colored by who dealt it (the target is in the title).
        boolean mine = sourceController.equals(youName);
        Integer cause = currentCause();
        if (cause != null) {
            addEffect(cause, sourceName + " deals " + e.amount() + " damage to " + target);
        }
        String key = Kind.DAMAGE + "|" + target + "|" + (e.combat() ? "combat" : "other");
        String line = sourceName + ": " + e.amount();
        HistoryEntry last = lastIfMergeable(key);
        if (last != null) {
            int total = parseTotal(last.title()) + e.amount();
            replaceLast(last, target + " takes " + total + (e.combat() ? " combat damage" : " damage"),
                line, source == null ? null : SnapshotBuilder.card(source), last.count() + 1);
            return;
        }
        add(Kind.DAMAGE, mine, sourceController, target + " takes " + e.amount() + (e.combat() ? " combat damage" : " damage"),
            List.of(line), source == null ? List.of() : List.of(SnapshotBuilder.card(source)), null, key);
    }

    // ------------------------------------------------------------------ building entries

    private void add(Kind kind, boolean byYou, String actor, String title, List<String> details,
                     List<CardSnapshot> cards, RelicSnapshot relic, String mergeKey) {
        title = grammar(title);
        entries.add(new HistoryEntry(++seq, game.getPhaseHandler().getTurn(), kind, byYou, actor, title,
            cap(details, MAX_DETAILS), cap(cards, MAX_CARDS), 1, relic));
        // Merge only with the next event of the same kind in the same step.
        lastMergeKey = mergeKey == null || mergeKey.isEmpty() ? "" : mergeKey + "|" + step();
        if (entries.size() > MAX_ENTRIES) {
            entries.remove(0);
        }
    }

    /** Adds to the last entry if it is the same kind of thing in the same step, else starts one. */
    private void merge(Kind kind, boolean mine, String who, String extraKey, CardSnapshot snap, String line,
                       Function<Integer, String> title) {
        Integer cause = currentCause();
        if (cause != null) {
            addEffect(cause, title.apply(1).replace(" a " + line, " " + line));
        }
        mergeEntry(kind, mine, who, extraKey, snap, line, title);
    }

    private void mergeEntry(Kind kind, boolean mine, String who, String extraKey, CardSnapshot snap, String line,
                            Function<Integer, String> title) {
        String key = kind + "|" + who + "|" + extraKey;
        HistoryEntry last = lastIfMergeable(key);
        if (last != null) {
            replaceLast(last, title.apply(last.count() + 1), line, snap, last.count() + 1);
            return;
        }
        add(kind, mine, who, title.apply(1), List.of(line), snap == null ? List.of() : List.of(snap), null, key);
    }

    private HistoryEntry lastIfMergeable(String key) {
        if (entries.isEmpty() || lastMergeKey.isEmpty()) {
            return null;
        }
        return (key + "|" + step()).equals(lastMergeKey) ? entries.get(entries.size() - 1) : null;
    }

    private String step() {
        var ph = game.getPhaseHandler();
        return ph.getTurn() + ":" + ph.getPhase();
    }

    private void replaceLast(HistoryEntry last, String title, String line, CardSnapshot snap, int count) {
        List<String> details = new ArrayList<>(last.details());
        // The same line again (e.g. "Goblin Token" for each of 200 tokens) becomes "Goblin Token ×200".
        int same = -1;
        for (int i = 0; i < details.size(); i++) {
            if (details.get(i).replaceAll(" ×\\d+$", "").equals(line)) {
                same = i;
                break;
            }
        }
        if (same >= 0) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(" ×(\\d+)$").matcher(details.get(same));
            int n = m.find() ? Integer.parseInt(m.group(1)) : 1;
            details.set(same, line + " ×" + (n + 1));
        } else if (details.size() < MAX_DETAILS) {
            details.add(line);
        } else if (details.size() == MAX_DETAILS) {
            details.add("…");
        }
        List<CardSnapshot> cards = new ArrayList<>(last.cards());
        if (snap != null && cards.size() < MAX_CARDS) {
            cards.add(snap);
        }
        entries.set(entries.size() - 1, new HistoryEntry(last.seq(), last.turn(), last.kind(), last.byYou(),
            last.actor(), grammar(title), details, cards, count, last.relic(), last.effects()));
    }

    // ------------------------------------------------------------------ effects (what an action led to)

    /** At most this many effect lines per entry (repeats are merged first). */
    static final int MAX_EFFECTS = 10;

    /** Stack item id -> history entry of that cast / ability / trigger. */
    private final Map<Integer, Integer> causeBySlot = new HashMap<>();
    /** The entry whose item just finished resolving (state-based deaths right after belong to it). */
    private Integer lastResolvedCause;

    /**
     * The entry that is causing what happens now: the item resolving on the stack, the one that
     * just resolved, or the attack during combat damage. Null if nothing in particular.
     */
    private Integer currentCause() {
        var stack = game.getStack();
        if (stack.isResolving() && stack.peek() != null) {
            Integer c = causeBySlot.get(stack.peek().getId());
            if (c != null) {
                return c;
            }
        }
        if (lastResolvedCause != null) {
            return lastResolvedCause;
        }
        var phase = game.getPhaseHandler().getPhase();
        if (phase == forge.game.phase.PhaseType.COMBAT_DAMAGE || phase == forge.game.phase.PhaseType.COMBAT_FIRST_STRIKE_DAMAGE
            || phase == forge.game.phase.PhaseType.COMBAT_END) {
            int turn = game.getPhaseHandler().getTurn();
            for (int i = entries.size() - 1; i >= 0 && entries.get(i).turn() == turn; i--) {
                if (entries.get(i).kind() == Kind.ATTACK) {
                    return entries.get(i).seq();
                }
            }
        }
        return null;
    }

    /** Adds "line" to the effects of entry {@code seq}; the same line again becomes "line ×2". */
    private void addEffect(int seqOfCause, String line) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            HistoryEntry e = entries.get(i);
            if (e.seq() != seqOfCause) {
                continue;
            }
            List<String> fx = new ArrayList<>(e.effects());
            String lastLine = fx.isEmpty() ? "" : fx.get(fx.size() - 1);
            String bare = lastLine.replaceAll(" \u00d7\\d+$", "");
            if (bare.equals(line)) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(" \u00d7(\\d+)$").matcher(lastLine);
                int n = m.find() ? Integer.parseInt(m.group(1)) : 1;
                fx.set(fx.size() - 1, line + " \u00d7" + (n + 1));
            } else if (fx.size() < MAX_EFFECTS) {
                fx.add(line);
            } else {
                String more = fx.get(fx.size() - 1);
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\u2026and (\\d+) more$").matcher(more);
                if (m.find()) {
                    fx.set(fx.size() - 1, "\u2026and " + (Integer.parseInt(m.group(1)) + 1) + " more");
                } else {
                    fx.add("\u2026and 1 more");
                }
            }
            entries.set(i, e.withEffects(fx));
            return;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static final java.util.regex.Pattern YOU_VERB =
        java.util.regex.Pattern.compile("^You (casts|activates|plays|attacks|blocks|takes|creates|wins)\\b");

    /** "You casts" -> "You cast" (the person playing is called "You"). */
    static String grammar(String title) {
        java.util.regex.Matcher m = YOU_VERB.matcher(title);
        return m.find() ? "You " + m.group(1).substring(0, m.group(1).length() - 1) + title.substring(m.end()) : title;
    }

    private Card card(CardView view) {
        if (view == null) {
            return null;
        }
        try {
            Card c = game.findByView(view);
            return c != null ? c : game.findById(view.getId());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String name(PlayerView p) {
        return p == null ? "" : p.getName();
    }

    private static ZoneType zone(ZoneView z) {
        return z == null ? null : z.zoneType();
    }

    /** The stack text without a leading "<card name> - " (the title already names the card). */
    private static String clean(String text, String sourceName) {
        if (text == null) {
            return "";
        }
        String t = text.replace("\\n", " ").replaceAll("\\s*\\(\\d+\\)", "").trim();   // engine ids, e.g. "(33)"
        if (t.startsWith(sourceName + " - ")) {
            t = t.substring(sourceName.length() + 3);
        }
        t = t.replaceAll("\\s*\\[[^\\]]*\\]\\s*", " ").trim();
        // A permanent spell's stack text is just its type line and stats: the card shows that.
        return t.matches("(Legendary )?(Artifact )?(Creature|Enchantment|Artifact|Planeswalker)[^.]*?(\\d+ / \\d+)?") ? "" : t;
    }

    /** "Goblin Token ×3, Krenko, Mob Boss" */
    private static String summarize(List<String> names) {
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        names.forEach(n -> counts.merge(n, 1, Integer::sum));
        List<String> parts = new ArrayList<>();
        counts.forEach((n, k) -> parts.add(k == 1 ? n : n + " ×" + k));
        return String.join(", ", parts);
    }

    private static int parseTotal(String title) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("takes (\\d+)").matcher(title);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static <T> List<T> cap(List<T> list, int max) {
        return list.size() <= max ? list : list.subList(0, max);
    }
}

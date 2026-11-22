package com.planesnomad.gui.engine;

import com.google.common.eventbus.Subscribe;
import com.planesnomad.gui.model.MatchSnapshot;
import com.planesnomad.gui.model.PlayerLook;
import forge.game.Game;
import forge.game.Match;
import forge.game.event.GameEvent;
import forge.game.event.GameEventAttackersDeclared;
import forge.game.event.GameEventBlockersDeclared;
import forge.game.event.GameEventCardDamaged;
import forge.game.event.GameEventGameFinished;
import forge.game.event.GameEventLandPlayed;
import forge.game.event.GameEventPlayerDamaged;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.event.GameEventSpellResolved;
import forge.game.event.GameEventTurnBegan;
import forge.game.event.GameEventTurnPhase;
import forge.game.player.Player;

import java.util.function.Consumer;

/**
 * Runs one engine match on a background thread and lets a viewer watch it.
 *
 * The engine posts events synchronously on its own thread. At "pace points" (a spell cast or
 * resolved, a land played, attackers/blockers declared, damage, a new turn or phase) this
 * class takes a snapshot, hands it to the listener, and then waits: for the speed delay,
 * while paused, or until "step" is pressed. Waiting on the engine thread is what slows the
 * AI down to watchable speed.
 */
public final class MatchRunner {

    /** Base delay per pace point at 1x speed, in milliseconds. */
    private static final int BASE_DELAY_MS = 700;

    private final Match match;
    private final String youName;
    private final String enemyName;
    private final PlayerLook youLook;
    private final PlayerLook enemyLook;
    private final Consumer<MatchSnapshot> listener;

    private final Object lock = new Object();
    private volatile boolean paused;
    private volatile double speed = 1.0;
    private volatile boolean revealEnemyHand;
    private volatile boolean noDelay;
    private int stepsAllowed;
    private boolean refreshRequested;

    private Game game;
    private SnapshotBuilder builder;
    private HistoryRecorder history;
    private Thread thread;

    /**
     * @param listener receives every new snapshot, on the engine thread; the GUI hops to
     *                 the Swing thread itself
     */
    public MatchRunner(Match match, String youName, String enemyName, PlayerLook youLook,
                       PlayerLook enemyLook, Consumer<MatchSnapshot> listener) {
        this.match = match;
        this.youName = youName;
        this.enemyName = enemyName;
        this.youLook = youLook;
        this.enemyLook = enemyLook;
        this.listener = listener;
    }

    /** Plain looks (subtitles only), e.g. for tests. */
    public MatchRunner(Match match, String youName, String enemyName, String youSubtitle,
                       String enemySubtitle, Consumer<MatchSnapshot> listener) {
        this(match, youName, enemyName, PlayerLook.plain(youSubtitle), PlayerLook.plain(enemySubtitle), listener);
    }

    public void start() {
        thread = new Thread(this::run, "planesnomad-match");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        game = match.createGame();
        builder = new SnapshotBuilder(game, player(youName), player(enemyName), youLook, enemyLook);
        history = builder.recordHistory();
        game.subscribeToEvents(this);
        publish();
        match.startGame(game);
        publish(); // final state, with the result
    }

    private Player player(String name) {
        for (Player p : game.getRegisteredPlayers()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        throw new IllegalArgumentException("No player named " + name);
    }

    /** Called by the engine's event bus, on the engine thread. */
    @Subscribe
    public void onEvent(GameEvent event) {
        if (history != null) {
            history.record(event);   // every event, also those that aren't pace points (e.g. deaths)
        }
        long delay = delayFor(event);
        if (delay < 0) {
            return;   // not something to show
        }
        publish();
        if (!(event instanceof GameEventGameFinished)) {
            pace(delay);
        }
    }

    // ------------------------------------------------------------------ pacing and stops

    /** Phase stops: where play pauses at a phase change. Key: "you:MAIN1", "enemy:END_OF_TURN". */
    private final java.util.Set<String> stops = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Enemy spells stay on screen at least this long, even when everything else runs fast. */
    private volatile long minEnemySpellMs = DEFAULT_MIN_ENEMY_SPELL_MS;

    public static final long DEFAULT_MIN_ENEMY_SPELL_MS = 1000;

    /** Default stops, as in Forge: your main phases and combat, the enemy's combat and end step. */
    public static java.util.Set<String> defaultStops() {
        return java.util.Set.of("you:UPKEEP", "you:MAIN1", "you:COMBAT_DECLARE_ATTACKERS", "you:MAIN2",
            "enemy:COMBAT_DECLARE_ATTACKERS", "enemy:COMBAT_DECLARE_BLOCKERS", "enemy:END_OF_TURN");
    }

    {
        stops.addAll(defaultStops());
    }

    public static String stopKey(boolean yourTurn, forge.game.phase.PhaseType phase) {
        return (yourTurn ? "you:" : "enemy:") + phase.name();
    }

    public void setStop(boolean yourTurn, forge.game.phase.PhaseType phase, boolean on) {
        if (on) {
            stops.add(stopKey(yourTurn, phase));
        } else {
            stops.remove(stopKey(yourTurn, phase));
        }
    }

    public boolean isStop(boolean yourTurn, forge.game.phase.PhaseType phase) {
        return stops.contains(stopKey(yourTurn, phase));
    }

    public void setMinEnemySpellMs(long ms) {
        minEnemySpellMs = Math.max(0, ms);
    }

    public long minEnemySpellMs() {
        return minEnemySpellMs;
    }

    /**
     * How long to show the moment after this event (ms), or -1 if it isn't worth a snapshot.
     * - Spells cast: the normal delay, but enemy spells at least {@link #minEnemySpellMs} (they
     *   must be seen, even when the player would auto-pass).
     * - A resolution: shown, no wait (simple resolutions don't linger on the stack).
     * - A phase change: a wait only at a stop; other phases go by quickly.
     * - New turn, attacks, blocks: a longer look. Lands, damage: the normal delay.
     */
    private long delayFor(GameEvent e) {
        long base = (long) (BASE_DELAY_MS / speed);
        if (e instanceof GameEventTurnBegan || e instanceof GameEventAttackersDeclared
            || e instanceof GameEventBlockersDeclared) {
            return 2 * base;
        }
        if (e instanceof GameEventSpellAbilityCast cast) {
            boolean spell = cast.si() != null && !cast.si().isAbility() && !cast.si().isTrigger();
            boolean enemy = cast.si() != null && cast.si().getActivatingPlayer() != null
                && !cast.si().getActivatingPlayer().getName().equals(youName);
            return spell && enemy ? Math.max(base, minEnemySpellMs) : base;
        }
        if (e instanceof GameEventSpellResolved) {
            return 0;
        }
        if (e instanceof GameEventTurnPhase tp) {
            boolean yourTurn = tp.playerTurn() != null && tp.playerTurn().getName().equals(youName);
            return tp.phase() != null && isStop(yourTurn, tp.phase()) ? base : 0;
        }
        if (e instanceof GameEventLandPlayed || e instanceof GameEventPlayerDamaged
            || e instanceof GameEventCardDamaged) {
            return base;
        }
        if (e instanceof GameEventGameFinished) {
            return 0;
        }
        return -1;
    }

    private void publish() {
        MatchSnapshot snapshot;
        try {
            snapshot = builder.build(revealEnemyHand);
        } catch (RuntimeException e) {
            // Never let a display problem stop the match.
            System.err.println("Snapshot failed: " + e);
            return;
        }
        listener.accept(snapshot);
    }

    private void pace(long delay) {
        if (noDelay && !paused) {
            return;
        }
        long until = System.currentTimeMillis() + delay;
        synchronized (lock) {
            try {
                while (true) {
                    if (refreshRequested) {
                        refreshRequested = false;
                        publish();   // safe: we are on the engine thread
                    }
                    if (paused) {
                        if (stepsAllowed > 0) {
                            stepsAllowed--;
                            return;
                        }
                        lock.wait();
                        continue;
                    }
                    long left = until - System.currentTimeMillis();
                    if (left <= 0) {
                        return;
                    }
                    lock.wait(left);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ------------------------------------------------------------------ controls (any thread)

    public void setPaused(boolean value) {
        synchronized (lock) {
            paused = value;
            stepsAllowed = 0;
            lock.notifyAll();
        }
    }

    public boolean isPaused() {
        return paused;
    }

    /** While paused: let the match advance to the next pace point. */
    public void step() {
        synchronized (lock) {
            if (paused) {
                stepsAllowed++;
                lock.notifyAll();
            }
        }
    }

    /** 0.25x to 8x. */
    public void setSpeed(double value) {
        synchronized (lock) {
            speed = Math.max(0.25, Math.min(8.0, value));
            lock.notifyAll();
        }
    }

    public void setRevealEnemyHand(boolean value) {
        revealEnemyHand = value;
        if (builder != null && isFinished()) {
            publish();   // the match is over (no engine thread to ask): the game no longer changes
        } else {
            requestRefresh();
        }
    }

    /** Ask for a fresh snapshot (e.g. after a display option changed while paused). */
    public void requestRefresh() {
        synchronized (lock) {
            refreshRequested = true;
            lock.notifyAll();
        }
    }

    /** Tests: skip the speed delay entirely (pause and step still work). */
    public void setNoDelay(boolean value) {
        noDelay = value;
    }

    /** Wait for the match thread to end. */
    public void join() throws InterruptedException {
        if (thread != null) {
            thread.join();
        }
    }

    public boolean isFinished() {
        return thread != null && !thread.isAlive();
    }
}

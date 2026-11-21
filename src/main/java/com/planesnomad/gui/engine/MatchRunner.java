package com.planesnomad.gui.engine;

import com.google.common.eventbus.Subscribe;
import com.planesnomad.gui.model.MatchSnapshot;
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
    private final String youSubtitle;
    private final String enemySubtitle;
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
    private Thread thread;

    /**
     * @param listener receives every new snapshot, on the engine thread; the GUI hops to
     *                 the Swing thread itself
     */
    public MatchRunner(Match match, String youName, String enemyName, String youSubtitle,
                       String enemySubtitle, Consumer<MatchSnapshot> listener) {
        this.match = match;
        this.youName = youName;
        this.enemyName = enemyName;
        this.youSubtitle = youSubtitle;
        this.enemySubtitle = enemySubtitle;
        this.listener = listener;
    }

    public void start() {
        thread = new Thread(this::run, "planesnomad-match");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        game = match.createGame();
        builder = new SnapshotBuilder(game, player(youName), player(enemyName), youSubtitle, enemySubtitle);
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
        int weight = paceWeight(event);
        if (weight == 0) {
            return;
        }
        publish();
        if (!(event instanceof GameEventGameFinished)) {
            pace(weight);
        }
    }

    /** 0 = not a pace point; 1 = normal; 2 = worth a longer look (new turn, combat). */
    private static int paceWeight(GameEvent e) {
        if (e instanceof GameEventTurnBegan || e instanceof GameEventAttackersDeclared
            || e instanceof GameEventBlockersDeclared) {
            return 2;
        }
        if (e instanceof GameEventSpellAbilityCast || e instanceof GameEventSpellResolved
            || e instanceof GameEventLandPlayed || e instanceof GameEventPlayerDamaged
            || e instanceof GameEventCardDamaged || e instanceof GameEventTurnPhase
            || e instanceof GameEventGameFinished) {
            return 1;
        }
        return 0;
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

    private void pace(int weight) {
        if (noDelay && !paused) {
            return;
        }
        long delay = (long) (BASE_DELAY_MS * weight / speed);
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
        requestRefresh();
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

package com.planesnomad.gui.engine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

/**
 * The hand-off between the engine (which asks the human player a question and waits) and the
 * window (which shows the question and sends the answer). The engine thread blocks in
 * {@link #ask}; the window calls {@link #answer} from the Swing thread.
 *
 * Questions carry only ids and text, never engine objects, so the window stays independent of
 * the engine (like the snapshots). Choices are keys: "card:123", "player:Blue AI", "stack:45",
 * or for priority "play:3" (an index into the listed options).
 */
public final class HumanBridge {

    /** A question for the human. */
    public sealed interface Request permits Priority, Targets, Attackers, Blockers, ManaPayment, ArrangeCards { String prompt(); }

    /** Something the human can do now: play a land, cast a spell, activate an ability. */
    public record Option(String key, int cardId, String cardName, String label, boolean relic) { }

    /** Priority: pick an option (answer: its key) or pass (answer: null). */
    public record Priority(String prompt, List<Option> options, boolean respondingToStack) implements Request { }

    /**
     * Choose targets (answer: List of keys; null = cancel the spell).
     * @param candidates legal targets, as keys
     * @param labels     what each key is, for the prompt
     */
    public record Targets(String prompt, List<String> candidates, Map<String, String> labels,
                          int min, int max) implements Request { }

    /**
     * Paying mana yourself (auto-tap off): tap lands (answer: an option's key) until the cost is
     * covered; "auto" = pay the rest automatically; null = cancel the spell.
     * @param pool mana already in your pool, e.g. "{R}{R}"
     */
    public record ManaPayment(String prompt, String cost, String pool, List<Option> sources) implements Request { }

    /**
     * Looking at cards from your library (scry, surveil, ...): each stays on top unless you send
     * it away (answer: List of the card ids sent away, e.g. to the bottom or the graveyard).
     * @param cards    the cards, as the screen draws them
     * @param awayTo   where a card is sent when clicked, e.g. "Bottom", "Graveyard"
     */
    public record ArrangeCards(String prompt, List<com.planesnomad.gui.model.CardSnapshot> cards,
                               String awayTo) implements Request { }

    /** Declare attackers (answer: List of card ids; empty = no attack). */
    public record Attackers(String prompt, List<Integer> candidates, String defender) implements Request { }

    /**
     * Declare blockers (answer: Map blocker id -> attacker id; empty = no blocks).
     * @param legal for each possible blocker, the attackers it may block
     */
    public record Blockers(String prompt, List<Integer> attackers, Map<Integer, List<Integer>> legal,
                           String error) implements Request { }

    private static final Object PASS = new Object();

    /** Auto-pass: none, until something happens (an enemy spell or ability, or your next turn), or until the turn ends. */
    public enum AutoPass { NONE, UNTIL_EVENT, END_OF_TURN }

    private volatile boolean autoTap = true;
    private volatile AutoPass autoPass = AutoPass.NONE;

    /** Setting: lands are tapped automatically to pay costs (default), or you tap them yourself. */
    public boolean autoTap() {
        return autoTap;
    }

    public void setAutoTap(boolean on) {
        autoTap = on;
    }

    public AutoPass autoPass() {
        return autoPass;
    }

    /** Window: start (or stop, with NONE) passing priority automatically; also answers a waiting priority question. */
    public void setAutoPass(AutoPass mode) {
        autoPass = mode == null ? AutoPass.NONE : mode;
        if (mode != AutoPass.NONE && current instanceof Priority) {
            answer(null);
        }
    }

    private final BlockingQueue<Object> answers = new LinkedBlockingQueue<>();
    private volatile Request current;
    private volatile Consumer<Request> listener = r -> { };
    private volatile Runnable beforeAsk = () -> { };

    /** The window: called (on the engine thread) whenever a question starts or ends (null). */
    public void setListener(Consumer<Request> l) {
        listener = l == null ? r -> { } : l;
    }

    /** The runner: called before each question, e.g. to publish a fresh snapshot. */
    public void setBeforeAsk(Runnable r) {
        beforeAsk = r == null ? () -> { } : r;
    }

    /** The question waiting for an answer, or null. */
    public Request current() {
        return current;
    }

    private volatile boolean closed;

    /** The match is being abandoned: every question (now and later) is answered with null at once. */
    public void close() {
        closed = true;
        answers.offer(PASS);
    }

    /** Engine thread: asks and waits for the answer (null = pass / cancel / nothing). */
    public Object ask(Request request) {
        if (closed) {
            return null;
        }
        answers.clear();
        beforeAsk.run();
        current = request;
        listener.accept(request);
        try {
            Object a = answers.take();
            return a == PASS ? null : a;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            current = null;
            listener.accept(null);
        }
    }

    /** Window: answers the current question (null = pass / cancel). */
    public void answer(Object value) {
        if (current != null) {
            answers.offer(value == null ? PASS : value);
        }
    }
}

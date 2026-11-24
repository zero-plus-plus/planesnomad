package com.planesnomad.encounter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.planesnomad.gui.model.PlayerLook;
import com.planesnomad.match.Guarantee;
import com.planesnomad.match.MatchSetup;
import com.planesnomad.match.StartRules;
import com.planesnomad.relic.RelicCatalog;
import forge.card.CardDb;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one encounter file and checks it, reporting every problem with the field it's in (a card
 * name that doesn't exist, a preset hand card that isn't in the deck, an unknown bark, ...).
 * The format is documented in {@code data/encounters/README.md}. Files are JSON; {@code //}
 * comments are allowed.
 */
public final class EncounterFile {

    /** The encounter (null if the file can't be used) and the problems found. */
    public record Result(Encounter encounter, List<String> problems) { }

    private static final Set<String> KEYS = Set.of("version", "name", "subtitle", "notes", "images", "life",
        "firstPlayer", "deck", "effects", "guarantees", "start", "barks", "rewards", "drops");
    private static final Set<String> TIERS = Set.of("easy", "hard", "elite", "boss");
    private static final Pattern DECK_LINE = Pattern.compile("^\\s*(\\d+)\\s+([^|]+?)\\s*(?:\\|\\s*([A-Za-z0-9]+)\\s*)?(?:\\|.*)?$");
    private static final Pattern GOOD_NAME = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final String id;
    private final CardDb db;
    private final List<String> problems = new ArrayList<>();

    private EncounterFile(String id, CardDb db) {
        this.id = id;
        this.db = db;
    }

    /**
     * @param root the encounters folder (data/encounters)
     * @param file a .json file inside it, at {@code <plane>/<tier>/<name>.json}
     */
    public static Result read(Path root, Path file, CardDb db) {
        String rel = root.relativize(file).toString().replace('\\', '/');
        String id = rel.replaceFirst("(?i)\\.json$", "");
        EncounterFile f = new EncounterFile(id, db);
        Encounter e = null;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonReader jr = new JsonReader(r);
            jr.setLenient(true);   // allows // comments and other small liberties in hand-written files
            JsonElement root0 = JsonParser.parseReader(jr);
            if (!root0.isJsonObject()) {
                f.problem("", "the file must be one JSON object { ... }");
            } else {
                e = f.parse(root0.getAsJsonObject());
            }
        } catch (IOException | JsonParseException | IllegalStateException ex) {
            f.problem("", "can't read the file: " + ex.getMessage());
        }
        return new Result(e, List.copyOf(f.problems));
    }

    private void problem(String field, String text) {
        problems.add(id + (field.isEmpty() ? "" : " [" + field + "]") + ": " + text);
    }

    // ------------------------------------------------------------------ parsing

    private Encounter parse(JsonObject o) {
        for (String k : o.keySet()) {
            if (!KEYS.contains(k)) {
                problem(k, "unknown setting (a typo?)");
            }
        }
        // Folder: <plane>/<tier>/<name>
        String[] parts = id.split("/");
        String plane = parts.length == 3 ? parts[0] : "";
        String tierName = parts.length == 3 ? parts[1] : "";
        if (parts.length != 3 || !TIERS.contains(tierName)) {
            problem("", "must be at <plane>/<easy|hard|elite|boss>/<name>.json");
        }
        for (String p : parts) {
            if (!GOOD_NAME.matcher(p).matches()) {
                problem("", "folder and file names: lowercase, words joined with '-', no spaces (\"" + p + "\")");
            }
        }
        int version = integer(o, "version", 1);
        if (version != 1) {
            problem("version", "this game reads version 1 files (found " + version + ")");
        }
        String name = string(o, "name", null);
        if (name == null || name.isBlank()) {
            problem("name", "missing");
            return null;
        }
        String subtitle = string(o, "subtitle", "");
        int life = integer(o, "life", 20);
        if (life < 1) {
            problem("life", "must be 1 or more");
        }

        // Images: "card:<name>" (that card's art) or "file:<path>" (a picture in the game folder).
        String icon = null;
        String background = null;
        if (o.has("images")) {
            JsonObject im = object(o, "images");
            if (im != null) {
                for (String k : im.keySet()) {
                    if (!k.equals("icon") && !k.equals("background")) {
                        problem("images." + k, "unknown setting (icon, background)");
                    }
                }
                icon = image(im, "icon");
                background = image(im, "background");
            }
        }

        // Deck
        List<String> deckLines = strings(o, "deck");
        Map<String, Integer> deckCounts = new LinkedHashMap<>();
        for (String line : deckLines) {
            Matcher m = DECK_LINE.matcher(line);
            if (!m.matches()) {
                problem("deck", "can't read \"" + line + "\" (write: 4 Lord of Atlantis, or 4 Lord of Atlantis|4ED)");
                continue;
            }
            int n = Integer.parseInt(m.group(1));
            String card = m.group(2);
            String set = m.group(3);
            if (!cardExists(card)) {
                problem("deck", "no card named \"" + card + "\"");
                continue;
            }
            if (set != null) {
                var edition = forge.StaticData.instance().getEditions().get(set);
                var printing = db.getCard(card, set);
                if (edition == null) {
                    problem("deck", "\"" + card + "|" + set + "\": unknown set code " + set + " (\"" + card + "\" has no printing in set " + set + ")");
                } else if (printing == null || !edition.getCode().equalsIgnoreCase(printing.getEdition())) {
                    problem("deck", "\"" + card + "\" has no printing in set " + set);
                }
            }
            deckCounts.merge(card.toLowerCase(Locale.ROOT), n, Integer::sum);
        }
        if (deckCounts.isEmpty()) {
            problem("deck", "missing or empty");
            return null;
        }

        // Effects (enemy command-zone cards: our own cards)
        List<String> effects = strings(o, "effects");
        for (String fx : effects) {
            if (!cardExists(fx)) {
                problem("effects", "no card named \"" + fx + "\" (enemy effects are our own cards)");
            }
        }

        // Guaranteed draws
        List<Guarantee> guarantees = new ArrayList<>();
        Map<String, String> notes = new HashMap<>();
        if (o.has("guarantees")) {
            JsonArray arr = array(o, "guarantees");
            for (int i = 0; arr != null && i < arr.size(); i++) {
                String f = "guarantees[" + i + "]";
                if (!arr.get(i).isJsonObject()) {
                    problem(f, "write {\"card\": \"...\", \"drawOnTurn\": 2}");
                    continue;
                }
                JsonObject g = arr.get(i).getAsJsonObject();
                String card = string(g, "card", null);
                int turn = integer(g, "drawOnTurn", 0);
                if (card == null || !deckCounts.containsKey(card.toLowerCase(Locale.ROOT))) {
                    problem(f, "card must be in the deck (\"" + card + "\")");
                    continue;
                }
                if (turn < 1) {
                    problem(f, "drawOnTurn must be 1 or more (the enemy's own turns)");
                    continue;
                }
                guarantees.add(new Guarantee(card, turn));
                notes.put(card, "Turn " + turn);
            }
        }

        // Starting positions
        MatchSetup.Start enemyStart = MatchSetup.Start.NORMAL;
        MatchSetup.Start playerStart = MatchSetup.Start.NORMAL;
        if (o.has("start")) {
            JsonObject st = object(o, "start");
            if (st != null) {
                for (String k : st.keySet()) {
                    if (!k.equals("enemy") && !k.equals("player")) {
                        problem("start." + k, "unknown side (enemy, player)");
                    }
                }
                enemyStart = start(st, "enemy", deckCounts, guarantees);
                playerStart = start(st, "player", null, List.of());
            }
        }
        if (enemyStart == MatchSetup.Start.NORMAL && !guarantees.isEmpty()) {
            enemyStart = new MatchSetup.Start(List.of(), List.of(), guarantees);
        }

        // First player
        String first = string(o, "firstPlayer", "random");
        MatchSetup.FirstPlayer firstPlayer = switch (first) {
            case "player" -> MatchSetup.FirstPlayer.YOU;
            case "enemy" -> MatchSetup.FirstPlayer.ENEMY;
            case "random" -> MatchSetup.FirstPlayer.RANDOM;
            default -> {
                problem("firstPlayer", "player, enemy or random (found \"" + first + "\")");
                yield MatchSetup.FirstPlayer.RANDOM;
            }
        };

        // Barks
        List<Bark> barks = new ArrayList<>();
        if (o.has("barks")) {
            JsonArray arr = array(o, "barks");
            for (int i = 0; arr != null && i < arr.size(); i++) {
                String f = "barks[" + i + "]";
                if (!arr.get(i).isJsonObject()) {
                    problem(f, "write {\"when\": \"lifeBelowHalf\", \"text\": \"...\"}");
                    continue;
                }
                JsonObject b = arr.get(i).getAsJsonObject();
                for (String k : b.keySet()) {
                    if (!Set.of("when", "text", "once").contains(k)) {
                        problem(f + "." + k, "unknown setting (when, text, once)");
                    }
                }
                String whenKey = string(b, "when", "");
                Bark.When when = Bark.When.of(whenKey);
                String text = string(b, "text", "");
                if (when == null) {
                    problem(f + ".when", "unknown \"" + whenKey + "\" (one of: " + Bark.When.keys() + ")");
                } else if (text.isBlank()) {
                    problem(f + ".text", "missing");
                } else {
                    barks.add(new Bark(when, text, bool(b, "once", true)));
                }
            }
        }

        // Rewards
        Rewards rewards = new Rewards(0, 0, List.of(), List.of());
        if (o.has("rewards")) {
            JsonObject rw = object(o, "rewards");
            if (rw != null) {
                for (String k : rw.keySet()) {
                    if (!Set.of("gold", "cardChoices", "cardPool", "relics").contains(k)) {
                        problem("rewards." + k, "unknown setting (gold, cardChoices, cardPool, relics)");
                    }
                }
                List<String> pool = strings(rw, "cardPool");
                pool.stream().filter(c -> !cardExists(c)).forEach(c -> problem("rewards.cardPool", "no card named \"" + c + "\""));
                List<String> relics = strings(rw, "relics");
                relics.stream().filter(r -> RelicCatalog.find(r).isEmpty())
                    .forEach(r -> problem("rewards.relics", "not a relic: \"" + r + "\""));
                rewards = new Rewards(Math.max(0, integer(rw, "gold", 0)), Math.max(0, integer(rw, "cardChoices", 0)),
                    pool.stream().filter(this::cardExists).toList(),
                    relics.stream().filter(r -> RelicCatalog.find(r).isPresent()).toList());
            }
        }
        List<String> drops = strings(o, "drops");
        drops.stream().filter(c -> !cardExists(c)).forEach(c -> problem("drops", "no card named \"" + c + "\""));

        PlayerLook look = new PlayerLook(subtitle, icon, background,
            guarantees.stream().map(Guarantee::card).toList(), notes);
        MatchSetup.Side enemy = new MatchSetup.Side(name, MatchSetup.Controller.AI, deckName(name), life,
            effects, look, enemyStart);
        Encounter.Tier tier = TIERS.contains(tierName) ? Encounter.Tier.valueOf(tierName.toUpperCase(Locale.ROOT)) : Encounter.Tier.EASY;
        return new Encounter(id, plane, tier, enemy, deckLines, firstPlayer, playerStart, barks, rewards, drops);
    }

    /** The deck name an encounter's deck is listed under in the deck library. */
    public static String deckName(String enemyName) {
        return "Enemy: " + enemyName;
    }

    /**
     * One side's start: preset hand (taken from the deck: checked against it for the enemy) and
     * bonus permanents (cards or token:script).
     */
    private MatchSetup.Start start(JsonObject st, String side, Map<String, Integer> deck, List<Guarantee> guarantees) {
        if (!st.has(side)) {
            return guarantees.isEmpty() ? MatchSetup.Start.NORMAL : new MatchSetup.Start(List.of(), List.of(), guarantees);
        }
        String f = "start." + side;
        JsonObject s = object(st, side);
        if (s == null) {
            return MatchSetup.Start.NORMAL;
        }
        for (String k : s.keySet()) {
            if (!k.equals("hand") && !k.equals("battlefield")) {
                problem(f + "." + k, "unknown setting (hand, battlefield)");
            }
        }
        List<String> hand = strings(s, "hand");
        if (hand.size() > 7) {
            problem(f + ".hand", "more than 7 cards");
        }
        Map<String, Integer> left = deck == null ? null : new HashMap<>(deck);
        for (String c : hand) {
            if (!cardExists(c)) {
                problem(f + ".hand", "no card named \"" + c + "\"");
            } else if (left != null) {
                String k = c.toLowerCase(Locale.ROOT);
                int n = left.getOrDefault(k, 0);
                if (n <= 0) {
                    problem(f + ".hand", "\"" + c + "\" isn't in the deck (or not that many copies): the hand is taken from the deck");
                }
                left.put(k, n - 1);
            }
        }
        List<String> board = strings(s, "battlefield");
        for (String b : board) {
            if (b.startsWith("token:")) {
                if (StartRules.token(b.substring(6)) == null) {
                    problem(f + ".battlefield", "no token script \"" + b.substring(6) + "\" (Forge's res/tokenscripts names, e.g. u_1_1_merfolk)");
                }
            } else if (!cardExists(b)) {
                problem(f + ".battlefield", "no card named \"" + b + "\" (tokens: token:<script>)");
            }
        }
        return new MatchSetup.Start(hand, board, guarantees);
    }

    private String image(JsonObject im, String key) {
        String v = string(im, key, null);
        if (v == null || v.isBlank()) {
            return null;
        }
        if (v.startsWith("card:")) {
            String card = v.substring(5).trim();
            if (!cardExists(card)) {
                problem("images." + key, "no card named \"" + card + "\"");
                return null;
            }
            return card;
        }
        if (v.startsWith("file:")) {
            String path = v.substring(5).trim();
            if (!Files.isRegularFile(Path.of(path))) {
                problem("images." + key, "picture not found: " + path + " (paths start at the game folder, e.g. data/...)");
                return null;
            }
            return "file:" + path;
        }
        problem("images." + key, "write card:<card name> or file:<path>");
        return null;
    }

    private boolean cardExists(String name) {
        return name != null && db.getCard(name) != null;
    }

    // ------------------------------------------------------------------ JSON helpers

    private String string(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        if (!e.isJsonPrimitive()) {
            problem(key, "should be text");
            return def;
        }
        return e.getAsString();
    }

    private int integer(JsonObject o, String key, int def) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        try {
            return e.getAsInt();
        } catch (RuntimeException ex) {
            problem(key, "should be a whole number");
            return def;
        }
    }

    private boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return def;
        }
        try {
            return e.getAsBoolean();
        } catch (RuntimeException ex) {
            problem(key, "should be true or false");
            return def;
        }
    }

    private JsonObject object(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonObject()) {
            problem(key, "should be { ... }");
            return null;
        }
        return e.getAsJsonObject();
    }

    private JsonArray array(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonArray()) {
            problem(key, "should be a list [ ... ]");
            return null;
        }
        return e.getAsJsonArray();
    }

    private List<String> strings(JsonObject o, String key) {
        if (!o.has(key)) {
            return List.of();
        }
        JsonArray arr = array(o, key);
        List<String> out = new ArrayList<>();
        for (int i = 0; arr != null && i < arr.size(); i++) {
            JsonElement e = arr.get(i);
            if (e.isJsonPrimitive()) {
                out.add(e.getAsString().trim());
            } else {
                problem(key + "[" + i + "]", "should be text");
            }
        }
        return out;
    }
}

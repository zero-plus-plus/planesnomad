package com.planesnomad.gui.images;

import com.planesnomad.gui.model.CardSnapshot;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;

/**
 * Downloads card pictures from Scryfall into a local cache, for cards the player has no picture
 * of, and art crops (Scryfall's own, exact for every card layout) for thumbnails, portraits and
 * playmats. Pictures are never bundled with the game: they are fetched on the player's machine.
 *
 * Polite use of Scryfall: one request at a time, at least {@link #MIN_INTERVAL_MS} apart (Scryfall
 * asks for no more than about 10 per second), a plain app User-Agent (nothing personal), and every
 * picture is cached so it is downloaded once. Pictures Scryfall doesn't have are remembered for the
 * session and not asked for again.
 *
 * Links come from Forge's own builder (ImageUtil.getScryfallDownloadUrl), supplied by
 * {@link ImageSetup} so this class stays independent of the engine.
 */
public final class ScryfallDownloader {

    /** Which picture: the full card, or only its art. */
    public enum Version {
        NORMAL("normal"),
        ART_CROP("art_crop");

        final String folder;

        Version(String folder) {
            this.folder = folder;
        }
    }

    public static final String BASE = "https://api.scryfall.com/cards/";
    public static final long MIN_INTERVAL_MS = 110;
    private static final String USER_AGENT = "Planesnomad/0.1 (fan-made game; card pictures cached locally)";

    private final Path cacheRoot;
    /** (card, version) -> link path after BASE, e.g. "2ED/175/en?format=image&version=normal"; null = can't download. */
    private final BiFunction<CardSnapshot, Version, String> linkBuilder;
    private final HttpClient http;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "scryfall");
        t.setDaemon(true);
        return t;
    });
    private final Set<String> missing = ConcurrentHashMap.newKeySet();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private volatile boolean enabled = true;
    private long lastRequestAt;
    private int downloads;

    public ScryfallDownloader(Path cacheRoot, BiFunction<CardSnapshot, Version, String> linkBuilder) {
        this.cacheRoot = cacheRoot;
        this.linkBuilder = linkBuilder;
        this.http = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())          // follow the system's proxy settings, if any
            .followRedirects(HttpClient.Redirect.NORMAL) // api.scryfall.com redirects to cards.scryfall.io
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /** The default cache folder: the player's local app data, never the game folder or the repository. */
    public static Path defaultCache() {
        String configured = System.getProperty("planesnomad.cache");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim(), "scryfall");
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win") && System.getenv("LOCALAPPDATA") != null) {
            return Path.of(System.getenv("LOCALAPPDATA"), "Planesnomad", "cache", "scryfall");
        }
        if (os.contains("mac")) {
            return Path.of(home, "Library", "Caches", "Planesnomad", "scryfall");
        }
        return Path.of(home, ".cache", "planesnomad", "scryfall");
    }

    public Path cacheRoot() {
        return cacheRoot;
    }

    /** Setting: allow downloads (off = only pictures already on this computer are used). */
    public void setEnabled(boolean value) {
        enabled = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Number of pictures downloaded this session (for tests and diagnostics). */
    public synchronized int downloads() {
        return downloads;
    }

    /** The cached picture for this card and version, or null if it hasn't been downloaded. */
    public Path cached(CardSnapshot card, Version v) {
        Path file = cacheFile(card, v);
        return file != null && Files.isRegularFile(file) ? file : null;
    }

    /** Queues a download in the background; {@code onDone} runs after a successful download. */
    public void request(CardSnapshot card, Version v, Runnable onDone) {
        String link = link(card, v);
        if (!enabled || link == null || missing.contains(link) || cached(card, v) != null || !pending.add(link)) {
            return;
        }
        worker.submit(() -> {
            try {
                if (download(card, v, link) != null && onDone != null) {
                    onDone.run();
                }
            } finally {
                pending.remove(link);
            }
        });
    }

    /** Downloads now, on the calling thread (tests, screenshots). Returns the cached file or null. */
    public Path fetchNow(CardSnapshot card, Version v) {
        Path have = cached(card, v);
        if (have != null) {
            return have;
        }
        String link = link(card, v);
        if (!enabled || link == null || missing.contains(link)) {
            return null;
        }
        return download(card, v, link);
    }

    private String link(CardSnapshot card, Version v) {
        if (card == null || card.isHidden() || card.token()) {
            return null;
        }
        try {
            return linkBuilder.apply(card, v);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** cache/<version>/<SET>/<collector number>[_<face>].jpg, from the link's set and number. */
    Path cacheFile(CardSnapshot card, Version v) {
        String link = link(card, v);
        if (link == null) {
            return null;
        }
        String path = link.contains("?") ? link.substring(0, link.indexOf('?')) : link;
        String[] parts = path.split("/");
        if (parts.length < 2) {
            return null;
        }
        String face = link.contains("face=back") ? "_back" : "";
        String set = safe(parts[0]).toUpperCase(Locale.ROOT);
        String number = safe(parts[1]);
        return cacheRoot.resolve(v.folder).resolve(set).resolve(number + face + ".jpg");
    }

    private static String safe(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private Path download(CardSnapshot card, Version v, String link) {
        Path target = cacheFile(card, v);
        if (target == null) {
            return null;
        }
        try {
            waitForTurn();
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + link))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/*,*/*;q=0.8")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
            Path tmp = Files.createTempFile("scryfall", ".part");
            HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(tmp));
            String type = response.headers().firstValue("Content-Type").orElse("");
            if (response.statusCode() != 200 || !type.startsWith("image/")) {
                Files.deleteIfExists(tmp);
                if (response.statusCode() == 404 || response.statusCode() == 422) {
                    missing.add(link);   // Scryfall doesn't have it; don't ask again this session
                } else {
                    System.err.println("Scryfall " + response.statusCode() + " for " + link);
                }
                return null;
            }
            Files.createDirectories(target.getParent());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            synchronized (this) {
                downloads++;
            }
            return target;
        } catch (IOException e) {
            System.err.println("Scryfall download failed for " + link + ": " + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Keeps requests at least MIN_INTERVAL_MS apart, across all threads. */
    private synchronized void waitForTurn() throws InterruptedException {
        long wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
        lastRequestAt = System.currentTimeMillis();
    }
}

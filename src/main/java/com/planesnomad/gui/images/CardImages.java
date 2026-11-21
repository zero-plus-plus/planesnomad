package com.planesnomad.gui.images;

import com.planesnomad.gui.model.CardSnapshot;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Finds, loads and scales card pictures from local folders. Images are never bundled with the
 * game: these folders are the player's own (e.g. Forge's picture cache, or our download cache).
 *
 * File lookup follows Forge's picture cache naming, using the card's Forge image key
 * (e.g. "U/Shivan Dragon.full", "U/Mountain2.full"). For each folder, in order:
 *   1. {folder}/{key folder}/{name}.fullborder.jpg   (also .full.jpg, .jpg, .png)
 *   2. {folder}/{set code}/{name}...                 (set code instead of Forge's short code)
 *   3. {folder}/{name}...                            (flat folder, e.g. a handful of test images)
 *
 * If the card's own printing has no picture, any other printing of the same card is used
 * (newest set first, regular printings before alternate arts); the art crop then follows the
 * printing actually shown.
 *
 * Loading runs on a background thread; until a picture is ready the caller gets null and
 * draws the text frame. Listeners are told when new pictures arrive, so the screen repaints.
 */
public final class CardImages {

    private static final String[] SUFFIXES = {".fullborder.jpg", ".full.jpg", ".jpg", ".png"};
    private static final int MAX_ORIGINALS = 400;
    private static final int MAX_SCALED = 900;

    private final List<Path> folders;
    private final ArtCrops crops;
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "card-images");
        t.setDaemon(true);
        return t;
    });

    /** Loaded originals by file path; Optional.empty() = looked for and not found. */
    private final Map<String, Optional<Loaded>> originals = lru(MAX_ORIGINALS);
    private final Map<String, Image> scaled = lru(MAX_SCALED);
    private final Set<String> pending = Collections.synchronizedSet(new HashSet<>());
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /** A loaded picture and the art crop box for the printing it shows (if known). */
    private record Loaded(BufferedImage image, ArtCrops.Box artBox) { }

    /**
     * One printing of a card, enough to find its picture and choose its crop box.
     * @param imageKey Forge image key, e.g. "U/Shivan Dragon.full"
     */
    public record Printing(String setCode, String imageKey, int artIndex) { }

    /** Other printings of a card by name, in preference order; empty if none are known. */
    private Function<String, List<Printing>> otherPrintings = name -> List.of();

    public CardImages(List<Path> folders, ArtCrops crops) {
        this.folders = List.copyOf(folders);
        this.crops = crops;
    }

    public List<Path> folders() {
        return folders;
    }

    /** Where to find other printings of a card (by name) when its own printing has no picture. */
    public void setOtherPrintings(Function<String, List<Printing>> source) {
        otherPrintings = source == null ? name -> List.of() : source;
    }

    public void addListener(Runnable onNewImages) {
        listeners.add(onNewImages);
    }

    /** The full card picture scaled to w x h, or null if none (yet). */
    public Image full(CardSnapshot card, int w, int h) {
        Loaded loaded = get(card);
        if (loaded == null || w <= 0 || h <= 0) {
            return null;
        }
        return scaledCopy(lookupKey(card) + "|full|" + w + "x" + h, loaded.image(), w, h);
    }

    /** The card's art (crop box for its set) scaled to w x h, or null if no picture or no crop box. */
    public Image art(CardSnapshot card, int w, int h) {
        Loaded loaded = get(card);
        if (loaded == null || loaded.artBox() == null || w <= 0 || h <= 0) {
            return null;
        }
        BufferedImage src = loaded.image();
        ArtCrops.Box b = loaded.artBox();
        int x1 = (int) Math.round(b.left() * src.getWidth());
        int y1 = (int) Math.round(b.top() * src.getHeight());
        int x2 = (int) Math.round(b.right() * src.getWidth());
        int y2 = (int) Math.round(b.bottom() * src.getHeight());
        // Fill w x h without distortion: cut the centre of the art box to the target's proportions.
        double target = w / (double) h;
        int bw = x2 - x1;
        int bh = y2 - y1;
        if (bw / (double) bh > target) {
            int nw = (int) Math.round(bh * target);
            x1 += (bw - nw) / 2;
            bw = nw;
        } else {
            int nh = (int) Math.round(bw / target);
            y1 += (bh - nh) / 2;
            bh = nh;
        }
        BufferedImage crop = src.getSubimage(x1, y1, Math.max(1, bw), Math.max(1, bh));
        return scaledCopy(lookupKey(card) + "|art|" + w + "x" + h, crop, w, h);
    }

    /** True if a picture exists for this card (loads it now, on the calling thread). */
    public boolean hasImage(CardSnapshot card) {
        return loadNow(card) != null;
    }

    /** Loads pictures for these cards right away (used by tests and screenshots). */
    public void preloadNow(Collection<CardSnapshot> cards) {
        for (CardSnapshot c : cards) {
            loadNow(c);
        }
    }

    /** Queues background loading for these cards (e.g. both decks at match start). */
    public void prefetch(Collection<CardSnapshot> cards) {
        for (CardSnapshot c : cards) {
            get(c);
        }
    }

    // ------------------------------------------------------------------ loading

    private Loaded get(CardSnapshot card) {
        if (card == null || card.isHidden() || card.faceDown() || card.name().isEmpty()) {
            return null;
        }
        String key = lookupKey(card);
        synchronized (originals) {
            Optional<Loaded> known = originals.get(key);
            if (known != null) {
                return known.orElse(null);
            }
        }
        if (pending.add(key)) {
            loader.submit(() -> {
                try {
                    loadNow(card);
                } finally {
                    pending.remove(key);
                }
                listeners.forEach(Runnable::run);
            });
        }
        return null;
    }

    private Loaded loadNow(CardSnapshot card) {
        if (card == null || card.isHidden() || card.name().isEmpty()) {
            return null;
        }
        String key = lookupKey(card);
        synchronized (originals) {
            Optional<Loaded> known = originals.get(key);
            if (known != null) {
                return known.orElse(null);
            }
        }
        Loaded loaded = loadPrinting(card, new Printing(card.setCode(), card.imageKey(), card.artIndex()));
        if (loaded == null) {
            for (Printing other : otherPrintings.apply(card.name())) {
                if (other.imageKey() != null && other.imageKey().equals(card.imageKey())) {
                    continue;
                }
                loaded = loadPrinting(card, other);
                if (loaded != null) {
                    break;
                }
            }
        }
        synchronized (originals) {
            originals.put(key, Optional.ofNullable(loaded));
        }
        return loaded;
    }

    /** Loads the picture of one printing of the card, with that printing's crop box. */
    private Loaded loadPrinting(CardSnapshot card, Printing printing) {
        for (Path file : candidates(card, printing)) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                BufferedImage img = ImageIO.read(file.toFile());
                if (img != null) {
                    // The crop box follows the printing shown. In a flat folder the file's printing
                    // isn't certain, but the lookup was for this printing.
                    boolean basicLand = card.typeLine() != null && card.typeLine().contains("Basic");
                    ArtCrops.Box box = crops.find(printing.setCode(), keyFolder(printing.imageKey()),
                        card.layout(), printing.artIndex(), basicLand).orElse(null);
                    return new Loaded(img, box);
                }
            } catch (IOException e) {
                System.err.println("Could not read card image " + file + ": " + e.getMessage());
            }
        }
        return null;
    }

    /** Where to look for the card's own printing's picture, most specific first. */
    List<Path> candidates(CardSnapshot card) {
        return candidates(card, new Printing(card.setCode(), card.imageKey(), card.artIndex()));
    }

    /** Where to look for one printing's picture, most specific first. */
    List<Path> candidates(CardSnapshot card, Printing printing) {
        String keyFolder = keyFolder(printing.imageKey());
        String keyName = keyName(printing.imageKey(), card.name());
        List<String> names = new ArrayList<>();
        names.add(keyName);
        String plain = safe(card.name());
        if (!plain.equals(keyName)) {
            names.add(plain);
        }
        List<Path> out = new ArrayList<>();
        for (Path folder : folders) {
            List<Path> dirs = new ArrayList<>();
            if (!keyFolder.isEmpty()) {
                dirs.add(folder.resolve(keyFolder));
            }
            String set = printing.setCode();
            if (set != null && !set.isEmpty() && !set.equals(keyFolder)) {
                dirs.add(folder.resolve(set));
            }
            dirs.add(folder);
            for (Path dir : dirs) {
                for (String name : names) {
                    for (String suffix : SUFFIXES) {
                        out.add(dir.resolve(name + suffix));
                    }
                }
            }
        }
        return out;
    }

    private static String lookupKey(CardSnapshot card) {
        return card.imageKey() == null || card.imageKey().isEmpty()
            ? card.setCode() + "/" + card.name() : card.imageKey();
    }

    /** "U/Shivan Dragon.full" -> "U". */
    private static String keyFolder(String key) {
        if (key == null) {
            return "";
        }
        int slash = key.indexOf('/');
        return slash > 0 ? key.substring(0, slash) : "";
    }

    /** "U/Mountain2.full" -> "Mountain2"; the card name if there is no key. */
    private static String keyName(String key, String cardName) {
        if (key == null || key.isEmpty()) {
            return safe(cardName);
        }
        String name = key.substring(key.indexOf('/') + 1);
        return name.endsWith(".full") ? name.substring(0, name.length() - 5) : name;
    }

    /** Characters Windows does not allow in file names are dropped, as Forge does. */
    private static String safe(String name) {
        return name.replace("//", "").replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    // ------------------------------------------------------------------ scaling

    private Image scaledCopy(String cacheKey, BufferedImage src, int w, int h) {
        synchronized (scaled) {
            Image hit = scaled.get(cacheKey);
            if (hit != null) {
                return hit;
            }
        }
        Image result = highQualityScale(src, w, h);
        synchronized (scaled) {
            scaled.put(cacheKey, result);
        }
        return result;
    }

    /** Halves the image step by step before the final resize, which looks much better than one big jump. */
    static BufferedImage highQualityScale(BufferedImage src, int w, int h) {
        BufferedImage current = src;
        int cw = src.getWidth();
        int ch = src.getHeight();
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            current = resize(current, cw, ch);
        }
        return resize(current, w, h);
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static <K, V> Map<K, V> lru(int max) {
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > max;
            }
        };
    }
}

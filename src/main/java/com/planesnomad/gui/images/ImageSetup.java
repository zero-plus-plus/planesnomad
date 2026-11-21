package com.planesnomad.gui.images;

import com.planesnomad.gui.ui.CardPainter;
import com.planesnomad.gui.ui.ManaSymbols;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the card picture folders and connects them to the card painter.
 *
 * Folders, in order:
 * 1. {@code -Dplanesnomad.cardImages=<folder>[;<folder>...]} if given (list separator is ';' on
 *    Windows, ':' elsewhere), e.g. a test folder or an existing picture collection.
 * 2. Forge's own picture cache, if it exists on this computer.
 *
 * Mana/card symbols ({R}, {T}, ...): {@code -Dplanesnomad.symbols=<folder>}, otherwise a
 * {@code symbols} folder next to a card picture folder (Forge's layout: pics/cards, pics/symbols).
 * Symbols and card pictures are copyrighted: they are only read from the player's own folders.
 */
public final class ImageSetup {

    public static final String PROPERTY = "planesnomad.cardImages";
    public static final String SYMBOLS_PROPERTY = "planesnomad.symbols";

    private ImageSetup() { }

    public static CardImages create() {
        List<Path> folders = new ArrayList<>();
        String configured = System.getProperty(PROPERTY);
        if (configured != null && !configured.isBlank()) {
            for (String part : configured.split(File.pathSeparator)) {
                if (!part.isBlank()) {
                    folders.add(Path.of(part.trim()));
                }
            }
        }
        forgeCache().ifPresent(folders::add);
        ManaSymbols.setFolder(symbolFolder(folders));
        CardImages images = new CardImages(folders, ArtCrops.load(Path.of("data", "art-crops.txt")));
        CardPainter.setImageSource(new CardPainter.ImageSource() {
            @Override
            public java.awt.Image full(com.planesnomad.gui.model.CardSnapshot card, int w, int h) {
                return images.full(card, w, h);
            }

            @Override
            public java.awt.Image art(com.planesnomad.gui.model.CardSnapshot card, int w, int h) {
                return images.art(card, w, h);
            }
        });
        return images;
    }

    /** The symbol folder to use, or null if none is found. */
    static Path symbolFolder(List<Path> cardFolders) {
        String configured = System.getProperty(SYMBOLS_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }
        for (Path cards : cardFolders) {
            Path parent = cards.toAbsolutePath().normalize().getParent();
            if (parent != null && Files.isDirectory(parent.resolve("symbols"))) {
                return parent.resolve("symbols");
            }
        }
        return null;
    }

    /** Forge's picture cache, where Forge's own downloader saves card images. */
    static java.util.Optional<Path> forgeCache() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        List<Path> guesses = new ArrayList<>();
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null) {
                guesses.add(Path.of(local, "Forge", "Cache", "pics", "cards"));
            }
        } else if (os.contains("mac")) {
            guesses.add(Path.of(home, "Library", "Caches", "Forge", "pics", "cards"));
        } else {
            guesses.add(Path.of(home, ".cache", "forge", "pics", "cards"));
        }
        return guesses.stream().filter(Files::isDirectory).findFirst();
    }
}

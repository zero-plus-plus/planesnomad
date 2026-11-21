package com.planesnomad.gui.images;

import com.planesnomad.gui.ui.CardPainter;

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
 */
public final class ImageSetup {

    public static final String PROPERTY = "planesnomad.cardImages";

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

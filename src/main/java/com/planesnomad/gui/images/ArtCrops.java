package com.planesnomad.gui.images;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Art crop boxes per set (or group of identically formatted sets), read from
 * {@code data/art-crops.txt}. Boxes are in pixels of a 488 x 680 image and are scaled to the
 * actual image size.
 */
public final class ArtCrops {

    public static final int REF_WIDTH = 488;
    public static final int REF_HEIGHT = 680;

    /** A crop box as fractions of the image (0..1), so it works at any image size. */
    public record Box(double left, double top, double right, double bottom) { }

    private final Map<String, Box> bySet = new HashMap<>();

    private ArtCrops() { }

    public static ArtCrops load(Path file) {
        ArtCrops crops = new ArtCrops();
        if (!Files.isRegularFile(file)) {
            return crops;
        }
        try {
            int lineNo = 0;
            for (String raw : Files.readAllLines(file)) {
                lineNo++;
                String line = raw.replaceFirst("#.*", "").trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("=");
                String[] nums = parts.length == 2 ? parts[1].trim().split("\\s*,\\s*") : new String[0];
                if (nums.length != 4) {
                    System.err.println("art-crops.txt line " + lineNo + " ignored: " + raw);
                    continue;
                }
                Box box = new Box(
                    Integer.parseInt(nums[0]) / (double) REF_WIDTH,
                    Integer.parseInt(nums[1]) / (double) REF_HEIGHT,
                    Integer.parseInt(nums[2]) / (double) REF_WIDTH,
                    Integer.parseInt(nums[3]) / (double) REF_HEIGHT);
                for (String set : parts[0].split(",")) {
                    crops.bySet.put(set.trim().toUpperCase(Locale.ROOT), box);
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Could not read " + file + ": " + e);
        }
        return crops;
    }

    /**
     * @param setCode     the card's set code (e.g. 2ED)
     * @param imageFolder the folder part of Forge's image key (e.g. U), may be empty
     */
    public Optional<Box> find(String setCode, String imageFolder) {
        Box box = setCode == null ? null : bySet.get(setCode.toUpperCase(Locale.ROOT));
        if (box == null && imageFolder != null && !imageFolder.isEmpty()) {
            box = bySet.get(imageFolder.toUpperCase(Locale.ROOT));
        }
        return Optional.ofNullable(box);
    }

    public int size() {
        return bySet.size();
    }
}

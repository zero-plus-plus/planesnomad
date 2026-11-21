package com.planesnomad.gui.images;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final Set<String> altArtsNoCrop = new HashSet<>();

    private ArtCrops() { }

    public static ArtCrops load(Path file) {
        ArtCrops crops = new ArtCrops();
        if (!Files.isRegularFile(file)) {
            return crops;
        }
        try {
            // Join continued lines (trailing backslash), dropping comments first.
            List<String> lines = new ArrayList<>();
            StringBuilder pending = new StringBuilder();
            for (String raw : Files.readAllLines(file)) {
                String line = raw.replaceFirst("#.*", "").trim();
                boolean continues = line.endsWith("\\");
                if (continues) {
                    line = line.substring(0, line.length() - 1).trim();
                }
                pending.append(pending.length() > 0 && !line.isEmpty() ? " " : "").append(line);
                if (!continues) {
                    if (pending.length() > 0) {
                        lines.add(pending.toString());
                    }
                    pending.setLength(0);
                }
            }
            for (String line : lines) {
                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    System.err.println("art-crops.txt line ignored: " + line);
                    continue;
                }
                String key = parts[0].trim();
                String value = parts[1].trim();
                if (key.equalsIgnoreCase("ALT-ARTS-NO-CROP")) {
                    for (String set : value.split(",")) {
                        crops.altArtsNoCrop.add(set.trim().toUpperCase(Locale.ROOT));
                    }
                    continue;
                }
                String[] nums = value.split("\\s*,\\s*");
                if (nums.length != 4) {
                    System.err.println("art-crops.txt line ignored: " + line);
                    continue;
                }
                Box box = new Box(
                    Integer.parseInt(nums[0]) / (double) REF_WIDTH,
                    Integer.parseInt(nums[1]) / (double) REF_HEIGHT,
                    Integer.parseInt(nums[2]) / (double) REF_WIDTH,
                    Integer.parseInt(nums[3]) / (double) REF_HEIGHT);
                for (String set : key.split(",")) {
                    crops.bySet.put(set.trim().toUpperCase(Locale.ROOT), box);
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Could not read " + file + ": " + e);
        }
        return crops;
    }

    /** Normal-layout box for a set (no layout or printing rules applied). */
    public Optional<Box> find(String setCode, String imageFolder) {
        return find(setCode, imageFolder, CardLayouts.NORMAL, 1, false);
    }

    /**
     * The crop box for one printing, or empty if it should get no crop.
     *
     * @param setCode     the card's set code (e.g. 2ED)
     * @param imageFolder the folder part of Forge's image key (e.g. U), may be empty
     * @param layout      card layout (see {@link CardLayouts}); special layouts need their own box
     * @param artIndex    which printing in the set (1 = regular)
     * @param basicLand   basic lands are never treated as special printings
     */
    public Optional<Box> find(String setCode, String imageFolder, String layout, int artIndex, boolean basicLand) {
        List<String> codes = new ArrayList<>();
        if (setCode != null && !setCode.isEmpty()) {
            codes.add(setCode.toUpperCase(Locale.ROOT));
        }
        if (imageFolder != null && !imageFolder.isEmpty()) {
            codes.add(imageFolder.toUpperCase(Locale.ROOT));
        }
        if (artIndex > 1 && !basicLand && codes.stream().anyMatch(altArtsNoCrop::contains)) {
            return Optional.empty();   // possibly a showcase/borderless frame
        }
        boolean special = layout != null && !layout.equals(CardLayouts.NORMAL);
        for (String code : codes) {
            Box box = bySet.get(special ? code + "@" + layout.toUpperCase(Locale.ROOT) : code);
            if (box != null) {
                return Optional.of(box);
            }
        }
        if (special) {
            // "*@layout" applies to every set that has a (checked) normal box.
            Box any = bySet.get("*@" + layout.toUpperCase(Locale.ROOT));
            if (any != null && codes.stream().anyMatch(bySet::containsKey)) {
                return Optional.of(any);
            }
        }
        return Optional.empty();
    }

    public int size() {
        return bySet.size();
    }
}

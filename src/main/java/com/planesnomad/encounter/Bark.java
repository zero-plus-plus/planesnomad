package com.planesnomad.encounter;

/**
 * A line an enemy says in a speech bubble by its portrait when something happens.
 *
 * @param when what makes it speak
 * @param text what it says
 * @param once true (default): only the first time in a fight
 */
public record Bark(When when, String text, boolean once) {

    /** What can make an enemy speak. The names are the ones used in encounter files. */
    public enum When {
        /** As the fight starts. */
        START("start"),
        /** Its life drops below half of its starting life. */
        LIFE_BELOW_HALF("lifeBelowHalf"),
        /** The player's life drops below half of their starting life. */
        PLAYER_LIFE_BELOW_HALF("playerLifeBelowHalf");

        public final String key;

        When(String key) {
            this.key = key;
        }

        public static When of(String key) {
            for (When w : values()) {
                if (w.key.equals(key)) {
                    return w;
                }
            }
            return null;
        }

        public static String keys() {
            StringBuilder sb = new StringBuilder();
            for (When w : values()) {
                sb.append(sb.length() == 0 ? "" : ", ").append(w.key);
            }
            return sb.toString();
        }
    }
}

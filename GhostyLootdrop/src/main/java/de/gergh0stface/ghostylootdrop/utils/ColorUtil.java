package de.gergh0stface.ghostylootdrop.utils;

import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for translating legacy & color codes and &#RRGGBB hex color codes.
 */
public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private ColorUtil() {}

    /**
     * Translates & color codes and &#RRGGBB hex codes in the given string.
     */
    public static String color(String input) {
        if (input == null) return "";

        // Handle hex codes first: &#RRGGBB
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, ChatColor.of("#" + hex).toString());
        }
        matcher.appendTail(buffer);

        // Handle standard & codes
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    /**
     * Strips all color from the given string.
     */
    public static String strip(String input) {
        return ChatColor.stripColor(color(input));
    }
}

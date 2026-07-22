package com.sulphate.chatcolor2.utils;

import org.bukkit.Bukkit;

public class CompatabilityUtils {

    private static final int HEX_SUPPORT_MINOR_VERSION = 16;

    private static boolean isHexLegacy;

    private CompatabilityUtils() {

    }

    public static void init() {
        isHexLegacy = parseMinorVersion(Bukkit.getBukkitVersion()) < HEX_SUPPORT_MINOR_VERSION;
    }

    private static int parseMinorVersion(String bukkitVersion) {
        int dashIndex = bukkitVersion.indexOf('-');
        String[] parts = (dashIndex == -1 ? bukkitVersion : bukkitVersion.substring(0, dashIndex)).split("\\.");

        if (parts.length < 2) {
            return HEX_SUPPORT_MINOR_VERSION;
        }

        try {
            return Integer.parseInt(parts[1]);
        }
        catch (NumberFormatException ex) {
            return HEX_SUPPORT_MINOR_VERSION;
        }
    }

    public static boolean isHexLegacy() {
        return isHexLegacy;
    }

}

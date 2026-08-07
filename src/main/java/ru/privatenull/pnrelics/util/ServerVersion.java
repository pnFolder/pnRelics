package ru.privatenull.pnrelics.util;

import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record ServerVersion(int major, int minor, int patch) implements Comparable<ServerVersion> {
    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    public static ServerVersion current() {
        return parse(Bukkit.getBukkitVersion());
    }

    public static ServerVersion parse(String value) {
        Matcher matcher = VERSION.matcher(value == null ? "" : value);
        if (!matcher.find()) return new ServerVersion(0, 0, 0);
        return new ServerVersion(number(matcher.group(1)), number(matcher.group(2)), number(matcher.group(3)));
    }

    public boolean supported() {
        return major > 1 || (major == 1 && minor >= 16);
    }

    public boolean atLeast(int wantedMajor, int wantedMinor, int wantedPatch) {
        return compareTo(new ServerVersion(wantedMajor, wantedMinor, wantedPatch)) >= 0;
    }

    @Override
    public int compareTo(ServerVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        if (result == 0) result = Integer.compare(patch, other.patch);
        return result;
    }

    private static int number(String value) {
        if (value == null) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}


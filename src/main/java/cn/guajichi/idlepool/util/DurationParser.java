package cn.guajichi.idlepool.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PART = Pattern.compile("(\\d+)([dhms])");

    private DurationParser() {
    }

    public static Duration parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration cannot be blank");
        }

        String normalized = input.toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = PART.matcher(normalized);
        long seconds = 0;
        int cursor = 0;
        while (matcher.find()) {
            if (matcher.start() != cursor) {
                throw new IllegalArgumentException("Invalid duration: " + input);
            }
            long value = Long.parseLong(matcher.group(1));
            seconds = Math.addExact(seconds, Math.multiplyExact(value, unitSeconds(matcher.group(2).charAt(0))));
            cursor = matcher.end();
        }
        if (cursor != normalized.length() || seconds <= 0) {
            throw new IllegalArgumentException("Invalid duration: " + input);
        }
        return Duration.ofSeconds(seconds);
    }

    private static long unitSeconds(char unit) {
        return switch (unit) {
            case 'd' -> 86_400;
            case 'h' -> 3_600;
            case 'm' -> 60;
            case 's' -> 1;
            default -> throw new IllegalArgumentException("Unsupported duration unit: " + unit);
        };
    }
}

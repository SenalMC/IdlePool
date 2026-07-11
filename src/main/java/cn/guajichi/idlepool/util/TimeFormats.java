package cn.guajichi.idlepool.util;

public final class TimeFormats {
    private TimeFormats() {
    }

    public static String clock(long totalSeconds) {
        long safe = Math.max(0, totalSeconds);
        long hours = safe / 3_600;
        long minutes = (safe % 3_600) / 60;
        long seconds = safe % 60;
        return "%02d:%02d:%02d".formatted(hours, minutes, seconds);
    }
}

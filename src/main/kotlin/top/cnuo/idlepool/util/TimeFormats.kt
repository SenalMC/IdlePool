package top.cnuo.idlepool.util

object TimeFormats {
    @JvmStatic
    fun clock(totalSeconds: Long): String {
        val safe = totalSeconds.coerceAtLeast(0)
        return "%02d:%02d:%02d".format(safe / 3600, (safe % 3600) / 60, safe % 60)
    }
}

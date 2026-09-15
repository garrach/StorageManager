package com.example.storagemanager.util

import java.util.Locale

object FormatUtils {

    private val SUFFIXES = arrayOf("B", "KB", "MB", "GB", "TB", "PB")

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val value = bytes.toDouble()
        val digitGroups = (Math.log10(value) / Math.log10(1024.0)).toInt()
        val group = digitGroups.coerceAtMost(SUFFIXES.size - 1)
        val display = value / Math.pow(1024.0, group.toDouble())
        val pattern = if (group == 0) "%.0f" else "%.1f"
        return String.format(Locale.US, "$pattern %s", display, SUFFIXES[group])
    }

    fun formatPercent(fraction: Float): String =
        String.format(Locale.US, "%.1f%%", fraction * 100f)

    fun formatCount(count: Long): String = when {
        count >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }

    fun formatMillisAgo(timestamp: Long): String {
        if (timestamp <= 0) return "—"
        val delta = System.currentTimeMillis() - timestamp
        val minutes = delta / 60_000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            delta < (24 * 60 * 60_000) -> "${minutes / 60} hr ago"
            else -> "${(delta / (24 * 60 * 60_000)).coerceAtLeast(1)} day${if (delta >= 48 * 60 * 60_000) "s" else ""} ago"
        }
    }
}
package com.haramhide.app.ui

import android.content.Context
import com.haramhide.app.R

/** Cool-down taymerini o'qiladigan ko'rinishga keltiradi. */
fun formatDuration(context: Context, ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return when {
        hours > 0 -> context.getString(R.string.hours_short, hours.toInt(), minutes.toInt())
        minutes > 0 -> context.getString(R.string.minutes_short, minutes.toInt())
        else -> context.getString(R.string.seconds_short, seconds.toInt())
    }
}

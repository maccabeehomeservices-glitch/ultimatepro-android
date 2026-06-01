package com.ultimatepro.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Timezone display for jobs (TZ 3/3). scheduled_start is stored as a UTC instant;
// we render it in the job's effective_timezone. java.time is native on minSdk 26+.
const val DEFAULT_TZ = "America/New_York"

fun zoneIdOf(tz: String?): ZoneId = try {
    ZoneId.of(if (tz.isNullOrBlank()) DEFAULT_TZ else tz)
} catch (_: Exception) {
    ZoneId.of(DEFAULT_TZ)
}

// Format a stored UTC instant (e.g. "2026-06-02T19:00:00.000Z") in the job's zone.
// `pattern` is a java.time pattern; use a `zzz` token for the zone label ("2:00 PM CDT").
// Falls back to a naive-local parse for legacy non-UTC strings, then to the raw value.
fun formatJobInstant(iso: String?, tz: String?, pattern: String): String {
    if (iso.isNullOrBlank()) return ""
    val fmt = DateTimeFormatter.ofPattern(pattern, Locale.US)
    return try {
        Instant.parse(iso).atZone(zoneIdOf(tz)).format(fmt)
    } catch (_: Exception) {
        try {
            LocalDateTime.parse(iso.take(19)).format(fmt)
        } catch (_: Exception) {
            iso
        }
    }
}

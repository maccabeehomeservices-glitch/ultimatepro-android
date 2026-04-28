package com.ultimatepro.ui.common

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Light palette ─────────────────────────────────────────────────────────────
private val LightBackground       = Color(0xFFF5F7FA)
private val LightSurface          = Color(0xFFFFFFFF)
private val LightSurfaceVariant   = Color(0xFFDDE3ED)
private val LightPrimary          = Color(0xFF1A73E8)
private val LightPrimaryVariant   = Color(0xFF1557B0)
private val LightSecondary        = Color(0xFF34A853)
private val LightOnPrimary        = Color(0xFFFFFFFF)
private val LightOnBackground     = Color(0xFF1C1C1E)
private val LightOnSurface        = Color(0xFF1C1C1E)
private val LightOnSurfaceVariant = Color(0xFF6B7280)
private val LightBorder           = Color(0xFFB0BEC5)
private val LightError            = Color(0xFFDC2626)

// ── Dark palette ──────────────────────────────────────────────────────────────
private val DarkBackground        = Color(0xFF0B1824)
private val DarkSurface           = Color(0xFF0F2032)
private val DarkSurfaceVariant    = Color(0xFF1A3A5C)
private val DarkPrimary           = Color(0xFF3A7BD5)
private val DarkPrimaryVariant    = Color(0xFF2D5FA8)
private val DarkSecondary         = Color(0xFF34A853)
private val DarkOnPrimary         = Color(0xFFFFFFFF)
private val DarkOnBackground      = Color(0xFFE8F0FE)
private val DarkOnSurface         = Color(0xFFE8F0FE)
private val DarkOnSurfaceVariant  = Color(0xFF8899AA)
private val DarkBorder            = Color(0xFF1E3A5F)
private val DarkError             = Color(0xFFEF4444)

// ── Status colors (canonical — must match ui-design-system.md §1) ─────────────
val StatusUnscheduled = Color(0xFF6B7280)  // Slate
val StatusScheduled   = Color(0xFF2563EB)  // Blue
val StatusEnRoute     = Color(0xFFF97316)  // Orange
val StatusInProgress  = Color(0xFF0EA5E9)  // Sky
val StatusHolding     = Color(0xFFD97706)  // Amber
val StatusCompleted   = Color(0xFF16A34A)  // Green
val StatusCancelled   = Color(0xFFDC2626)  // Red
val StatusDeleted     = Color(0xFF9CA3AF)  // Light gray
@Deprecated("Legacy: 'on_hold' is no longer a live job status; rows migrated to 'holding'")
val StatusOnHold      = Color(0xFF0891B2)

// ── Brand / accent colors (used as fixed accent tints across the app) ─────────
object AppColors {
    val Blue      = Color(0xFF1A73E8)
    val BlueDark  = Color(0xFF1557B0)
    val BlueLight = Color(0xFF4A9EF5)
    val Accent    = Color(0xFF34A853)
    val Green     = Color(0xFF16A34A)
    val Orange    = Color(0xFFF97316)
    val Red       = Color(0xFFDC2626)
    val Purple    = Color(0xFF7C3AED)
    val Slate     = Color(0xFF6B7280)
    val Gold      = Color(0xFFF59E0B)

    fun jobStatus(status: String?): Color = when (status) {
        "scheduled"   -> StatusScheduled
        "en_route"    -> StatusEnRoute
        "in_progress" -> StatusInProgress
        "holding"     -> StatusHolding
        "completed"   -> StatusCompleted
        "cancelled"   -> StatusCancelled
        "deleted"     -> StatusDeleted
        else          -> StatusUnscheduled
    }

    fun invoiceStatus(status: String): Color = when (status) {
        "paid"    -> Green
        "partial" -> Orange
        "overdue" -> Red
        "sent"    -> Blue
        "void"    -> Slate
        else      -> StatusUnscheduled
    }

    fun priority(priority: String): Color = when (priority) {
        "urgent" -> Red
        "high"   -> Orange
        "medium" -> Blue
        else     -> Slate
    }

    fun leadStatus(status: String): Color = when (status) {
        "new"       -> Blue
        "contacted" -> Purple
        "qualified" -> Accent
        "proposal"  -> Orange
        "won"       -> Green
        "lost"      -> Slate
        else        -> StatusUnscheduled
    }
}

// ── Color schemes ─────────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    background         = LightBackground,
    surface            = LightSurface,
    surfaceVariant     = LightSurfaceVariant,
    primary            = LightPrimary,
    primaryContainer   = LightPrimaryVariant,
    secondary          = LightSecondary,
    onPrimary          = LightOnPrimary,
    onBackground       = LightOnBackground,
    onSurface          = LightOnSurface,
    onSurfaceVariant   = LightOnSurfaceVariant,
    error              = LightError,
    outline            = LightBorder
)

private val DarkColorScheme = darkColorScheme(
    background         = DarkBackground,
    surface            = DarkSurface,
    surfaceVariant     = DarkSurfaceVariant,
    primary            = DarkPrimary,
    primaryContainer   = DarkPrimaryVariant,
    secondary          = DarkSecondary,
    onPrimary          = DarkOnPrimary,
    onBackground       = DarkOnBackground,
    onSurface          = DarkOnSurface,
    onSurfaceVariant   = DarkOnSurfaceVariant,
    error              = DarkError,
    outline            = DarkBorder
)

// ── Typography ────────────────────────────────────────────────────────────────
private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 32.sp, letterSpacing = (-0.5).sp),
    titleLarge   = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 21.sp),
    titleMedium  = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 19.sp),
    titleSmall   = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 16.sp),
    bodyLarge    = TextStyle(fontWeight = FontWeight.Normal,     fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium   = TextStyle(fontWeight = FontWeight.Normal,     fontSize = 15.sp),
    bodySmall    = TextStyle(fontWeight = FontWeight.Normal,     fontSize = 15.sp),
    labelLarge   = TextStyle(fontWeight = FontWeight.Medium,     fontSize = 14.sp),
    labelMedium  = TextStyle(fontWeight = FontWeight.Medium,     fontSize = 13.sp),
    labelSmall   = TextStyle(fontWeight = FontWeight.Medium,     fontSize = 13.sp, letterSpacing = 0.4.sp),
)

// ── Theme composable ──────────────────────────────────────────────────────────
@Composable
fun CRMTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = AppTypography,
        content     = content
    )
}

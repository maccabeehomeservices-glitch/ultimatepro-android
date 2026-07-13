package com.ultimatepro.ui.common

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Light palette (P3.1b pearl/brass tokens) ──────────────────────────────────
private val LightBackground       = Color(0xFFF6F3EC)  // background
private val LightSurface          = Color(0xFFFFFFFF)  // card
private val LightSurfaceVariant   = Color(0xFFE4DFD2)  // hairline (nearest neutral token)
private val LightPrimary          = Color(0xFF2D6FC2)  // blue
private val LightPrimaryVariant   = Color(0xFF1E4E8C)  // blueTextOnPearl (deep blue)
private val LightSecondary        = Color(0xFFA9812E)  // brassText (brass = secondary accent)
private val LightOnPrimary        = Color(0xFFFFFFFF)
private val LightOnBackground     = Color(0xFF1D1C18)  // ink
private val LightOnSurface        = Color(0xFF1D1C18)  // ink
private val LightOnSurfaceVariant = Color(0xFF7A7466)  // muted
private val LightBorder           = Color(0xFFE4DFD2)  // hairline
private val LightError            = Color(0xFFDC2626)  // status red (unchanged)

// ── Dark palette (P3.1b pearl/brass tokens) ───────────────────────────────────
private val DarkBackground        = Color(0xFF141419)  // background
private val DarkSurface           = Color(0xFF1D1D24)  // card
private val DarkSurfaceVariant    = Color(0xFF2B2B33)  // hairline
private val DarkPrimary           = Color(0xFF4C8BE0)  // blue (dark)
private val DarkPrimaryVariant    = Color(0xFF2D6FC2)  // light blue as dark container
private val DarkSecondary         = Color(0xFFD8B87C)  // brassText/Border (dark)
private val DarkOnPrimary         = Color(0xFFFFFFFF)
private val DarkOnBackground      = Color(0xFFF2EEE4)  // ink (dark)
private val DarkOnSurface         = Color(0xFFF2EEE4)  // ink (dark)
private val DarkOnSurfaceVariant  = Color(0xFFA29C8E)  // muted (dark)
private val DarkBorder            = Color(0xFF2B2B33)  // hairline (dark)
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
    // NOTE: Blue/Accent/Green/Orange/Red/Purple/Slate are LEFT UNCHANGED — they feed the
    // invoiceStatus/priority/leadStatus mappers below, which law 4 freezes. Recoloring the
    // brand blue to token #2D6FC2 for the ~31 AppColors call sites is a sweep-phase decision
    // (would require decoupling the mapper blue). See the P3.1b unknowns ledger.
    val Blue      = Color(0xFF1A73E8)  // FROZEN (mapper-fed)
    val BlueDark  = Color(0xFF1E4E8C)  // → blueTextOnPearl
    val BlueLight = Color(0xFF4C8BE0)  // → blue (dark)
    val Accent    = Color(0xFF34A853)  // FROZEN (leadStatus qualified)
    val Green     = Color(0xFF16A34A)  // FROZEN (mapper)
    val Orange    = Color(0xFFF97316)  // FROZEN (mapper)
    val Red       = Color(0xFFDC2626)  // FROZEN (mapper)
    val Purple    = Color(0xFF7C3AED)  // FROZEN (mapper)
    val Slate     = Color(0xFF6B7280)  // FROZEN (mapper)
    val Gold      = Color(0xFFA9812E)  // → brassText

    // ── P3.1b pearl/brass vocabulary (for the pearl button + sweep-phase adoption) ──
    val PearlFace       = Color(0xFFFDFCF8)  // signature pearl (stays pearl in dark)
    val BrassText       = Color(0xFFA9812E)
    val BrassBorder     = Color(0xFFC9A25C)
    val BrassDeep       = Color(0xFF8A6A3B)
    val BrassBright     = Color(0xFFE7CE99)
    val BlueTextOnPearl = Color(0xFF1E4E8C)

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

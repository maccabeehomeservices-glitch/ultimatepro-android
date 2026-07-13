package com.ultimatepro.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ── P3.1b central shape scale (pearl/brass) ───────────────────────────────────
// Cards + buttons = 11dp (per Fable component specs); pill/chip corners use
// RoundedCornerShape(50) inline. Defined here as the single source of truth.
//
// NOTE (Phase 0): this object is intentionally NOT yet wired into CRMTheme —
// passing `shapes = AppShapes` would shift Material3 default-shape components
// (medium 12dp → 11dp) app-wide. Wiring + per-component adoption happens in the
// screen-by-screen sweep phase so each change is visible + reviewable. Adding the
// object now is pure infra: zero visual change.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(11.dp),
    large      = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

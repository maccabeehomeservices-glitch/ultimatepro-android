package com.ultimatepro.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── P3.1c/d — the ONE pearl-inlay button of the app language ──────────────────
// Pearl face #FDFCF8 · 2.5dp blue outer border · 2.5dp brass inner ring flush inside ·
// radius 11dp · label 15sp/Medium ink · leading icon 18dp brass.
//
// P3.1d fixes:
//  • CLIP-PROOF geometry (defect: Job Detail labels cut off): the two rings are now
//    drawn with a `drawBehind` modifier (blue + brass concentric rounded-rect strokes on
//    a pearl fill) instead of nested `.padding(2.5.dp)` — draw modifiers add ZERO layout
//    height. Intrinsic ≈ 44dp, so any container >=44dp fits at font-scale 1.0–1.3 with no
//    bottom-clip. Explicit lineHeight keeps the glyph box tight.
//  • GHOST variant (David-approved): `ghost = true` → text + accent, no pearl fill, for
//    tertiary/inline/toolbar actions. Still the primitive → one-system holds.
//  • STATUS colour (David-approved): pass `labelColor` (danger red #DC2626 / success green
//    #16A34A / warning amber #D97706) — the LABEL + leading ICON take the colour; the pearl
//    face + blue/brass rings are unchanged. Same rule on web (<Button> success/warning).
//
// Redesign-only wrapper: it carries onClick/enabled/modifier and nothing about behavior.
@Composable
fun AppButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    ghost: Boolean = false,
    labelColor: Color = Color(0xFF1D1C18),    // ink; status call sites pass red/green/amber
    leadingIcon: ImageVector? = null,
    leadingPainter: Painter? = null,
    fillMaxWidth: Boolean = false,
) {
    val outer = RoundedCornerShape(11.dp)
    val active = enabled && !loading
    val blue = MaterialTheme.colorScheme.primary
    val isStatus = labelColor != Color(0xFF1D1C18)

    // Label colour: pearl → labelColor (ink or status); ghost → blue accent unless status.
    val contentColor = if (ghost) (if (isStatus) labelColor else blue) else labelColor
    // Icon tint: pearl → brass (or status); ghost → blue (or status).
    val iconTint = if (ghost) (if (isStatus) labelColor else blue)
                   else (if (isStatus) labelColor else AppColors.BrassText)

    val shell = modifier
        .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
        .heightIn(min = 44.dp)
        .alpha(if (active) 1f else 0.5f)
        .clip(outer)
        .let { m ->
            if (ghost) m
            else m.drawBehind {
                val r = 11.dp.toPx()
                val bs = 2.5.dp.toPx()
                drawRoundRect(color = AppColors.PearlFace, cornerRadius = CornerRadius(r, r))
                drawRoundRect(                                   // blue outer ring (0..2.5dp)
                    color = blue,
                    topLeft = Offset(bs / 2f, bs / 2f),
                    size = Size(size.width - bs, size.height - bs),
                    cornerRadius = CornerRadius(r - bs / 2f, r - bs / 2f),
                    style = Stroke(width = bs)
                )
                drawRoundRect(                                   // brass inner ring (2.5..5dp)
                    color = AppColors.BrassRing,
                    topLeft = Offset(bs * 1.5f, bs * 1.5f),
                    size = Size(size.width - bs * 3f, size.height - bs * 3f),
                    cornerRadius = CornerRadius(r - bs * 1.5f, r - bs * 1.5f),
                    style = Stroke(width = bs)
                )
            }
        }
        .clickable(enabled = active, onClick = onClick)
        .padding(
            horizontal = if (ghost) 12.dp else 18.dp,
            vertical = if (ghost) 9.dp else 10.dp
        )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = shell
    ) {
        when {
            loading -> {
                CircularProgressIndicator(color = iconTint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            leadingIcon != null -> {
                Icon(leadingIcon, null, tint = iconTint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            leadingPainter != null -> {
                Icon(leadingPainter, null, tint = iconTint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(
            label,
            color = contentColor,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

// ── P3.1c — the 2dp brass "shine" hairline under screen headers ───────────────
// Brush: BrassDeep -> BrassBorder -> BrassShineHi -> BrassBorder -> BrassDeep
// (#8A6A3B -> #C9A25C -> #F0D9A6 -> #C9A25C -> #8A6A3B).
val BrassShineBrush: Brush = Brush.horizontalGradient(
    listOf(
        AppColors.BrassDeep,
        AppColors.BrassBorder,
        AppColors.BrassShineHi,
        AppColors.BrassBorder,
        AppColors.BrassDeep,
    )
)

@Composable
fun ShineHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(BrassShineBrush)
    )
}

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── P3.1c — the ONE pearl-inlay button of the app language ────────────────────
// Pearl face #FDFCF8 · 2.5dp blue outer border (colorScheme.primary = #2D6FC2 light
// / #4C8BE0 dark) · 2.5dp brass inner ring (#B08D57) flush inside it · radius 11dp ·
// height >=46dp · label 15sp/Medium ink #1D1C18 · leading icon 18dp brass.
//
// This is a REDESIGN-ONLY wrapper: it carries onClick/enabled/modifier and nothing
// about behavior. Sweep call sites keep their exact onClick/enabled/modifier.
// `labelColor` lets destructive call sites keep a RED label on the same pearl inlay
// (David's keep-and-log rule for delete/confirm dialogs).
@Composable
fun AppButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,                 // shows a brass spinner + blocks the click
    labelColor: Color = Color(0xFF1D1C18),    // ink; danger call sites pass AppColors.Red
    leadingIcon: ImageVector? = null,
    leadingPainter: Painter? = null,
    fillMaxWidth: Boolean = false,
) {
    val blue = MaterialTheme.colorScheme.primary
    val outer = RoundedCornerShape(11.dp)
    val active = enabled && !loading
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 46.dp)
            .alpha(if (active) 1f else 0.5f)
            .clip(outer)
            .background(blue, outer)                                        // blue outer border
            .padding(2.5.dp)
            .background(AppColors.BrassRing, RoundedCornerShape(8.5.dp))    // brass inner ring
            .padding(2.5.dp)
            .background(AppColors.PearlFace, RoundedCornerShape(6.dp))      // pearl face
            .clickable(enabled = active, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        when {
            loading -> {
                CircularProgressIndicator(
                    color = AppColors.BrassText,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            leadingIcon != null -> {
                Icon(leadingIcon, null, tint = AppColors.BrassText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            leadingPainter != null -> {
                Icon(leadingPainter, null, tint = AppColors.BrassText, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(label, color = labelColor, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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

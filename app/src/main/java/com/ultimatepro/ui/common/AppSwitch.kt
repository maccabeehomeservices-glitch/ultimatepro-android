package com.ultimatepro.ui.common

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            uncheckedThumbColor  = Color(0xFF78909C),
            uncheckedTrackColor  = Color(0xFFCFD8DC),
            uncheckedBorderColor = Color(0xFF90A4AE),
            checkedThumbColor    = Color(0xFFFFFFFF),
            checkedTrackColor    = Color(0xFF1A73E8),
            checkedBorderColor   = Color(0xFF1A73E8)
        )
    )
}

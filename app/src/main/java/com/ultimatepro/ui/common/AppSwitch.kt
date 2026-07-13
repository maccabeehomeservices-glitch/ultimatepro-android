package com.ultimatepro.ui.common

import androidx.compose.material3.MaterialTheme
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
            checkedTrackColor    = MaterialTheme.colorScheme.primary,
            checkedBorderColor   = MaterialTheme.colorScheme.primary
        )
    )
}

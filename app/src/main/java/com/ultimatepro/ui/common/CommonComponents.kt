package com.ultimatepro.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

// ── Loading ────────────────────────────────────────────────────────────────
@Composable
fun LoadingView(modifier: Modifier = Modifier.fillMaxSize()) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AppColors.Blue)
    }
}

// ── Error ─────────────────────────────────────────────────────────────────
@Composable
fun ErrorView(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ErrorOutline, null,
            Modifier.size(56.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        onRetry?.let {
            Spacer(Modifier.height(24.dp))
            Button(onClick = it, shape = RoundedCornerShape(10.dp)) { Text("Retry") }
        }
    }
}

// ── Empty ─────────────────────────────────────────────────────────────────
@Composable
fun EmptyView(message: String, icon: ImageVector = Icons.Default.Inbox) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Search Bar ────────────────────────────────────────────────────────────
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search…",
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon  = { Icon(Icons.Default.Search, null) },
        trailingIcon = if (value.isNotBlank()) {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Clear, null) } }
        } else null,
        singleLine = true,
        shape      = RoundedCornerShape(28.dp),
        modifier   = modifier
    )
}

// ── Status Chip ───────────────────────────────────────────────────────────
@Composable
fun StatusBadge(label: String, color: Color, small: Boolean = false) {
    Surface(
        color  = color.copy(alpha = 0.12f),
        shape  = RoundedCornerShape(20.dp)
    ) {
        Text(
            label,
            color    = color,
            fontSize = if (small) 12.sp else 13.sp,  // was 10/12sp — label minimum 12sp
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = if (small) 6.dp else 10.dp, vertical = if (small) 2.dp else 4.dp)
        )
    }
}

// ── Avatar ────────────────────────────────────────────────────────────────
@Composable
fun AvatarCircle(
    initials: String,
    color: Color,
    size: Dp = 40.dp,
    textSize: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(initials.take(2).uppercase(), color = Color.White, fontSize = textSize, fontWeight = FontWeight.Bold)
    }
}

// ── Section header ────────────────────────────────────────────────────────
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold, color = AppColors.Blue,
        letterSpacing = 0.6.sp,
        modifier = modifier.padding(vertical = 4.dp)
    )
}

// ── Info Row ─────────────────────────────────────────────────────────────
@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(16.dp).padding(top = 1.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        Text("$label  ", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

// ── Card wrapper ──────────────────────────────────────────────────────────
@Composable
fun CRMCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        content   = {
            Column(Modifier.padding(16.dp), content = content)
        }
    )
}

// ── Currency formatter ────────────────────────────────────────────────────
fun formatMoney(amount: Double, currency: String = "USD"): String =
    NumberFormat.getCurrencyInstance(Locale.US).format(amount)

// ── Quantity formatter ─────────────────────────────────────────────────────
fun formatQty(qty: Double): String =
    if (qty == qty.toLong().toDouble()) qty.toLong().toString() else "%.2f".format(qty)

// ── Colored accent bar (left side of cards) ───────────────────────────────
@Composable
fun AccentBar(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)).background(color))
}

// ── Stat card (for dashboard KPI tiles) ───────────────────────────────────
@Composable
fun KpiTile(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Card(modifier.height(100.dp), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp), tint = color)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
            Text(title, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

// ── Bottom sheet handle ───────────────────────────────────────────────────
@Composable
fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.width(40.dp).height(4.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
    }
}

// ── Priority badge ────────────────────────────────────────────────────────
@Composable
fun PriorityBadge(priority: String) {
    StatusBadge(
        label = priority.replaceFirstChar { it.uppercase() },
        color = AppColors.priority(priority),
        small = true
    )
}

// ── Qty Stepper Row ───────────────────────────────────────────────────────
@Composable
fun QtyStepperRow(
    qty: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    minQty: Int = 0,
    maxQty: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    val canDecrement = qty > minQty
    val canIncrement = qty < maxQty
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onDecrement,
            enabled = canDecrement,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Decrease",
                tint = if (canDecrement) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = qty.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 28.dp)
        )
        IconButton(
            onClick = onIncrement,
            enabled = canIncrement,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Increase",
                tint = if (canIncrement) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

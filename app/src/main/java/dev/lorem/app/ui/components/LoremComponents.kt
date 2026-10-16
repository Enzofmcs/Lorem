package dev.lorem.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.lorem.app.R

enum class LoremTone { NEUTRAL, SUCCESS, ATTENTION, ERROR }

@Composable
private fun LoremTone.colors() = with(MaterialTheme.colorScheme) {
    when (this@colors) {
        LoremTone.NEUTRAL -> secondaryContainer to onSecondaryContainer
        LoremTone.SUCCESS -> primaryContainer to onPrimaryContainer
        LoremTone.ATTENTION -> tertiaryContainer to onTertiaryContainer
        LoremTone.ERROR -> errorContainer to onErrorContainer
    }
}

private fun LoremTone.icon() = when (this) {
    LoremTone.SUCCESS -> R.drawable.ic_check
    LoremTone.ATTENTION, LoremTone.ERROR -> R.drawable.ic_attention
    LoremTone.NEUTRAL -> R.drawable.ic_info
}

@Composable
fun LoremPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Button(
        onClick = onClick, enabled = enabled && !busy,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp), shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        Text(label, Modifier.padding(horizontal = 8.dp), textAlign = TextAlign.Center)
    }
}

@Composable
fun LoremMetricCard(label: String, value: String, modifier: Modifier = Modifier, supportingText: String? = null) {
    Card(modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
            supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun LoremSyncStatus(label: String, tone: LoremTone = LoremTone.NEUTRAL, detail: String? = null) {
    val (background, foreground) = tone.colors()
    Surface(color = background, contentColor = foreground, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(tone.icon()), contentDescription = null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
fun LoremBadge(label: String, tone: LoremTone, modifier: Modifier = Modifier) {
    val (background, foreground) = tone.colors()
    Surface(modifier = modifier, color = background, contentColor = foreground, shape = MaterialTheme.shapes.small) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(tone.icon()), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LoremEmptyState(title: String, detail: String? = null) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun LoremErrorState(message: String) = LoremSyncStatus(message, LoremTone.ERROR)

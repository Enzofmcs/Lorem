package dev.lorem.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.lorem.app.R
import dev.lorem.app.ui.navigation.LoremDestination

private val tabs = listOf(LoremDestination.Home, LoremDestination.History, LoremDestination.Statistics)

@Composable
fun LoremScaffold(
    destination: LoremDestination?,
    showSettings: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onTab: (LoremDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (canGoBack) IconButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Voltar")
                    }
                    Text(
                        if (destination in tabs || !canGoBack) "Lorem" else when (destination) {
                            LoremDestination.Configuration -> "Configurações"
                            LoremDestination.Ipsum -> "Treino"
                            else -> "Resultado"
                        },
                        modifier = Modifier.weight(1f).semantics { heading() }, style = if (canGoBack) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    )
                    if (showSettings) IconButton(onClick = onSettings, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Configurações")
                    }
                }
            }
        },
        bottomBar = {
            if (destination in tabs) LoremBottomBar(requireNotNull(destination), onTab)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) { content() }
    }
}

/** Labels may wrap at 200% font size; every tab grows with its content. */
@Composable
private fun LoremBottomBar(selected: LoremDestination, onSelect: (LoremDestination) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().selectableGroup()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .height(IntrinsicSize.Min).padding(horizontal = 8.dp),
        ) {
            tabs.forEach { destination ->
                val isSelected = destination == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().heightIn(min = 72.dp)
                        .testTag("tab-${destination.route}")
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(destination) })
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Icon(painterResource(when (destination) {
                            LoremDestination.Home -> R.drawable.ic_home
                            LoremDestination.History -> R.drawable.ic_history
                            else -> R.drawable.ic_statistics
                        }), contentDescription = null, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).size(24.dp))
                    }
                    Text(destination.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

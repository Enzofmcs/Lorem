package dev.lorem.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.lorem.app.domain.model.ThemePreference
import dev.lorem.app.ui.components.LoremErrorState

@Composable
internal fun ThemePicker(selected: ThemePreference, onSelect: (ThemePreference) -> Unit, error: String?) {
    Text("Aparência", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text("Escolha o tema do Lorem. Sistema acompanha a aparência do aparelho.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.fillMaxWidth().selectableGroup()) {
        ThemePreference.entries.forEach { preference ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("theme-${preference.name}")
                    .selectable(selected = preference == selected, role = Role.RadioButton, onClick = { onSelect(preference) })
                    .padding(vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                RadioButton(selected = preference == selected, onClick = null)
                Text(preference.label, Modifier.weight(1f).padding(start = 12.dp))
            }
        }
    }
    error?.let { LoremErrorState(it) }
}

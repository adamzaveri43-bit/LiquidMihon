package eu.kanade.presentation.more.settings.widget

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UiPreset
import eu.kanade.domain.ui.UiPresets
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

/**
 * One tap switching between whole looks (theme, glass, accent, font and library background).
 */
@Composable
internal fun UiPresetsPreferenceWidget(uiPreferences: UiPreferences) {
    val context = LocalContext.current
    // Recompose when presets are saved or deleted
    val raw by uiPreferences.uiPresets.collectAsState()
    val saved = remember(raw) { UiPresets.saved(uiPreferences) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<UiPreset?>(null) }

    val apply = { preset: UiPreset ->
        UiPresets.apply(uiPreferences, preset)
        (context as? Activity)?.let { ActivityCompat.recreate(it) }
    }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = stringResource(MR.strings.pref_ui_presets),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = PrefsHorizontalPadding, vertical = 4.dp),
        )
        Text(
            text = stringResource(MR.strings.ui_preset_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = PrefsHorizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(UiPresets.builtIn + saved, key = { (if (it.builtIn) "b:" else "s:") + it.name }) { preset ->
                PresetChip(
                    label = preset.name,
                    highlighted = preset.builtIn,
                    onClick = { apply(preset) },
                    onLongClick = { if (!preset.builtIn) toDelete = preset },
                )
            }
            item(key = "save") {
                PresetChip(
                    label = "+ " + stringResource(MR.strings.ui_preset_save),
                    highlighted = false,
                    onClick = { showSaveDialog = true },
                    onLongClick = {},
                )
            }
        }
    }

    if (showSaveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text(stringResource(MR.strings.ui_preset_name)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        UiPresets.save(uiPreferences, name.trim())
                        showSaveDialog = false
                    },
                ) { Text(stringResource(MR.strings.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text(stringResource(MR.strings.action_cancel)) }
            },
        )
    }

    toDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            text = { Text(stringResource(MR.strings.ui_preset_delete, preset.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        UiPresets.delete(uiPreferences, preset.name)
                        toDelete = null
                    },
                ) { Text(stringResource(MR.strings.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text(stringResource(MR.strings.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = if (highlighted) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (highlighted) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

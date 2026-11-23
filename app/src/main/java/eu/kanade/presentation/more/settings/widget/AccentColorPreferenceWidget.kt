package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Check
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Icon
import tachiyomi.presentation.core.i18n.stringResource

private val AccentPresets = listOf(
    0xFF007AFF, // Blue
    0xFF5856D6, // Indigo
    0xFFAF52DE, // Purple
    0xFFFF2D55, // Pink
    0xFFFF3B30, // Red
    0xFFFF9500, // Orange
    0xFFFFCC00, // Yellow
    0xFF34C759, // Green
    0xFF00C7BE, // Mint
    0xFF30B0C7, // Teal
    0xFF32ADE6, // Cyan
    0xFFA2845E, // Brown
).map { it.toInt() }

/**
 * Row of accent swatches: the theme's own color, presets and a custom color picker.
 */
@Composable
internal fun AccentColorPreferenceWidget(
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = stringResource(MR.strings.pref_accent_color),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = PrefsHorizontalPadding, vertical = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = PrefsHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // Theme default, drawn as a split swatch
                Swatch(
                    brush = Brush.sweepGradient(
                        listOf(Color(0xFF007AFF), Color(0xFF34C759), Color(0xFFFF9500), Color(0xFF007AFF)),
                    ),
                    selected = value == 0,
                    onClick = { onValueChange(0) },
                )
            }
            items(AccentPresets) { color ->
                Swatch(
                    brush = Brush.linearGradient(listOf(Color(color), Color(color))),
                    selected = value == color,
                    onClick = { onValueChange(color) },
                )
            }
            item {
                val isCustom = value != 0 && value !in AccentPresets
                Swatch(
                    brush = if (isCustom) {
                        Brush.linearGradient(listOf(Color(value), Color(value)))
                    } else {
                        Brush.sweepGradient(
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red,
                            ),
                        )
                    },
                    selected = isCustom,
                    onClick = { showPicker = true },
                )
            }
        }
    }
    if (showPicker) {
        AccentPickerDialog(
            initial = if (value != 0) Color(value) else MaterialTheme.colorScheme.primary,
            onDismissRequest = { showPicker = false },
            onConfirm = {
                showPicker = false
                onValueChange(it.toArgb())
            },
        )
    }
}

@Composable
private fun Swatch(
    brush: Brush,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(brush)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = MaterialSymbols.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun AccentPickerDialog(
    initial: Color,
    onDismissRequest: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.pref_accent_color_custom)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(color),
                )
                Text(stringResource(MR.strings.gradient_editor_hue), style = MaterialTheme.typography.labelLarge)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                Text(stringResource(MR.strings.gradient_editor_saturation), style = MaterialTheme.typography.labelLarge)
                Slider(value = saturation, onValueChange = { saturation = it })
                Text(stringResource(MR.strings.gradient_editor_brightness), style = MaterialTheme.typography.labelLarge)
                Slider(value = brightness, onValueChange = { brightness = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(color) }) { Text(stringResource(MR.strings.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

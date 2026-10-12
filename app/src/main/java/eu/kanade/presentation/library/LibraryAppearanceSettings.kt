package eu.kanade.presentation.library

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPresets
import eu.kanade.domain.ui.model.LibraryBackground
import eu.kanade.presentation.library.components.DefaultCustomGradient
import eu.kanade.presentation.library.components.parseGradientColors
import eu.kanade.presentation.library.components.previewColors
import eu.kanade.presentation.library.components.serializeGradientColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import mihon.app.di.appGraph
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.io.File
import kotlin.random.Random

/**
 * Library background (gradient presets, a custom gradient or a picked image/GIF) and animation settings.
 */
@Composable
internal fun LibraryAppearanceSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiPreferences = remember { context.appGraph.uiPreferences }
    val background by uiPreferences.libraryBackground.collectAsState()
    val imagePath by uiPreferences.libraryBackgroundImage.collectAsState()
    val gradientColorsPref by uiPreferences.libraryGradientColors.collectAsState()
    val gradientColors = remember(gradientColorsPref) { parseGradientColors(gradientColorsPref) }
    val blur by uiPreferences.libraryBackgroundBlur.collectAsState()
    val dim by uiPreferences.libraryBackgroundDim.collectAsState()
    var showGradientEditor by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                // Keep images that saved presets still point to
                val keep = UiPresets.saved(uiPreferences).mapNotNull { it.libraryBackgroundImage }.toSet()
                context.copyBackgroundImage(uri, keep)
            } ?: return@launch
            uiPreferences.libraryBackgroundImage.set(path)
            uiPreferences.libraryBackground.set(LibraryBackground.CUSTOM)
        }
    }
    val launchPicker = {
        // Image only still includes GIFs and animated WebPs
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    SettingsChipRow(MR.strings.library_background) {
        LibraryBackground.entries.forEach { option ->
            val previewColors = option.previewColors(gradientColors)
            FilterChip(
                selected = background == option,
                onClick = {
                    when {
                        option == LibraryBackground.CUSTOM && imagePath.isBlank() -> launchPicker()
                        option == LibraryBackground.CUSTOM_GRADIENT && gradientColors.isEmpty() -> {
                            uiPreferences.libraryBackground.set(option)
                            showGradientEditor = true
                        }
                        else -> uiPreferences.libraryBackground.set(option)
                    }
                },
                label = { Text(stringResource(option.titleRes)) },
                leadingIcon = previewColors?.let { colors ->
                    {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (colors.size >
                                        1
                                    ) {
                                        Brush.linearGradient(colors)
                                    } else {
                                        Brush.linearGradient(colors + colors)
                                    },
                                ),
                        )
                    }
                },
            )
        }
    }

    when (background) {
        LibraryBackground.CUSTOM -> {
            TextButton(
                onClick = launchPicker,
                modifier = Modifier.padding(horizontal = SettingsItemsPaddings.Horizontal - 12.dp),
            ) {
                Text(stringResource(MR.strings.library_background_change_image))
            }
            SliderItem(
                value = blur,
                valueRange = 0..25,
                label = stringResource(MR.strings.library_background_blur),
                onChange = uiPreferences.libraryBackgroundBlur::set,
                pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            SliderItem(
                value = dim,
                valueRange = 0..90 step 5,
                steps = 17,
                label = stringResource(MR.strings.library_background_dim),
                valueString = "$dim%",
                onChange = uiPreferences.libraryBackgroundDim::set,
                pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
        LibraryBackground.CUSTOM_GRADIENT -> {
            Row(
                modifier = Modifier.padding(horizontal = SettingsItemsPaddings.Horizontal),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gradientColors.ifEmpty { DefaultCustomGradient }.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
                TextButton(onClick = { showGradientEditor = true }) {
                    Text(stringResource(MR.strings.library_background_edit_colors))
                }
            }
        }
        else -> Unit
    }

    CheckboxItem(
        label = stringResource(MR.strings.library_background_animated),
        pref = uiPreferences.libraryBackgroundAnimated,
    )
    CheckboxItem(
        label = stringResource(MR.strings.library_background_cover_flow_adaptive),
        pref = uiPreferences.libraryCoverFlowAdaptiveBackground,
    )
    CheckboxItem(
        label = stringResource(MR.strings.library_animations),
        pref = uiPreferences.libraryAnimations,
    )

    if (showGradientEditor) {
        GradientEditorDialog(
            initialColors = gradientColors.ifEmpty { DefaultCustomGradient },
            onDismissRequest = { showGradientEditor = false },
            onConfirm = {
                uiPreferences.libraryGradientColors.set(serializeGradientColors(it))
                uiPreferences.libraryBackground.set(LibraryBackground.CUSTOM_GRADIENT)
                showGradientEditor = false
            },
        )
    }
}

private const val MAX_GRADIENT_COLORS = 5

@Composable
private fun GradientEditorDialog(
    initialColors: List<Color>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<Color>) -> Unit,
) {
    val colors = remember { initialColors.toMutableStateList() }
    var selected by remember { mutableIntStateOf(0) }
    val hsv = remember(selected, colors.size) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(colors[selected].toArgb(), it) }
    }
    var hue by remember(selected, colors.size) { mutableStateOf(hsv[0]) }
    var saturation by remember(selected, colors.size) { mutableStateOf(hsv[1]) }
    var brightness by remember(selected, colors.size) { mutableStateOf(hsv[2]) }
    val update = {
        colors[selected] = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.gradient_editor_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Live preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (colors.size >
                                1
                            ) {
                                Brush.linearGradient(colors.toList())
                            } else {
                                Brush.linearGradient(colors + colors)
                            },
                        ),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    colors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (index == selected) 3.dp else 1.dp,
                                    color = if (index == selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { selected = index },
                        )
                    }
                }
                Row {
                    if (colors.size < MAX_GRADIENT_COLORS) {
                        TextButton(
                            onClick = {
                                colors.add(randomVividColor())
                                selected = colors.lastIndex
                            },
                        ) { Text(stringResource(MR.strings.gradient_editor_add)) }
                    }
                    if (colors.size > 1) {
                        TextButton(
                            onClick = {
                                colors.removeAt(selected)
                                selected = selected.coerceAtMost(colors.lastIndex)
                            },
                        ) { Text(stringResource(MR.strings.gradient_editor_remove)) }
                    }
                    TextButton(
                        onClick = {
                            for (i in colors.indices) colors[i] = randomVividColor()
                            selected = 0
                        },
                    ) { Text(stringResource(MR.strings.gradient_editor_shuffle)) }
                }
                Text(stringResource(MR.strings.gradient_editor_hue), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = hue,
                    onValueChange = {
                        hue = it
                        update()
                    },
                    valueRange = 0f..360f,
                )
                Text(stringResource(MR.strings.gradient_editor_saturation), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = saturation,
                    onValueChange = {
                        saturation = it
                        update()
                    },
                )
                Text(stringResource(MR.strings.gradient_editor_brightness), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        update()
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(colors.toList()) }) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

private fun randomVividColor(): Color = Color(
    android.graphics.Color.HSVToColor(
        floatArrayOf(Random.nextFloat() * 360f, 0.55f + Random.nextFloat() * 0.4f, 0.75f + Random.nextFloat() * 0.25f),
    ),
)

/**
 * Copies the picked image into app storage so it survives the picker's temporary permission.
 */
private fun Context.copyBackgroundImage(uri: Uri, keep: Set<String>): String? {
    return try {
        val dir = File(filesDir, "library_background").apply { mkdirs() }
        dir.listFiles()?.filterNot { it.absolutePath in keep }?.forEach { it.delete() }
        // Unique name so cached decodes of a previous image are never reused
        val file = File(dir, "background_${System.currentTimeMillis()}")
        val copied = contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        if (copied == null) null else file.absolutePath
    } catch (e: Exception) {
        logcat(LogPriority.ERROR, e) { "Failed to copy library background" }
        null
    }
}

package eu.kanade.presentation.library

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.model.LibraryBackground
import eu.kanade.presentation.library.components.previewColors
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
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import java.io.File

/**
 * Library background (gradient presets or a picked image) and animation settings.
 */
@Composable
internal fun LibraryAppearanceSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiPreferences = remember { context.appGraph.uiPreferences }
    val background by uiPreferences.libraryBackground.collectAsState()
    val imagePath by uiPreferences.libraryBackgroundImage.collectAsState()

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) { context.copyBackgroundImage(uri) } ?: return@launch
            uiPreferences.libraryBackgroundImage.set(path)
            uiPreferences.libraryBackground.set(LibraryBackground.CUSTOM)
        }
    }
    val launchPicker = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    SettingsChipRow(MR.strings.library_background) {
        LibraryBackground.entries.forEach { option ->
            val previewColors = option.previewColors()
            FilterChip(
                selected = background == option,
                onClick = {
                    if (option == LibraryBackground.CUSTOM && imagePath.isBlank()) {
                        launchPicker()
                    } else {
                        uiPreferences.libraryBackground.set(option)
                    }
                },
                label = { Text(stringResource(option.titleRes)) },
                leadingIcon = previewColors?.let { colors ->
                    {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(colors)),
                        )
                    }
                },
            )
        }
    }
    if (background == LibraryBackground.CUSTOM) {
        TextButton(
            onClick = launchPicker,
            modifier = Modifier.padding(horizontal = SettingsItemsPaddings.Horizontal - 12.dp),
        ) {
            Text(stringResource(MR.strings.library_background_change_image))
        }
    }
    CheckboxItem(
        label = stringResource(MR.strings.library_animations),
        pref = uiPreferences.libraryAnimations,
    )
}

/**
 * Copies the picked image into app storage so it survives the picker's temporary permission.
 */
private fun Context.copyBackgroundImage(uri: Uri): String? {
    return try {
        val dir = File(filesDir, "library_background").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
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

package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AppFont
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.GlassStyle
import eu.kanade.domain.ui.model.LibraryBackground
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A named snapshot of the app's look. Unset fields leave the current value alone when applied, so the built-in
 * presets don't wipe someone's picked image or custom gradient.
 */
@Serializable
data class UiPreset(
    val name: String,
    val appTheme: AppTheme? = null,
    val glassStyle: GlassStyle? = null,
    val accentColor: Int? = null,
    val appFont: AppFont? = null,
    val amoled: Boolean? = null,
    val libraryBackground: LibraryBackground? = null,
    val libraryBackgroundImage: String? = null,
    val libraryGradientColors: String? = null,
    val libraryBackgroundBlur: Int? = null,
    val libraryBackgroundDim: Int? = null,
    val libraryBackgroundAnimated: Boolean? = null,
    val libraryCoverFlowAdaptiveBackground: Boolean? = null,
    val libraryAnimations: Boolean? = null,
    val builtIn: Boolean = false,
)

object UiPresets {

    val builtIn = listOf(
        UiPreset(
            name = "Liquid Glass",
            appTheme = AppTheme.LIQUID_GLASS,
            glassStyle = GlassStyle.LIQUID_GLASS,
            accentColor = 0,
            appFont = AppFont.DEFAULT,
            libraryBackground = LibraryBackground.AURORA,
            libraryBackgroundAnimated = true,
            builtIn = true,
        ),
        UiPreset(
            name = "Frutiger Aero",
            appTheme = AppTheme.FRUTIGER_AERO,
            glassStyle = GlassStyle.FRUTIGER_AERO,
            accentColor = 0,
            appFont = AppFont.OPEN_SANS,
            libraryBackground = LibraryBackground.AERO,
            libraryBackgroundAnimated = true,
            builtIn = true,
        ),
        UiPreset(
            name = "Classic",
            appTheme = AppTheme.DEFAULT,
            glassStyle = GlassStyle.OFF,
            accentColor = 0,
            appFont = AppFont.DEFAULT,
            libraryBackground = LibraryBackground.NONE,
            builtIn = true,
        ),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun saved(preferences: UiPreferences): List<UiPreset> {
        val raw = preferences.uiPresets.get()
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<UiPreset>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(preferences: UiPreferences, name: String) {
        val preset = snapshot(preferences, name)
        val presets = saved(preferences).filterNot { it.name == name } + preset
        preferences.uiPresets.set(json.encodeToString(presets))
    }

    fun delete(preferences: UiPreferences, name: String) {
        val presets = saved(preferences).filterNot { it.name == name }
        preferences.uiPresets.set(json.encodeToString(presets))
    }

    fun snapshot(preferences: UiPreferences, name: String) = UiPreset(
        name = name,
        appTheme = preferences.appTheme.get(),
        glassStyle = preferences.glassStyle.get(),
        accentColor = preferences.accentColor.get(),
        appFont = preferences.appFont.get(),
        amoled = preferences.themeDarkAmoled.get(),
        libraryBackground = preferences.libraryBackground.get(),
        libraryBackgroundImage = preferences.libraryBackgroundImage.get(),
        libraryGradientColors = preferences.libraryGradientColors.get(),
        libraryBackgroundBlur = preferences.libraryBackgroundBlur.get(),
        libraryBackgroundDim = preferences.libraryBackgroundDim.get(),
        libraryBackgroundAnimated = preferences.libraryBackgroundAnimated.get(),
        libraryCoverFlowAdaptiveBackground = preferences.libraryCoverFlowAdaptiveBackground.get(),
        libraryAnimations = preferences.libraryAnimations.get(),
    )

    fun apply(preferences: UiPreferences, preset: UiPreset) {
        with(preferences) {
            preset.appTheme?.let(appTheme::set)
            preset.glassStyle?.let(glassStyle::set)
            preset.accentColor?.let(accentColor::set)
            preset.appFont?.let(appFont::set)
            preset.amoled?.let(themeDarkAmoled::set)
            preset.libraryBackgroundImage?.let(libraryBackgroundImage::set)
            preset.libraryGradientColors?.let(libraryGradientColors::set)
            // An image preset whose file is gone falls back to no background
            preset.libraryBackground?.let { background ->
                val imageMissing = libraryBackgroundImage.get().let { it.isBlank() || !File(it).exists() }
                libraryBackground.set(
                    if (background == LibraryBackground.CUSTOM && imageMissing) LibraryBackground.NONE else background,
                )
            }
            preset.libraryBackgroundBlur?.let(libraryBackgroundBlur::set)
            preset.libraryBackgroundDim?.let(libraryBackgroundDim::set)
            preset.libraryBackgroundAnimated?.let(libraryBackgroundAnimated::set)
            preset.libraryCoverFlowAdaptiveBackground?.let(libraryCoverFlowAdaptiveBackground::set)
            preset.libraryAnimations?.let(libraryAnimations::set)
        }
    }
}

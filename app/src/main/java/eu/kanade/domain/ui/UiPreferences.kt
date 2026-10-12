package eu.kanade.domain.ui

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.domain.ui.model.AppFont
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.GlassStyle
import eu.kanade.domain.ui.model.LibraryBackground
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Inject
@SingleIn(AppScope::class)
class UiPreferences(
    preferenceStore: PreferenceStore,
) {

    val themeMode: Preference<ThemeMode> = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    val appTheme: Preference<AppTheme> = preferenceStore.getEnum(
        "pref_app_theme",
        AppTheme.LIQUID_GLASS,
    )

    val themeDarkAmoled: Preference<Boolean> = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    val glassStyle: Preference<GlassStyle> = preferenceStore.getEnum("pref_glass_style", GlassStyle.LIQUID_GLASS)

    /** ARGB accent color overriding the theme's, 0 for the theme's own. */
    val accentColor: Preference<Int> = preferenceStore.getInt("pref_accent_color", 0)

    val appFont: Preference<AppFont> = preferenceStore.getEnum("pref_app_font", AppFont.DEFAULT)

    val libraryBackground: Preference<LibraryBackground> = preferenceStore.getEnum(
        "pref_library_background",
        LibraryBackground.AURORA,
    )

    val libraryBackgroundImage: Preference<String> = preferenceStore.getString("pref_library_background_image", "")

    val libraryAnimations: Preference<Boolean> = preferenceStore.getBoolean("pref_library_animations", true)

    val libraryBackgroundAnimated: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_library_background_animated",
        true,
    )

    /** Comma separated ARGB hex colors of the custom gradient. */
    val libraryGradientColors: Preference<String> = preferenceStore.getString("pref_library_gradient_colors", "")

    /** Blur radius in dp applied to an image background. */
    val libraryBackgroundBlur: Preference<Int> = preferenceStore.getInt("pref_library_background_blur", 0)

    val libraryCoverFlowAdaptiveBackground: Preference<Boolean> = preferenceStore.getBoolean(
        "pref_library_cover_flow_adaptive_background",
        true,
    )

    val relativeTime: Preference<Boolean> = preferenceStore.getBoolean("relative_time_v2", true)

    val dateFormat: Preference<String> = preferenceStore.getString("app_date_format", "")

    val tabletUiMode: Preference<TabletUiMode> = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    val imagesInDescription: Preference<Boolean> = preferenceStore.getBoolean("pref_render_images_description", true)

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}

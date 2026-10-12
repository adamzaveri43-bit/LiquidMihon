package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

enum class LibraryBackground(val titleRes: StringResource) {
    NONE(MR.strings.library_background_none),
    AURORA(MR.strings.library_background_aurora),
    SUNSET(MR.strings.library_background_sunset),
    OCEAN(MR.strings.library_background_ocean),
    FOREST(MR.strings.library_background_forest),
    NEBULA(MR.strings.library_background_nebula),
    CUSTOM_GRADIENT(MR.strings.library_background_custom_gradient),
    CUSTOM(MR.strings.library_background_custom),
}

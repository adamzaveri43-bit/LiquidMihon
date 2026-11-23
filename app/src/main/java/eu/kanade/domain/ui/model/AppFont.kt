package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

enum class AppFont(val titleRes: StringResource) {
    DEFAULT(MR.strings.label_default),
    INTER(MR.strings.font_inter),
    NUNITO(MR.strings.font_nunito),
    LEXEND(MR.strings.font_lexend),
    QUICKSAND(MR.strings.font_quicksand),
    COMFORTAA(MR.strings.font_comfortaa),
    OPEN_SANS(MR.strings.font_open_sans),
    ATKINSON_HYPERLEGIBLE(MR.strings.font_atkinson_hyperlegible),
    SERIF(MR.strings.font_serif),
    MONOSPACE(MR.strings.font_monospace),
}

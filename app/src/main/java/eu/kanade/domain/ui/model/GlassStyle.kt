package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

enum class GlassStyle(val titleRes: StringResource) {
    OFF(MR.strings.glass_style_off),
    LIQUID_GLASS(MR.strings.glass_style_liquid_glass),
    FRUTIGER_AERO(MR.strings.glass_style_frutiger_aero),
}

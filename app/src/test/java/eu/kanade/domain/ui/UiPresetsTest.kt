package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AppFont
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.GlassStyle
import eu.kanade.domain.ui.model.LibraryBackground
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.core.common.preference.InMemoryPreferenceStore

@Execution(ExecutionMode.CONCURRENT)
class UiPresetsTest {

    private fun preferences() = UiPreferences(InMemoryPreferenceStore())

    @Test
    fun `Saved preset restores every setting`() {
        val prefs = preferences()
        prefs.appTheme.set(AppTheme.NORD)
        prefs.glassStyle.set(GlassStyle.FRUTIGER_AERO)
        prefs.accentColor.set(0xFFFF2D55.toInt())
        prefs.appFont.set(AppFont.LEXEND)
        prefs.libraryBackground.set(LibraryBackground.CUSTOM_GRADIENT)
        prefs.libraryGradientColors.set("FF112233,FF445566")
        prefs.libraryBackgroundDim.set(70)
        UiPresets.save(prefs, "Mine")

        UiPresets.apply(prefs, UiPresets.builtIn.first { it.name == "Classic" })
        prefs.glassStyle.get() shouldBe GlassStyle.OFF

        UiPresets.apply(prefs, UiPresets.saved(prefs).single())
        prefs.appTheme.get() shouldBe AppTheme.NORD
        prefs.glassStyle.get() shouldBe GlassStyle.FRUTIGER_AERO
        prefs.accentColor.get() shouldBe 0xFFFF2D55.toInt()
        prefs.appFont.get() shouldBe AppFont.LEXEND
        prefs.libraryBackground.get() shouldBe LibraryBackground.CUSTOM_GRADIENT
        prefs.libraryGradientColors.get() shouldBe "FF112233,FF445566"
        prefs.libraryBackgroundDim.get() shouldBe 70
    }

    @Test
    fun `Built-in presets keep custom gradient colors`() {
        val prefs = preferences()
        prefs.libraryGradientColors.set("FF112233")

        UiPresets.apply(prefs, UiPresets.builtIn.first { it.name == "Frutiger Aero" })

        prefs.libraryGradientColors.get() shouldBe "FF112233"
        prefs.libraryBackground.get() shouldBe LibraryBackground.AERO
    }

    @Test
    fun `Image preset without its image falls back to no background`() {
        val prefs = preferences()
        prefs.libraryBackground.set(LibraryBackground.CUSTOM)
        prefs.libraryBackgroundImage.set("/does/not/exist")
        UiPresets.save(prefs, "Gone")

        UiPresets.apply(prefs, UiPresets.builtIn.first { it.name == "Liquid Glass" })
        UiPresets.apply(prefs, UiPresets.saved(prefs).single())

        prefs.libraryBackground.get() shouldBe LibraryBackground.NONE
    }

    @Test
    fun `Deleting a preset removes only that one`() {
        val prefs = preferences()
        UiPresets.save(prefs, "A")
        UiPresets.save(prefs, "B")

        UiPresets.delete(prefs, "A")

        UiPresets.saved(prefs).map { it.name } shouldBe listOf("B")
    }
}

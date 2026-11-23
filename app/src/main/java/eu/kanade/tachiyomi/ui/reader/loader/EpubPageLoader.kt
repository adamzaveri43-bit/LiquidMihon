package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.book.BookPage
import eu.kanade.tachiyomi.ui.reader.loader.book.BookPageRenderer
import eu.kanade.tachiyomi.ui.reader.loader.book.BookPageStyle
import eu.kanade.tachiyomi.ui.reader.loader.book.EpubBookParser
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.readerBackgroundColor
import mihon.core.archive.EpubReader
import java.util.Locale

/**
 * Loader used to load a chapter from a .epub file.
 *
 * Comics packed as epubs are read as their images. Books are laid out with the reader's book settings into
 * pages the size of the screen, with their illustrations on pages of their own.
 */
internal class EpubPageLoader(
    private val reader: EpubReader,
    private val context: Context,
    private val readerPreferences: ReaderPreferences,
) : PageLoader() {

    override var isLocal: Boolean = true

    override suspend fun getPages(): List<ReaderPage> {
        val book = EpubBookParser(reader).parse()
        if (!book.isTextBook) return getImagePages()

        val renderer = BookPageRenderer(bookPageStyle(book.language)) { reader.getInputStream(it) }
        val pages = renderer.paginate(book.blocks)
        return pages.mapIndexed { i, page ->
            ReaderPage(i).apply {
                stream = when (page) {
                    is BookPage.Image -> {
                        { reader.getInputStream(page.path)!! }
                    }
                    is BookPage.Text -> {
                        { renderer.render(i, page) }
                    }
                }
                status = Page.State.Ready
            }
        }
    }

    private fun getImagePages(): List<ReaderPage> {
        return reader.getImagesFromPages().mapIndexed { i, path ->
            ReaderPage(i).apply {
                stream = { reader.getInputStream(path)!! }
                status = Page.State.Ready
            }
        }
    }

    private fun bookPageStyle(language: String?): BookPageStyle {
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val background = context.readerBackgroundColor(readerPreferences.readerTheme.get())
        val isDark = ColorUtils.calculateLuminance(background) < 0.5
        return BookPageStyle(
            pageWidth = width,
            pageHeight = height,
            textSize = readerPreferences.bookTextSize.get().coerceIn(8, 48) * metrics.scaledDensity(),
            lineSpacing = readerPreferences.bookLineSpacing.get().coerceIn(100, 250) / 100f,
            horizontalMargin = (width * readerPreferences.bookMargins.get().coerceIn(0, 25) / 100f).toInt(),
            verticalMargin = (height * VERTICAL_MARGIN).toInt(),
            typeface = when (readerPreferences.bookFont.get()) {
                ReaderPreferences.BookFont.SERIF -> Typeface.SERIF
                ReaderPreferences.BookFont.SANS_SERIF -> Typeface.SANS_SERIF
                ReaderPreferences.BookFont.MONOSPACE -> Typeface.MONOSPACE
            },
            justify = readerPreferences.bookJustify.get(),
            // Softer than pure white or black, like print
            textColor = if (isDark) Color.rgb(0xDA, 0xDA, 0xDA) else Color.rgb(0x1E, 0x1E, 0x1E),
            backgroundColor = background,
            density = metrics.density,
            locale = language?.let(Locale::forLanguageTag)?.takeIf { it.language.isNotEmpty() },
        )
    }

    private fun android.util.DisplayMetrics.scaledDensity(): Float {
        // scaledDensity is deprecated in favour of font scaling curves; a linear scale is what pages need here
        return density * context.resources.configuration.fontScale
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        super.recycle()
        reader.close()
    }

    private companion object {
        const val VERTICAL_MARGIN = 0.05f
    }
}

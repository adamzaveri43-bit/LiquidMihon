package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.book.BookPage
import eu.kanade.tachiyomi.ui.reader.loader.book.BookPageRenderer
import eu.kanade.tachiyomi.ui.reader.loader.book.BookPageStyle
import eu.kanade.tachiyomi.ui.reader.loader.book.EpubBook
import eu.kanade.tachiyomi.ui.reader.loader.book.EpubBookParser
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.system.readerBackgroundColor
import kotlinx.coroutines.CancellationException
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
        if (isDrmProtected()) {
            throw Exception(
                "This EPUB is copy-protected (DRM), so only the store's own app can open it. " +
                    "DRM-free EPUBs (Standard Ebooks, Project Gutenberg, many indie shops) open here.",
            )
        }
        val book = try {
            EpubBookParser(reader).parse()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Can't be read as a book (unusual packaging): show whatever images it has, if any
            return getImagePages().ifEmpty {
                throw Exception("This EPUB couldn't be read: ${e.message ?: e::class.simpleName}", e)
            }
        }
        if (!book.isTextBook) {
            return getImagePages().ifEmpty {
                if (book.blocks.isEmpty()) throw Exception("This EPUB has no readable pages.")
                textPages(book)
            }
        }
        return textPages(book)
    }

    private fun textPages(book: EpubBook): List<ReaderPage> {
        val renderer = BookPageRenderer(bookPageStyle(book.language)) { reader.getInputStream(it) }
        val pages = renderer.paginate(book.blocks)
        return pages.mapIndexed { i, page ->
            ReaderPage(i).apply {
                stream = when (page) {
                    is BookPage.Image -> {
                        {
                            reader.getInputStream(page.path)
                                ?: throw Exception("This picture is missing from the EPUB (${page.path}).")
                        }
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
        val images = try {
            reader.getImagesFromPages()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        return images.mapIndexed { i, path ->
            ReaderPage(i).apply {
                stream = { reader.getInputStream(path) ?: throw Exception("This picture is missing from the EPUB.") }
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

    /**
     * Store-bought EPUBs are usually encrypted (Adobe, Readium LCP, Apple, Kobo): their content files can't be
     * read without the store's keys. Fonts are sometimes obfuscated through encryption.xml too; that's fine.
     */
    private fun isDrmProtected(): Boolean {
        if (reader.getInputStream("META-INF/rights.xml")?.use { true } == true) return true
        val encryption = reader.getInputStream("META-INF/encryption.xml")?.use { it.reader().readText() }
            ?: return false
        val encrypted = CIPHER_REFERENCE.findAll(encryption).map { it.groupValues[1].lowercase() }.toList()
        return encrypted.any { uri -> FONT_EXTENSIONS.none { uri.endsWith(it) } }
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
        val FONT_EXTENSIONS = listOf(".ttf", ".otf", ".woff", ".woff2")
        val CIPHER_REFERENCE = Regex("""CipherReference[^>]*URI\s*=\s*"([^"]+)"""")
    }
}

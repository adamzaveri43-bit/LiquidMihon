package eu.kanade.tachiyomi.ui.reader.loader.book

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How a book's text pages look.
 *
 * @param textSize body text size, in pixels.
 * @param lineSpacing line height as a multiple of the text size's natural line height.
 * @param horizontalMargin space at the left and right of the text, in pixels.
 * @param verticalMargin space above and below the text, in pixels.
 * @param density pixels per CSS pixel, to size images the way the book intends.
 */
internal data class BookPageStyle(
    val pageWidth: Int,
    val pageHeight: Int,
    val textSize: Float,
    val lineSpacing: Float,
    val horizontalMargin: Int,
    val verticalMargin: Int,
    val typeface: Typeface,
    val justify: Boolean,
    val textColor: Int,
    val backgroundColor: Int,
    val density: Float,
    val locale: Locale?,
) {
    val contentWidth: Int get() = (pageWidth - horizontalMargin * 2).coerceAtLeast(1)
    val contentHeight: Int get() = (pageHeight - verticalMargin * 2).coerceAtLeast(1)
}

internal sealed interface BookPage {
    /** A page showing one of the book's images on its own, as it is. */
    data class Image(val path: String) : BookPage

    /** A page of text, with any small images placed in it. */
    class Text(val items: List<BookPageItem>) : BookPage
}

internal sealed interface BookPageItem {
    /** Lines [startLine] until [endLine] of [layout], drawn with their top at [y] of the content area. */
    class Lines(val layout: StaticLayout, val startLine: Int, val endLine: Int, val x: Float, val y: Float) :
        BookPageItem

    class InlineImage(val path: String, val bounds: RectF) : BookPageItem
}

/**
 * Lays a book's text out into pages the size of the screen, then draws those pages as images for the reader.
 *
 * Laying out the whole book up front gives the reader its page count; drawing happens per page, when the
 * reader asks for it.
 */
internal class BookPageRenderer(
    private val style: BookPageStyle,
    private val openImage: (String) -> InputStream?,
) {

    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = style.textColor
        textSize = style.textSize
        typeface = style.typeface
        style.locale?.let { textLocale = it }
    }

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // Layouts share paints that aren't safe to draw with from several threads, and the reader loads
    // pages in parallel
    private val drawLock = Any()

    // The reader may open a page's stream more than once (to check its type, then to decode it)
    private val rendered = LruCache<Int, ByteArray>(4)

    fun paginate(blocks: List<BookBlock>): List<BookPage> {
        val pages = mutableListOf<BookPage>()
        var items = mutableListOf<BookPageItem>()
        var y = 0f
        val contentHeight = style.contentHeight.toFloat()
        val bodyLineHeight = bodyPaint.fontSpacing * style.lineSpacing

        fun newPage() {
            if (items.isNotEmpty()) pages += BookPage.Text(items)
            items = mutableListOf()
            y = 0f
        }

        blocks.forEach { block ->
            when (block) {
                BookBlock.PageBreak -> newPage()
                is BookBlock.Image -> {
                    val size = imageSize(block.path)
                    if (size == null) {
                        // Can't be measured (an SVG, say): give it a page and let the reader decode it
                        newPage()
                        pages += BookPage.Image(block.path)
                        return@forEach
                    }
                    val width = min(size.first * style.density, style.contentWidth.toFloat())
                    val height = width * size.second / size.first
                    if (height > contentHeight * INLINE_IMAGE_MAX_HEIGHT) {
                        // Illustrations and full page art are shown on a page of their own, like a comic page
                        newPage()
                        pages += BookPage.Image(block.path)
                    } else {
                        val spacing = if (y > 0f) style.textSize * PARAGRAPH_GAP_EM else 0f
                        if (y + spacing + height > contentHeight) newPage()
                        val top = if (y > 0f) y + spacing else 0f
                        val left = (style.contentWidth - width) / 2f
                        items += BookPageItem.InlineImage(block.path, RectF(left, top, left + width, top + height))
                        y = top + height
                    }
                }
                is BookBlock.Text -> {
                    val indent = (block.indentEm * style.textSize).roundToInt()
                        .coerceAtMost(style.contentWidth / 2)
                    val layout = layoutText(block, style.contentWidth - indent)
                    if (layout.lineCount == 0) return@forEach
                    val spacing = if (y > 0f) block.spacingEm * style.textSize else 0f
                    // Keep a heading together with the start of what it heads
                    val needed = if (block.isHeading) layout.height + bodyLineHeight * 2 else lineHeight(layout, 0)
                    if (y > 0f && y + spacing + min(needed, contentHeight) > contentHeight) {
                        newPage()
                    } else {
                        y += spacing
                    }
                    var line = 0
                    while (line < layout.lineCount) {
                        val top = layout.getLineTop(line)
                        var end = line
                        while (end < layout.lineCount && y + (layout.getLineBottom(end) - top) <= contentHeight) {
                            end++
                        }
                        if (end == line) {
                            if (items.isNotEmpty()) {
                                newPage()
                                continue
                            }
                            // Taller than a page on its own: show it cut off rather than never
                            end = line + 1
                        }
                        items += BookPageItem.Lines(layout, line, end, indent.toFloat(), y)
                        y += layout.getLineBottom(end - 1) - top
                        line = end
                        if (line < layout.lineCount) newPage()
                    }
                }
            }
        }
        newPage()
        return pages
    }

    /** Draws text page [index] as a PNG image. */
    fun render(index: Int, page: BookPage.Text): InputStream {
        rendered.get(index)?.let { return ByteArrayInputStream(it) }
        val bytes = synchronized(drawLock) {
            rendered.get(index) ?: draw(page).also { rendered.put(index, it) }
        }
        return ByteArrayInputStream(bytes)
    }

    private fun draw(page: BookPage.Text): ByteArray {
        val bitmap = Bitmap.createBitmap(style.pageWidth, style.pageHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(style.backgroundColor)
            canvas.translate(style.horizontalMargin.toFloat(), style.verticalMargin.toFloat())
            page.items.forEach { item ->
                when (item) {
                    is BookPageItem.Lines -> drawLines(canvas, item)
                    is BookPageItem.InlineImage -> drawImage(canvas, item)
                }
            }
            return ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawLines(canvas: Canvas, item: BookPageItem.Lines) {
        val layout = item.layout
        val top = layout.getLineTop(item.startLine).toFloat()
        val bottom = layout.getLineBottom(item.endLine - 1).toFloat()
        canvas.save()
        canvas.translate(item.x, item.y - top)
        canvas.clipRect(0f, top, layout.width.toFloat(), bottom)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawImage(canvas: Canvas, item: BookPageItem.InlineImage) {
        val size = imageSize(item.path) ?: return
        val options = BitmapFactory.Options().apply {
            // Decoding at about the drawn size saves memory on large images drawn small
            inSampleSize = max(1, min(size.first / item.bounds.width(), size.second / item.bounds.height()).toInt())
        }
        val image = openImage(item.path)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return
        canvas.drawBitmap(image, null, item.bounds, imagePaint)
        image.recycle()
    }

    private fun layoutText(block: BookBlock.Text, width: Int): StaticLayout {
        val paint = if (block.sizeScale == 1f) {
            bodyPaint
        } else {
            TextPaint(bodyPaint).apply { textSize = style.textSize * block.sizeScale }
        }
        val justify = style.justify && block.alignment == BookAlignment.Default && !block.isHeading
        return StaticLayout.Builder.obtain(block.text, 0, block.text.length, paint, width.coerceAtLeast(1))
            .setAlignment(block.alignment.toLayoutAlignment())
            .setLineSpacing(0f, style.lineSpacing)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .setJustificationMode(
                if (justify) Layout.JUSTIFICATION_MODE_INTER_WORD else Layout.JUSTIFICATION_MODE_NONE,
            )
            .build()
    }

    private fun lineHeight(layout: StaticLayout, line: Int): Float =
        (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat()

    private val imageSizes = HashMap<String, Pair<Int, Int>?>()

    private fun imageSize(path: String): Pair<Int, Int>? = synchronized(imageSizes) {
        imageSizes.getOrPut(path) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openImage(path)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
        }
    }

    private companion object {
        /** Images taller than this part of the page get a page of their own. */
        const val INLINE_IMAGE_MAX_HEIGHT = 0.45f

        const val PARAGRAPH_GAP_EM = 0.6f
    }
}

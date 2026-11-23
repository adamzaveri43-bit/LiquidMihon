package eu.kanade.tachiyomi.ui.reader.loader.book

import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import mihon.core.archive.EpubReader
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * The readable content of an epub: its text, split in styled blocks, and the images placed between them.
 */
internal class EpubBook(
    val blocks: List<BookBlock>,
    val language: String?,
    private val textLength: Int,
    private val imageCount: Int,
) {
    /**
     * Whether this is a book to read as text. Comics packed as epubs are pages of images with little or no
     * text, and keep being shown as those images.
     */
    val isTextBook: Boolean
        get() = textLength >= MIN_BOOK_TEXT && textLength / imageCount.coerceAtLeast(1) >= MIN_TEXT_PER_IMAGE

    private companion object {
        const val MIN_BOOK_TEXT = 1000
        const val MIN_TEXT_PER_IMAGE = 300
    }
}

internal sealed interface BookBlock {

    /**
     * A paragraph-like run of text.
     *
     * @param sizeScale text size relative to the body text.
     * @param indentEm start indent, in multiples of the text size.
     * @param spacingEm space above, in multiples of the text size.
     */
    data class Text(
        val text: CharSequence,
        val sizeScale: Float = 1f,
        val alignment: BookAlignment = BookAlignment.Default,
        val indentEm: Float = 0f,
        val spacingEm: Float = PARAGRAPH_SPACING_EM,
        val isHeading: Boolean = false,
    ) : BookBlock

    /** An image, as a zip path inside the epub. */
    data class Image(val path: String) : BookBlock

    /** Start of a new section of the book, which starts on a new page. */
    data object PageBreak : BookBlock
}

internal enum class BookAlignment {
    /** Justified or ragged as the reader prefers. */
    Default,
    Start,
    Center,
    End,
    ;

    fun toLayoutAlignment(): Layout.Alignment = when (this) {
        Default, Start -> Layout.Alignment.ALIGN_NORMAL
        Center -> Layout.Alignment.ALIGN_CENTER
        End -> Layout.Alignment.ALIGN_OPPOSITE
    }
}

private const val PARAGRAPH_SPACING_EM = 0.6f

/**
 * Reads the content documents of an epub, in reading order, into [EpubBook] blocks. CSS isn't applied: books
 * are shown with the reader's own typography, keeping only structure (headings, quotes, lists) and inline
 * emphasis.
 */
internal class EpubBookParser(private val epub: EpubReader) {

    private val blocks = mutableListOf<BookBlock>()
    private var text = SpannableStringBuilder()
    private var textLength = 0
    private var imageCount = 0

    fun parse(): EpubBook {
        epub.getPageDocumentPaths().forEach { path ->
            // A chapter that can't be read is skipped rather than failing the whole book
            val document = runCatching { epub.getInputStream(path)?.use { Jsoup.parse(it, null, "") } }
                .getOrNull() ?: return@forEach
            if (blocks.isNotEmpty() && blocks.last() != BookBlock.PageBreak) {
                blocks += BookBlock.PageBreak
            }
            val body = document.body()
            walkChildren(body, BlockStyle(), path)
            flush(BlockStyle())
        }
        while (blocks.lastOrNull() == BookBlock.PageBreak) blocks.removeAt(blocks.lastIndex)
        return EpubBook(
            blocks = blocks.toList(),
            language = runCatching { epub.getLanguage() }.getOrNull(),
            textLength = textLength,
            imageCount = imageCount,
        )
    }

    private data class BlockStyle(
        val sizeScale: Float = 1f,
        val bold: Boolean = false,
        val alignment: BookAlignment = BookAlignment.Default,
        val indentEm: Float = 0f,
        val spacingEm: Float = PARAGRAPH_SPACING_EM,
        val isHeading: Boolean = false,
        val preformatted: Boolean = false,
    )

    private fun walkChildren(element: Element, style: BlockStyle, documentPath: String) {
        var listNumber = element.attr("start").toIntOrNull() ?: 1
        element.childNodes().forEach { node ->
            if (element.normalName() == "ol" && node is Element && node.normalName() == "li") {
                walk(node, style, documentPath, listMarker = "${listNumber++}. ")
            } else {
                walk(node, style, documentPath, listMarker = if (element.normalName() == "ul") "• " else null)
            }
        }
    }

    private fun walk(node: Node, style: BlockStyle, documentPath: String, listMarker: String? = null) {
        when (node) {
            is TextNode -> appendText(node, style)
            is Element -> walkElement(node, style, documentPath, listMarker)
        }
    }

    private fun walkElement(element: Element, style: BlockStyle, documentPath: String, listMarker: String?) {
        val tag = element.normalName()
        when {
            tag in IGNORED_TAGS -> return
            tag == "br" -> text.append('\n')
            tag == "img" -> addImage(element.attr("src"), style, documentPath)
            tag == "image" -> {
                val href = element.attr("xlink:href").ifEmpty { element.attr("href") }
                addImage(href, style, documentPath)
            }
            tag == "hr" -> {
                flush(style)
                addText(SEPARATOR, BlockStyle(alignment = BookAlignment.Center, spacingEm = 1f))
            }
            tag in BLOCK_TAGS -> {
                flush(style)
                val blockStyle = blockStyleFor(element, tag, style)
                if (tag == "li" && listMarker != null) text.append(listMarker)
                walkChildren(element, blockStyle, documentPath)
                flush(blockStyle)
            }
            else -> {
                val start = text.length
                walkChildren(element, style, documentPath)
                applyInlineStyle(tag, start, text.length)
                // Table cells are kept on their row's line, apart
                if (tag == "td" || tag == "th") text.append(' ')
            }
        }
    }

    private fun blockStyleFor(element: Element, tag: String, parent: BlockStyle): BlockStyle {
        var style = when (tag) {
            "h1" -> parent.copy(sizeScale = 1.5f, bold = true, isHeading = true, spacingEm = 1.4f)
            "h2" -> parent.copy(sizeScale = 1.35f, bold = true, isHeading = true, spacingEm = 1.3f)
            "h3" -> parent.copy(sizeScale = 1.2f, bold = true, isHeading = true, spacingEm = 1.2f)
            "h4" -> parent.copy(sizeScale = 1.1f, bold = true, isHeading = true, spacingEm = 1.1f)
            "h5", "h6" -> parent.copy(bold = true, isHeading = true, spacingEm = 1f)
            "blockquote" -> parent.copy(indentEm = parent.indentEm + 1.5f)
            "ul", "ol", "dd" -> parent.copy(indentEm = parent.indentEm + 1f)
            "pre" -> parent.copy(preformatted = true, alignment = BookAlignment.Start)
            "figcaption", "caption" -> parent.copy(
                sizeScale = parent.sizeScale * 0.9f,
                alignment = BookAlignment.Center,
            )
            "center" -> parent.copy(alignment = BookAlignment.Center)
            "li" -> parent.copy(spacingEm = PARAGRAPH_SPACING_EM / 2)
            else -> parent.copy(spacingEm = PARAGRAPH_SPACING_EM)
        }
        alignmentOf(element)?.let { style = style.copy(alignment = it) }
        return style
    }

    private fun alignmentOf(element: Element): BookAlignment? {
        val declared = element.attr("style")
            .split(';')
            .map { it.split(':', limit = 2) }
            .firstOrNull { it.size == 2 && it[0].trim().equals("text-align", ignoreCase = true) }
            ?.get(1)
            ?.trim()
            ?.lowercase()
            ?: element.attr("align").lowercase().ifEmpty { null }
            ?: return null
        return when (declared) {
            "center" -> BookAlignment.Center
            "right", "end" -> BookAlignment.End
            "left", "start" -> BookAlignment.Start
            else -> BookAlignment.Default
        }
    }

    private fun applyInlineStyle(tag: String, start: Int, end: Int) {
        if (start >= end) return
        val span: Any = when (tag) {
            "b", "strong" -> StyleSpan(Typeface.BOLD)
            "i", "em", "cite", "var", "dfn" -> StyleSpan(Typeface.ITALIC)
            "u", "ins" -> UnderlineSpan()
            "s", "strike", "del" -> StrikethroughSpan()
            "small" -> RelativeSizeSpan(0.85f)
            "big" -> RelativeSizeSpan(1.15f)
            "code", "tt", "kbd", "samp" -> TypefaceSpan("monospace")
            "sup" -> {
                text.setSpan(RelativeSizeSpan(0.7f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                SuperscriptSpan()
            }
            "sub" -> {
                text.setSpan(RelativeSizeSpan(0.7f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                SubscriptSpan()
            }
            else -> return
        }
        text.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun appendText(node: TextNode, style: BlockStyle) {
        if (style.preformatted) {
            text.append(node.wholeText)
            return
        }
        // HTML collapses runs of whitespace, also across elements
        val collapsed = WHITESPACE.replace(node.wholeText, " ")
        if (collapsed.isEmpty()) return
        val endsWithSpace = text.isEmpty() || text.last().isWhitespace()
        text.append(if (endsWithSpace) collapsed.trimStart() else collapsed)
    }

    private fun addImage(src: String, style: BlockStyle, documentPath: String) {
        if (src.isBlank() || src.startsWith("data:")) return
        flush(style)
        blocks += BookBlock.Image(epub.resolveRelativeTo(documentPath, src))
        imageCount++
    }

    private fun flush(style: BlockStyle) {
        val current = text
        text = SpannableStringBuilder()
        var start = 0
        var end = current.length
        while (start < end && current[start].isWhitespace()) start++
        while (end > start && current[end - 1].isWhitespace()) end--
        if (start >= end) return
        val content = SpannableStringBuilder(current, start, end)
        if (style.bold) {
            content.setSpan(StyleSpan(Typeface.BOLD), 0, content.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
        addText(content, style)
    }

    private fun addText(content: CharSequence, style: BlockStyle) {
        if (content !== SEPARATOR) textLength += content.length
        blocks += BookBlock.Text(
            text = content,
            sizeScale = style.sizeScale,
            alignment = style.alignment,
            indentEm = style.indentEm,
            spacingEm = style.spacingEm,
            isHeading = style.isHeading,
        )
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")

        const val SEPARATOR = "⁂"

        val IGNORED_TAGS = setOf("head", "script", "style", "title", "noscript", "template", "math")

        val BLOCK_TAGS = setOf(
            "p", "div", "section", "article", "header", "footer", "aside", "main", "nav",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "blockquote", "pre", "ul", "ol", "li", "dl", "dt", "dd",
            "figure", "figcaption", "table", "caption", "tr", "address", "center",
        )
    }
}

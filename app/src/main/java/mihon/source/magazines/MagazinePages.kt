package mihon.source.magazines

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Lays articles out as magazine pages and draws them as images, on the device.
 *
 * The reader only shows images, so each text page is encoded into a fake URL on [HOST]; the
 * [Interceptor] answers those URLs with a rendered PNG instead of going to the network. That
 * keeps downloads, the chapter cache and covers working exactly as for any other source.
 */
internal object MagazinePages {

    const val HOST = "magazines.liquidmihon.invalid"

    private const val WIDTH = 1200
    private const val HEIGHT = 1700
    private const val MARGIN_X = 100
    private const val MARGIN_TOP = 120
    private const val MARGIN_BOTTOM = 140
    private const val CONTENT_WIDTH = WIDTH - MARGIN_X * 2
    private const val QUOTE_INDENT = 36
    private const val COVER_WIDTH = 720
    private const val COVER_HEIGHT = 1032
    private const val KEEP_WITH_NEXT = 120

    private const val PAPER = 0xFFFBF8F3.toInt()
    private const val INK = 0xFF1B1B1B.toInt()
    private const val MUTED = 0xFF6B6B6B.toInt()

    private val json = Json

    /** A block of text on a page. Kinds: h(eading), p(aragraph), q(uote), l(ist item), c(aption). */
    data class TextBlock(val kind: Char, val text: String)

    data class Header(val kicker: String, val title: String, val byline: String?)

    data class PageSpec(
        val accent: Int,
        val header: Header?,
        val blocks: List<TextBlock>,
        val folio: String,
    )

    // ---- Pagination -----------------------------------------------------------------------------

    /**
     * Returns the article as page image URLs: the lead picture, then text pages with the article's
     * pictures placed after the text page they appear in.
     */
    @Synchronized
    fun paginate(
        article: Article,
        publication: Publication,
        byline: String? = null,
        firstPageNumber: Int = 1,
        shownImages: MutableSet<String> = HashSet(),
    ): List<String> {
        val accent = accentFor(publication.color)
        val urls = mutableListOf<String>()
        val pendingImages = mutableListOf<String>()
        // Pictures already shown (in this article, or earlier in the issue) aren't shown again
        val seen = shownImages

        val lead = article.leadImage?.takeIf { seen.add(ArticleExtractor.imageKey(it)) }
        if (lead != null) urls += lead

        val header = Header(
            kicker = publication.name.uppercase(),
            title = article.title,
            byline = byline ?: listOfNotNull(
                article.author?.let { "By $it" },
                article.date?.let(::formatDate),
            ).joinToString("  ·  ").ifBlank { null },
        )

        var pageHeader: Header? = header
        var blocks = mutableListOf<TextBlock>()
        var y = MARGIN_TOP + headerHeight(header)
        val bottom = HEIGHT - MARGIN_BOTTOM

        fun flush() {
            if (blocks.isNotEmpty() || pageHeader != null) {
                urls += encode(PageSpec(accent, pageHeader, blocks, folio = ""))
            }
            urls += pendingImages
            pendingImages.clear()
            pageHeader = null
            blocks = mutableListOf()
            y = MARGIN_TOP
        }

        for (block in article.blocks) {
            if (block is ArticleBlock.Image) {
                if (seen.add(ArticleExtractor.imageKey(block.url))) pendingImages += block.url
                continue
            }
            var text = block.text()
            val kind = kindOf(block)
            while (text.isNotEmpty()) {
                val gap = if (blocks.isEmpty() && pageHeader == null) 0 else spaceBefore(kind)
                val layout = layout(kind, text)
                // A heading needs room for a couple of lines after it, or it'd be stranded at the bottom.
                val keepWithNext = if (kind == 'h') KEEP_WITH_NEXT else 0
                if (y + gap + layout.height + keepWithNext <= bottom) {
                    blocks += TextBlock(kind, text)
                    y += gap + layout.height
                    break
                }
                // Split the block at the last line that still fits, keeping at least two lines together.
                val available = bottom - y - gap
                var fitting = 0
                while (fitting < layout.lineCount && layout.getLineBottom(fitting) <= available) fitting++
                val pageIsEmpty = blocks.isEmpty() && pageHeader == null
                val before = blocks.size
                if (kind != 'h' && (fitting >= 2 || (pageIsEmpty && fitting >= 1))) {
                    // The layout's text may carry a prefix (the list bullet) the block's own text doesn't.
                    val end = (layout.getLineEnd(fitting - 1) - prefixOf(kind).length).coerceIn(1, text.length)
                    blocks += TextBlock(kind, text.substring(0, end).trimEnd())
                    text = text.substring(end).trimStart()
                }
                if (pageIsEmpty && blocks.size == before) {
                    // Too tall for even an empty page: let the page grow rather than loop forever.
                    blocks += TextBlock(kind, text)
                    text = ""
                }
                flush()
            }
        }
        flush()

        // Number the text pages now that the order is final.
        var number = firstPageNumber - 1
        return urls.map { url ->
            number++
            if (!url.isTextPage()) return@map url
            val spec = decode(url) ?: return@map url
            encode(spec.copy(folio = "${publication.name}  ·  $number"))
        }
    }

    private fun kindOf(block: ArticleBlock) = when (block) {
        is ArticleBlock.Heading -> 'h'
        is ArticleBlock.Quote -> 'q'
        is ArticleBlock.ListItem -> 'l'
        is ArticleBlock.Caption -> 'c'
        else -> 'p'
    }

    private fun spaceBefore(kind: Char) = when (kind) {
        'h' -> 56
        'q' -> 44
        'l' -> 14
        'c' -> 18
        else -> 30
    }

    // ---- Text layout ----------------------------------------------------------------------------

    private val bodyPaint = textPaint(40f, Typeface.SERIF, INK)
    private val headingPaint = textPaint(46f, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), INK)
    private val quotePaint = textPaint(48f, Typeface.create(Typeface.SERIF, Typeface.ITALIC), INK)
    private val captionPaint = textPaint(28f, Typeface.SANS_SERIF, MUTED)
    private val kickerPaint = textPaint(30f, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), INK).apply {
        letterSpacing = 0.18f
    }
    private val titlePaint = textPaint(78f, Typeface.create(Typeface.SERIF, Typeface.BOLD), INK)
    private val bylinePaint = textPaint(30f, Typeface.SANS_SERIF, MUTED)
    private val folioPaint = textPaint(24f, Typeface.SANS_SERIF, MUTED).apply { letterSpacing = 0.08f }

    private fun textPaint(size: Float, typeface: Typeface, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.typeface = typeface
        this.color = color
    }

    private fun layout(kind: Char, text: String): StaticLayout {
        val (paint, width, content) = when (kind) {
            'h' -> Triple(headingPaint, CONTENT_WIDTH, text)
            'q' -> Triple(quotePaint, CONTENT_WIDTH - QUOTE_INDENT, text)
            'l' -> Triple(bodyPaint, CONTENT_WIDTH, prefixOf(kind) + text)
            'c' -> Triple(captionPaint, CONTENT_WIDTH, text)
            else -> Triple(bodyPaint, CONTENT_WIDTH, text)
        }
        val extra = when (kind) {
            'h' -> 8f
            'c' -> 6f
            else -> 18f
        }
        return makeLayout(content, paint, width, extra)
    }

    private fun prefixOf(kind: Char) = if (kind == 'l') "•  " else ""

    private fun makeLayout(text: CharSequence, paint: TextPaint, width: Int, extra: Float = 0f) =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(extra, 1f)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
            .build()

    private class HeaderLayouts(val kicker: StaticLayout, val title: StaticLayout, val byline: StaticLayout?)

    private fun headerLayouts(header: Header) = HeaderLayouts(
        kicker = makeLayout(header.kicker, kickerPaint, CONTENT_WIDTH),
        title = makeLayout(header.title, titlePaint, CONTENT_WIDTH, extra = 6f),
        byline = header.byline?.let { makeLayout(it, bylinePaint, CONTENT_WIDTH) },
    )

    private fun headerHeight(header: Header): Int {
        val l = headerLayouts(header)
        return l.kicker.height + 28 + l.title.height + 32 + (l.byline?.let { it.height + 24 } ?: 0) + 4 + 64
    }

    // ---- Drawing --------------------------------------------------------------------------------

    @Synchronized
    fun renderPage(spec: PageSpec): ByteArray {
        // Measure first: a page is never cut off, it grows instead.
        var contentHeight = MARGIN_TOP + (spec.header?.let(::headerHeight) ?: 0)
        val layouts = spec.blocks.mapIndexed { i, block ->
            val gap = if (i == 0 && spec.header == null) 0 else spaceBefore(block.kind)
            val layout = layout(block.kind, block.text)
            contentHeight += gap + layout.height
            Triple(block, layout, gap)
        }
        val height = maxOf(HEIGHT, contentHeight + MARGIN_BOTTOM)

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAPER)
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = spec.accent }

        var y = MARGIN_TOP.toFloat()
        spec.header?.let { header ->
            val l = headerLayouts(header)
            kickerPaint.color = spec.accent
            y = drawLayout(canvas, l.kicker, MARGIN_X.toFloat(), y) + 28
            y = drawLayout(canvas, l.title, MARGIN_X.toFloat(), y) + 32
            l.byline?.let { y = drawLayout(canvas, it, MARGIN_X.toFloat(), y) + 24 }
            canvas.drawRect(MARGIN_X.toFloat(), y, MARGIN_X + 140f, y + 4f, rule)
            y += 4 + 64
        }

        for ((block, layout, gap) in layouts) {
            y += gap
            if (block.kind == 'q') {
                canvas.drawRect(MARGIN_X.toFloat(), y + 6, MARGIN_X + 6f, y + layout.height - 6, rule)
                drawLayout(canvas, layout, (MARGIN_X + QUOTE_INDENT).toFloat(), y)
            } else {
                drawLayout(canvas, layout, MARGIN_X.toFloat(), y)
            }
            y += layout.height
        }

        if (spec.folio.isNotEmpty()) {
            val width = folioPaint.measureText(spec.folio)
            canvas.drawText(spec.folio, (WIDTH - width) / 2f, height - MARGIN_BOTTOM / 2f + 8, folioPaint)
        }
        return bitmap.toPng()
    }

    /** A magazine-shelf style cover with the publication's masthead. */
    @Synchronized
    fun renderCover(publication: Publication, kicker: String): ByteArray {
        val width = 600
        val height = 860
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(publication.color or 0xFF000000.toInt())
        val onColor = if (luminance(publication.color) > 0.6) INK else Color.WHITE

        val innerWidth = width - 80
        val masthead = textPaint(150f, Typeface.create(Typeface.SERIF, Typeface.BOLD), onColor)
        // Shrink the masthead until its longest word fits, then let it wrap onto at most two lines.
        val longestWord = publication.name.split(' ').maxBy { masthead.measureText(it) }
        while (masthead.textSize > 48f && masthead.measureText(longestWord) > innerWidth) masthead.textSize -= 4f
        var mastheadLayout = makeLayout(publication.name, masthead, innerWidth)
        while (mastheadLayout.lineCount > 2 && masthead.textSize > 48f) {
            masthead.textSize -= 6f
            mastheadLayout = makeLayout(publication.name, masthead, innerWidth)
        }
        drawLayout(canvas, mastheadLayout, 40f, 56f)

        val line = Paint().apply {
            color = onColor
            alpha = 160
        }
        val lineY = 56f + mastheadLayout.height + 26
        canvas.drawRect(40f, lineY, width - 40f, lineY + 3, line)

        val kickerText = textPaint(24f, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), onColor).apply {
            letterSpacing = 0.2f
            alpha = 220
        }
        drawLayout(canvas, makeLayout(kicker.uppercase(), kickerText, innerWidth), 40f, lineY + 22)

        val tagline = textPaint(34f, Typeface.create(Typeface.SERIF, Typeface.ITALIC), onColor)
        val taglineLayout = makeLayout(publication.tagline, tagline, innerWidth, extra = 6f)
        drawLayout(canvas, taglineLayout, 40f, height - 60f - taglineLayout.height)
        return bitmap.toPng()
    }

    /**
     * A newsstand-style cover: [photo] full-bleed, the masthead across the top, and the issue month
     * and latest headline as a cover line at the bottom.
     */
    @Synchronized
    fun renderPhotoCover(publication: Publication, photo: Bitmap, issue: String?, headline: String?): ByteArray {
        val width = COVER_WIDTH
        val height = COVER_HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(publication.color or 0xFF000000.toInt())

        // Centre-crop the photo to the cover's shape
        val scale = maxOf(width / photo.width.toFloat(), height / photo.height.toFloat())
        val srcWidth = width / scale
        val srcHeight = height / scale
        val left = (photo.width - srcWidth) / 2f
        val top = (photo.height - srcHeight) / 3f // favour the upper part, where faces and cars usually are
        canvas.drawBitmap(
            photo,
            android.graphics.Rect(left.toInt(), top.toInt(), (left + srcWidth).toInt(), (top + srcHeight).toInt()),
            android.graphics.Rect(0, 0, width, height),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )

        // Shade the top and bottom so the white type reads on any photo
        val shade = Paint()
        shade.shader = android.graphics.LinearGradient(
            0f,
            0f,
            0f,
            height * 0.38f,
            0xA6000000.toInt(),
            0x00000000,
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.38f, shade)
        shade.shader = android.graphics.LinearGradient(
            0f,
            height * 0.55f,
            0f,
            height.toFloat(),
            0x00000000,
            0xD9000000.toInt(),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, height * 0.55f, width.toFloat(), height.toFloat(), shade)

        val margin = 44f
        val innerWidth = (width - margin * 2).toInt()
        val masthead = textPaint(170f, Typeface.create(Typeface.SERIF, Typeface.BOLD), Color.WHITE).apply {
            setShadowLayer(12f, 0f, 2f, 0x66000000)
        }
        val longestWord = publication.name.split(' ').maxBy { masthead.measureText(it) }
        while (masthead.textSize > 56f && masthead.measureText(longestWord) > innerWidth) masthead.textSize -= 4f
        var mastheadLayout = makeLayout(publication.name, masthead, innerWidth)
        while (mastheadLayout.lineCount > 2 && masthead.textSize > 56f) {
            masthead.textSize -= 6f
            mastheadLayout = makeLayout(publication.name, masthead, innerWidth)
        }
        drawLayout(canvas, mastheadLayout, margin, 40f)

        val headlinePaint = textPaint(46f, Typeface.create(Typeface.SERIF, Typeface.BOLD), Color.WHITE).apply {
            setShadowLayer(8f, 0f, 1f, 0x80000000.toInt())
        }
        var headlineLayout = headline?.let { makeLayout(it, headlinePaint, innerWidth, extra = 4f) }
        while (headlineLayout != null && headlineLayout.lineCount > 4 && headlinePaint.textSize > 30f) {
            headlinePaint.textSize -= 4f
            headlineLayout = makeLayout(headline!!, headlinePaint, innerWidth, extra = 4f)
        }
        var y = height - 52f - (headlineLayout?.height ?: 0)
        if (issue != null) {
            val issuePaint = textPaint(26f, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD), Color.WHITE).apply {
                letterSpacing = 0.22f
            }
            val issueLayout = makeLayout(issue.uppercase(), issuePaint, innerWidth)
            val issueTop = y - issueLayout.height - 18f
            val accent = Paint().apply { color = accentFor(publication.color) }
            canvas.drawRect(margin, issueTop - 16f, margin + 64f, issueTop - 10f, accent)
            drawLayout(canvas, issueLayout, margin, issueTop)
        }
        headlineLayout?.let { y = drawLayout(canvas, it, margin, y) }
        return bitmap.toPng()
    }

    /** Decodes a downloaded photo at about cover size, or null if it isn't an image Android can read. */
    fun decodePhoto(bytes: ByteArray): Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= COVER_WIDTH && bounds.outHeight / (sample * 2) >= COVER_HEIGHT) {
            sample *= 2
        }
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float): Float {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }

    private fun Bitmap.toPng(): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, out)
        recycle()
        return out.toByteArray()
    }

    private fun luminance(rgb: Int): Double {
        val r = (rgb shr 16 and 0xFF) / 255.0
        val g = (rgb shr 8 and 0xFF) / 255.0
        val b = (rgb and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** The publication color, or a deep red when it is too close to black or white to read as an accent. */
    private fun accentFor(rgb: Int): Int {
        val lum = luminance(rgb)
        val color = if (lum < 0.06 || lum > 0.85) 0xB3261E else rgb
        return color or 0xFF000000.toInt()
    }

    private fun formatDate(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.ENGLISH))

    // ---- URLs -----------------------------------------------------------------------------------

    /**
     * The cover for [publication]: plain with just the masthead, or a photo cover when the latest
     * story's [photo] is known (with its [headline] and [issue] month as cover lines).
     */
    fun coverUrl(publication: Publication, photo: String? = null, issue: String? = null, headline: String? = null) =
        "https://$HOST/cover/${publication.slug}".toHttpUrl().newBuilder().apply {
            addQueryParameter("v", "2")
            if (photo != null) {
                addQueryParameter("img", photo)
                issue?.let { addQueryParameter("issue", it) }
                headline?.let { addQueryParameter("h", it.take(MAX_COVER_LINE)) }
            }
        }.build().toString()

    private const val MAX_COVER_LINE = 140

    private fun String.isTextPage() = startsWith("https://$HOST/page/")

    private fun encode(spec: PageSpec): String {
        val obj = buildJsonObject {
            put("a", spec.accent)
            put("f", spec.folio)
            spec.header?.let {
                put("k", it.kicker)
                put("t", it.title)
                it.byline?.let { byline -> put("b", byline) }
            }
            put(
                "x",
                buildJsonArray {
                    spec.blocks.forEach { block ->
                        add(
                            buildJsonArray {
                                add(JsonPrimitive(block.kind.toString()))
                                add(JsonPrimitive(block.text))
                            },
                        )
                    }
                },
            )
        }
        val bytes = obj.toString().toByteArray()
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(bytes)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return "https://$HOST/page/" + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    private fun decode(url: String): PageSpec? = runCatching {
        val data = Base64.getUrlDecoder().decode(url.substringAfter("/page/").substringBefore('?'))
        val inflater = Inflater(true)
        inflater.setInput(data)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, n)
        }
        inflater.end()
        val obj = json.parseToJsonElement(out.toString(Charsets.UTF_8.name())) as JsonObject
        fun str(key: String) = (obj[key] as? JsonPrimitive)?.contentOrNull
        PageSpec(
            accent = obj["a"]!!.jsonPrimitive.content.toInt(),
            header = str("t")?.let { Header(str("k").orEmpty(), it, str("b")) },
            blocks = (obj["x"] as JsonArray).map {
                val pair = it.jsonArray
                TextBlock(pair[0].jsonPrimitive.content.first(), pair[1].jsonPrimitive.content)
            },
            folio = str("f").orEmpty(),
        )
    }.getOrNull()

    /** Answers [HOST] URLs with rendered images; everything else goes to the network as usual. */
    object RenderInterceptor : Interceptor {
        private val png = "image/png".toMediaType()

        /** The cover photo, fetched with the same headers as the cover request; null if it can't be had. */
        private fun downloadPhoto(chain: Interceptor.Chain, url: String): Bitmap? = try {
            val photoRequest = chain.request().newBuilder().url(url).build()
            chain.proceed(photoRequest).use { response ->
                if (response.isSuccessful) decodePhoto(response.body.bytes()) else null
            }
        } catch (_: Exception) {
            null
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.url.host != HOST) return chain.proceed(request)
            val segments = request.url.pathSegments
            val bytes = when (segments.firstOrNull()) {
                "page" -> decode(request.url.toString())?.let(::renderPage)
                "cover" -> {
                    val slug = segments.drop(1).joinToString("/")
                    val custom = CustomPublications.fromSlug(slug)
                    val (publication, shelf) = if (custom != null) {
                        custom to CustomPublications.hostOf(custom.homepage)
                    } else {
                        MagazineCategory.entries.firstNotNullOfOrNull { category ->
                            category.publications.find { it.slug == slug }?.let { it to category.title }
                        } ?: (null to "")
                    }
                    publication?.let {
                        val photo = request.url.queryParameter("img")?.let { url -> downloadPhoto(chain, url) }
                        if (photo != null) {
                            try {
                                renderPhotoCover(
                                    it,
                                    photo,
                                    issue = request.url.queryParameter("issue"),
                                    headline = request.url.queryParameter("h"),
                                )
                            } finally {
                                photo.recycle()
                            }
                        } else {
                            renderCover(it, shelf)
                        }
                    }
                }
                else -> null
            }
            val builder = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
            return if (bytes != null) {
                builder.code(200).message("OK").body(bytes.toResponseBody(png)).build()
            } else {
                builder.code(404).message("Not Found").body(ByteArray(0).toResponseBody(png)).build()
            }
        }
    }
}

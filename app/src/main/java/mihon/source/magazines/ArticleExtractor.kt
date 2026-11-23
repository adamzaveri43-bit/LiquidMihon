package mihon.source.magazines

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** One piece of an article, in reading order. */
sealed interface ArticleBlock {
    data class Heading(val text: String) : ArticleBlock
    data class Paragraph(val text: String) : ArticleBlock
    data class Quote(val text: String) : ArticleBlock
    data class ListItem(val text: String) : ArticleBlock
    data class Caption(val text: String) : ArticleBlock
    data class Image(val url: String) : ArticleBlock
}

/** One article from a feed. [contentHtml] is the full body when the feed carries it, otherwise a summary or null. */
data class FeedItem(
    val title: String,
    val link: String,
    val date: Long?,
    val author: String?,
    val imageUrl: String?,
    val summary: String?,
    val contentHtml: String?,
)

/** What a feed says about itself. */
data class FeedInfo(val title: String, val description: String?, val homepage: String?)

/** A parsed article, ready to lay out as pages. */
data class Article(
    val title: String,
    val author: String?,
    val date: Long?,
    val leadImage: String?,
    val blocks: List<ArticleBlock>,
) {
    val textLength: Int get() = blocks.sumOf { if (it is ArticleBlock.Image) 0 else it.text().length }
}

internal fun ArticleBlock.text(): String = when (this) {
    is ArticleBlock.Heading -> text
    is ArticleBlock.Paragraph -> text
    is ArticleBlock.Quote -> text
    is ArticleBlock.ListItem -> text
    is ArticleBlock.Caption -> text
    is ArticleBlock.Image -> ""
}

/**
 * Turns RSS/Atom feeds and article web pages into [FeedItem]s and [Article]s.
 *
 * Article pages are read with a small Readability-style heuristic: the element holding the most
 * paragraph text is taken as the body. When the page's JSON-LD `articleBody` is clearly longer
 * (some sites split the body into many containers), that text is used instead.
 */
object ArticleExtractor {

    private val json = Json { ignoreUnknownKeys = true }

    // ---- Feeds ----------------------------------------------------------------------------------

    fun parseFeed(xml: String, baseUrl: String): List<FeedItem> {
        val doc = Jsoup.parse(xml, baseUrl, Parser.xmlParser())
        val items = doc.getElementsByTag("item").ifEmpty { doc.getElementsByTag("entry") }
        return items.mapNotNull { item ->
            val title = item.childText("title")?.let(::cleanText).orEmpty()
            val link = item.feedLink() ?: return@mapNotNull null
            val content = item.childText("content:encoded")
                ?: item.children().firstOrNull { it.tagName() == "content" }?.let { it.text().ifBlank { it.html() } }
            val summary = item.childText("description") ?: item.childText("summary")
            FeedItem(
                title = title.ifBlank { link },
                link = link,
                date = parseDate(
                    item.childText("pubDate") ?: item.localChildText("published") ?: item.childText("dc:date")
                        ?: item.localChildText("updated"),
                ),
                author = cleanAuthor(
                    item.childText("dc:creator")
                        ?: item.children().firstOrNull { it.tagName().substringAfter(':') == "author" }?.let { author ->
                            author.children().firstOrNull { it.tagName().substringAfter(':') == "name" }?.text()
                                ?: author.text()
                        },
                ),
                imageUrl = item.feedImage() ?: (content ?: summary)?.let(::firstImageIn),
                summary = summary,
                contentHtml = content,
            )
        }
    }

    /** A feed's own name, description and site, or null if [xml] isn't a feed with stories in it. */
    fun parseFeedInfo(xml: String, feedUrl: String): FeedInfo? {
        val doc = Jsoup.parse(xml, feedUrl, Parser.xmlParser())
        val channel = doc.getElementsByTag("channel").firstOrNull()
            ?: doc.children().firstOrNull { it.tagName().substringAfter(':') == "feed" }
            ?: return null
        if (parseFeed(xml, feedUrl).isEmpty()) return null
        val title = channel.childText("title")?.let(::cleanText).orEmpty()
        val description = (channel.childText("description") ?: channel.localChildText("subtitle"))
            ?.let(::cleanText)
        val homepage = channel.feedLink()?.takeIf { it.startsWith("http") }
        return FeedInfo(title, description, homepage)
    }

    /** Feed addresses a web page advertises with `<link rel="alternate">`, best first. */
    fun feedLinksIn(html: String, pageUrl: String): List<String> {
        val doc = Jsoup.parse(html, pageUrl)
        return doc.select("link[rel~=(?i)alternate][href]")
            .filter { it.attr("type").lowercase() in FEED_TYPES }
            .filterNot { COMMENTS_FEED.containsMatchIn(it.attr("href") + " " + it.attr("title")) }
            .map { it.absUrl("href") }
            .filter { it.startsWith("http") }
            .distinct()
    }

    private val FEED_TYPES = setOf("application/rss+xml", "application/atom+xml", "application/feed+xml")
    private val COMMENTS_FEED = Regex("comments", RegexOption.IGNORE_CASE)

    private fun Element.childText(tag: String): String? =
        children().firstOrNull { it.tagName().equals(tag, ignoreCase = true) }?.text()?.trim()?.ifBlank { null }

    /** Matches a tag with any namespace prefix, e.g. "a10:updated" for "updated". */
    private fun Element.localChildText(tag: String): String? =
        children().firstOrNull { it.tagName().substringAfter(':') == tag }?.text()?.trim()?.ifBlank { null }

    /** "staff@site.com (Jane Doe)" becomes "Jane Doe"; bare e-mail addresses are dropped. */
    private fun cleanAuthor(raw: String?): String? {
        val text = raw?.trim()?.ifBlank { null } ?: return null
        EMAIL_WITH_NAME.matchEntire(text)?.let { return it.groupValues[1].trim() }
        return text.takeIf { '@' !in it }
    }

    private fun Element.feedLink(): String? {
        val links = children().filter { it.tagName() == "link" }
        links.firstOrNull { it.text().isNotBlank() }?.let { return it.text().trim() }
        val atom = links.firstOrNull { it.attr("rel").ifBlank { "alternate" } == "alternate" && it.hasAttr("href") }
        return atom?.attr("href")?.ifBlank { null } ?: childText("guid")?.takeIf { it.startsWith("http") }
    }

    private fun Element.feedImage(): String? {
        val candidates = children().filter {
            it.tagName() in setOf("media:content", "media:thumbnail", "enclosure") ||
                it.tagName() == "media:group"
        }.flatMap { if (it.tagName() == "media:group") it.children() else listOf(it) }
        return candidates
            .filter { el ->
                val type = el.attr("type")
                el.attr("medium") == "image" || type.startsWith("image") ||
                    (type.isBlank() && IMAGE_EXT.containsMatchIn(el.attr("url"))) ||
                    el.tagName() == "media:thumbnail"
            }
            .map { it.attr("url") }
            .firstOrNull { it.startsWith("http") }
    }

    private fun firstImageIn(html: String): String? =
        Jsoup.parseBodyFragment(html).select("img").asSequence().map { imageSrc(it) }.firstOrNull { it != null }

    // ---- Articles -------------------------------------------------------------------------------

    /** Builds an article from a feed item's own HTML, or null if the feed has none. */
    fun fromFeedItem(item: FeedItem): Article? {
        val html = item.contentHtml ?: return null
        val blocks = blocksOf(Jsoup.parseBodyFragment(html, item.link).body())
        return Article(item.title, item.author, item.date, item.imageUrl, blocks).takeIf { it.textLength > 0 }
    }

    /**
     * True when a feed's copy of an article can be read as is. Many feeds that carry "full" content
     * actually cut it off after a few paragraphs, so short or trailing-off copies don't count.
     */
    fun isComplete(article: Article): Boolean {
        val last = article.blocks.lastOrNull { it !is ArticleBlock.Image }?.text().orEmpty()
        val trailsOff = last.endsWith("…") || last.endsWith("...") || last.endsWith("[…]")
        return article.textLength >= COMPLETE_TEXT && !trailsOff
    }

    /** The fuller of two versions of the same article, keeping a lead picture if either has one. */
    fun pick(a: Article?, b: Article?): Article? {
        if (a == null || b == null) return a ?: b
        val best = if (b.textLength > a.textLength) b else a
        val other = if (best === a) b else a
        return if (best.leadImage == null) best.copy(leadImage = other.leadImage) else best
    }

    /** Builds an article from its web page. [item] fills in what the page leaves out. */
    fun fromPage(html: String, url: String, item: FeedItem?): Article {
        val doc = Jsoup.parse(html, url)
        val ldArticle = doc.ldArticle()
        val title = item?.title
            ?: ldArticle?.string("headline")
            ?: doc.meta("og:title")
            ?: doc.title()
        val leadImage = item?.imageUrl ?: doc.meta("og:image") ?: ldArticle?.imageUrl()
        val author = item?.author ?: cleanAuthor(ldArticle?.authorName())
        val date = item?.date ?: parseDate(ldArticle?.string("datePublished") ?: doc.meta("article:published_time"))

        doc.select(JUNK).remove()
        val fromHtml = listOfNotNull(bestByDirectParagraphs(doc), bestByScoring(doc))
            .map { blocksOf(it) }
            .maxByOrNull { blocks -> blocks.sumOf { it.text().length } }
            .orEmpty()
        val htmlLength = fromHtml.sumOf { it.text().length }

        val ldBody = ldArticle?.string("articleBody")?.let(::cleanText).orEmpty()
        val blocks = when {
            ldBody.length > htmlLength * 3 / 2 && ldBody.length > MIN_FULL_TEXT -> {
                // Keep the images the page body had, then the structured text.
                fromHtml.filterIsInstance<ArticleBlock.Image>() + splitPlainText(ldBody)
            }
            htmlLength > 0 -> fromHtml
            else -> item?.summary?.let { blocksOf(Jsoup.parseBodyFragment(it, url).body()) }.orEmpty()
        }
        return Article(title.ifBlank { url }, author, date, leadImage, blocks)
    }

    /** The element whose own (and figure/div children's) paragraphs hold the most text. */
    private fun bestByDirectParagraphs(doc: Document): Element? {
        var best: Element? = null
        var bestScore = 0
        for (candidate in doc.select("article, main, section, div")) {
            val paragraphs = candidate.children().flatMap { child ->
                when (child.tagName()) {
                    "p" -> listOf(child)
                    "div", "section", "figure" -> child.children().filter { it.tagName() == "p" }
                    else -> emptyList()
                }
            }
            val score = paragraphs.sumOf { p -> p.text().length.takeIf { it > MIN_PARAGRAPH * 2 } ?: 0 }
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }

    /**
     * Readability-style scoring: each paragraph adds its length to its parent and half to its
     * grandparent. Siblings of the winner that also score well are kept (bodies split into chunks).
     */
    private fun bestByScoring(doc: Document): Element? {
        val scores = HashMap<Element, Int>()
        for (p in doc.select("p")) {
            val length = p.text().length
            if (length < MIN_PARAGRAPH) continue
            val parent = p.parent() ?: continue
            scores.merge(parent, length, Int::plus)
            parent.parent()?.let { scores.merge(it, length / 2, Int::plus) }
        }
        val (best, bestScore) = scores.maxByOrNull { it.value } ?: return null
        val parent = best.parent() ?: return best
        val threshold = maxOf(bestScore / 5, 100)
        val kept = parent.children().filter { child ->
            val looksLikeBody = !NEGATIVE.containsMatchIn(child.className() + child.id())
            child == best || ((scores[child] ?: 0) >= threshold && looksLikeBody)
        }
        if (kept.size == 1) return best
        return Element("div").apply { kept.forEach { appendChild(it.clone()) } }
    }

    /** Walks [root] in document order and collects text blocks and images. */
    internal fun blocksOf(root: Element): List<ArticleBlock> {
        val blocks = mutableListOf<ArticleBlock>()
        val seenImages = HashSet<String>()
        fun walk(el: Element) {
            if (NEGATIVE.containsMatchIn(el.className() + " " + el.id()) && el != root) return
            when (el.tagName()) {
                "img" -> {
                    val src = imageSrc(el) ?: return
                    if (seenImages.add(imageKey(src))) blocks += ArticleBlock.Image(src)
                    return
                }
                "picture" -> {
                    val src = el.selectFirst("img")?.let(::imageSrc)
                        ?: el.select("source").firstNotNullOfOrNull { srcsetLargest(it.attr("srcset")) }
                    if (src != null && seenImages.add(imageKey(src))) blocks += ArticleBlock.Image(src)
                    return
                }
                "p" -> {
                    // Paragraphs can hold inline images (common in WordPress feeds).
                    el.select("img").forEach { img ->
                        imageSrc(img)?.let { if (seenImages.add(imageKey(it))) blocks += ArticleBlock.Image(it) }
                    }
                    cleanText(el.text()).takeIf { it.isNotEmpty() }?.let { blocks += ArticleBlock.Paragraph(it) }
                    return
                }
                "h1", "h2", "h3", "h4", "h5" -> {
                    cleanText(el.text()).takeIf { it.isNotEmpty() }?.let { blocks += ArticleBlock.Heading(it) }
                    return
                }
                "blockquote" -> {
                    if (el.hasClass("twitter-tweet") || el.hasClass("instagram-media")) return
                    cleanText(el.text()).takeIf { it.isNotEmpty() }?.let { blocks += ArticleBlock.Quote(it) }
                    return
                }
                "li" -> {
                    if (el.select("p, li").none { it != el }) {
                        cleanText(el.text()).takeIf { it.isNotEmpty() }?.let { blocks += ArticleBlock.ListItem(it) }
                        return
                    }
                }
                "figcaption" -> {
                    cleanText(el.text()).takeIf { it.isNotEmpty() }?.let { blocks += ArticleBlock.Caption(it) }
                    return
                }
            }
            val children = el.children()
            if (children.isEmpty() || el.ownText().length > MIN_PARAGRAPH) {
                // Loose text straight in a div (some feeds don't wrap paragraphs).
                if (el.tagName() in setOf("div", "span", "section", "body")) {
                    cleanText(el.ownText()).takeIf { it.length > MIN_PARAGRAPH }?.let {
                        blocks += ArticleBlock.Paragraph(it)
                    }
                }
            }
            children.forEach(::walk)
        }
        walk(root)
        return blocks.dropBoilerplate().take(MAX_BLOCKS)
    }

    /** Drops lines like "Read more", affiliate notices and newsletter pitches. */
    private fun List<ArticleBlock>.dropBoilerplate(): List<ArticleBlock> = filterNot { block ->
        val text = block.text()
        block !is ArticleBlock.Image && (text.length < 2 || (BOILERPLATE.containsMatchIn(text) && text.length < 300))
    }

    private fun splitPlainText(text: String): List<ArticleBlock> {
        val paragraphs = text.split(Regex("""\n+""")).flatMap { paragraph ->
            if (paragraph.length <= PLAIN_PARAGRAPH * 2) return@flatMap listOf(paragraph)
            // Many sites drop the paragraph breaks from articleBody, sometimes even the space after a
            // full stop ("...punch.Euro-spec cars..."). Split into sentences and regroup them.
            paragraph.split(SENTENCE_END).fold(mutableListOf<StringBuilder>()) { acc, sentence ->
                if (acc.isEmpty() || acc.last().length > PLAIN_PARAGRAPH) acc += StringBuilder()
                acc.last().append(sentence.trim()).append(' ')
                acc
            }.map { it.toString() }
        }
        return paragraphs.map(::cleanText).filter { it.isNotEmpty() }.map { ArticleBlock.Paragraph(it) }
    }

    // ---- JSON-LD & meta -------------------------------------------------------------------------

    private fun Document.meta(property: String): String? =
        selectFirst("meta[property=$property], meta[name=$property]")?.attr("content")?.ifBlank { null }

    private fun Document.ldArticle(): JsonObject? {
        for (script in select("script[type=application/ld+json]")) {
            val root = runCatching { json.parseToJsonElement(script.data()) }.getOrNull() ?: continue
            findArticle(root)?.let { return it }
        }
        return null
    }

    private fun findArticle(element: JsonElement): JsonObject? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull(::findArticle)
        is JsonObject -> when {
            element["articleBody"] != null || element.types().any { it.endsWith("Article") } -> element
            else -> element["@graph"]?.let(::findArticle)
        }
        else -> null
    }

    private fun JsonObject.types(): List<String> = when (val type = this["@type"]) {
        is JsonPrimitive -> listOfNotNull(type.contentOrNull)
        is JsonArray -> type.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.ifBlank { null }

    private fun JsonObject.imageUrl(): String? = when (val image = this["image"]) {
        is JsonPrimitive -> image.contentOrNull
        is JsonArray -> image.firstNotNullOfOrNull {
            (it as? JsonPrimitive)?.contentOrNull ?: (it as? JsonObject)?.string("url")
        }
        is JsonObject -> image.string("url")
        else -> null
    }

    private fun JsonObject.authorName(): String? = when (val author = this["author"]) {
        is JsonObject -> author.string("name")
        is JsonArray -> author.mapNotNull { (it as? JsonObject)?.string("name") }.joinToString().ifBlank { null }
        is JsonPrimitive -> author.contentOrNull
        else -> null
    }

    // ---- Helpers --------------------------------------------------------------------------------

    internal fun imageSrc(img: Element): String? {
        val candidates = listOf(
            srcsetLargest(img.attr("data-srcset")),
            srcsetLargest(img.attr("srcset")),
            img.attr("data-src"),
            img.attr("data-lazy-src"),
            img.attr("data-original"),
            img.attr("src"),
        )
        val src = candidates.firstOrNull { !it.isNullOrBlank() && !it.startsWith("data:") } ?: return null
        val absolute = resolve(img.baseUri(), src) ?: return null
        val width = img.attr("width").toIntOrNull()
        val height = img.attr("height").toIntOrNull()
        if ((width != null && width < MIN_IMAGE_SIDE) || (height != null && height < MIN_IMAGE_SIDE)) return null
        if (SKIP_IMAGE.containsMatchIn(absolute)) return null
        return absolute
    }

    private fun srcsetLargest(srcset: String): String? {
        if (srcset.isBlank()) return null
        return srcset.split(Regex(""",\s+"""))
            .mapNotNull { entry ->
                val parts = entry.trim().split(Regex("""\s+"""))
                val url = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val size = parts.getOrNull(1)?.dropLast(1)?.toFloatOrNull() ?: 0f
                url to size
            }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun resolve(base: String, src: String): String? = when {
        src.startsWith("//") -> "https:$src"
        src.startsWith("http://") || src.startsWith("https://") -> src
        base.isBlank() -> null
        else -> runCatching { java.net.URI(base).resolve(src.replace(" ", "%20")).toString() }.getOrNull()
            ?.takeIf { it.startsWith("http") }
    }

    /** Same picture at different sizes or CDN params counts once. */
    internal fun imageKey(url: String): String = url.substringBefore('?').substringAfter("://")

    private fun cleanText(text: String): String = Jsoup.parse(text).text()
        .replace(' ', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()

    fun parseDate(value: String?): Long? {
        val text = value?.trim()?.ifBlank { null } ?: return null
        return runCatching { ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME) }
            .recoverCatching { OffsetDateTime.parse(text).toZonedDateTime() }
            // "Sun, 22 Nov 2026 16:00:00 EST" and similar named zones
            .recoverCatching { ZonedDateTime.parse(text, NAMED_ZONE_DATE) }
            .getOrNull()
            ?.toInstant()
            ?.toEpochMilli()
    }

    /** Between a sentence's end and the next sentence's capital, with or without a space. */
    private val SENTENCE_END = Regex("""(?<=[a-z0-9)][.!?]|[.!?][”"’])\s*(?=[A-Z“"‘])""")

    private val NAMED_ZONE_DATE = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", java.util.Locale.US)

    private val EMAIL_WITH_NAME = Regex("""\S+@\S+\s*\((.+)\)""")

    private const val MIN_PARAGRAPH = 25
    private const val MIN_FULL_TEXT = 600
    private const val COMPLETE_TEXT = 2000
    private const val PLAIN_PARAGRAPH = 420
    private const val MAX_BLOCKS = 600
    private const val MIN_IMAGE_SIDE = 120

    private const val JUNK =
        "script, style, noscript, nav, header, footer, aside, form, iframe, svg, button, " +
            "[aria-hidden=true], [role=navigation], [role=complementary]"

    private val NEGATIVE = Regex(
        "comment|related|newsletter|promo|sidebar|share|social|recirc|signup|subscribe|advert|" +
            "\\bad-|-ad\\b|outbrain|taboola|byline|author-bio|breadcrumb|paywall|most-popular|" +
            "modal|login|password|popup|overlay|cookie|consent",
        RegexOption.IGNORE_CASE,
    )

    private val BOILERPLATE = Regex(
        "^(read more|related:|see also|sign up|subscribe|advertisement|share this|follow us|comments$|" +
            "read (the )?full (article|story)|updated:|readtime:|image credits)|" +
            "we may earn (a )?commission|may receive a commission|editor-tested|independently selected|" +
            "affiliate (link|marketing)|the post .* appeared first on|unlock premium|unlimited access to|" +
            "become a .{0,30}member|follow .{0,30} to ensure|please enable js|disable any ad ?blocker",
        RegexOption.IGNORE_CASE,
    )

    private val SKIP_IMAGE = Regex(
        """gravatar|/avatar|logo|/icons?/|favicon|pixel|spacer|emoji|badge|\.svg(\?|$)|feedburner|""" +
            """doubleclick|/ads?/|1x1|blank\.gif""",
        RegexOption.IGNORE_CASE,
    )

    private val IMAGE_EXT = Regex("""\.(jpe?g|png|webp|avif|gif)(\?|$)""", RegexOption.IGNORE_CASE)
}

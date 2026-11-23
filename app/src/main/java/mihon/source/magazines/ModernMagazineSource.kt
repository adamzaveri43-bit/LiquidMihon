package mihon.source.magazines

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Response
import tachiyomi.domain.library.model.LibraryContentType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A built-in source for current magazines, read from the publishers' own public RSS feeds.
 *
 * Each publication is an entry and each article a chapter, so library updates bring in new
 * issues' stories as they're published. Chapters are labelled with their month ("November 2026")
 * in the scanlator line. Articles are laid out on-device as magazine pages, with the article's
 * photos as full pages in between; the publisher's site is always one tap away through WebView.
 *
 * Feeds only list recent stories, so articles already in the library are kept when they drop out
 * of the feed.
 */
abstract class ModernMagazineSource(private val category: MagazineCategory) : HttpSource() {

    override val name = "Magazines Today: ${category.title}"

    override val lang = "all"

    override val baseUrl = "https://${MagazinePages.HOST}"

    override val supportsLatest = false

    val contentType = LibraryContentType.MAGAZINE

    override val client: OkHttpClient by lazy {
        network.client.newBuilder().addInterceptor(MagazinePages.RenderInterceptor).build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "application/rss+xml, application/atom+xml, application/xml, text/html;q=0.9, */*;q=0.8")

    override fun getHomeUrl(): String = category.publications.first().homepage

    // ---- Browsing -------------------------------------------------------------------------------

    override suspend fun getPopularManga(page: Int): MangasPage =
        MangasPage(category.publications.map(::toSManga), hasNextPage = false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val words = query.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val matches = category.publications.filter { publication ->
            val haystack = "${publication.name} ${publication.tagline} ${publication.homepage}".lowercase()
            words.all { it in haystack }
        }
        return MangasPage(matches.map(::toSManga), hasNextPage = false)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Each magazine is an entry and each new article a chapter."),
        Filter.Header("Add one to your library to get new stories on library update."),
    )

    private fun toSManga(publication: Publication) = SManga.create().apply {
        url = "/${publication.slug}"
        title = publication.name
        thumbnail_url = MagazinePages.coverUrl(publication)
        update_strategy = UpdateStrategy.ALWAYS_UPDATE
    }

    // ---- Details & chapters ---------------------------------------------------------------------

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val publication = publicationOf(manga.url)

        val details = if (fetchDetails) {
            SManga.create().apply {
                url = manga.url
                title = publication.name
                author = publication.name
                description = buildString {
                    append(publication.tagline)
                    append("\n\nCurrent stories from ${publication.homepage.removePrefix("https://")}, ")
                    append("read from the publication's public feed. Each article is a chapter, labelled with ")
                    append("the month it ran. Open an article in WebView to read it on the publisher's site.")
                }
                genre = listOf("Magazine", category.title).joinToString()
                status = SManga.ONGOING
                thumbnail_url = MagazinePages.coverUrl(publication)
                update_strategy = UpdateStrategy.ALWAYS_UPDATE
                initialized = true
            }
        } else {
            manga
        }

        val chapterList = if (fetchChapters) {
            val known = chapters.associateBy { it.url }
            val fresh = fetchFeed(publication).map { item ->
                val chapter = item.toSChapter(publication)
                // An undated story keeps the date it was first seen, so it doesn't jump to the top on every refresh.
                val previous = known[chapter.url]
                if (item.date == null && previous != null) {
                    chapter.date_upload = previous.date_upload
                    chapter.scanlator = previous.scanlator
                    chapter.chapter_number = previous.chapter_number
                }
                chapter
            }
            val freshUrls = fresh.mapTo(HashSet()) { it.url }
            // Keep older articles the feed no longer lists, so the back catalogue grows over time.
            (fresh + chapters.filter { it.url !in freshUrls })
                .sortedByDescending { it.date_upload }
                .take(MAX_CHAPTERS)
        } else {
            chapters
        }

        return SMangaUpdate(details, chapterList)
    }

    private fun FeedItem.toSChapter(publication: Publication) = SChapter.create().apply {
        // The fragment ties the article to its publication; it's never sent to the server.
        url = "${link.substringBefore('#')}#${publication.slug}"
        name = title
        val millis = date ?: System.currentTimeMillis()
        date_upload = millis
        scanlator = monthLabel(millis)
        chapter_number = chapterNumber(millis)
    }

    // ---- Pages ----------------------------------------------------------------------------------

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val publication = publicationForArticle(chapter.url)
        val article = loadArticle(publication, chapter)
        if (article.blocks.isEmpty() && article.leadImage == null) {
            throw Exception("Couldn't read this article here. Open it in WebView to read it on the publisher's site.")
        }
        return MagazinePages.paginate(article, publication).mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private suspend fun loadArticle(publication: Publication, chapter: SChapter): Article {
        // The feed often has the whole article (and is cheaper than the web page); look there first.
        val link = getChapterUrl(chapter)
        val item = try {
            fetchFeed(publication).find { sameArticle(it.link, link) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val fromFeed = item?.let(ArticleExtractor::fromFeedItem)
        if (fromFeed != null && ArticleExtractor.isComplete(fromFeed)) return fromFeed

        val fallback = item ?: FeedItem(
            title = chapter.name,
            link = link,
            date = chapter.date_upload.takeIf { it > 0 },
            author = null,
            imageUrl = null,
            summary = null,
            contentHtml = null,
        )
        val fromPage = try {
            val html = client.newCall(GET(link, headers)).awaitSuccess().bodyString()
            ArticleExtractor.fromPage(html, link, fallback)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Some sites block apps (bot checks, JS walls); the feed's copy is still worth showing,
            // with a pointer to the rest.
            val excerpt = fromFeed ?: throw e
            return excerpt.copy(
                blocks = excerpt.blocks + ArticleBlock.Caption(
                    "The rest of this story is on ${hostOf(link)}. Open this chapter in WebView to keep reading.",
                ),
            )
        }
        return ArticleExtractor.pick(fromFeed, fromPage) ?: fromPage
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private suspend fun fetchFeed(publication: Publication): List<FeedItem> {
        val response = client.newCall(GET(publication.feed, headers)).awaitSuccess()
        val items = ArticleExtractor.parseFeed(response.bodyString(), publication.feed)
        if (items.isEmpty()) throw Exception("${publication.name}'s feed had no stories. Try again later.")
        return items
    }

    private fun Response.bodyString(): String = use { it.body.string() }

    private fun publicationOf(url: String): Publication {
        val slug = url.trim('/').substringBefore('/')
        return category.publications.find { it.slug == slug }
            ?: throw Exception("This magazine is no longer available as a built-in source.")
    }

    /** Finds which of this source's publications an article belongs to: by its tag, else by its site. */
    private fun publicationForArticle(url: String): Publication {
        val slug = url.substringAfter('#', "")
        category.publications.find { it.slug == slug }?.let { return it }
        val host = hostOf(url)
        val path = url.substringBefore('#').substringAfter(host, "")
        return category.publications
            .filter { hostOf(it.homepage) == host }
            // Prefer the most specific section, e.g. "Esquire UK" for esquire.com/uk/...
            .sortedByDescending { it.homepage.substringAfter(host).length }
            .firstOrNull { path.startsWith(it.homepage.substringAfter(host).trimEnd('/')) }
            ?: category.publications.firstOrNull { hostOf(it.homepage) == host }
            ?: category.publications.first()
    }

    private fun sameArticle(a: String, b: String) = normalize(a) == normalize(b)

    private fun normalize(url: String) = url.substringBefore('#').substringBefore('?').trimEnd('/')
        .removePrefix("https://").removePrefix("http://").removePrefix("www.")

    private fun hostOf(url: String) = url.removePrefix("https://").removePrefix("http://").substringBefore('/')
        .removePrefix("www.")

    override fun getMangaUrl(manga: SManga): String =
        category.publications.find { it.slug == manga.url.trim('/') }?.homepage ?: getHomeUrl()

    override fun getChapterUrl(chapter: SChapter): String = chapter.url.substringBefore('#')

    companion object {
        private const val MAX_CHAPTERS = 400

        private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        private val EPOCH_2000: Long = LocalDate.of(2000, 1, 1).toEpochDay()

        private fun monthLabel(millis: Long): String =
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(MONTH)

        /**
         * Days since 2000 with the time of day as the fraction: always increasing, so "next chapter"
         * and number sorting follow publication order, and small enough to stay exact as a Float.
         */
        private fun chapterNumber(millis: Long): Float {
            val time = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)
            val day = time.toLocalDate().toEpochDay() - EPOCH_2000
            val fraction = time.toLocalTime().toSecondOfDay() / 86_400f
            return day + (fraction * 100).toInt() / 100f
        }
    }
}

class MensStyleMagazines : ModernMagazineSource(MagazineCategory.MENS_STYLE)

class CarMagazines : ModernMagazineSource(MagazineCategory.CARS)

class DesignMagazines : ModernMagazineSource(MagazineCategory.DESIGN)

class ArtMagazines : ModernMagazineSource(MagazineCategory.ART)

class TechMagazines : ModernMagazineSource(MagazineCategory.TECH)

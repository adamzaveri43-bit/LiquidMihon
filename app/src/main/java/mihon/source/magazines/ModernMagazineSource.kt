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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import tachiyomi.domain.library.model.LibraryContentType
import java.time.Instant
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
abstract class ModernMagazineSource(
    sourceTitle: String,
    protected val publications: List<Publication>,
    /** Shown in each entry's genres. */
    private val shelfLabel: String,
) : HttpSource() {

    constructor(category: MagazineCategory) : this(category.title, category.publications, category.title)

    override val name = "Magazines Today: $sourceTitle"

    override val lang = "all"

    override val baseUrl = "https://${MagazinePages.HOST}"

    override val supportsLatest = false

    val contentType = LibraryContentType.MAGAZINE

    override val client: OkHttpClient by lazy {
        network.client.newBuilder().addInterceptor(MagazinePages.RenderInterceptor).build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept", "application/rss+xml, application/atom+xml, application/xml, text/html;q=0.9, */*;q=0.8")

    override fun getHomeUrl(): String = publications.firstOrNull()?.homepage ?: "https://duckduckgo.com"

    // ---- Browsing -------------------------------------------------------------------------------

    override suspend fun getPopularManga(page: Int): MangasPage =
        MangasPage(publications.map(::toSManga), hasNextPage = false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val words = query.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val matches = publications.filter { publication ->
            val haystack = "${publication.name} ${publication.tagline} ${publication.homepage}".lowercase()
            words.all { it in haystack }
        }
        return MangasPage(matches.map(::toSManga), hasNextPage = false)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Each magazine is an entry and each new article a chapter."),
        Filter.Header("Add one to your library to get new stories on library update."),
    )

    protected fun toSManga(publication: Publication) = SManga.create().apply {
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
        val latest = when {
            fetchChapters -> fetchFeed(publication)
            // Details alone only want the latest story for the cover; without it the plain masthead does.
            fetchDetails -> try {
                fetchFeed(publication)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            else -> emptyList()
        }

        val details = if (fetchDetails) {
            SManga.create().apply {
                url = manga.url
                title = publication.name
                author = publication.name
                description = buildString {
                    append(publication.tagline)
                    append("\n\nCurrent stories from ${publication.homepage.removePrefix("https://")}, ")
                    append(
                        "read from the publication's public feed and bundled into ${periodName(publication.issues)}. ",
                    )
                    append("Each issue opens with its contents, then every story in it, laid out as magazine pages.")
                }
                genre = listOf("Magazine", shelfLabel).joinToString()
                status = SManga.ONGOING
                thumbnail_url = coverFor(publication, latest)
                update_strategy = UpdateStrategy.ALWAYS_UPDATE
                initialized = true
            }
        } else {
            manga
        }

        val chapterList = if (fetchChapters) {
            // Older stories are a bonus: if the archive can't be reached, the latest ones still load.
            val known = knownArticles(chapters)
            val older = try {
                fetchArchive(publication, known = known, latest = latest)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            buildIssues(publication, known, latest + older)
        } else {
            chapters
        }

        return SMangaUpdate(details, chapterList)
    }

    /** A photo cover from the newest story that has a picture, or the plain masthead if none do. */
    private fun coverFor(publication: Publication, latest: List<FeedItem>): String {
        val lead = latest.sortedByDescending { it.date ?: 0L }.firstOrNull { it.imageUrl != null }
            ?: return MagazinePages.coverUrl(publication)
        return MagazinePages.coverUrl(
            publication,
            photo = lead.imageUrl,
            issue = monthLabel(lead.date ?: System.currentTimeMillis()),
            // Sitemap listings only have titles made up from the address; those make poor cover lines
            headline = lead.title.takeUnless { publication.kind == FeedKind.SITEMAP },
        )
    }

    /** Every story already in the library's issues (and in article chapters from before issues existed). */
    private fun knownArticles(chapters: List<SChapter>): List<ArticleRef> = chapters.flatMap { chapter ->
        if (Issues.isIssueUrl(chapter.url)) {
            Issues.decode(chapter.memo)
        } else {
            listOf(ArticleRef(getChapterUrl(chapter), chapter.name, chapter.date_upload, null))
        }
    }

    /**
     * Bundles the known stories and the newly listed [items] into issues, newest first. Titles a listing
     * had to make up from the address never replace a real one, and an undated story keeps the date it
     * was first seen so it doesn't jump to the newest issue on every refresh.
     */
    private suspend fun buildIssues(
        publication: Publication,
        known: List<ArticleRef>,
        items: List<FeedItem>,
    ): List<SChapter> {
        val merged = LinkedHashMap<String, ArticleRef>()
        known.forEach { merged[normalize(it.link)] = it }
        val now = System.currentTimeMillis()
        for (item in items) {
            val key = normalize(item.link)
            val previous = merged[key]
            merged[key] = ArticleRef(
                link = item.link.substringBefore('#'),
                title = if (previous != null && hasMadeUpTitle(item.title, item.link) &&
                    !hasMadeUpTitle(previous.title, previous.link)
                ) {
                    previous.title
                } else {
                    item.title
                },
                date = item.date ?: previous?.date ?: now,
                image = item.imageUrl ?: previous?.image,
            )
        }
        val articles = merged.values.sortedByDescending { it.date }.take(MAX_ARTICLES)
        // Fill in real headlines a batch at a time, newest first; later refreshes do the rest.
        fillInTitles(articles.filter { hasMadeUpTitle(it.title, it.link) }.take(MAX_TITLE_LOOKUPS))

        val issues = articles.groupBy { Issues.keyFor(it.date, publication.issues) }
            .entries
            .sortedByDescending { it.key.drop(1) }
        return issues.mapIndexed { index, (key, stories) ->
            SChapter.create().apply {
                url = Issues.chapterUrl(key, publication)
                name = Issues.label(key)
                date_upload = stories.maxOf { it.date }
                // Issues are numbered 1, 2, 3… oldest first, so there are never gaps to call "missing"
                chapter_number = (issues.size - index).toFloat()
                // Read in the order they ran
                memo = Issues.encode(stories.sortedBy { it.date })
            }
        }
    }

    private fun hasMadeUpTitle(title: String, link: String): Boolean =
        title.isBlank() || title.equals(ArticleExtractor.titleFromUrl(link), ignoreCase = true)

    private fun hasMadeUpTitle(chapter: SChapter): Boolean = hasMadeUpTitle(chapter.name, getChapterUrl(chapter))

    /**
     * Stories from just before the oldest one already known, so each refresh reaches a little
     * further back: one more month of a monthly sitemap, or a few more pages of a paged feed.
     */
    private suspend fun fetchArchive(
        publication: Publication,
        known: List<ArticleRef>,
        latest: List<FeedItem>,
    ): List<FeedItem> {
        val archive = publication.archive ?: return emptyList()
        val oldest = (known.map { it.date } + latest.mapNotNull { it.date }).filter { it > 0 }.minOrNull()
            ?: System.currentTimeMillis()
        // Pages hold as many stories as the first one; how many are known says roughly where they end.
        val pageSize = latest.size.coerceAtLeast(1)
        val knownCount = (known.map { normalize(it.link) } + latest.map { normalize(it.link) }).toSet().size
        val firstPage = maxOf(2, knownCount / pageSize + 1)
        return when (archive) {
            Archive.FeedPages -> fetchPages(firstPage) { page ->
                val separator = if ('?' in publication.feed) '&' else '?'
                val url = "${publication.feed}${separator}paged=$page"
                ArticleExtractor.parseFeed(fetchText(url), url)
            }
            is Archive.ListingPages -> fetchPages(firstPage) { page ->
                val url = archive.url(page)
                ArticleExtractor.parseNextData(fetchText(url), url)
            }
            is Archive.MonthlySitemap -> {
                // The oldest story's month (to fill it in) and the one before it (to reach further back)
                val month = Instant.ofEpochMilli(oldest).atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1)
                listOf(month, month.minusMonths(1)).flatMap { date ->
                    try {
                        ArticleExtractor.parseSitemap(fetchText(archive.url(date.year, date.monthValue)), Int.MAX_VALUE)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }.filter { item -> archive.pathFilter == null || archive.pathFilter in item.link }
            }
            Archive.FullSitemap -> ArticleExtractor.parseSitemap(fetchText(publication.feed), Int.MAX_VALUE)
                .filter { (it.date ?: 0L) < oldest }
                .take(SITEMAP_BACKFILL)
        }
    }

    /** Up to [ARCHIVE_PAGES] pages from [firstPage], stopping at the first one that's empty or fails. */
    private suspend fun fetchPages(firstPage: Int, load: suspend (Int) -> List<FeedItem>): List<FeedItem> {
        val items = mutableListOf<FeedItem>()
        for (page in firstPage until firstPage + ARCHIVE_PAGES) {
            val pageItems = try {
                load(page)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                break
            }
            if (pageItems.isEmpty()) break
            items += pageItems
        }
        return items
    }

    private suspend fun fetchText(url: String): String = client.newCall(GET(url, headers)).awaitSuccess().bodyString()

    // ---- Pages ----------------------------------------------------------------------------------

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val urls = if (Issues.isIssueUrl(chapter.url)) issuePages(chapter) else articlePages(chapter)
        return urls.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    /** A story saved as its own chapter, from before stories were bundled into issues. */
    private suspend fun articlePages(chapter: SChapter): List<String> {
        val publication = publicationForArticle(chapter.url)
        val link = getChapterUrl(chapter)
        val feed = feedFor(publication)
        val article = loadArticle(
            publication,
            ArticleRef(link, chapter.name.takeUnless { hasMadeUpTitle(chapter) }.orEmpty(), chapter.date_upload, null),
            feed,
        )
        if (article.blocks.isEmpty() && article.leadImage == null) {
            throw Exception("Couldn't read this article here. Open it in WebView to read it on the publisher's site.")
        }
        return MagazinePages.paginate(article, publication)
    }

    /**
     * An issue: a contents page, then every story in it. Stories are fetched a few at a time; one that
     * can't be read is kept as a short page pointing to the site rather than failing the whole issue.
     */
    private suspend fun issuePages(chapter: SChapter): List<String> = coroutineScope {
        val publication = findPublication(chapter.url.substringAfter('#', ""))
            ?: throw Exception("This magazine is no longer available as a built-in source.")
        val stories = Issues.decode(chapter.memo)
        if (stories.isEmpty()) throw Exception("This issue is empty. Refresh the magazine and try again.")
        val feed = feedFor(publication)
        val permits = Semaphore(ISSUE_FETCH_CONCURRENCY)
        val articles = stories.map { story ->
            async {
                permits.withPermit {
                    try {
                        loadArticle(publication, story, feed)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Article(
                            title = story.title.ifBlank { ArticleExtractor.titleFromUrl(story.link) },
                            author = null,
                            date = story.date,
                            leadImage = story.image,
                            blocks = listOf(
                                ArticleBlock.Caption(
                                    "This story couldn't be loaded here. It's at ${story.link} - " +
                                        "open the magazine in WebView to read it on the site.",
                                ),
                            ),
                        )
                    }
                }
            }
        }.awaitAll()

        val contents = Article(
            title = Issues.label(Issues.keyOf(chapter.url)),
            author = null,
            date = null,
            leadImage = articles.firstNotNullOfOrNull { it.leadImage },
            blocks = articles.map { ArticleBlock.ListItem(it.title) },
        )
        // One set for the whole issue: the cover photo is usually the first story's lead picture
        val shownImages = HashSet<String>()
        val pages = MagazinePages.paginate(
            contents,
            publication,
            shownImages = shownImages,
            byline = if (articles.size == 1) "1 story" else "${articles.size} stories",
        ).toMutableList()
        for (article in articles) {
            pages += MagazinePages.paginate(
                article,
                publication,
                firstPageNumber = pages.size + 1,
                shownImages = shownImages,
            )
        }
        pages
    }

    /** The feed's current stories by address (RSS only: they may carry whole articles), or none. */
    private suspend fun feedFor(publication: Publication): Map<String, FeedItem> {
        if (publication.kind != FeedKind.RSS) return emptyMap()
        return try {
            fetchFeed(publication).associateBy { normalize(it.link) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private suspend fun loadArticle(publication: Publication, story: ArticleRef, feed: Map<String, FeedItem>): Article {
        // The feed often has the whole article (and is cheaper than the web page); look there first.
        val link = story.link
        val item = feed[normalize(link)]
        val fromFeed = item?.let(ArticleExtractor::fromFeedItem)
        if (fromFeed != null && ArticleExtractor.isComplete(fromFeed)) return fromFeed

        val fallback = item ?: FeedItem(
            // A title made up from the address is left out, so the page's own headline is used
            title = story.title.takeUnless { hasMadeUpTitle(it, link) }.orEmpty(),
            link = link,
            date = story.date.takeIf { it > 0 },
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
                    "The rest of this story is on ${hostOf(link)}. Open it in WebView to keep reading.",
                ),
            )
        }
        return ArticleExtractor.pick(fromFeed, fromPage) ?: fromPage
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private suspend fun fetchFeed(publication: Publication): List<FeedItem> {
        val response = client.newCall(GET(publication.feed, headers)).awaitSuccess()
        val body = response.bodyString()
        val items = when (publication.kind) {
            FeedKind.RSS -> ArticleExtractor.parseFeed(body, publication.feed)
            FeedKind.SITEMAP -> ArticleExtractor.parseSitemap(body, limit = SITEMAP_ITEMS)
            FeedKind.NEXT_DATA -> ArticleExtractor.parseNextData(body, publication.feed)
        }
        if (items.isEmpty()) throw Exception("${publication.name}'s feed had no stories. Try again later.")
        return items
    }

    /** Replaces made-up titles with each article's own headline, a few pages at a time. */
    private suspend fun fillInTitles(articles: List<ArticleRef>) = coroutineScope {
        articles.chunked(TITLE_LOOKUP_BATCH).forEach { batch ->
            batch.map { article ->
                async {
                    try {
                        val html = client.newCall(GET(article.link, headers)).awaitSuccess().bodyString()
                        ArticleExtractor.pageTitle(html)?.let { article.title = it }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Keep the title made from the address.
                    }
                }
            }.awaitAll()
        }
    }

    protected fun Response.bodyString(): String = use { it.body.string() }

    private fun publicationOf(url: String): Publication =
        findPublication(url.trim('/')) ?: throw Exception("This magazine is no longer available as a built-in source.")

    private fun findPublication(slug: String): Publication? =
        publications.find { it.slug == slug } ?: CustomPublications.fromSlug(slug)

    /** Finds which of this source's publications an article belongs to: by its tag, else by its site. */
    private fun publicationForArticle(url: String): Publication {
        findPublication(url.substringAfter('#', ""))?.let { return it }
        val host = hostOf(url)
        val path = url.substringBefore('#').substringAfter(host, "")
        return publications
            .filter { hostOf(it.homepage) == host }
            // Prefer the most specific section, e.g. "Esquire UK" for esquire.com/uk/...
            .sortedByDescending { it.homepage.substringAfter(host).length }
            .firstOrNull { path.startsWith(it.homepage.substringAfter(host).trimEnd('/')) }
            ?: publications.firstOrNull { hostOf(it.homepage) == host }
            ?: publications.firstOrNull()
            ?: throw Exception("Couldn't tell which magazine this article is from. Refresh the entry and try again.")
    }

    private fun sameArticle(a: String, b: String) = normalize(a) == normalize(b)

    private fun normalize(url: String) = url.substringBefore('#').substringBefore('?').trimEnd('/')
        .removePrefix("https://").removePrefix("http://").removePrefix("www.")

    private fun hostOf(url: String) = url.removePrefix("https://").removePrefix("http://").substringBefore('/')
        .removePrefix("www.")

    override fun getMangaUrl(manga: SManga): String =
        findPublication(manga.url.trim('/'))?.homepage ?: getHomeUrl()

    override fun getChapterUrl(chapter: SChapter): String = if (Issues.isIssueUrl(chapter.url)) {
        // An issue has no page of its own; WebView opens the magazine's site
        findPublication(chapter.url.substringAfter('#', ""))?.homepage ?: getHomeUrl()
    } else {
        chapter.url.substringBefore('#')
    }

    companion object {
        private const val MAX_ARTICLES = 3000
        private const val ISSUE_FETCH_CONCURRENCY = 6
        private const val SITEMAP_ITEMS = 30
        private const val SITEMAP_BACKFILL = 60
        private const val ARCHIVE_PAGES = 3
        private const val MAX_TITLE_LOOKUPS = 24
        private const val TITLE_LOOKUP_BATCH = 6

        private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

        private fun periodName(period: IssuePeriod) = when (period) {
            IssuePeriod.MONTH -> "monthly issues"
            IssuePeriod.WEEK -> "weekly issues"
            IssuePeriod.DAY -> "daily editions"
        }

        private fun monthLabel(millis: Long): String =
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(MONTH)
    }
}

class MensStyleMagazines : ModernMagazineSource(MagazineCategory.MENS_STYLE)

class CarMagazines : ModernMagazineSource(MagazineCategory.CARS)

class DesignMagazines : ModernMagazineSource(MagazineCategory.DESIGN)

class ArtMagazines : ModernMagazineSource(MagazineCategory.ART)

class TechMagazines : ModernMagazineSource(MagazineCategory.TECH)

class TravelMagazines : ModernMagazineSource(MagazineCategory.TRAVEL)

class InteriorMagazines : ModernMagazineSource(MagazineCategory.INTERIORS)

/**
 * Any magazine with a feed: search a site's address (or its feed's) and it becomes an entry like
 * the built-in ones. The feed is found from the page's `<link rel="alternate">` tags, or by trying
 * the usual feed paths.
 */
class CustomMagazines : ModernMagazineSource("Add your own", emptyList(), "Your magazines") {

    override suspend fun getPopularManga(page: Int): MangasPage = throw Exception(HINT)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val address = toAddress(query) ?: throw Exception(HINT)
        return MangasPage(listOf(toSManga(discover(address))), hasNextPage = false)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Search a magazine's website, like monocle.com."),
        Filter.Header("Its feed is found for you; add the result to your library to follow it."),
    )

    private suspend fun discover(address: HttpUrl): Publication {
        val first = try {
            fetch(address.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw Exception("Couldn't open ${address.host}: ${e.message ?: "no response"}")
        }

        // The address may already be a feed.
        ArticleExtractor.parseFeedInfo(first.body, first.url)?.let {
            return publicationFor(first.url, it, null, feedBody = first.body)
        }

        val page = Jsoup.parse(first.body, first.url)
        val siteName = page.selectFirst("meta[property=og:site_name]")?.attr("content")?.ifBlank { null }
            ?: page.title().ifBlank { null }
        val origin = first.url.toHttpUrl().let { "${it.scheme}://${it.host}" }
        val candidates = (ArticleExtractor.feedLinksIn(first.body, first.url) + COMMON_FEED_PATHS.map { origin + it })
            .distinct()
            .take(MAX_TRIES)

        for (candidate in candidates) {
            val response = try {
                fetch(candidate)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                continue
            }
            val info = ArticleExtractor.parseFeedInfo(response.body, response.url) ?: continue
            return publicationFor(response.url, info, siteName, homepage = first.url, feedBody = response.body)
        }
        throw Exception(
            "Couldn't find a feed on ${address.host}. If the site has an RSS link (often ending in /feed or " +
                "/rss), search that instead.",
        )
    }

    private fun publicationFor(
        feed: String,
        info: FeedInfo,
        siteName: String?,
        homepage: String? = null,
        feedBody: String,
    ): Publication {
        val site = info.homepage ?: homepage ?: feed.toHttpUrl().let { "${it.scheme}://${it.host}/" }
        val host = CustomPublications.hostOf(site)
        val name = cleanName(siteName, host) ?: cleanName(info.title, host) ?: host
        return CustomPublications.create(feed, name, site, info.description, issuePeriodFor(feedBody, feed))
    }

    /** Issue size from how often the feed's stories come out, so a busy site gets weekly or daily issues. */
    private fun issuePeriodFor(feedBody: String, feed: String): IssuePeriod {
        val dates = ArticleExtractor.parseFeed(feedBody, feed).mapNotNull { it.date }
        if (dates.size < 3) return IssuePeriod.MONTH
        val spanDays = ((dates.max() - dates.min()) / 86_400_000.0).coerceAtLeast(0.25)
        return IssuePeriod.forRate(dates.size / spanDays * 30)
    }

    private class Fetched(val url: String, val body: String)

    private suspend fun fetch(url: String): Fetched {
        val response = client.newCall(GET(url, headers)).awaitSuccess()
        val finalUrl = response.request.url.toString()
        return Fetched(finalUrl, response.bodyString())
    }

    companion object {
        private const val HINT =
            "Search a magazine's website address, like monocle.com, to add it. Any site with an RSS feed works."

        private const val MAX_TRIES = 14
        private const val MAX_NAME = 48

        private val COMMON_FEED_PATHS = listOf(
            "/feed/",
            "/rss/",
            "/feed.xml",
            "/rss.xml",
            "/atom.xml",
            "/index.xml",
            "/feeds/all",
            "/rss",
            "/rss/index.xml",
            "/?feed=rss2",
            "/blog/feed/",
        )

        private val NAME_SEPARATOR = Regex("""\s+[|–—•·-]\s+|:\s+""")

        /** A typed address, with https:// added when it's left out; null for anything that isn't one. */
        fun toAddress(query: String): HttpUrl? {
            val text = query.trim()
            if (text.isEmpty() || text.any { it.isWhitespace() } || '.' !in text) return null
            val withScheme = if (text.contains("://")) text else "https://$text"
            return withScheme.toHttpUrlOrNull()?.takeIf { it.scheme == "https" || it.scheme == "http" }
        }

        /**
         * "Latest News | Monocle" -> "Monocle": of the title's parts, the one that names the site,
         * else the first. Null when there's nothing usable.
         */
        fun cleanName(title: String?, host: String): String? {
            val parts = title?.split(NAME_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            if (parts.isEmpty()) return null
            val site = host.substringBefore('.').lettersOnly()
            val best = parts.firstOrNull { part ->
                val letters = part.lettersOnly()
                letters.isNotEmpty() && (site.contains(letters) || letters.contains(site))
            } ?: parts.first()
            return if (best.length > MAX_NAME) best.take(MAX_NAME).substringBeforeLast(' ') else best
        }

        private fun String.lettersOnly() = lowercase().filter { it.isLetterOrDigit() }
    }
}

package mihon.source.archive

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import tachiyomi.domain.library.model.LibraryContentType
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * A built-in source backed by the Internet Archive's public search and BookReader APIs.
 *
 * Every archive.org item (one magazine issue or one book) becomes an entry with a single
 * chapter. Pages are streamed as images from archive.org's page renderer, so scanned PDFs and
 * EPUBs that the archive has processed read like any other chapter, with nothing saved to the
 * device unless you download it.
 */
abstract class InternetArchiveSource : HttpSource() {

    /** Type the library files this source's entries under. */
    abstract val contentType: LibraryContentType

    /** Query clause every search from this source is limited to. */
    protected abstract val baseQuery: String

    /** Sections shown in the "Section" filter: label to query clause (blank clause means no extra limit). */
    protected abstract val sections: List<Pair<String, String>>

    /** Tag added to every entry's genres so the library can tell what it is even without this source. */
    protected abstract val typeTag: String

    override val baseUrl = "https://archive.org"

    override val lang = "all"

    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true }

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mihon (Internet Archive built-in source)")

    // ---- Browsing -------------------------------------------------------------------------------

    override suspend fun getPopularManga(page: Int): MangasPage =
        search(page, query = "", filters = getFilterList(), sortOverride = "downloads desc")

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        search(page, query = "", filters = getFilterList(), sortOverride = "addeddate desc")

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        search(page, query, filters, sortOverride = null)

    private suspend fun search(page: Int, query: String, filters: FilterList, sortOverride: String?): MangasPage {
        val clauses = mutableListOf(baseQuery)

        val sectionFilter = filters.filterIsInstance<SectionFilter>().firstOrNull()
        val sectionClause = sectionFilter?.let { sections.getOrNull(it.state)?.second }
        if (!sectionClause.isNullOrBlank()) clauses += "($sectionClause)"

        if (filters.filterIsInstance<HideAdultFilter>().firstOrNull()?.state != false) {
            clauses += ADULT_EXCLUSION
        }

        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            val idMatch = ID_QUERY.matchEntire(trimmed)
            clauses += if (idMatch != null) {
                "identifier:\"${idMatch.groupValues[1]}\""
            } else {
                "(${escape(trimmed)})"
            }
        }

        val sort = sortOverride
            ?: filters.filterIsInstance<SortFilter>().firstOrNull()?.toSortParam()
            ?: if (trimmed.isEmpty()) "downloads desc" else null

        val searchUrl = "$baseUrl/advancedsearch.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", clauses.joinToString(" AND "))
            SEARCH_FIELDS.forEach { addQueryParameter("fl[]", it) }
            if (sort != null) addQueryParameter("sort[]", sort)
            addQueryParameter("rows", PAGE_SIZE.toString())
            addQueryParameter("page", page.toString())
            addQueryParameter("output", "json")
        }.build()

        val response = client.newCall(GET(searchUrl, headers)).awaitSuccess().parseJson().jsonObject
        val body = response["response"]?.jsonObject ?: return MangasPage(emptyList(), false)
        val total = body["numFound"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val mangas = body["docs"]?.jsonArray.orEmpty().map { doc ->
            val obj = doc.jsonObject
            SManga.create().apply {
                val id = obj.string("identifier")
                url = "/details/$id"
                title = obj.string("title").ifBlank { id }
                author = obj.strings("creator").joinToString().ifBlank { null }
                thumbnail_url = coverUrl(id)
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }
        return MangasPage(mangas, hasNextPage = page * PAGE_SIZE < total)
    }

    // ---- Details & chapters ---------------------------------------------------------------------

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val identifier = manga.url.substringAfterLast('/')
        val metadata = fetchMetadata(identifier)
        val meta = metadata["metadata"]?.jsonObject ?: JsonObject(emptyMap())

        val details = if (fetchDetails) {
            SManga.create().apply {
                url = manga.url
                title = meta.string("title").ifBlank { manga.title }
                author = meta.strings("creator").joinToString().ifBlank { null }
                artist = meta.strings("publisher").joinToString().ifBlank { null }
                description = buildDescription(meta)
                genre = (listOf(typeTag) + meta.strings("subject").flatMap { it.split(';') })
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }
                    .take(MAX_GENRES)
                    .joinToString()
                status = SManga.COMPLETED
                thumbnail_url = coverUrl(identifier)
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                initialized = true
            }
        } else {
            manga
        }

        val chapterList = if (fetchChapters) {
            val isRestricted = meta.string("access-restricted-item") == "true"
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = when {
                        isRestricted -> "Read (borrow-only on archive.org)"
                        else -> meta.string("date").takeIf { it.isNotBlank() }?.let { "Issue · $it" } ?: "Read"
                    }
                    chapter_number = 1f
                    date_upload = parseDate(meta.string("publicdate")) ?: parseDate(meta.string("addeddate")) ?: 0L
                },
            )
        } else {
            chapters
        }

        return SMangaUpdate(details, chapterList)
    }

    private fun buildDescription(meta: JsonObject): String? {
        val parts = buildList {
            val raw = meta.strings("description").joinToString("\n\n")
            if (raw.isNotBlank()) add(Jsoup.parse(raw).wholeText().trim())
            meta.string("date").takeIf { it.isNotBlank() }?.let { add("Date: $it") }
            meta.strings("language").takeIf { it.isNotEmpty() }?.let { add("Language: ${it.joinToString()}") }
            meta.strings("collection").takeIf { it.isNotEmpty() }?.let { add("Collections: ${it.joinToString()}") }
        }
        return parts.joinToString("\n\n").ifBlank { null }
    }

    // ---- Pages ----------------------------------------------------------------------------------

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val identifier = chapter.url.substringAfterLast('/')
        val metadata = fetchMetadata(identifier)
        val server = metadata["server"]?.jsonPrimitive?.contentOrNull
            ?: metadata["d1"]?.jsonPrimitive?.contentOrNull
            ?: throw Exception("archive.org didn't say where this item is stored. Try again later.")
        val dir = metadata["dir"]?.jsonPrimitive?.contentOrNull
            ?: throw Exception("archive.org didn't say where this item is stored. Try again later.")

        val pageImages = metadata["files"]?.jsonArray.orEmpty()
            .map { it.jsonObject.string("name") }
            .firstOrNull { name -> PAGE_ARCHIVE_SUFFIXES.any { name.endsWith(it) } }
            ?: throw Exception("This item has no page scans on archive.org, so it can't be shown as pages.")
        val subPrefix = PAGE_ARCHIVE_SUFFIXES.fold(pageImages) { name, suffix -> name.removeSuffix(suffix) }

        val pageListUrl = "https://$server/BookReader/BookReaderJSIA.php".toHttpUrl().newBuilder()
            .addQueryParameter("id", identifier)
            .addQueryParameter("itemPath", dir)
            .addQueryParameter("server", server)
            .addQueryParameter("subPrefix", subPrefix)
            .addQueryParameter("format", "json")
            .build()
        val data = client.newCall(GET(pageListUrl, headers)).awaitSuccess().parseJson().jsonObject["data"]?.jsonObject
            ?: throw Exception("archive.org returned no page list for this item.")

        val restricted = data["data"]?.jsonObject?.get("isRestricted")?.jsonPrimitive?.contentOrNull == "true"
        val spreads = data["brOptions"]?.jsonObject?.get("data")?.jsonArray.orEmpty()
        val uris = spreads.flatMap { spread ->
            (spread as? JsonArray).orEmpty().mapNotNull { it.jsonObject["uri"]?.jsonPrimitive?.contentOrNull }
        }
        if (uris.isEmpty()) {
            throw Exception(
                if (restricted) {
                    "This item is borrow-only. Borrow it on archive.org to read it."
                } else {
                    "archive.org returned no pages for this item."
                },
            )
        }

        return uris.mapIndexed { index, uri ->
            val absolute = if (uri.startsWith("//")) "https:$uri" else uri
            val separator = if ('?' in absolute) '&' else '?'
            Page(index, imageUrl = "$absolute${separator}scale=$IMAGE_SCALE&rotate=0")
        }
    }

    // ---- Filters --------------------------------------------------------------------------------

    override fun getFilterList() = FilterList(
        SectionFilter(sections.map { it.first }.toTypedArray()),
        SortFilter(),
        HideAdultFilter(),
        Filter.Separator(),
        Filter.Header("Tip: search \"id:<archive.org identifier>\" to open a specific item."),
    )

    private class SectionFilter(values: Array<String>) : Filter.Select<String>("Section", values)

    private class HideAdultFilter : Filter.CheckBox("Hide adult content", true)

    private class SortFilter : Filter.Sort(
        "Sort by",
        SORTS.map { it.first }.toTypedArray(),
        Filter.Sort.Selection(0, false),
    ) {
        fun toSortParam(): String {
            val selection = state ?: return SORTS[0].second + " desc"
            val field = SORTS.getOrNull(selection.index)?.second ?: SORTS[0].second
            return "$field ${if (selection.ascending) "asc" else "desc"}"
        }
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private suspend fun fetchMetadata(identifier: String): JsonObject {
        val response = client.newCall(GET("$baseUrl/metadata/$identifier", headers)).awaitSuccess()
        val obj = response.parseJson().jsonObject
        if (obj.isEmpty()) throw Exception("archive.org has no item called \"$identifier\".")
        return obj
    }

    private fun Response.parseJson(): JsonElement = use { json.parseToJsonElement(it.body.string()) }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    companion object {
        private const val PAGE_SIZE = 50
        private const val MAX_GENRES = 12

        /** archive.org's page renderer downsamples by this factor; 2 is about 1200px wide for a magazine page. */
        private const val IMAGE_SCALE = 2

        private val PAGE_ARCHIVE_SUFFIXES = listOf("_jp2.zip", "_jp2.tar")

        private val SEARCH_FIELDS = listOf("identifier", "title", "creator")

        private val SORTS = listOf(
            "Popularity" to "downloads",
            "Date added" to "addeddate",
            "Publication date" to "date",
            "Title" to "titleSorter",
        )

        private val ID_QUERY = Regex("""id:\s*([A-Za-z0-9._-]+)""")

        private const val ADULT_EXCLUSION =
            "-subject:(erotica OR erotic OR porn OR pornography OR adult OR nsfw OR nude OR nudity OR sex) " +
                "AND -title:(playboy OR penthouse OR hustler)"

        /** Requires page scans (so the item can be read as images) and skips borrow-only items. */
        const val READABLE =
            "mediatype:texts AND format:\"Single Page Processed JP2 ZIP\" AND -access-restricted-item:true"

        private fun coverUrl(identifier: String) = "https://archive.org/services/img/$identifier"

        /** Keeps the user's words but stops stray Lucene syntax characters from breaking the query. */
        private val LUCENE_SPECIAL = Regex("""[\\+!(){}\[\]^"~*?:/]|&&|\|\|""")

        private fun escape(query: String): String = query.replace(LUCENE_SPECIAL, " ").trim()

        private fun parseDate(value: String): Long? {
            if (value.isBlank()) return null
            return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching {
                    // archive.org usually sends "2016-01-05 12:34:56" in UTC
                    java.time.LocalDateTime.parse(value.replace(' ', 'T')).toInstant(ZoneOffset.UTC).toEpochMilli()
                }.getOrNull()
                ?: runCatching {
                    LocalDate.parse(value.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
                }.getOrNull()
        }

        private fun JsonObject.string(key: String): String = strings(key).firstOrNull().orEmpty()

        /** archive.org fields can be a string or a list of strings depending on the item. */
        private fun JsonObject.strings(key: String): List<String> = when (val value = this[key]) {
            is JsonPrimitive -> listOfNotNull(value.contentOrNull)
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            else -> emptyList()
        }
    }
}

/** Magazines from the Internet Archive's Magazine Rack, with sections for fashion, art, cars and more. */
class InternetArchiveMagazines : InternetArchiveSource() {
    override val name = "Internet Archive: Magazines"
    override val contentType = LibraryContentType.MAGAZINE
    override val typeTag = "Magazine"
    override val baseQuery = "collection:magazine_rack AND $READABLE"
    override val sections = listOf(
        "All magazines" to "",
        "Men's fashion & style" to
            "subject:(menswear OR \"mens fashion\" OR \"men's fashion\" OR \"men fashion\" OR \"mens style\" OR " +
            "\"men's style\" OR \"men's magazine\" OR \"mens magazine\") OR title:(GQ OR Esquire OR " +
            "\"Arena Homme\" OR \"Men's Health\" OR \"Fantastic Man\" OR Monocle OR \"Men's Journal\")",
        "Fashion" to
            "subject:(fashion OR style OR clothing OR couture) OR " +
            "title:(Vogue OR \"Harper's Bazaar\" OR Elle OR \"i-D\")",
        "Art & design" to
            "subject:(art OR \"fine art\" OR painting OR sculpture OR \"contemporary art\" OR design OR " +
            "\"graphic design\" OR illustration) OR title:(Artforum OR Frieze OR \"Art in America\" OR Juxtapoz)",
        "Photography" to
            "subject:(photography OR photographs OR camera) OR title:(Aperture OR \"Popular Photography\")",
        "Architecture & interiors" to "subject:(architecture OR \"interior design\" OR interiors OR \"home design\")",
        "Cars & motoring" to
            "subject:(cars OR car OR automobile OR automobiles OR automotive OR \"hot rod\" OR motorsport OR " +
            "\"motor racing\" OR motoring) OR title:(\"Road & Track\" OR \"Car and Driver\" OR \"Motor Trend\" OR " +
            "\"Top Gear\" OR Autocar OR evo OR \"Classic & Sports Car\")",
        "Motorcycles" to "subject:(motorcycle OR motorcycles OR motorbike OR motorbikes)",
        "Music" to "subject:(music OR rock OR jazz OR \"hip hop\") OR title:(\"Rolling Stone\" OR Spin OR Mojo OR NME)",
        "Computers & tech" to "subject:(computers OR computing OR technology OR electronics OR \"video games\")",
        "Science & nature" to "subject:(science OR nature OR astronomy OR wildlife)",
    )
}

/** Public-domain scanned books from the Internet Archive. */
class InternetArchiveBooks : InternetArchiveSource() {
    override val name = "Internet Archive: Books"
    override val contentType = LibraryContentType.BOOK
    override val typeTag = "Book"
    override val baseQuery = "-collection:magazine_rack AND -collection:comics AND $READABLE"
    override val sections = listOf(
        "All books" to "",
        "Fiction" to "subject:(fiction OR novel OR novels OR \"short stories\")",
        "Art & design" to "subject:(art OR painting OR design OR architecture OR photography)",
        "Fashion & costume" to "subject:(fashion OR costume OR clothing OR tailoring OR dress)",
        "Cars & engineering" to "subject:(automobiles OR automobile OR engineering OR mechanics OR motor)",
        "History" to "subject:(history OR biography)",
        "Science" to "subject:(science OR mathematics OR physics OR chemistry OR biology OR astronomy)",
        "Philosophy" to "subject:(philosophy OR ethics)",
        "Cookbooks" to "subject:(cooking OR cookery OR recipes)",
    )
}

/** Built-in online sources that ship with the app, alongside the local source. */
object BuiltinSources {
    val all: List<InternetArchiveSource> by lazy { listOf(InternetArchiveMagazines(), InternetArchiveBooks()) }

    /** Content type for entries from a built-in source, or null if the source isn't one of them. */
    fun contentTypeOf(sourceId: Long): LibraryContentType? = all.find { it.id == sourceId }?.contentType
}

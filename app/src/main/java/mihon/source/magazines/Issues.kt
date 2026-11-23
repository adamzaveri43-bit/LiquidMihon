package mihon.source.magazines

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * How a magazine's stories are bundled into issues. Each issue is one chapter, so it should hold few
 * enough stories to open quickly: busy sites get weekly issues or daily editions.
 */
enum class IssuePeriod {
    MONTH,
    WEEK,
    DAY,
    ;

    companion object {
        /** The period that keeps issues to a comfortable size for a site publishing [perMonth] stories. */
        fun forRate(perMonth: Double): IssuePeriod = when {
            perMonth <= MONTHLY_MAX -> MONTH
            perMonth <= WEEKLY_MAX -> WEEK
            else -> DAY
        }

        private const val MONTHLY_MAX = 60.0
        private const val WEEKLY_MAX = 250.0
    }
}

/** One story in an issue: what's needed to list it and to fetch it when the issue is read. */
data class ArticleRef(
    val link: String,
    var title: String,
    val date: Long,
    val image: String?,
)

object Issues {

    private const val PATH_PREFIX = "/issue/"

    private val MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val WEEK_LABEL = DateTimeFormatter.ofPattern("'Week of' d MMMM yyyy", Locale.ENGLISH)
    private val DAY_LABEL = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH)

    /**
     * The issue a story dated [millis] belongs to, as a stable key: "m2026-11", "w2026-11-16" (the
     * Monday) or "d2026-11-22". Dates are taken in UTC so keys never shift with the phone's time zone.
     */
    fun keyFor(millis: Long, period: IssuePeriod): String {
        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        return when (period) {
            IssuePeriod.MONTH -> "m" + date.toString().take(7)
            IssuePeriod.WEEK -> "w" + date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            IssuePeriod.DAY -> "d$date"
        }
    }

    /** "November 2026", "Week of 16 November 2026" or "Sunday 22 November 2026". */
    fun label(key: String): String {
        val body = key.drop(1)
        return runCatching {
            when (key.first()) {
                'm' -> LocalDate.parse("$body-01").format(MONTH_LABEL)
                'w' -> LocalDate.parse(body).format(WEEK_LABEL)
                else -> LocalDate.parse(body).format(DAY_LABEL)
            }
        }.getOrDefault(body)
    }

    fun chapterUrl(key: String, publication: Publication) = "$PATH_PREFIX$key#${publication.slug}"

    fun isIssueUrl(url: String) = url.startsWith(PATH_PREFIX)

    fun keyOf(url: String) = url.removePrefix(PATH_PREFIX).substringBefore('#')

    // The issue's stories travel with the chapter in its memo, so reading it needs no other lookup.

    fun encode(articles: List<ArticleRef>): JsonObject = buildJsonObject {
        put("v", 1)
        put(
            "a",
            buildJsonArray {
                articles.forEach { article ->
                    add(
                        buildJsonObject {
                            put("u", article.link)
                            put("t", article.title)
                            put("d", article.date)
                            article.image?.let { put("i", it) }
                        },
                    )
                }
            },
        )
    }

    fun decode(memo: JsonObject): List<ArticleRef> {
        val list = memo["a"] as? JsonArray ?: return emptyList()
        return list.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val link = (obj["u"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            ArticleRef(
                link = link,
                title = (obj["t"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                date = (obj["d"] as? JsonPrimitive)?.longOrNull ?: 0L,
                image = (obj["i"] as? JsonPrimitive)?.contentOrNull,
            )
        }
    }
}

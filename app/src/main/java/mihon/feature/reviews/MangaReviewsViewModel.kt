package mihon.feature.reviews

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.network.okHttpClient
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.graphql.anilist.AniListGetMangaReviewsQuery
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.track.interactor.GetTracks

@AssistedInject
class MangaReviewsViewModel(
    @Assisted private val mangaId: Long,
    private val getManga: GetManga,
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
    network: NetworkHelper,
) : ViewModel() {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    interface Factory : ManualViewModelAssistedFactory {
        fun create(mangaId: Long): MangaReviewsViewModel
    }

    // Public AniList data needs no authentication
    private val graphQlClient = ApolloClient.Builder()
        .serverUrl("https://graphql.anilist.co")
        .okHttpClient(network.client)
        .dispatcher(Dispatchers.IO)
        .build()

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = State.Loading
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = try {
                fetch()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to load reviews" }
                State.Error(e.message)
            }
        }
    }

    fun toggleSpoilers() {
        _state.update { if (it is State.Success) it.copy(showSpoilers = !it.showSpoilers) else it }
    }

    private suspend fun fetch(): State {
        val manga = getManga.await(mangaId) ?: return State.NotFound(title = null)
        // Prefer the exact entry the user linked through the AniList tracker, fall back to a title search
        val aniListId = getTracks.await(mangaId)
            .firstOrNull { it.trackerId == trackerManager.aniList.id }
            ?.remoteId
            ?.toInt()

        val query = if (aniListId != null) {
            AniListGetMangaReviewsQuery(id = Optional.present(aniListId))
        } else {
            AniListGetMangaReviewsQuery(search = Optional.present(manga.title))
        }
        val response = graphQlClient.query(query).execute()
        val media = response.data?.Media
        if (media == null) {
            // AniList answers a search without matches with a "Not Found" error
            val notFound = response.errors.orEmpty().any { it.message.contains("not found", ignoreCase = true) }
            if (notFound || response.exception == null) return State.NotFound(title = manga.title)
            throw response.exception!!
        }

        return State.Success(
            title = media.title?.userPreferred ?: manga.title,
            matchedByTracker = aniListId != null,
            siteUrl = media.siteUrl,
            description = media.description?.let(::cleanAniListText),
            averageScore = media.averageScore,
            meanScore = media.meanScore,
            popularity = media.popularity,
            favourites = media.favourites,
            genres = media.genres.orEmpty().filterNotNull(),
            rankings = media.rankings.orEmpty().filterNotNull()
                .sortedWith(
                    compareByDescending<AniListGetMangaReviewsQuery.Ranking> {
                        it.allTime == true
                    }.thenBy { it.rank },
                )
                .take(4)
                .map { "#${it.rank} ${it.context.replaceFirstChar(Char::uppercase)}" },
            tags = media.tags.orEmpty().filterNotNull()
                .sortedByDescending { it.rank ?: 0 }
                .map {
                    Tag(
                        name = it.name,
                        description = it.description,
                        rank = it.rank ?: 0,
                        isSpoiler = it.isMediaSpoiler == true,
                    )
                },
            reviews = media.reviews?.nodes.orEmpty().filterNotNull().map {
                Review(
                    id = it.id,
                    userName = it.user?.name,
                    userAvatar = it.user?.avatar?.medium,
                    summary = it.summary,
                    body = it.body?.let(::cleanAniListText).orEmpty(),
                    score = it.score,
                    helpful = it.rating,
                    helpfulTotal = it.ratingAmount,
                    siteUrl = it.siteUrl,
                    createdAtEpochSeconds = it.createdAt.toLong(),
                )
            },
        )
    }

    sealed interface State {
        data object Loading : State

        data class Error(val message: String?) : State

        data class NotFound(val title: String?) : State

        @Immutable
        data class Success(
            val title: String,
            val matchedByTracker: Boolean,
            val siteUrl: String?,
            val description: String?,
            val averageScore: Int?,
            val meanScore: Int?,
            val popularity: Int?,
            val favourites: Int?,
            val genres: List<String>,
            val rankings: List<String>,
            val tags: List<Tag>,
            val reviews: List<Review>,
            val showSpoilers: Boolean = false,
        ) : State
    }

    @Immutable
    data class Tag(
        val name: String,
        val description: String?,
        val rank: Int,
        val isSpoiler: Boolean,
    )

    @Immutable
    data class Review(
        val id: Int,
        val userName: String?,
        val userAvatar: String?,
        val summary: String?,
        val body: String,
        val score: Int?,
        val helpful: Int?,
        val helpfulTotal: Int?,
        val siteUrl: String?,
        val createdAtEpochSeconds: Long,
    )
}

private val ANILIST_MEDIA = Regex("""(?:img|webm|youtube)\d*%?\(([^)]*)\)""", RegexOption.IGNORE_CASE)
private val ANILIST_CENTER = Regex("""~{3}""")
private val ANILIST_SPOILER = Regex("""~!(.*?)!~""", RegexOption.DOT_MATCHES_ALL)
private val HTML_BREAK = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val HTML_TAG = Regex("""</?[a-zA-Z][^>]*>""")
private val EXCESS_NEWLINES = Regex("""\n{3,}""")

/**
 * Turns AniList flavoured markdown into plain CommonMark: drops embedded media and layout markers, strips
 * leftover HTML and marks spoilers so the UI can hide them.
 */
internal fun cleanAniListText(text: String): String {
    return text
        .replace(ANILIST_MEDIA, "")
        .replace(ANILIST_SPOILER) { "$SPOILER_START${it.groupValues[1]}$SPOILER_END" }
        .replace(ANILIST_CENTER, "")
        .replace(HTML_BREAK, "\n")
        .replace(HTML_TAG, "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace(EXCESS_NEWLINES, "\n\n")
        .trim()
}

internal const val SPOILER_START = "⁣S⁣"
internal const val SPOILER_END = "⁣E⁣"
private val SPOILER = Regex("""${SPOILER_START}(.*?)$SPOILER_END""", RegexOption.DOT_MATCHES_ALL)

internal fun String.withSpoilers(show: Boolean): String {
    return replace(SPOILER) { if (show) it.groupValues[1] else "*[spoiler hidden]*" }
}

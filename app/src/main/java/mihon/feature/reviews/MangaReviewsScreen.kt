package mihon.feature.reviews

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.manga.components.MarkdownRender
import eu.kanade.presentation.util.Screen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Public
import mihon.icons.materialsymbols.rounded.Refresh
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import java.text.DateFormat
import java.util.Date

data class MangaReviewsScreen(
    private val mangaId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val uriHandler = LocalUriHandler.current
        val viewModel = assistedMetroViewModel<MangaReviewsViewModel, MangaReviewsViewModel.Factory> {
            create(mangaId = mangaId)
        }
        val state by viewModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                val success = state as? MangaReviewsViewModel.State.Success
                AppBar(
                    title = stringResource(MR.strings.action_reviews),
                    subtitle = success?.title,
                    navigateUp = navigator::pop,
                    actions = {
                        val siteUrl = success?.siteUrl
                        if (siteUrl != null) {
                            AppBarActions(
                                listOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_open_in_browser),
                                        icon = MaterialSymbols.Rounded.Public,
                                        onClick = { uriHandler.openUri(siteUrl) },
                                    ),
                                ),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when (val s = state) {
                MangaReviewsViewModel.State.Loading -> LoadingScreen(Modifier.padding(contentPadding))
                is MangaReviewsViewModel.State.Error -> EmptyScreen(
                    message = s.message ?: stringResource(MR.strings.reviews_error),
                    modifier = Modifier.padding(contentPadding),
                    actions = listOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.action_retry,
                            icon = MaterialSymbols.Rounded.Refresh,
                            onClick = viewModel::load,
                        ),
                    ),
                )
                is MangaReviewsViewModel.State.NotFound -> EmptyScreen(
                    message = stringResource(MR.strings.reviews_not_found),
                    modifier = Modifier.padding(contentPadding),
                )
                is MangaReviewsViewModel.State.Success -> ReviewsContent(
                    state = s,
                    contentPadding = contentPadding,
                    onToggleSpoilers = viewModel::toggleSpoilers,
                    onOpenUrl = uriHandler::openUri,
                )
            }
        }
    }
}

@Composable
private fun ReviewsContent(
    state: MangaReviewsViewModel.State.Success,
    contentPadding: PaddingValues,
    onToggleSpoilers: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        item(key = "overview") {
            OverviewCard(state)
        }

        val tags = state.tags.filter { state.showSpoilers || !it.isSpoiler }
        if (tags.isNotEmpty()) {
            item(key = "themes") {
                ThemesCard(tags = tags)
            }
        }

        if (!state.description.isNullOrBlank()) {
            item(key = "synopsis") {
                SectionCard(title = stringResource(MR.strings.reviews_synopsis)) {
                    MarkdownRender(
                        content = state.description.withSpoilers(state.showSpoilers),
                        loadImages = false,
                    )
                }
            }
        }

        item(key = "reviews_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(MR.strings.reviews_community),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onToggleSpoilers) {
                    val labelRes = if (state.showSpoilers) {
                        MR.strings.reviews_hide_spoilers
                    } else {
                        MR.strings.reviews_show_spoilers
                    }
                    Text(text = stringResource(labelRes))
                }
            }
        }

        if (state.reviews.isEmpty()) {
            item(key = "no_reviews") {
                Text(
                    text = stringResource(MR.strings.reviews_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                )
            }
        }

        items(items = state.reviews, key = { it.id }) { review ->
            ReviewCard(
                review = review,
                showSpoilers = state.showSpoilers,
                onOpenUrl = onOpenUrl,
            )
        }

        item(key = "attribution") {
            Text(
                text = stringResource(
                    if (state.matchedByTracker) MR.strings.reviews_source_tracker else MR.strings.reviews_source_search,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(MaterialTheme.padding.medium),
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String?,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.padding.medium)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = MaterialTheme.padding.small),
                )
            }
            content()
        }
    }
}

@Composable
private fun OverviewCard(state: MangaReviewsViewModel.State.Success) {
    SectionCard(title = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.averageScore?.let { "$it%" } ?: "–",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(MR.strings.reviews_average_score),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Stat(value = state.popularity, label = stringResource(MR.strings.reviews_members))
            Spacer(modifier = Modifier.width(MaterialTheme.padding.large))
            Stat(value = state.favourites, label = stringResource(MR.strings.reviews_favourites))
        }
        if (state.rankings.isNotEmpty() || state.genres.isNotEmpty()) {
            Spacer(modifier = Modifier.height(MaterialTheme.padding.small))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                state.rankings.forEach { ranking ->
                    AssistChip(onClick = {}, label = { Text(ranking) })
                }
                state.genres.forEach { genre ->
                    SuggestionChip(onClick = {}, label = { Text(genre) })
                }
            }
        }
    }
}

@Composable
private fun Stat(value: Int?, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value?.let(::formatCount) ?: "–",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Community-voted themes with how strongly they apply to this title, a quick read on what the work is about.
 */
@Composable
private fun ThemesCard(tags: List<MangaReviewsViewModel.Tag>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(MR.strings.reviews_themes)) {
        Column(
            modifier = Modifier.animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            val shown = if (expanded) tags else tags.take(6)
            shown.forEach { tag ->
                var showDescription by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = tag.description != null) { showDescription = !showDescription },
                ) {
                    Row {
                        Text(
                            text = tag.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${tag.rank}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { tag.rank / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        drawStopIndicator = {},
                    )
                    if (showDescription && tag.description != null) {
                        Text(
                            text = tag.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            if (tags.size > 6) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(if (expanded) MR.strings.manga_info_collapse else MR.strings.manga_info_expand))
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    review: MangaReviewsViewModel.Review,
    showSpoilers: Boolean,
    onOpenUrl: (String) -> Unit,
) {
    var expanded by rememberSaveable(review.id) { mutableStateOf(false) }
    val body = remember(review.body, showSpoilers) { review.body.withSpoilers(showSpoilers) }
    SectionCard(title = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = review.userAvatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
            )
            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = review.userName.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = remember(review.createdAtEpochSeconds) {
                        DateFormat.getDateInstance(DateFormat.MEDIUM)
                            .format(Date(review.createdAtEpochSeconds * 1000))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (review.score != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = "${review.score}/100",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (!review.summary.isNullOrBlank()) {
            Text(
                text = review.summary,
                style = MaterialTheme.typography.titleSmall,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = MaterialTheme.padding.small),
            )
        }

        Box(
            modifier = Modifier
                .padding(top = MaterialTheme.padding.small)
                .animateContentSize(),
        ) {
            if (expanded) {
                MarkdownRender(content = body, loadImages = false)
            } else {
                Text(
                    text = body.take(PREVIEW_LENGTH).replace("*", "").replace("#", ""),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (review.helpful != null && review.helpfulTotal != null && review.helpfulTotal > 0) {
                Text(
                    text = stringResource(MR.strings.reviews_helpful, review.helpful, review.helpfulTotal),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            if (expanded && review.siteUrl != null) {
                TextButton(onClick = { onOpenUrl(review.siteUrl) }) {
                    Text(stringResource(MR.strings.action_open_in_browser))
                }
            }
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) MR.strings.manga_info_collapse else MR.strings.manga_info_expand))
            }
        }
    }
}

private const val PREVIEW_LENGTH = 600

private fun formatCount(value: Int): String = when {
    value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
    value >= 1_000 -> "%.1fK".format(value / 1_000f)
    else -> value.toString()
}

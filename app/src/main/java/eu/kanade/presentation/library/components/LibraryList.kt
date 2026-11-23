package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.library.folder.LibraryEntry
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.util.plus

@Composable
internal fun LibraryList(
    entries: List<LibraryEntry>,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickFolder: (LibraryEntry.Folder) -> Unit,
    onLongClickFolder: (LibraryEntry.Folder) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item {
            if (!searchQuery.isNullOrEmpty()) {
                GlobalSearchItem(
                    modifier = Modifier.fillMaxWidth(),
                    searchQuery = searchQuery,
                    onClick = onGlobalSearchClicked,
                )
            }
        }

        items(
            items = entries,
            key = { it.key },
            contentType = { if (it is LibraryEntry.Folder) "library_folder_item" else "library_list_item" },
        ) { entry ->
            AnimatedLibraryItem(modifier = Modifier.animateItem()) {
                when (entry) {
                    is LibraryEntry.Folder -> FolderListItem(
                        folder = entry,
                        onClick = { onClickFolder(entry) },
                        onLongClick = { onLongClickFolder(entry) },
                    )
                    is LibraryEntry.Manga -> {
                        val libraryItem = entry.item
                        val manga = libraryItem.libraryManga.manga
                        MangaListItem(
                            isSelected = manga.id in selection,
                            title = manga.title,
                            coverData = MangaCover(
                                mangaId = manga.id,
                                sourceId = manga.source,
                                isMangaFavorite = manga.favorite,
                                url = manga.thumbnailUrl,
                                lastModified = manga.coverLastModified,
                            ),
                            badge = {
                                DownloadsBadge(count = libraryItem.badges.downloadCount)
                                UnreadBadge(count = libraryItem.badges.unreadCount)
                                LanguageBadge(
                                    isLocal = libraryItem.badges.isLocal,
                                    sourceLanguage = libraryItem.badges.sourceLanguage,
                                )
                            },
                            onLongClick = { onLongClick(libraryItem.libraryManga) },
                            onClick = { onClick(libraryItem.libraryManga) },
                            onClickContinueReading = if (onClickContinueReading != null &&
                                libraryItem.unreadCount > 0
                            ) {
                                { onClickContinueReading(libraryItem.libraryManga) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

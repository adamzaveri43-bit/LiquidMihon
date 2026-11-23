package eu.kanade.presentation.library.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.library.folder.LibraryEntry
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.roundedfilled.Folder
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup

/**
 * A folder's cover: its image with the edges of more pages peeking out above it.
 */
@Composable
private fun FolderCoverStack(
    folder: LibraryEntry.Folder,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.extraSmall
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 6.dp, top = 4.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
        )
        MangaCover.Book(
            data = folder.coverData,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
        )
    }
}

@Composable
private fun FolderBadges(folder: LibraryEntry.Folder) {
    BadgeGroup {
        Badge(imageVector = MaterialSymbols.RoundedFilled.Folder)
        Badge(
            text = "${folder.items.size}",
            color = MaterialTheme.colorScheme.secondaryContainer,
            textColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        UnreadBadge(count = folder.unreadCount)
    }
}

@Composable
internal fun FolderCompactGridItem(
    folder: LibraryEntry.Folder,
    showTitle: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    GridItemSelectable(isSelected = false, onClick = onClick, onLongClick = onLongClick) {
        MangaGridCover(
            cover = { FolderCoverStack(folder = folder, modifier = Modifier.fillMaxSize()) },
            content = {
                if (showTitle) CoverTextOverlay(title = folder.folder.name)
                Box(modifier = Modifier.padding(start = 4.dp, top = 12.dp).align(Alignment.TopStart)) {
                    FolderBadges(folder)
                }
            },
        )
    }
}

@Composable
internal fun FolderComfortableGridItem(
    folder: LibraryEntry.Folder,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    GridItemSelectable(isSelected = false, onClick = onClick, onLongClick = onLongClick) {
        Column {
            MangaGridCover(
                cover = { FolderCoverStack(folder = folder, modifier = Modifier.fillMaxSize()) },
                content = {
                    Box(modifier = Modifier.padding(start = 4.dp, top = 12.dp).align(Alignment.TopStart)) {
                        FolderBadges(folder)
                    }
                },
            )
            GridItemTitle(
                modifier = Modifier.padding(4.dp),
                title = folder.folder.name,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
            )
        }
    }
}

@Composable
internal fun FolderListItem(
    folder: LibraryEntry.Folder,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.fillMaxHeight()) {
            MangaCover.Square(
                modifier = Modifier.fillMaxHeight(),
                data = folder.coverData,
            )
            Badge(
                imageVector = MaterialSymbols.RoundedFilled.Folder,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        }
        Text(
            text = folder.folder.name,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
        BadgeGroup {
            Badge(
                text = "${folder.items.size}",
                color = MaterialTheme.colorScheme.secondaryContainer,
                textColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            UnreadBadge(count = folder.unreadCount)
        }
    }
}

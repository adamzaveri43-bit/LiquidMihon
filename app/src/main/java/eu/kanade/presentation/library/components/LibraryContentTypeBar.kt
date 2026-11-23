package eu.kanade.presentation.library.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.icerock.moko.resources.StringResource
import tachiyomi.domain.library.model.LibraryContentType
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Row of chips that splits the library into Manga, Books and Magazines.
 *
 * @param selected the section being shown, or null for the whole library
 * @param counts how many entries of each type match the current search and filters
 */
@Composable
fun LibraryContentTypeBar(
    selected: LibraryContentType?,
    counts: Map<LibraryContentType, Int>,
    onSelect: (LibraryContentType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ContentTypeChip(
            label = stringResource(MR.strings.content_type_all),
            count = counts.values.sum(),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        LibraryContentType.entries.forEach { type ->
            ContentTypeChip(
                label = stringResource(type.sectionLabel),
                count = counts[type] ?: 0,
                selected = selected == type,
                onClick = { onSelect(type) },
            )
        }
    }
}

@Composable
private fun ContentTypeChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = "$label · $count") },
    )
}

/** Plural label used for a library section. */
val LibraryContentType.sectionLabel: StringResource
    get() = when (this) {
        LibraryContentType.MANGA -> MR.strings.content_type_manga
        LibraryContentType.BOOK -> MR.strings.content_type_books
        LibraryContentType.MAGAZINE -> MR.strings.content_type_magazines
    }

/** Singular label used when filing one entry under a type. */
val LibraryContentType.itemLabel: StringResource
    get() = when (this) {
        LibraryContentType.MANGA -> MR.strings.content_type_manga
        LibraryContentType.BOOK -> MR.strings.content_type_book
        LibraryContentType.MAGAZINE -> MR.strings.content_type_magazine
    }

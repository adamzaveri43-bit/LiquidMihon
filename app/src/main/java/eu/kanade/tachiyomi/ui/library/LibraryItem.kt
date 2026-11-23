package eu.kanade.tachiyomi.ui.library

import tachiyomi.domain.library.model.LibraryContentType
import tachiyomi.domain.library.model.LibraryManga

data class LibraryItem(
    val libraryManga: LibraryManga,
    val downloadCount: Int,
    val unreadCount: Long,
    val isLocal: Boolean,
    val sourceName: String,
    val sourceLanguage: String,
    val contentType: LibraryContentType = LibraryContentType.MANGA,
    val badges: Badges,
) {
    val id: Long = libraryManga.id

    data class Badges(
        val downloadCount: Int,
        val unreadCount: Long,
        val isLocal: Boolean,
        val sourceLanguage: String,
    )
}

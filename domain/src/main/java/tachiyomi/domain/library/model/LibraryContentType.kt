package tachiyomi.domain.library.model

/**
 * What kind of reading material a library entry is. Used to split the library into
 * separate Manga, Books and Magazines sections.
 */
enum class LibraryContentType {
    MANGA,
    BOOK,
    MAGAZINE,
    ;

    companion object {
        /** Genre tags that built-in sources (and some extensions) use to mark an entry's type. */
        private val MAGAZINE_TAGS = setOf("magazine", "magazines", "periodical", "periodicals", "zine")
        private val BOOK_TAGS = setOf("book", "books", "ebook", "e-book", "novel", "novels", "light novel", "web novel")

        /**
         * Classifies an entry.
         *
         * @param override a type the user picked for this entry, which wins over everything else
         * @param sourceType the type the entry's source declares for all its entries, if any
         * @param genres the entry's genre tags
         */
        fun classify(
            override: LibraryContentType?,
            sourceType: LibraryContentType?,
            genres: List<String>?,
        ): LibraryContentType {
            if (override != null) return override
            if (sourceType != null) return sourceType
            val tags = genres.orEmpty().map { it.trim().lowercase() }
            return when {
                tags.any { it in MAGAZINE_TAGS } -> MAGAZINE
                tags.any { it in BOOK_TAGS } -> BOOK
                else -> MANGA
            }
        }

        /** Encodes per-entry overrides as "mangaId:TYPE" strings so they fit a string set preference. */
        fun decodeOverrides(raw: Set<String>): Map<Long, LibraryContentType> = raw.mapNotNull { entry ->
            val (id, type) = entry.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val mangaId = id.toLongOrNull() ?: return@mapNotNull null
            val contentType = entries.find { it.name == type } ?: return@mapNotNull null
            mangaId to contentType
        }.toMap()

        fun encodeOverrides(overrides: Map<Long, LibraryContentType>): Set<String> =
            overrides.mapTo(mutableSetOf()) { (id, type) -> "$id:${type.name}" }
    }
}

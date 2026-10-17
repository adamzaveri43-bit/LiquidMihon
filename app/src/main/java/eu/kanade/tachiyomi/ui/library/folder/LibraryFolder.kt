package eu.kanade.tachiyomi.ui.library.folder

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.MangaCover
import java.io.File

/**
 * A folder grouping library entries, e.g. all volumes of a series, shown as a single tile in the library.
 *
 * A manga is in at most one folder. [mangaIds] is the order the folder's contents are shown in.
 */
@Immutable
@Serializable
data class LibraryFolder(
    val id: Long,
    val name: String,
    val mangaIds: List<Long> = emptyList(),
    val cover: FolderCover = FolderCover.Auto,
) {
    val key: String get() = folderKey(id)
}

@Immutable
@Serializable
sealed interface FolderCover {
    /** The cover of the folder's first entry. */
    @Serializable
    @SerialName("auto")
    data object Auto : FolderCover

    /** The cover of one of the folder's entries. */
    @Serializable
    @SerialName("manga")
    data class Manga(val mangaId: Long) : FolderCover

    /** An image picked by the user, copied into app storage. */
    @Serializable
    @SerialName("image")
    data class Image(val path: String) : FolderCover
}

/**
 * What the library shows at one position: a manga, or a folder of them.
 */
@Immutable
sealed interface LibraryEntry {
    val key: String

    data class Manga(val item: LibraryItem) : LibraryEntry {
        override val key: String = mangaKey(item.id)
    }

    data class Folder(
        val folder: LibraryFolder,
        /** The folder's entries shown in the current category, in the folder's order. */
        val items: List<LibraryItem>,
    ) : LibraryEntry {
        override val key: String = folder.key

        /** Unread chapters across the entries, when the unread badge is on. */
        val unreadCount: Long = items.sumOf { it.badges.unreadCount }

        /** Data for the cover image, a [MangaCover] or a [File]. */
        val coverData: Any? = when (val cover = folder.cover) {
            is FolderCover.Image -> File(cover.path)
            is FolderCover.Manga -> (items.find { it.id == cover.mangaId } ?: items.firstOrNull())?.coverData()
            FolderCover.Auto -> items.firstOrNull()?.coverData()
        }
    }
}

fun mangaKey(mangaId: Long) = "m$mangaId"
fun folderKey(folderId: Long) = "f$folderId"

fun LibraryItem.coverData(): MangaCover {
    val manga = libraryManga.manga
    return MangaCover(
        mangaId = manga.id,
        sourceId = manga.source,
        isMangaFavorite = manga.favorite,
        url = manga.thumbnailUrl,
        lastModified = manga.coverLastModified,
    )
}

object LibraryFolders {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(LibraryFolder.serializer())

    fun parse(serialized: String): List<LibraryFolder> {
        if (serialized.isBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, serialized)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to read library folders" }
            emptyList()
        }
    }

    fun serialize(folders: List<LibraryFolder>): String = json.encodeToString(serializer, folders)

    fun parseOrder(serialized: String): List<String> = serialized.split(',').filter { it.isNotBlank() }

    fun serializeOrder(order: List<String>): String = order.joinToString(",")

    /**
     * Groups [items] (already sorted) into what the library shows: folders and the manga not in any.
     *
     * With the custom sort the entries follow [customOrder], otherwise folders come first by name.
     */
    fun group(
        items: List<LibraryItem>,
        folders: List<LibraryFolder>,
        customOrder: List<String>?,
        descending: Boolean,
    ): List<LibraryEntry> {
        if (folders.isEmpty()) return items.map(LibraryEntry::Manga)
        val itemsById = items.associateBy { it.id }
        val inFolder = HashSet<Long>()
        val folderEntries = folders.mapNotNull { folder ->
            val folderItems = folder.mangaIds.mapNotNull { itemsById[it] }
            if (folderItems.isEmpty()) return@mapNotNull null
            folderItems.mapTo(inFolder) { it.id }
            LibraryEntry.Folder(folder, folderItems)
        }
        val mangaEntries = items.filter { it.id !in inFolder }.map(LibraryEntry::Manga)
        if (customOrder == null) {
            return folderEntries.sortedBy { it.folder.name.lowercase() } + mangaEntries
        }
        val positions = HashMap<String, Int>(customOrder.size)
        customOrder.forEachIndexed { index, key -> positions.putIfAbsent(key, index) }
        // Stable, so anything not arranged yet keeps its order at the end
        val entries = (folderEntries + mangaEntries).sortedBy { positions[it.key] ?: Int.MAX_VALUE }
        return if (descending) entries.reversed() else entries
    }
}

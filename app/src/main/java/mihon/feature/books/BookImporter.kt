package mihon.feature.books

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dev.zacsweers.metro.Inject
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.source.local.LocalSource

/**
 * Brings an EPUB or PDF opened from another app (Files, a browser's downloads, an email) into the library:
 * the file is copied into the local source folder as a book of its own and added to the library, where it's
 * filed under Books.
 */
@Inject
class BookImporter(
    private val context: Context,
    private val storageManager: StorageManager,
    private val networkToLocalManga: NetworkToLocalManga,
    private val updateManga: UpdateManga,
) {

    /** Whether [uri] (with the type the sending app gave, if any) looks like a book this importer can take. */
    fun canImport(uri: Uri, mimeType: String?): Boolean {
        val type = mimeType ?: context.contentResolver.getType(uri)
        return type in BOOK_TYPES || extensionOf(displayName(uri)) != null
    }

    /** Copies the book in and adds it to the library. Returns the library entry's id. */
    suspend fun import(uri: Uri, mimeType: String?): Long = withContext(Dispatchers.IO) {
        val folder = storageManager.getLocalSourceDirectory()
            ?: throw IllegalStateException(
                "Choose a storage location first (More › Settings › Data and storage), then open the book again.",
            )
        val type = mimeType ?: context.contentResolver.getType(uri)
        val original = displayName(uri)?.replace('/', '_')?.trim().orEmpty()
        val extension = extensionOf(original) ?: BOOK_TYPES[type]
            ?: throw IllegalArgumentException("This file isn't an EPUB or PDF.")
        val baseName = original.removeSuffix(".$extension").removeSuffix(".${extension.uppercase()}")
            .ifBlank { "Book" }

        val size = fileSize(uri)
        // Opening the same book again reuses the copy already there instead of piling up duplicates
        var name = "$baseName.$extension"
        var counter = 2
        while (true) {
            val existing = folder.findFile(name) ?: break
            if (size != null && existing.length() == size) break
            name = "$baseName ($counter).$extension"
            counter++
        }
        if (folder.findFile(name) == null) {
            val target = folder.createFile(name) ?: throw IllegalStateException("Couldn't save the book to $name.")
            context.contentResolver.openInputStream(uri).use { input ->
                checkNotNull(input) { "Couldn't read the file." }
                target.openOutputStream().use { output -> input.copyTo(output) }
            }
        }

        val book = SManga.create().apply {
            url = name
            title = name.substringBeforeLast('.')
        }
        val manga = networkToLocalManga(book.toDomainManga(LocalSource.ID))
        if (!manga.favorite) updateManga.awaitUpdateFavorite(manga.id, true)
        manga.id
    }

    private fun displayName(uri: Uri): String? {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun fileSize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }
    }.getOrNull()

    private fun extensionOf(name: String?): String? {
        val extension = name?.substringAfterLast('.', "")?.lowercase()
        return extension?.takeIf { it == "epub" || it == "pdf" }
    }

    private companion object {
        val BOOK_TYPES = mapOf(
            "application/epub+zip" to "epub",
            "application/epub" to "epub",
            "application/pdf" to "pdf",
        )
    }
}

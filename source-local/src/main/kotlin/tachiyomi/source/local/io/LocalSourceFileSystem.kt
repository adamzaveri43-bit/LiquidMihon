package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import tachiyomi.domain.storage.service.StorageManager

@Inject
@SingleIn(AppScope::class)
class LocalSourceFileSystem(
    private val storageManager: StorageManager,
) {

    fun getBaseDirectory(): UniFile? {
        return storageManager.getLocalSourceDirectory()
    }

    fun getFilesInBaseDirectory(): List<UniFile> {
        return getBaseDirectory()?.listFiles().orEmpty().toList()
    }

    fun getMangaDirectory(name: String): UniFile? {
        return getBaseDirectory()
            ?.findFile(name)
            ?.takeIf { it.isDirectory }
    }

    fun getFilesInMangaDirectory(name: String): List<UniFile> {
        return getMangaDirectory(name)?.listFiles().orEmpty().toList()
    }

    /**
     * Returns the book file (epub or pdf) placed directly in the local source folder that forms the entry
     * [name], if it's one.
     */
    fun getBookFile(name: String): UniFile? {
        return getBaseDirectory()
            ?.findFile(name)
            ?.takeIf { Format.isBook(it) }
    }

    /**
     * Hidden folder holding the covers of book files placed directly in the local source folder, which have no
     * folder of their own to keep one in.
     */
    fun getBookCoversDirectory(create: Boolean): UniFile? {
        val base = getBaseDirectory() ?: return null
        return base.findFile(BOOK_COVERS_DIRECTORY)?.takeIf { it.isDirectory }
            ?: if (create) base.createDirectory(BOOK_COVERS_DIRECTORY) else null
    }

    companion object {
        const val BOOK_COVERS_DIRECTORY = ".covers"
    }
}

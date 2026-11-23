package tachiyomi.source.local.image

import android.content.Context
import com.hippo.unifile.UniFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.core.common.storage.nameWithoutExtension
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.source.local.io.LocalSourceFileSystem
import java.io.InputStream

private const val DEFAULT_COVER_NAME = "cover.jpg"

@Inject
@SingleIn(AppScope::class)
class LocalCoverManager(
    private val context: Context,
    private val fileSystem: LocalSourceFileSystem,
) {

    fun find(mangaUrl: String): UniFile? {
        // A book file on its own keeps its cover in the shared covers folder, named after it
        if (fileSystem.getBookFile(mangaUrl) != null) {
            return fileSystem.getBookCoversDirectory(create = false)
                ?.findFile(bookCoverName(mangaUrl))
                ?.takeIf { it.isFile }
        }
        return fileSystem.getFilesInMangaDirectory(mangaUrl)
            // Get all file whose names start with "cover"
            .filter { it.isFile && it.nameWithoutExtension.equals("cover", ignoreCase = true) }
            // Get the first actual image
            .firstOrNull { ImageUtil.isImage(it.name) { it.openInputStream() } }
    }

    fun update(
        manga: SManga,
        inputStream: InputStream,
    ): UniFile? {
        val isBookFile = fileSystem.getBookFile(manga.url) != null
        val directory = if (isBookFile) {
            fileSystem.getBookCoversDirectory(create = true)
        } else {
            fileSystem.getMangaDirectory(manga.url)
        }
        if (directory == null) {
            inputStream.close()
            return null
        }

        val targetFile = find(manga.url)
            ?: directory.createFile(if (isBookFile) bookCoverName(manga.url) else DEFAULT_COVER_NAME)
            ?: run {
                inputStream.close()
                return null
            }

        inputStream.use { input ->
            targetFile.openOutputStream().use { output ->
                input.copyTo(output)
            }
        }

        DiskUtil.createNoMediaFile(directory, context)

        manga.thumbnail_url = targetFile.uri.toString()
        return targetFile
    }

    private fun bookCoverName(bookFileName: String) = "$bookFileName.jpg"
}

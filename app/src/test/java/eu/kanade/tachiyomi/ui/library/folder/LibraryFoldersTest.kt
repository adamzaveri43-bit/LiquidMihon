package eu.kanade.tachiyomi.ui.library.folder

import eu.kanade.tachiyomi.ui.library.LibraryItem
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga

@Execution(ExecutionMode.CONCURRENT)
class LibraryFoldersTest {

    private fun item(id: Long, title: String = "Manga $id") = LibraryItem(
        libraryManga = LibraryManga(
            manga = Manga.create().copy(id = id, title = title),
            categories = listOf(0),
            totalChapters = 0,
            readCount = 0,
            bookmarkCount = 0,
            latestUpload = null,
            chapterFetchedAt = null,
            lastRead = null,
        ),
        downloadCount = 0,
        unreadCount = 0,
        isLocal = false,
        sourceName = "",
        sourceLanguage = "",
        badges = LibraryItem.Badges(downloadCount = 0, unreadCount = 0, isLocal = false, sourceLanguage = ""),
    )

    private val items = (1L..5L).map { item(it) }

    @Test
    fun `Folders replace their entries and come first`() {
        val folder = LibraryFolder(id = 10, name = "Tokyo Ghoul", mangaIds = listOf(4, 2))
        val entries = LibraryFolders.group(items, listOf(folder), customOrder = null, descending = false)

        entries.map { it.key } shouldBe listOf("f10", "m1", "m3", "m5")
        (entries.first() as LibraryEntry.Folder).items.map { it.id } shouldBe listOf(4L, 2L)
    }

    @Test
    fun `Folders without entries in the category are hidden`() {
        val folder = LibraryFolder(id = 10, name = "Elsewhere", mangaIds = listOf(99))
        LibraryFolders.group(items, listOf(folder), customOrder = null, descending = false)
            .map { it.key } shouldBe listOf("m1", "m2", "m3", "m4", "m5")
    }

    @Test
    fun `Custom order places folders among manga, unarranged entries last`() {
        val folder = LibraryFolder(id = 10, name = "Folder", mangaIds = listOf(2))
        val order = listOf("m5", "f10", "m1")
        LibraryFolders.group(items, listOf(folder), customOrder = order, descending = false)
            .map { it.key } shouldBe listOf("m5", "f10", "m1", "m3", "m4")
        LibraryFolders.group(items, listOf(folder), customOrder = order, descending = true)
            .map { it.key } shouldBe listOf("m4", "m3", "m1", "f10", "m5")
    }

    @Test
    fun `Folders survive serialization`() {
        val folders = listOf(
            LibraryFolder(id = 1, name = "A", mangaIds = listOf(1, 2), cover = FolderCover.Manga(2)),
            LibraryFolder(id = 2, name = "B", cover = FolderCover.Image("/data/cover")),
            LibraryFolder(id = 3, name = "C"),
        )
        LibraryFolders.parse(LibraryFolders.serialize(folders)) shouldBe folders
        LibraryFolders.parse("not json") shouldBe emptyList()
        LibraryFolders.parseOrder(LibraryFolders.serializeOrder(listOf("m1", "f2"))) shouldBe listOf("m1", "f2")
    }
}

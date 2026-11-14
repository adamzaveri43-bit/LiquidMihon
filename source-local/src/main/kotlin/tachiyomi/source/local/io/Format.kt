package tachiyomi.source.local.io

import com.hippo.unifile.UniFile
import tachiyomi.core.common.storage.extension
import tachiyomi.source.local.io.Archive.isSupported as isArchiveSupported

sealed interface Format {
    data class Directory(val file: UniFile) : Format
    data class Archive(val file: UniFile) : Format
    data class Epub(val file: UniFile) : Format
    data class Pdf(val file: UniFile) : Format

    class UnknownFormatException : Exception()

    companion object {

        fun valueOf(file: UniFile) = when {
            file.isDirectory -> Directory(file)
            file.extension.equals("epub", true) -> Epub(file)
            file.extension.equals("pdf", true) -> Pdf(file)
            isArchiveSupported(file) -> Archive(file)
            else -> throw UnknownFormatException()
        }

        /**
         * Whether [file] is a book (epub or pdf), which can also sit directly in the local source folder as an
         * entry of its own.
         */
        fun isBook(file: UniFile): Boolean {
            val extension = file.extension?.lowercase()
            return file.isFile && (extension == "epub" || extension == "pdf")
        }

        /** Whether [file] can be read as a chapter. */
        fun isSupported(file: UniFile): Boolean = file.isDirectory || isArchiveSupported(file) || isBook(file)
    }
}

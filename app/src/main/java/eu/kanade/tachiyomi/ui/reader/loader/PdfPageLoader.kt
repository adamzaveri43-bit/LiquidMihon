package eu.kanade.tachiyomi.ui.reader.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.util.LruCache
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Loader used to load a chapter from a .pdf file. Pages are drawn when the reader asks for them, sharp enough to
 * zoom in on.
 */
internal class PdfPageLoader(file: UniFile, private val context: Context) : PageLoader() {

    override var isLocal: Boolean = true

    private val descriptor = context.contentResolver.openFileDescriptor(file.uri, "r")
        ?: error("Failed to open ${file.filePath ?: file.uri}")

    private val renderer = try {
        PdfRenderer(descriptor)
    } catch (e: Exception) {
        descriptor.close()
        throw e
    }

    // A renderer can only have one page open at a time
    private val lock = Any()

    // The reader may open a page's stream more than once (to check its type, then to decode it)
    private val rendered = LruCache<Int, ByteArray>(3)

    override suspend fun getPages(): List<ReaderPage> {
        val count = synchronized(lock) { renderer.pageCount }
        return List(count) { i ->
            ReaderPage(i).apply {
                stream = { render(i) }
                status = Page.State.Ready
            }
        }
    }

    private fun render(index: Int): InputStream {
        rendered.get(index)?.let { return ByteArrayInputStream(it) }
        val bytes = synchronized(lock) {
            check(!isRecycled) { "Pdf is closed" }
            rendered.get(index) ?: renderPage(index).also { rendered.put(index, it) }
        }
        return ByteArrayInputStream(bytes)
    }

    private fun renderPage(index: Int): ByteArray {
        val page = renderer.openPage(index)
        try {
            val screenWidth = context.resources.displayMetrics.widthPixels
            // Twice the screen width keeps text crisp when zoomed in, without huge bitmaps
            val width = min(screenWidth * 2, MAX_WIDTH).coerceAtLeast(1)
            val aspect = page.height.toFloat() / page.width.coerceAtLeast(1)
            var targetWidth = width
            var targetHeight = (width * aspect).roundToInt().coerceAtLeast(1)
            if (targetHeight > MAX_HEIGHT) {
                targetWidth = (targetWidth * MAX_HEIGHT.toFloat() / targetHeight).roundToInt().coerceAtLeast(1)
                targetHeight = MAX_HEIGHT
            }
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            try {
                // Pages are transparent where nothing is drawn, paper is white
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return ByteArrayOutputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        } finally {
            page.close()
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        check(!isRecycled)
    }

    override fun recycle() {
        synchronized(lock) {
            super.recycle()
            renderer.close()
            descriptor.close()
        }
        rendered.evictAll()
    }

    private companion object {
        const val MAX_WIDTH = 2560
        const val MAX_HEIGHT = 6000
        const val JPEG_QUALITY = 92
    }
}

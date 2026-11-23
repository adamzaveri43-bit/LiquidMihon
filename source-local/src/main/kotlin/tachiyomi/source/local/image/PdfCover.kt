package tachiyomi.source.local.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import com.hippo.unifile.UniFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val COVER_WIDTH = 720

/**
 * Renders the first page of this pdf as a JPEG cover image, or returns null if it has none.
 */
internal fun UniFile.renderPdfCover(context: Context): InputStream? {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
    return descriptor.use { fd ->
        PdfRenderer(fd).use { renderer ->
            if (renderer.pageCount == 0) return@use null
            val page = renderer.openPage(0)
            try {
                val width = COVER_WIDTH
                val height = (width.toFloat() * page.height / page.width.coerceAtLeast(1))
                    .toInt()
                    .coerceIn(1, width * 3)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // Pages are transparent where nothing is drawn, paper is white
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
                bitmap.recycle()
                ByteArrayInputStream(output.toByteArray())
            } finally {
                page.close()
            }
        }
    }
}

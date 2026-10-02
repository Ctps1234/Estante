package com.example.estante.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.ceil

/**
 * Encapsula um [PdfRenderer] com cache LRU de páginas já renderizadas.
 * O acesso é serializado por um Mutex, pois PdfRenderer não é thread-safe.
 */
class PdfBook(file: File) {

    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = PdfRenderer(pfd)

    val pageCount: Int = renderer.pageCount

    private val mutex = Mutex()
    private val cache = object : LruCache<Int, Bitmap>(CACHE_MAX_BYTES) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    /**
     * Renderiza a página [index] com a largura [targetWidth] em pixels.
     * Retorna null se os argumentos forem inválidos ou se a renderização falhar.
     */
    suspend fun renderPage(index: Int, targetWidth: Int): Bitmap? {
        if (index < 0 || index >= pageCount || targetWidth <= 0) return null
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val key = cacheKey(index, targetWidth)
                cache.get(key)?.let { return@withLock it }
                try {
                    val page = renderer.openPage(index)
                    try {
                        if (page.width <= 0) return@withLock null
                        val scale = targetWidth.toFloat() / page.width
                        val height = ceil(page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            Matrix().apply { setScale(scale, scale) },
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        cache.put(key, bitmap)
                        bitmap
                    } finally {
                        page.close()
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        cache.evictAll()
    }

    companion object {
        // Até 48 MB de páginas renderizadas em cache (~6 telas cheias).
        private const val CACHE_MAX_BYTES = 48 * 1024 * 1024

        private fun cacheKey(index: Int, width: Int): Int = index * 100_000 + width
    }
}

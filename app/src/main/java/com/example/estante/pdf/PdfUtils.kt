package com.example.estante.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/** Funções utilitárias para arquivos PDF. */
object PdfUtils {

    /** Retorna o total de páginas do PDF (0 em caso de falha). */
    fun getPageCount(file: File): Int {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(pfd)
                try {
                    renderer.pageCount
                } finally {
                    renderer.close()
                }
            } finally {
                pfd.close()
            }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Renderiza a primeira página do PDF como capa e salva em [outFile] (JPEG).
     * Retorna true quando a capa foi gerada com sucesso.
     */
    fun renderCover(file: File, outFile: File, targetWidth: Int = 400): Boolean {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(pfd)
                try {
                    val page = renderer.openPage(0)
                    try {
                        if (page.width <= 0) return false
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
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        }
                        bitmap.recycle()
                        true
                    } finally {
                        page.close()
                    }
                } finally {
                    renderer.close()
                }
            } finally {
                pfd.close()
            }
        } catch (e: Exception) {
            false
        }
    }
}

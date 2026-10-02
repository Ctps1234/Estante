package com.example.estante.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.estante.data.db.Book
import com.example.estante.data.db.BookDao
import com.example.estante.data.db.Bookmark
import com.example.estante.data.db.BookmarkDao
import com.example.estante.data.gutenberg.GutenbergNetwork
import com.example.estante.pdf.PdfUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Fonte única de verdade para os livros: banco Room + arquivos no
 * armazenamento interno do app (books/ e covers/).
 */
class BookRepository(
    private val bookDao: BookDao,
    private val bookmarkDao: BookmarkDao,
    private val context: Context
) {

    /* ------------------------- consultas ------------------------- */

    fun observeBooks(): Flow<List<Book>> = bookDao.observeAll()
    fun observeBook(id: Long): Flow<Book?> = bookDao.observeById(id)
    fun observeBookmarks(bookId: Long): Flow<List<Bookmark>> = bookmarkDao.observeForBook(bookId)

    suspend fun updateBook(book: Book) = bookDao.update(book)

    suspend fun deleteBook(book: Book) {
        bookDao.delete(book)
        bookmarkDao.deleteForBook(book.id)
        runCatching { File(book.filePath).delete() }
        book.coverPath?.let { coverPath -> runCatching { File(coverPath).delete() } }
    }

    suspend fun updateProgress(bookId: Long, page: Int, totalPages: Int) {
        val book = bookDao.getById(bookId) ?: return
        bookDao.update(
            book.copy(
                currentPage = page.coerceIn(0, (totalPages - 1).coerceAtLeast(0)),
                totalPages = totalPages,
                lastReadAt = System.currentTimeMillis()
            )
        )
    }

    /* ------------------------- marcadores ------------------------- */

    suspend fun addBookmark(bookId: Long, pageIndex: Int): Long =
        bookmarkDao.insert(Bookmark(bookId = bookId, pageIndex = pageIndex))

    suspend fun deleteBookmark(bookmark: Bookmark) = bookmarkDao.delete(bookmark)

    /* ---------------------- importação local ---------------------- */

    /** Copia um PDF escolhido pelo usuário para o app e o adiciona à estante. */
    suspend fun importPdf(uri: Uri): Long = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri)
        val baseName = sanitizeFileName(displayName?.substringBeforeLast('.') ?: "livro")

        val dir = booksDir()
        var dest = File(dir, "$baseName.pdf")
        var counter = 1
        while (dest.exists()) {
            dest = File(dir, "$baseName (${counter++}).pdf")
        }

        val input = resolver.openInputStream(uri)
            ?: throw IOException("Não foi possível ler o arquivo selecionado")
        input.use { src ->
            FileOutputStream(dest).use { out -> src.copyTo(out) }
        }

        finishImport(
            file = dest,
            title = baseName,
            author = "",
            source = Book.SOURCE_LOCAL
        )
    }

    /* ------------------- download (Project Gutenberg) ------------------- */

    /** Baixa o PDF de um livro do Gutenberg e o adiciona à estante. */
    suspend fun downloadGutenbergBook(
        gutenbergId: Long,
        title: String,
        author: String,
        pdfUrl: String,
        coverUrl: String?,
        onProgress: (Float) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        val dest = File(booksDir(), "gutenberg_$gutenbergId.pdf")
        GutenbergNetwork.downloadToFile(pdfUrl, dest, onProgress)
        finishImport(
            file = dest,
            title = title.ifBlank { "Livro $gutenbergId" },
            author = author,
            source = Book.SOURCE_GUTENBERG,
            coverUrl = coverUrl
        )
    }

    /* --------------------------- helpers --------------------------- */

    private suspend fun finishImport(
        file: File,
        title: String,
        author: String,
        source: String,
        coverUrl: String? = null
    ): Long {
        val pageCount = PdfUtils.getPageCount(file)
        if (pageCount <= 0) {
            runCatching { file.delete() }
            throw IOException("O arquivo não é um PDF válido (ou está protegido por senha).")
        }

        val coverFile = File(coversDir(), file.nameWithoutExtension + ".jpg")
        var coverOk = false
        if (coverUrl != null) {
            coverOk = runCatching {
                GutenbergNetwork.downloadToFile(coverUrl, coverFile)
            }.isSuccess && coverFile.exists() && coverFile.length() > 0
        }
        if (!coverOk) {
            coverOk = PdfUtils.renderCover(file, coverFile)
        }

        return bookDao.insert(
            Book(
                title = title,
                author = author,
                filePath = file.absolutePath,
                coverPath = if (coverOk) coverFile.absolutePath else null,
                totalPages = pageCount,
                source = source
            )
        )
    }

    private fun booksDir(): File = File(context.filesDir, "books").apply { mkdirs() }

    private fun coversDir(): File = File(context.filesDir, "covers").apply { mkdirs() }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifEmpty { "livro" }
            .take(80)
}

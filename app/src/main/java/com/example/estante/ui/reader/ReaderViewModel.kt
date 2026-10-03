package com.example.estante.ui.reader

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.estante.data.BookRepository
import com.example.estante.data.ReadingMode
import com.example.estante.data.SettingsRepository
import com.example.estante.data.db.Book
import com.example.estante.data.db.Bookmark
import com.example.estante.pdf.PdfBook
import com.example.estante.pdf.PdfToc
import com.example.estante.pdf.PdfTocParser
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReaderViewModel(
    private val bookId: Long,
    private val repository: BookRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    var book by mutableStateOf<Book?>(null)
        private set
    var pageCount by mutableIntStateOf(0)
        private set
    var currentPage by mutableIntStateOf(0)
        private set
    var bookmarks by mutableStateOf<List<Bookmark>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Sumário do livro (outline do PDF ou páginas de índice), ou null. */
    var toc by mutableStateOf<PdfToc?>(null)
        private set
    /** true enquanto o sumário está sendo analisado em segundo plano. */
    var tocLoading by mutableStateOf(false)
        private set

    var readingMode by mutableStateOf(ReadingMode.LIGHT)
        private set

    /** Brilho da tela durante a leitura (null = usar o do sistema). */
    var brightnessOverride by mutableStateOf<Float?>(null)

    private var pdfBook: PdfBook? = null
    private var opened = false

    init {
        repository.observeBook(bookId)
            .onEach { loaded ->
                book = loaded
                if (loaded != null && !opened) open(loaded)
            }
            .launchIn(viewModelScope)

        repository.observeBookmarks(bookId)
            .onEach { bookmarks = it }
            .launchIn(viewModelScope)

        settings.readingMode
            .onEach { readingMode = it }
            .launchIn(viewModelScope)
    }

    private suspend fun open(book: Book) {
        opened = true
        withContext(Dispatchers.IO) {
            val file = File(book.filePath)
            if (!file.exists()) {
                error = "Arquivo não encontrado: ${file.name}"
                return@withContext
            }
            try {
                val newPdfBook = PdfBook(file)
                pdfBook = newPdfBook
                pageCount = newPdfBook.pageCount
                currentPage = book.currentPage.coerceIn(0, (newPdfBook.pageCount - 1).coerceAtLeast(0))
            } catch (e: Exception) {
                error = "Não foi possível abrir este PDF (ele pode estar corrompido ou protegido por senha)."
                return@withContext
            }

            // Sumário: análise em segundo plano (pode levar alguns segundos).
            tocLoading = true
            toc = null
            val parsedToc = runCatching { PdfTocParser.parse(file) }.getOrNull()
            withContext(Dispatchers.Main) {
                toc = parsedToc
                tocLoading = false
            }
        }
    }

    suspend fun renderPage(index: Int, width: Int): Bitmap? = pdfBook?.renderPage(index, width)

    /** Chamado sempre que a página exibida muda; salva o progresso. */
    fun onPageChanged(page: Int) {
        currentPage = page
        if (pageCount <= 0) return
        viewModelScope.launch {
            repository.updateProgress(bookId, page, pageCount)
        }
    }

    fun applyReadingMode(mode: ReadingMode) {
        readingMode = mode
        viewModelScope.launch { settings.setReadingMode(mode) }
    }

    fun addBookmark() {
        viewModelScope.launch { repository.addBookmark(bookId, currentPage) }
    }

    fun removeBookmark(bookmark: Bookmark) {
        viewModelScope.launch { repository.deleteBookmark(bookmark) }
    }

    override fun onCleared() {
        pdfBook?.close()
        pdfBook = null
        super.onCleared()
    }
}

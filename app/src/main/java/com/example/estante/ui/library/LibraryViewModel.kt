package com.example.estante.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.estante.data.BookRepository
import com.example.estante.data.SettingsRepository
import com.example.estante.data.ThemeMode
import com.example.estante.data.db.Book
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: BookRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    val books: StateFlow<List<Book>> = repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events

    fun importPdf(uri: Uri) {
        viewModelScope.launch {
            try {
                repository.importPdf(uri)
                _events.emit("Livro adicionado à estante")
            } catch (e: Exception) {
                _events.emit("Falha ao importar: ${e.message ?: "erro desconhecido"}")
            }
        }
    }

    fun updateDetails(book: Book, title: String, author: String) {
        viewModelScope.launch {
            repository.updateBook(
                book.copy(
                    title = title.trim().ifEmpty { book.title },
                    author = author.trim()
                )
            )
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            repository.deleteBook(book)
            _events.emit("\"${book.title}\" foi removido da estante")
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }
}

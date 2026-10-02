package com.example.estante.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.estante.data.BookRepository
import com.example.estante.data.gutenberg.GutenbergNetwork
import com.example.estante.data.gutenberg.GutendexBook
import com.example.estante.data.gutenberg.displayAuthor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class BrowserItem(
    val gutenbergId: Long,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val pdfUrl: String?
)

data class BrowserUiState(
    val query: String = "",
    val items: List<BrowserItem> = emptyList(),
    val isLoading: Boolean = false,
    val loadingMore: Boolean = false,
    val nextUrl: String? = null,
    val error: String? = null,
    /** id Gutenberg -> progresso do download (0f..1f, ou -1f se tamanho desconhecido). */
    val downloading: Map<Long, Float> = emptyMap(),
    /** id Gutenberg -> id do livro na estante (download concluído). */
    val downloaded: Map<Long, Long> = emptyMap()
)

fun GutendexBook.toBrowserItem() = BrowserItem(
    gutenbergId = id,
    title = title,
    author = displayAuthor(),
    coverUrl = coverUrl,
    pdfUrl = pdfUrl
)

class BrowserViewModel(private val repository: BookRepository) : ViewModel() {

    private val _state = MutableStateFlow(BrowserUiState(isLoading = true))
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Long>()
    val events: SharedFlow<Long> = _events.asSharedFlow()

    init {
        reload()
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun search() = reload()

    fun reload() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val query = _state.value.query.trim()
                val response = if (query.isEmpty()) {
                    GutenbergNetwork.api.list(sort = "popular")
                } else {
                    GutenbergNetwork.api.list(search = query)
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        items = response.results.map { book -> book.toBrowserItem() },
                        nextUrl = response.next
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = "Não foi possível carregar o catálogo. Verifique sua conexão com a internet.")
                }
            }
        }
    }

    fun loadMore() {
        val url = _state.value.nextUrl ?: return
        if (_state.value.loadingMore) return
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            try {
                val response = GutenbergNetwork.api.byUrl(url)
                _state.update {
                    it.copy(
                        loadingMore = false,
                        items = it.items + response.results.map { book -> book.toBrowserItem() },
                        nextUrl = response.next
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, error = "Falha ao carregar mais resultados.") }
            }
        }
    }

    fun download(item: BrowserItem) {
        if (_state.value.downloading.containsKey(item.gutenbergId)) return
        if (_state.value.downloaded.containsKey(item.gutenbergId)) return
        val pdfUrl = item.pdfUrl ?: return

        viewModelScope.launch {
            _state.update {
                it.copy(downloading = it.downloading + (item.gutenbergId to 0f), error = null)
            }
            try {
                val bookId = repository.downloadGutenbergBook(
                    gutenbergId = item.gutenbergId,
                    title = item.title,
                    author = item.author,
                    pdfUrl = pdfUrl,
                    coverUrl = item.coverUrl
                ) { progress ->
                    _state.update { state ->
                        state.copy(downloading = state.downloading + (item.gutenbergId to progress))
                    }
                }
                _state.update { state ->
                    state.copy(
                        downloading = state.downloading - item.gutenbergId,
                        downloaded = state.downloaded + (item.gutenbergId to bookId)
                    )
                }
                _events.emit(bookId)
            } catch (e: IOException) {
                _state.update { state ->
                    state.copy(
                        downloading = state.downloading - item.gutenbergId,
                        error = "Falha no download: ${e.message ?: "erro desconhecido"}"
                    )
                }
            } catch (e: Exception) {
                _state.update { state ->
                    state.copy(
                        downloading = state.downloading - item.gutenbergId,
                        error = "Falha no download: ${e.message ?: "erro desconhecido"}"
                    )
                }
            }
        }
    }
}

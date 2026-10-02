package com.example.estante.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val author: String = "",
    val filePath: String,
    val coverPath: String? = null,
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val lastReadAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val source: String = SOURCE_LOCAL
) {
    /** Progresso de leitura entre 0f e 1f. */
    val progress: Float
        get() = if (totalPages > 0) {
            (currentPage + 1).coerceAtMost(totalPages).toFloat() / totalPages
        } else 0f

    companion object {
        const val SOURCE_LOCAL = "local"
        const val SOURCE_GUTENBERG = "gutenberg"
    }
}

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bookId: Long,
    val pageIndex: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

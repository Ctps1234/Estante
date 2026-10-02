package com.example.estante.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY lastReadAt IS NULL, lastReadAt DESC, addedAt DESC")
    fun observeAll(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observeById(id: Long): Flow<Book?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: Long): Book?

    @Insert
    suspend fun insert(book: Book): Long

    @Update
    suspend fun update(book: Book)

    @Delete
    suspend fun delete(book: Book)
}

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY pageIndex ASC, createdAt ASC")
    fun observeForBook(bookId: Long): Flow<List<Bookmark>>

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)
}

package com.example.estante

import android.app.Application
import com.example.estante.data.BookRepository
import com.example.estante.data.SettingsRepository
import com.example.estante.data.db.AppDatabase
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class EstanteApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    val repository: BookRepository by lazy {
        BookRepository(database.bookDao(), database.bookmarkDao(), this)
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: EstanteApp
            private set
    }
}

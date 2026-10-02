package com.example.estante.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Tema geral do app. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Modo de leitura aplicado às páginas do PDF. */
enum class ReadingMode { LIGHT, SEPIA, NIGHT }

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.settingsDataStore

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[KEY_THEME]) {
            VALUE_LIGHT -> ThemeMode.LIGHT
            VALUE_DARK -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val readingMode: Flow<ReadingMode> = dataStore.data.map { prefs ->
        when (prefs[KEY_READING_MODE]) {
            VALUE_SEPIA -> ReadingMode.SEPIA
            VALUE_NIGHT -> ReadingMode.NIGHT
            else -> ReadingMode.LIGHT
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.toValue() }
    }

    suspend fun setReadingMode(mode: ReadingMode) {
        dataStore.edit { it[KEY_READING_MODE] = mode.toValue() }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme_mode")
        private val KEY_READING_MODE = stringPreferencesKey("reading_mode")

        private const val VALUE_LIGHT = "light"
        private const val VALUE_DARK = "dark"
        private const val VALUE_SEPIA = "sepia"
        private const val VALUE_NIGHT = "night"

        private fun ThemeMode.toValue(): String = when (this) {
            ThemeMode.SYSTEM -> "system"
            ThemeMode.LIGHT -> VALUE_LIGHT
            ThemeMode.DARK -> VALUE_DARK
        }

        private fun ReadingMode.toValue(): String = when (this) {
            ReadingMode.LIGHT -> VALUE_LIGHT
            ReadingMode.SEPIA -> VALUE_SEPIA
            ReadingMode.NIGHT -> VALUE_NIGHT
        }
    }
}

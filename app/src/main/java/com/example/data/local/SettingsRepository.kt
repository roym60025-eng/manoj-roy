package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import com.example.data.model.PageSizeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_pdf_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val HAS_SEEN_WELCOME = booleanPreferencesKey("has_seen_welcome")
        val DEFAULT_PAGE_SIZE = stringPreferencesKey("default_page_size")
        val DEFAULT_IMAGE_QUALITY = intPreferencesKey("default_image_quality")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val pageSizeName = prefs[Keys.DEFAULT_PAGE_SIZE] ?: PageSizeOption.A4.name
        val pageSize = runCatching { PageSizeOption.valueOf(pageSizeName) }
            .getOrDefault(PageSizeOption.A4)
        AppSettings(
            isDarkMode = prefs[Keys.IS_DARK_MODE] ?: true,
            hasSeenWelcome = prefs[Keys.HAS_SEEN_WELCOME] ?: false,
            defaultPageSize = pageSize,
            defaultImageQuality = prefs[Keys.DEFAULT_IMAGE_QUALITY] ?: 85
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_DARK_MODE] = enabled }
    }

    suspend fun setHasSeenWelcome(seen: Boolean) {
        context.dataStore.edit { it[Keys.HAS_SEEN_WELCOME] = seen }
    }

    suspend fun setDefaultPageSize(option: PageSizeOption) {
        context.dataStore.edit { it[Keys.DEFAULT_PAGE_SIZE] = option.name }
    }

    suspend fun setDefaultImageQuality(quality: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_IMAGE_QUALITY] = quality.coerceIn(40, 100) }
    }
}

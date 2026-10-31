package com.kharcha.tracker.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themePreferenceStore by preferencesDataStore(name = "theme_preference")

/**
 * The app-wide theme choice. [SYSTEM] follows the device setting, the other
 * two force a scheme; [SYSTEM] is the default when nothing is saved yet.
 * The wire value is the enum's [wire] string (DataStore prefs are string-typed).
 */
enum class ThemeMode(val wire: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromWire(value: String?): ThemeMode =
            entries.firstOrNull { it.wire == value } ?: SYSTEM
    }
}

/**
 * Persists the theme preference in Jetpack DataStore (Preferences), mirroring
 * [TokenStore]'s setup. Written by the settings screen, consumed app-wide in
 * [com.kharcha.tracker.MainActivity] (same store name, so a write in the
 * settings screen republishes the [theme] flow there).
 */
class ThemePreferenceStore(context: Context) {

    private val dataStore = context.themePreferenceStore

    /** Emits the stored theme mode ([ThemeMode.SYSTEM] when unset). */
    val theme: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.fromWire(prefs[KEY_THEME])
    }

    /** Persists the chosen [mode]. */
    suspend fun setTheme(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.wire }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
    }
}

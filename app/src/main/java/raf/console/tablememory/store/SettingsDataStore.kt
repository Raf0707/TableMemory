package raf.console.tablememory.store

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore
val Context.dataStore by preferencesDataStore("settings")

object SettingsKeys {
    val TABLE_SIZE = stringPreferencesKey("table_size")
    val TABLE_MODE = stringPreferencesKey("table_mode")
    val TABLE_STYLE = stringPreferencesKey("table_style")
    val LANGUAGE = stringPreferencesKey("language")
    val SHUFFLE_ON_CLICK = booleanPreferencesKey("shuffle_on_click")
    val VIBRATION = booleanPreferencesKey("vibration")
    val RED_FLASH = booleanPreferencesKey("red_flash")
    val CENTER_DOT = booleanPreferencesKey("center_dot")
    val DIM_MARKED = booleanPreferencesKey("dim_marked")
    val GAME_MODE = stringPreferencesKey("game_mode")

    // 🔹 режим букв и смешанные алфавиты
    val LETTER_MODE = stringPreferencesKey("letter_mode")             // "Алфавит" | "Рандом"
    val MIXED_ALPHABETS = stringPreferencesKey("mixed_alphabets")     // выбранные языки через |

    // 🔹 тема
    val DARK_THEME = booleanPreferencesKey("dark_theme")

    // 🔹 новые ключи для таймера запоминания
    val MEMORY_TIME = stringPreferencesKey("memory_time")             // секунды (строкой)
    val MEMORY_NO_TIMER = booleanPreferencesKey("memory_no_timer")    // true = без таймера
}

class SettingsRepository(private val context: Context) {
    val settingsFlow: Flow<SettingsState> = context.dataStore.data.map { prefs ->
        SettingsState(
            tableSize = prefs[SettingsKeys.TABLE_SIZE] ?: "5x5",
            tableMode = prefs[SettingsKeys.TABLE_MODE] ?: "Цифры",
            tableStyle = prefs[SettingsKeys.TABLE_STYLE] ?: "Классический",
            language = prefs[SettingsKeys.LANGUAGE] ?: "Русский",
            shuffleOnClick = prefs[SettingsKeys.SHUFFLE_ON_CLICK] ?: false,
            vibration = prefs[SettingsKeys.VIBRATION] ?: true,
            redFlash = prefs[SettingsKeys.RED_FLASH] ?: false,
            centerDot = prefs[SettingsKeys.CENTER_DOT] ?: false,
            dimMarked = prefs[SettingsKeys.DIM_MARKED] ?: false,
            gameMode = prefs[SettingsKeys.GAME_MODE] ?: "Стандартный",

            mixedAlphabets = prefs[SettingsKeys.MIXED_ALPHABETS] ?: "",
            darkTheme = prefs[SettingsKeys.DARK_THEME] ?: false,

            memoryTime = prefs[SettingsKeys.MEMORY_TIME] ?: "5",
            memoryNoTimer = prefs[SettingsKeys.MEMORY_NO_TIMER] ?: false
        )
    }

    suspend fun save(setting: Preferences.Key<*>, value: Any) {
        context.dataStore.edit { prefs ->
            when (value) {
                is String -> prefs[setting as Preferences.Key<String>] = value
                is Boolean -> prefs[setting as Preferences.Key<Boolean>] = value
            }
        }
    }
}

data class SettingsState(
    val tableSize: String,
    val tableMode: String,
    val tableStyle: String,
    val language: String,
    val shuffleOnClick: Boolean,
    val vibration: Boolean,
    val redFlash: Boolean,
    val centerDot: Boolean,
    val dimMarked: Boolean,
    val gameMode: String,
    val mixedAlphabets: String,
    val darkTheme: Boolean,
    val memoryTime: String = "5",       // секунды по умолчанию
    val memoryNoTimer: Boolean = false  // выключен ли таймер
)

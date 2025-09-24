package raf.console.tablememory

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import raf.console.tablememory.store.SettingsKeys
import raf.console.tablememory.store.dataStore
import raf.console.tablememory.ui.theme.TableMemoryTheme
import raf.console.tablememory.vk_admob.VKBannerAd

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialDarkTheme = runBlocking {
            dataStore.data.first()[SettingsKeys.DARK_THEME] ?: false
        }

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            val darkThemeFlow = context.dataStore.data.map { prefs ->
                prefs[SettingsKeys.DARK_THEME] ?: false
            }
            val darkTheme by darkThemeFlow.collectAsState(initial = initialDarkTheme)

            TableMemoryTheme(darkTheme = darkTheme) {
                SettingsScreen(
                    darkTheme = darkTheme,
                    onToggleTheme = {
                        scope.launch {
                            context.dataStore.edit { prefs ->
                                prefs[SettingsKeys.DARK_THEME] = !darkTheme
                            }
                        }
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tableSize by remember { mutableStateOf("5x5") }
    var tableMode by remember { mutableStateOf("Цифры") }
    var tableStyle by remember { mutableStateOf("Классический") }
    var language by remember { mutableStateOf("Русский") }
    var shuffleOnClick by remember { mutableStateOf(false) }
    var vibration by remember { mutableStateOf(true) }
    var redFlash by remember { mutableStateOf(false) }
    var centerDot by remember { mutableStateOf(false) }
    var dimMarked by remember { mutableStateOf(false) }
    var gameMode by remember { mutableStateOf("Стандартный") }
    var mixedAlphabets by remember { mutableStateOf(setOf<String>()) }

    // новые поля
    var memoryTime by remember { mutableStateOf("5") }
    var memoryNoTimer by remember { mutableStateOf(false) }

    val supportedLanguages = listOf(
        "Русский","English","Español","Français","Italiano","Deutsch","Polski",
        "العربية (Арабский)","עברית (Иврит)","हिन्दी (Хинди)","中文 (Китайский)","日本語 (Японский)",
        "ܐܣܛܢܓܠܐ (Эстангело)","አማርኛ (Амхарский)","བོད་སྐད (Тибетский)","မြန်မာ (Бирманский)",
        "ខ្មែរ (Кхмерский)","ລາວ (Лаосский)","ไทย (Тайский)","සිංහල (Сингальский)",
        "ᰛᰵᰎᰵ (Лепча)","ᤕᤠᤰᤌᤢᤱ (Лимбу)","ᎠᏂᏴᏫ (Чероки)"
    )

    // загрузка сохранённых
    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        tableSize = prefs[SettingsKeys.TABLE_SIZE] ?: "5x5"
        tableMode = prefs[SettingsKeys.TABLE_MODE] ?: "Цифры"
        tableStyle = prefs[SettingsKeys.TABLE_STYLE] ?: "Классический"
        language = prefs[SettingsKeys.LANGUAGE] ?: "Русский"
        shuffleOnClick = prefs[SettingsKeys.SHUFFLE_ON_CLICK] ?: false
        vibration = prefs[SettingsKeys.VIBRATION] ?: true
        redFlash = prefs[SettingsKeys.RED_FLASH] ?: false
        centerDot = prefs[SettingsKeys.CENTER_DOT] ?: false
        dimMarked = prefs[SettingsKeys.DIM_MARKED] ?: false
        gameMode = prefs[SettingsKeys.GAME_MODE] ?: "Стандартный"
        val mixRaw = prefs[SettingsKeys.MIXED_ALPHABETS] ?: ""
        mixedAlphabets = if (mixRaw.isBlank()) emptySet() else mixRaw.split("|").toSet()

        memoryTime = prefs[SettingsKeys.MEMORY_TIME] ?: "5"
        memoryNoTimer = prefs[SettingsKeys.MEMORY_NO_TIMER] ?: false
    }

    fun save(key: Preferences.Key<String>, value: String) {
        scope.launch { context.dataStore.edit { it[key] = value } }
    }
    fun saveBool(key: Preferences.Key<Boolean>, value: Boolean) {
        scope.launch { context.dataStore.edit { it[key] = value } }
    }
    fun saveMixed(set: Set<String>) {
        scope.launch { context.dataStore.edit { it[SettingsKeys.MIXED_ALPHABETS] = set.joinToString("|") } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (vibration) vibrate(context)
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (vibration) vibrate(context)
                        onToggleTheme()
                    }) {
                        Icon(
                            imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Смена темы"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // --- таблица
            item { VKBannerAd(1912287) }
            item { Section("Таблица") }
            item { SettingWrapper {
                DropdownSetting("Размер таблицы", tableSize, (3..15).map { "${it}x$it" }) {
                    tableSize = it; save(SettingsKeys.TABLE_SIZE, it)
                }
            } }
            item { SettingWrapper {
                DropdownSetting(
                    "Режим таблицы",
                    tableMode,
                    listOf("Цифры","Буквы","Смесь букв разных алфавитов")
                ) {
                    tableMode = it; save(SettingsKeys.TABLE_MODE, it)
                }
            } }

            if (tableMode.startsWith("Буквы")) {
                item { SettingWrapper {
                    DropdownSetting("Язык букв", language, supportedLanguages) {
                        language = it; save(SettingsKeys.LANGUAGE, it)
                    }
                } }
            }

            if (tableMode == "Смесь букв разных алфавитов") {
                item { Section("Алфавиты для смеси") }
                items(supportedLanguages.size) { idx ->
                    val lang = supportedLanguages[idx]
                    val checked = mixedAlphabets.contains(lang)
                    SettingWrapper {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    mixedAlphabets = if (checked) mixedAlphabets - lang else mixedAlphabets + lang
                                    saveMixed(mixedAlphabets)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(lang)
                            Checkbox(checked, onCheckedChange = {
                                mixedAlphabets = if (it) mixedAlphabets + lang else mixedAlphabets - lang
                                saveMixed(mixedAlphabets)
                            })
                        }
                    }
                }
            }

            // --- таймер запоминания
            item { Section("Таймер запоминания") }
            item {
                SettingWrapper {
                    DropdownSetting(
                        "Время показа (секунды)",
                        memoryTime,
                        (5..300 step 5).map { it.toString() }
                    ) {
                        memoryTime = it
                        save(SettingsKeys.MEMORY_TIME, it)
                    }
                }
            }
            item {
                SettingWrapper {
                    SwitchSetting("Без таймера", memoryNoTimer) {
                        memoryNoTimer = it
                        saveBool(SettingsKeys.MEMORY_NO_TIMER, it)
                    }
                }
            }

            // --- управление
            item { VKBannerAd(1912290) }
            /*item { Section("Управление") }
            */

            item { SettingWrapper {
                SwitchSetting("Вибрация по клику", vibration) {
                    vibration = it; saveBool(SettingsKeys.VIBRATION, it)
                }
            } }

            // --- прочее
            item {
                SettingWrapper {
                    Text(
                        text = "Оценить приложение",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.rustore.ru/catalog/app/${context.packageName}")
                            )
                            context.startActivity(intent)
                        }.padding(vertical = 12.dp)
                    )
                }
            }

            item {
                SettingWrapper {
                    Text(
                        text = "Наши другие приложения",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.rustore.ru/catalog/developer/90b1826e")
                            )
                            context.startActivity(intent)
                        }.padding(vertical = 12.dp)
                    )
                }
            }

            item { VKBannerAd(1912293) }
        }
    }
}


@Composable
fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun SettingWrapper(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        content()
    }
    HorizontalDivider()
}



// UI компоненты
@Composable
fun DropdownSetting(title: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = selected,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 4.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}

@Composable
fun SwitchSetting(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

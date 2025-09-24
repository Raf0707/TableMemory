@file:OptIn(ExperimentalLayoutApi::class)

package raf.console.tablememory

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import raf.console.tablememory.store.SettingsKeys
import raf.console.tablememory.store.SettingsState
import raf.console.tablememory.store.dataStore
import raf.console.tablememory.ui.theme.TableMemoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current

            val settingsFlow = context.dataStore.data.map { prefs: Preferences ->
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

                    // 🔹 новые поля
                    memoryTime = prefs[SettingsKeys.MEMORY_TIME] ?: "5",
                    memoryNoTimer = prefs[SettingsKeys.MEMORY_NO_TIMER] ?: false
                )
            }


            val settings by settingsFlow.collectAsState(
                initial = SettingsState(
                    tableSize = "5x5",
                    tableMode = "Цифры",
                    tableStyle = "Классический",
                    language = "Русский",
                    shuffleOnClick = false,
                    vibration = true,
                    redFlash = false,
                    centerDot = false,
                    dimMarked = false,
                    gameMode = "Стандартный",
                    mixedAlphabets = "",
                    darkTheme = false,

                    memoryTime = "5",
                    memoryNoTimer = false
                )

            )

            TableMemoryTheme(darkTheme = settings.darkTheme) {
                TableMemoryApp(
                    darkTheme = settings.darkTheme,
                    onToggleTheme = {
                        lifecycleScope.launch {
                            context.dataStore.edit { prefs ->
                                prefs[SettingsKeys.DARK_THEME] = !settings.darkTheme
                            }
                        }
                    },
                    tableSize = settings.tableSize.substringBefore("x").toInt(),
                    tableMode = settings.tableMode,
                    language = settings.language,
                    shuffleOnClick = settings.shuffleOnClick,
                    vibration = settings.vibration,
                    redFlash = settings.redFlash,
                    centerDot = settings.centerDot,
                    dimMarked = settings.dimMarked,
                    mixedAlphabets = if (settings.mixedAlphabets.isBlank()) emptySet()
                    else settings.mixedAlphabets.split("|").toSet(),
                    memoryTime = if (settings.memoryNoTimer) null
                    else settings.memoryTime.toLongOrNull()?.times(1000)
                )
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun TableMemoryApp(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    tableSize: Int,
    tableMode: String,
    language: String,
    shuffleOnClick: Boolean, // не используется в режимах ввода по символу, оставлен для совместимости
    vibration: Boolean,
    redFlash: Boolean,       // не используется в новом UX, оставлен для совместимости
    centerDot: Boolean,
    dimMarked: Boolean,      // не используется в новом UX, оставлен для совместимости
    mixedAlphabets: Set<String>,
    memoryTime: Long?,       // null = Без таймера
) {
    // Клавиатура, таблица и ответы пользователя
    var symbols by remember { mutableStateOf<List<String>>(emptyList()) }
    var grid by remember { mutableStateOf<List<String>>(emptyList()) }
    var userGrid by remember { mutableStateOf<List<String>>(emptyList()) }

    // Фазы и состояние
    var memorizeVisible by remember { mutableStateOf(false) } // таблица показана для запоминания
    var isInputRunning by remember { mutableStateOf(false) }  // идёт ввод
    var finished by remember { mutableStateOf(false) }
    var showCorrect by remember { mutableStateOf(false) }     // переключатель "правильный вариант / мои ответы"

    // Таймеры
    var countdown by remember { mutableStateOf(0) }  // обратный отсчёт (сек)
    var inputTime by remember { mutableStateOf(0L) } // таймер ввода (мс)

    // Курсор ввода
    var currentIndex by remember { mutableStateOf(0) }

    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    // Сброс при изменении настроек — до нового старта
    LaunchedEffect(tableSize, tableMode, language, mixedAlphabets, memoryTime) {
        symbols = emptyList()
        grid = emptyList()
        userGrid = emptyList()
        memorizeVisible = false
        isInputRunning = false
        finished = false
        showCorrect = false
        countdown = 0
        inputTime = 0
        currentIndex = 0
    }

    // Обратный отсчёт (режим с таймером)
    LaunchedEffect(memorizeVisible, countdown, memoryTime) {
        if (memorizeVisible && memoryTime != null && countdown > 0) {
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            // Переход к вводу
            memorizeVisible = false
            isInputRunning = true
            inputTime = 0
        }
    }

    // Таймер вверх во время ввода
    LaunchedEffect(isInputRunning) {
        while (isInputRunning) {
            delay(10)
            inputTime += 10
        }
    }

    // Вспомогательные генераторы — объёмно, но локально, чтобы код был самодостаточным
    fun buildLetterKeyboard(): List<String> {
        val base = if (tableMode == "Смесь букв разных алфавитов") {
            mixedAlphabets.flatMap { getAlphabet(it) }.distinct()
        } else {
            getAlphabet(language)
        }
        // случайные 18 уникальных (или меньше, если алфавит меньше)
        val pool = base.shuffled()
        val take = minOf(18, pool.size)
        return pool.take(take)
    }

    fun buildDigitKeyboard(): List<String> =
        listOf("1","2","3","4","5","6","7","8","9","0")

    fun buildKeyboard(): List<String> =
        if (tableMode.startsWith("Цифры")) buildDigitKeyboard() else buildLetterKeyboard()

    fun generateGridFromSymbols(n: Int, pool: List<String>): List<String> {
        val total = n * n
        if (pool.isEmpty()) return emptyList()
        return List(total) { pool.random() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background, contentColor = colors.onBackground) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            // Верхняя панель
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    onToggleTheme()
                    if (vibration) vibrate(context)
                }) {
                    Icon(
                        imageVector = if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "Смена темы",
                        tint = colors.onBackground
                    )
                }
                IconButton(onClick = {
                    if (vibration) vibrate(context)
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = colors.onBackground)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Индикаторы времени
            when {
                memorizeVisible && countdown > 0 -> {
                    Text(
                        text = countdown.toString(),
                        fontSize = 48.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                isInputRunning -> {
                    Text(
                        text = formatTimeShort(inputTime),
                        fontSize = 28.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (!memorizeVisible && !isInputRunning && !finished) {
                // Инструкция (до старта)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Вам будет показана таблица.\n" +
                                "Запомните её и воспроизведите\n" +
                                "с помощью клавиатуры ниже.",
                        fontSize = 20.sp,
                        color = colors.onBackground,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                // --- Таблица ---
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val areaW = maxWidth
                    val areaH = maxHeight
                    val stroke = 1.dp
                    val rawCell = (minOf(areaW, areaH) / tableSize)
                    val cellSize = (rawCell - stroke).coerceAtLeast(10.dp)
                    val fontSp = (cellSize.value * 0.42f).coerceIn(10f, 24f).sp

                    val isOdd = tableSize % 2 == 1

                    Column {
                        grid.chunked(tableSize).forEachIndexed { r, row ->
                            Row {
                                row.forEachIndexed { c, value ->
                                    val idx = r * tableSize + c
                                    val isCorrect = userGrid.getOrNull(idx) == value

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(cellSize)
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    when {
                                                        finished && !showCorrect && isCorrect -> Color.Green
                                                        finished && !showCorrect && (userGrid.getOrNull(
                                                            idx
                                                        )
                                                            ?.isNotBlank() == true) && !isCorrect -> Color.Red

                                                        else -> colors.outline
                                                    }
                                                )
                                            )
                                            .background(
                                                when {
                                                    finished && !showCorrect && isCorrect ->
                                                        Color.Green.copy(alpha = 0.2f)

                                                    finished && !showCorrect &&
                                                            (userGrid.getOrNull(idx)
                                                                ?.isNotBlank() == true) && !isCorrect ->
                                                        Color.Red.copy(alpha = 0.2f)

                                                    !memorizeVisible && isInputRunning && idx == currentIndex ->
                                                        colors.surfaceVariant

                                                    else -> Color.Transparent
                                                }
                                            )
                                    ) {
                                        when {
                                            // фаза показа либо просмотр правильного варианта
                                            memorizeVisible || (finished && showCorrect) -> {
                                                Text(
                                                    text = value,
                                                    fontSize = fontSp,
                                                    color = colors.onSurface,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1
                                                )
                                            }
                                            // фаза ввода или просмотр моих ответов
                                            isInputRunning || finished -> {
                                                Text(
                                                    text = userGrid.getOrNull(idx).orEmpty(),
                                                    fontSize = fontSp,
                                                    color = colors.onSurface,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1
                                                )
                                            }

                                            else -> Unit
                                        }

                                        // Точка в центре (для нечётной — в центральной ячейке)
                                        if (centerDot && isOdd && r == tableSize / 2 && c == tableSize / 2) {
                                            Box(
                                                modifier = Modifier
                                                    .size((cellSize * 0.18f).coerceAtLeast(6.dp))
                                                    .background(
                                                        Color.Red.copy(alpha = 0.5f),
                                                        CircleShape
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Результат и переключатель вида
            if (finished) {
                val correct = userGrid.zip(grid).count { it.first == it.second }
                Text(
                    text = "Правильно: $correct из ${grid.size}",
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        if (vibration) vibrate(context)
                        showCorrect = !showCorrect
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (showCorrect) "Показать мои ответы" else "Показать правильный вариант")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Кнопки управления (под таблицей — близко)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                when {
                    // Старт новой игры (первая фаза)
                    finished || (!memorizeVisible && !isInputRunning) -> {
                        val label = if (memoryTime == null) "Показать таблицу" else "Начать"
                        Button(onClick = {
                            if (vibration) vibrate(context)

                            // 1) Генерируем клавиатуру
                            symbols = buildKeyboard()

                            // 2) Генерируем таблицу из этих символов
                            grid = generateGridFromSymbols(tableSize, symbols)
                            userGrid = MutableList(grid.size) { "" }

                            // 3) Сбрасываем состояние
                            finished = false
                            showCorrect = false
                            inputTime = 0
                            currentIndex = 0

                            // 4) Фаза показа
                            memorizeVisible = true
                            if (memoryTime != null) {
                                countdown = (memoryTime / 1000).toInt()
                                isInputRunning = false
                            }
                        }) { Text(label) }
                    }

                    // Без таймера — вторая фаза (таблица показана, надо начать ввод)
                    memoryTime == null && memorizeVisible && !isInputRunning -> {
                        Button(onClick = {
                            if (vibration) vibrate(context)
                            memorizeVisible = false
                            isInputRunning = true
                            inputTime = 0
                        }) { Text("Начать") }
                    }

                    // Режим ввода — кнопка "Проверить"
                    isInputRunning && !finished -> {
                        Button(
                            onClick = {
                                if (vibration) vibrate(context)
                                isInputRunning = false
                                finished = true
                            }
                        ) { Text("Проверить") }
                    }
                }
            }

            // --- Клавиатура (только при вводе) ---
            if (isInputRunning && !finished) {
                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (tableMode.startsWith("Цифры")) {
                        // Ряды: 1 2 3 / 4 5 6 / 7 8 9 / 0 ⌫ ⏮ ⏭
                        @Composable
                        fun digitKey(label: String, onClick: () -> Unit) {
                            KeyButton(
                                label = label,
                                onClick = {
                                    if (vibration) vibrate(context)
                                    onClick()
                                },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .height(52.dp)
                                    .width(52.dp),
                                darkTheme = darkTheme
                            )
                        }

                        val row1 = listOf("1","2","3")
                        val row2 = listOf("4","5","6")
                        val row3 = listOf("7","8","9")

                        listOf(row1, row2, row3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                row.forEach { s ->
                                    digitKey(s) {
                                        userGrid = userGrid.toMutableList().also { list ->
                                            list[currentIndex] = s
                                        }
                                        if (currentIndex < userGrid.lastIndex) currentIndex++
                                    }
                                }
                            }
                        }

                        // Последний ряд: 0 ⌫ ⏮ ⏭
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // 0
                            digitKey("0") {
                                userGrid = userGrid.toMutableList().also { list ->
                                    list[currentIndex] = "0"
                                }
                                if (currentIndex < userGrid.lastIndex) currentIndex++
                            }

                            // ⌫
                            digitKey("⌫") {
                                userGrid = userGrid.toMutableList().also { list ->
                                    if (list[currentIndex].isNotEmpty()) {
                                        list[currentIndex] = ""
                                    } else if (currentIndex > 0) {
                                        currentIndex--
                                        list[currentIndex] = ""
                                    }
                                }
                            }

                            // ⏮
                            digitKey("⏮") {
                                if (currentIndex > 0) currentIndex--
                            }

                            // ⏭
                            digitKey("⏭") {
                                if (currentIndex < userGrid.lastIndex) currentIndex++
                            }
                        }
                    } else {
                        // Буквы: 3 ряда × 6 символов (итого 18)
                        val letters = symbols // уже сгенерированные 18 символов
                        val rows = letters.chunked(6)
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                row.forEach { s ->
                                    KeyButton(
                                        label = s,
                                        onClick = {
                                            if (vibration) vibrate(context)
                                            userGrid = userGrid.toMutableList().also { list ->
                                                list[currentIndex] = s
                                            }
                                            if (currentIndex < userGrid.lastIndex) currentIndex++
                                        },
                                        modifier = Modifier
                                            .weight(1f)   // каждая кнопка занимает равную долю строки
                                            .aspectRatio(1f) // квадратная форма
                                            .padding(4.dp),
                                        darkTheme = darkTheme
                                    )
                                }
                            }
                        }
                        // Ряд спец-кнопок: ⌫ ⏮ ⏭
                        // ряд спец-кнопок (буквы)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            KeyButton(
                                label = "⌫",
                                onClick = {
                                    if (vibration) vibrate(context)
                                    userGrid = userGrid.toMutableList().also { list ->
                                        if (list[currentIndex].isNotEmpty()) {
                                            list[currentIndex] = ""
                                        } else if (currentIndex > 0) {
                                            currentIndex--
                                            list[currentIndex] = ""
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .height(52.dp)
                                    .width(52.dp),
                                darkTheme = darkTheme
                            )

                            KeyButton(
                                label = "⏮",
                                onClick = {
                                    if (vibration) vibrate(context)
                                    if (currentIndex > 0) currentIndex--
                                },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .height(52.dp)
                                    .width(52.dp),
                                darkTheme = darkTheme
                            )

                            KeyButton(
                                label = "⏭",
                                onClick = {
                                    if (vibration) vibrate(context)
                                    if (currentIndex < userGrid.lastIndex) currentIndex++
                                },
                                modifier = Modifier
                                    .padding(4.dp)
                                    .height(52.dp)
                                    .width(52.dp),
                                darkTheme = darkTheme
                            )
                        }

                    }
                }
            }
        }
    }
}



/** Генерация содержимого таблицы */
/*private fun generateGrid(
    n: Int,
    mode: String,
    language: String,
    mixed: Set<String>
): List<String> {
    val total = n * n
    return when {
        // если режим Цифры и размер от 4х4 до 15х15 → цифры (0-9) с повторами
        mode.startsWith("Цифры") && n in 4..15 -> List(total) { ('0'..'9').random().toString() }
        mode.startsWith("Цифры") -> (1..total).map { it.toString() }.shuffled()
        mode == "Смесь букв разных алфавитов" -> {
            val combined = mixed.flatMap { getAlphabet(it) }.ifEmpty { getAlphabet("English") }
            if (combined.size >= total) combined.shuffled().take(total)
            else List(total) { combined.random() }
        }
        else -> {
            val alphabet = getAlphabet(language)
            if (alphabet.size >= total) alphabet.shuffled().take(total)
            else List(total) { alphabet.random() }
        }
    }
}*/
fun generateGrid(
    size: Int,
    mode: String,
    language: String,
    mixedAlphabets: Set<String>
): List<String> {
    val count = size * size

    return when {
        mode.startsWith("Цифры") -> {
            // 🔹 всегда цифры 0–9
            List(count) { (0..9).random().toString() }
        }
        mode.startsWith("Буквы") -> {
            val fullAlphabet = getAlphabet(language)
            val subset = fullAlphabet.shuffled().take(18) // случайные 18 букв
            List(count) { subset.random() }
        }
        mode == "Смесь букв разных алфавитов" -> {
            val all = mixedAlphabets.flatMap { getAlphabet(it) }
            val subset = all.shuffled().take(18).ifEmpty { all }
            List(count) { subset.random() }
        }
        else -> {
            // fallback — цифры
            List(count) { (0..9).random().toString() }
        }
    }
}


/** Алфавит для клавиатуры */
// Получение клавиатурных символов
fun getKeyboardSymbols(
    tableSize: Int,
    tableMode: String,
    language: String,
    mixedAlphabets: Set<String>
): List<String> {
    return when {
        tableMode.startsWith("Цифры") -> {
            // фиксированная клавиатура цифр
            listOf("1","2","3","4","5","6","7","8","9","0")
        }

        tableMode.startsWith("Буквы") -> {
            val alphabet = getAlphabet(language)
            // случайные 18 букв из алфавита
            alphabet.shuffled().take(18)
        }

        tableMode == "Смесь букв разных алфавитов" -> {
            val all = mixedAlphabets.flatMap { getAlphabet(it) }
            if (all.isNotEmpty()) all.shuffled().take(18) else ('A'..'Z').map { it.toString() }.take(18)
        }

        else -> {
            // fallback — английский алфавит
            ('A'..'Z').map { it.toString() }.take(18)
        }
    }
}

// Генерация таблицы только из допустимых символов
fun generateGridFromSymbols(tableSize: Int, symbols: List<String>): List<String> {
    val total = tableSize * tableSize
    return List(total) { symbols.random() }
}





/** Генерация содержимого таблицы (всегда список строк). */
/*private fun generateGrid(
    n: Int,
    mode: String,
    language: String,
    mixed: Set<String>
): List<String> {
    val total = n * n
    return when (mode) {
        "Цифры", "Цифры (обратный порядок)" ->
            (1..total).map { it.toString() }.shuffled()

        "Смесь букв разных алфавитов" -> {
            val combined = mixed.flatMap { getAlphabet(it) }.ifEmpty { getAlphabet("English") }
            if (combined.size >= total) combined.shuffled().take(total)
            else List(total) { combined.random() } // если букв меньше — допускаем повторы
        }

        else -> { // "Буквы" / "Буквы (обратный порядок)" — единственный режим: рандом
            val alphabet = getAlphabet(language)
            if (alphabet.size >= total) alphabet.shuffled().take(total)
            else List(total) { alphabet.random() }
        }
    }
}*/

/** Алфавиты (как в твоих заготовках). */
@Suppress("SpellCheckingInspection")
fun getAlphabet(lang: String): List<String> = when (lang) {
    "Русский" -> listOf(
        "А","Б","В","Г","Д","Е","Ё","Ж","З","И","Й","К","Л","М","Н","О","П","Р","С","Т","У","Ф","Х","Ц","Ч","Ш","Щ","Ъ","Ы","Ь","Э","Ю","Я"
    )
    "English","Français","Italiano","Deutsch","Polski","Español" -> when (lang) {
        "Deutsch" -> listOf("A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z","Ä","Ö","Ü","ẞ")
        "Polski"  -> listOf("A","Ą","B","C","Ć","D","E","Ę","F","G","H","I","J","K","L","Ł","M","N","Ń","O","Ó","P","Q","R","S","Ś","T","U","V","W","X","Y","Z","Ź","Ż")
        "Español" -> listOf("A","B","C","D","E","F","G","H","I","J","K","L","M","N","Ñ","O","P","Q","R","S","T","U","V","W","X","Y","Z")
        "Français"-> listOf("A","À","Â","Æ","B","C","Ç","D","E","É","È","Ê","Ë","F","G","H","I","Î","Ï","J","K","L","M","N","O","Ô","Œ","P","Q","R","S","T","U","Ù","Û","Ü","V","W","X","Y","Ÿ","Z")
        "Italiano"-> listOf("A","B","C","D","E","È","É","F","G","H","I","Ì","Í","Î","J","K","L","M","N","О","Ò","Ó","P","Q","R","S","T","U","Ù","Ú","V","W","X","Y","Z")
        else -> ('A'..'Z').map { it.toString() }
    }
    "العربية (Арабский)" -> listOf("ا","ب","ت","ث","ج","ح","خ","د","ذ","ر","ز","س","ش","ص","ض","ط","ظ","ع","غ","ف","ق","ك","ل","م","ن","ه","و","ي")
    "עברית (Иврит)" -> listOf("א","ב","ג","ד","ה","ו","ז","ח","ט","י","כ","ל","מ","נ","ס","ע","פ","צ","ק","ר","ש","ת")
    "हिन्दी (Хинди)" -> listOf(
        "अ","आ","इ","ई","उ","ऊ","ए","ऐ","ओ","औ",
        "क","ख","ग","घ","ङ","च","छ","ज","झ","ञ",
        "ट","ठ","ड","ढ","ण","त","थ","द","ध","न",
        "प","फ","ब","भ","म","य","र","ल","व","श","ष","स","ह"
    )
    "中文 (Китайский)" -> listOf(
        "的","一","是","了","我","不","在","人","有","这","中","大","来","上","国","个","到","说","们","为",
        "子","和","你","地","出","道","也","时","要","就","下","得","里","后","生","会","自","着","去","之",
        "过","家","学","对","多","天","小","心","只","如","新","见","分","因","经","其"
    )
    "日本語 (Японский)" -> listOf(
        "あ","い","う","え","お","か","き","く","け","こ",
        "さ","し","す","せ","そ","た","ち","つ","て","と",
        "な","に","ぬ","ね","の","は","ひ","ふ","へ","ほ",
        "ま","み","む","め","も","や","ゆ","よ","ら","り","る","れ","ろ","わ","を","ん"
    )
    "ܐܣܛܢܓܠܐ (Эстангело)" -> listOf("ܐ","ܒ","ܓ","ܕ","ܗ","ܘ","ܙ","ܚ","ܛ","ܝ","ܟ","ܠ","ܡ","ܢ","ܣ","ܥ","ܦ","ܨ","ܩ","ܪ","ܫ","ܬ")
    "አማርኛ (Амхарский)" -> listOf("ሀ","ለ","መ","ሠ","ረ","ሰ","ሸ","ቀ","በ","ተ","ቸ","ኀ","ነ","ኘ","አ","ከ","ወ","ዐ","ዘ","ዠ","የ","ደ","ጀ","ገ","ጐ","ጠ","ጨ","ጰ","ጸ","ፀ","ፈ","ፐ")
    "བོད་སྐད (Тибетский)" -> listOf("ཀ","ཁ","ག","ང","ཅ","ཆ","ཇ","ཉ","ཏ","ཐ","ད","ན","པ","ཕ","བ","མ","ཙ","ཚ","ཛ","ཝ","ཞ","ཟ","འ","ཡ","ར","ལ","ཤ","ས","ཧ","ཨ")
    "မြန်မာ (Бирманский)" -> listOf("က","ခ","ဂ","ဃ","င","စ","ဆ","ဇ","ဈ","ည","ဋ","ဌ","ဍ","ဎ","ဏ","တ","ထ","ဒ","ဓ","န","ပ","ဖ","ဗ","ဘ","မ","ယ","ရ","လ","ဝ","သ","ဟ","ဠ","အ")
    "ខ្មែរ (Кхмерский)" -> listOf("ក","ខ","គ","ឃ","ង","ច","ឆ","ជ","ឈ","ញ","ដ","ឋ","ឌ","ឍ","ណ","ត","ថ","ទ","ធ","ន","ប","ផ","ព","ភ","ម","យ","រ","ល","វ","ស","ហ","ឡ","អ")
    "ລາວ (Лаосский)" -> listOf("ກ","ຂ","ຄ","ງ","ຈ","ສ","ຊ","ຍ","ດ","ຕ","ຖ","ທ","ນ","ບ","ປ","ຜ","ຝ","ພ","ຟ","ມ","ຢ","ຣ","ລ","ວ","ຫ","ອ","ຮ")
    "ไทย (Тайский)" -> listOf("ก","ข","ค","ฆ","ง","จ","ฉ","ช","ซ","ฌ","ญ","ฎ","ฏ","ฐ","ฑ","ฒ","ณ","ด","ต","ถ","ท","ธ","น","บ","ป","ผ","พ","ภ","ม","ย","ร","ล","ว","ศ","ษ","ส","ห","ฬ","อ","ฮ")
    "සිංහල (Сингальский)" -> listOf("අ","ආ","ඇ","ඈ","ඉ","ඊ","උ","ඌ","එ","ඒ","ඔ","ඕ","ක","ඛ","ග","ඝ","ඞ","ච","ඡ","ජ","ඣ","ඤ","ට","ඨ","ඩ","ඪ","ණ","ත","ථ","ද","ධ","න","ප","ඵ","බ","භ","ම","ය","ර","ල","ව","ශ","ෂ","ස","හ","ළ","ෆ")
    "ᰛᰵᰎᰵ (Лепча)" -> listOf("ᰛ","ᰜ","ᰝ","ᰞ","ᰟ","ᰠ","ᰡ","ᰢ","ᰣ","ᰤ","ᰥ","ᰦ","ᰧ","ᰨ","ᰩ","ᰪ","ᰫ","ᰬ","ᰭ","ᰮ","ᰯ","ᰰ","ᰱ","ᰲ","ᰳ","ᰴ")
    "ᤕᤠᤰᤌᤢᤱ (Лимбу)" -> listOf("ᤀ","ᤁ","ᤂ","ᤃ","ᤄ","ᤅ","ᤆ","ᤇ","ᤈ","ᤉ","ᤊ","ᤋ","ᤌ","ᤍ","ᤎ","ᤏ","ᤐ","ᤑ","ᤒ","ᤓ","ᤔ","ᤕ","ᤖ","ᤗ","ᤘ","ᤙ")
    "ᎠᏂᏴᏫ (Чероки)" -> listOf("Ꭰ","Ꭱ","Ꭲ","Ꭳ","Ꭴ","Ꭵ","Ꭶ","Ꭷ","Ꭸ","Ꭹ","Ꭺ","Ꭻ","Ꭼ","Ꭽ","Ꭾ","Ꭿ","Ꮀ","Ꮁ","Ꮂ","Ꮃ","Ꮄ","Ꮅ","Ꮆ","Ꮇ","Ꮈ","Ꮉ","Ꮊ","Ꮋ","Ꮌ","Ꮍ")
    else -> ('A'..'Z').map { it.toString() }
}

fun vibrate(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(VibratorManager::class.java)
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    vibrator?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            it.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            it.vibrate(100)
        }
    }
}

// Формат времени
fun formatTimeShort(ms: Long): String {
    val seconds = ms / 1000
    val hundredths = (ms % 1000) / 10
    return String.format("%02d.%02d", seconds, hundredths)
}
fun formatTimeDetailed(ms: Long): String {
    var rest = ms
    val hours = rest / 3_600_000
    rest %= 3_600_000
    val minutes = rest / 60_000
    rest %= 60_000
    val seconds = rest / 1000
    rest %= 1000
    val millis = rest
    val sb = StringBuilder()
    if (hours > 0) sb.append("$hours час ")
    if (minutes > 0) sb.append("$minutes мин ")
    if (seconds > 0) sb.append("$seconds сек ")
    if (millis > 0) sb.append("$millis мс")
    if (sb.isEmpty()) sb.append("0 сек")
    return sb.toString().trim()
}

@Composable
fun KeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    darkTheme: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val isDark = darkTheme

    val bgColor = if (isDark) Color.Black else Color.White
    val textColor = if (isDark) Color.White else Color.Black
    val borderColor = textColor

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}


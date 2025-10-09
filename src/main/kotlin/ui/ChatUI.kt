package org.kozyrev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch
import org.kozyrev.claude.ChatManager
import org.kozyrev.claude.LLMProvider
import org.kozyrev.claude.Message
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(chatManager: ChatManager) {
    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentProvider by remember { mutableStateOf(chatManager.getLLMProvider()) }
    var temperature by remember { mutableStateOf(1.0f) }
    var temperatureText by remember { mutableStateOf("1.0") }
    var temperatureUpdateJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Функция для валидации и применения температуры
    fun applyTemperature() {
        temperatureText.toDoubleOrNull()?.let { value ->
            val validatedValue = when {
                value < 0.0 -> 0.0
                value > 1.0 -> 1.0
                else -> (value * 10).toInt() / 10.0 // Округление до 1 знака
            }
            temperature = validatedValue.toFloat()
            temperatureText = validatedValue.toString()
            chatManager.setTemperature(validatedValue)
        } ?: run {
            // Если введено невалидное значение, возвращаем текущее
            temperatureText = temperature.toString()
        }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp)
        ) {
            // Заголовок и индикатор режима
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI Помощник",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Выбор LLM провайдера
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF3E5F5),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🤖 Выбор AI модели",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6A1B9A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (currentProvider == LLMProvider.CLAUDE) Color(0xFF9C27B0) else Color(0xFFBDBDBD),
                            modifier = Modifier.clickable {
                                chatManager.setLLMProvider(LLMProvider.CLAUDE)
                                currentProvider = LLMProvider.CLAUDE
                            }
                        ) {
                            Text(
                                text = "🔵 Claude",
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (currentProvider == LLMProvider.YANDEX_GPT) Color(0xFF9C27B0) else Color(0xFFBDBDBD),
                            modifier = Modifier.clickable {
                                if (chatManager.isYandexGPTAvailable()) {
                                    chatManager.setLLMProvider(LLMProvider.YANDEX_GPT)
                                    currentProvider = LLMProvider.YANDEX_GPT
                                }
                            }
                        ) {
                            Text(
                                text = "🟡 Yandex GPT",
                                fontSize = 12.sp,
                                color = if (chatManager.isYandexGPTAvailable()) Color.White else Color.Gray,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (!chatManager.isYandexGPTAvailable()) {
                        Text(
                            text = "Yandex GPT недоступен (не настроен API ключ)",
                            fontSize = 10.sp,
                            color = Color(0xFF6A1B9A).copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Управление температурой
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFE3F2FD),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🌡️ Температура модели",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1565C0)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Slider(
                            value = temperature,
                            onValueChange = { newValue ->
                                temperature = newValue
                                val roundedValue = ((newValue * 10).toInt() / 10.0)
                                temperatureText = roundedValue.toString()
                                chatManager.setTemperature(roundedValue)
                            },
                            valueRange = 0f..1f,
                            steps = 9,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF1976D2),
                                activeTrackColor = Color(0xFF1976D2),
                                inactiveTrackColor = Color(0xFFBBDEFB)
                            )
                        )

                        OutlinedTextField(
                            value = temperatureText,
                            onValueChange = { newText ->
                                temperatureText = newText

                                // Отменяем предыдущий таймер
                                temperatureUpdateJob?.cancel()

                                // Запускаем новый таймер на 1 секунду
                                temperatureUpdateJob = scope.launch {
                                    delay(1000)
                                    applyTemperature()
                                }
                            },
                            modifier = Modifier
                                .width(80.dp)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                        temperatureUpdateJob?.cancel()
                                        applyTemperature()
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .onFocusChanged { focusState ->
                                    if (!focusState.isFocused) {
                                        temperatureUpdateJob?.cancel()
                                        applyTemperature()
                                    }
                                },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                        )
                    }

                    Text(
                        text = "0 = более точные ответы, 1 = более креативные",
                        fontSize = 10.sp,
                        color = Color(0xFF1565C0).copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Подсказки по использованию
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFF3E0),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .drawWithContent {
                        val heightPx = 1f
                        drawContent()
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = heightPx
                        )
                    }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Как пользоваться:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Задавайте любые вопросы AI помощнику",
                        fontSize = 11.sp,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        text = "• Получайте подробные и полезные ответы",
                        fontSize = 11.sp,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        text = "• Используйте температуру для управления стилем ответов",
                        fontSize = 11.sp,
                        color = Color(0xFFE65100)
                    )
                }
            }

            // Область сообщений
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message, chatManager)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Поле ввода и кнопка отправки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Введите ваш вопрос...") },
                    enabled = !isLoading,
                    maxLines = 3
                )

                Button(
                    onClick = {
                        if (messageText.isNotBlank() && !isLoading) {
                            val userMessage = messageText.trim()
                            messageText = ""

                            // Сразу добавляем сообщение пользователя в UI
                            messages = messages + Message(
                                role = "user",
                                content = userMessage
                            )

                            isLoading = true

                            scope.launch {
                                try {
                                    chatManager.sendMessage(userMessage)
                                    // Обновляем весь список из истории (включает и user, и assistant)
                                    messages = chatManager.getHistory()

                                    // Прокрутка вниз к последнему сообщению
                                    listState.animateScrollToItem(messages.size - 1)
                                } catch (e: Exception) {
                                    // Обработка ошибок
                                    e.printStackTrace()
                                    // Добавляем сообщение об ошибке в UI
                                    // Сообщение пользователя уже отображается, добавляем только ошибку
                                    val errorMessage = """
                                        ⚠️ Произошла ошибка при получении ответа: ${e.message}

                                        Пожалуйста, попробуйте задать вопрос ещё раз.
                                    """.trimIndent()

                                    messages = messages + Message(
                                        role = "assistant",
                                        content = errorMessage,
                                        isSystemMessage = true
                                    )

                                    // Прокрутка к сообщению об ошибке
                                    listState.animateScrollToItem(messages.size - 1)
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = !isLoading && messageText.isNotBlank(),
                    modifier = Modifier.height(56.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    } else {
                        Text("Отправить")
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message, chatManager: ChatManager) {
    val isUser = message.role == "user"
    val backgroundColor = if (isUser) Color(0xFF2196F3) else Color(0xFFE0E0E0)
    val textColor = if (isUser) Color.White else Color.Black
    val alignment = if (isUser) Alignment.BottomEnd else Alignment.BottomStart

    // Все сообщения выводим как есть (простой текст)
    val displayContent = message.content

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
            modifier = Modifier
                .widthIn(max = 500.dp)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = if (isUser) "Вы" else "AI Помощник",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = displayContent,
                    color = textColor,
                    fontSize = 14.sp
                )
            }
        }
    }
}

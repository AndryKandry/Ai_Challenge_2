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
import org.kozyrev.claude.ChatMode
import org.kozyrev.claude.Message
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import coil3.compose.AsyncImage

@Composable
fun ChatScreen(chatManager: ChatManager) {
    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf(ChatMode.SIMPLE) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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
                Column {
                    Text(
                        text = "🍳 Кулинарный Помощник",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "AI помощник для приготовления блюд",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (currentMode == ChatMode.JSON) Color(0xFF4CAF50) else Color(0xFF9E9E9E),
                    modifier = Modifier.clickable {
                        // Переключаем режим
                        val newMode = if (currentMode == ChatMode.SIMPLE) ChatMode.JSON else ChatMode.SIMPLE
                        chatManager.setChatMode(newMode)
                        currentMode = newMode
                    }
                ) {
                    Text(
                        text = if (currentMode == ChatMode.JSON) "JSON режим" else "Обычный режим",
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
                        text = "💡 Как пользоваться:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Расскажите, что хотите приготовить (суп, второе, кашу)",
                        fontSize = 11.sp,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        text = "• Укажите доступные ингредиенты и способы приготовления",
                        fontSize = 11.sp,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        text = "• Получите 2-3 готовых рецепта с подробными инструкциями",
                        fontSize = 11.sp,
                        color = Color(0xFFE65100)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Команды: /simple_chat (обычный режим), /json_chat (JSON режим)",
                        fontSize = 10.sp,
                        color = Color(0xFFE65100).copy(alpha = 0.7f),
                        fontWeight = FontWeight.Light
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
                    placeholder = { Text("Например: Хочу приготовить суп...") },
                    enabled = !isLoading,
                    maxLines = 3
                )

                Button(
                    onClick = {
                        if (messageText.isNotBlank() && !isLoading) {
                            val userMessage = messageText.trim()
                            messageText = ""

                            // Проверка на команды переключения режима
                            when (userMessage) {
                                "/simple_chat" -> {
                                    chatManager.setChatMode(ChatMode.SIMPLE)
                                    currentMode = ChatMode.SIMPLE
                                    return@Button
                                }
                                "/json_chat" -> {
                                    chatManager.setChatMode(ChatMode.JSON)
                                    currentMode = ChatMode.JSON
                                    return@Button
                                }
                            }

                            isLoading = true

                            scope.launch {
                                try {
                                    chatManager.sendMessage(userMessage)
                                    messages = chatManager.getHistory()

                                    // Прокрутка вниз к последнему сообщению
                                    listState.animateScrollToItem(messages.size - 1)
                                } catch (e: Exception) {
                                    // Обработка ошибок
                                    e.printStackTrace()
                                    // Добавляем сообщение об ошибке в историю
                                    val errorMessage = """
                                        ⚠️ Произошла ошибка при получении ответа: ${e.message}

                                        Пожалуйста, попробуйте задать вопрос ещё раз.
                                    """.trimIndent()
                                    // Создаем сообщение об ошибке и обновляем список
                                    messages = messages + Message(
                                        role = "assistant",
                                        content = errorMessage,
                                        isSystemMessage = true
                                    )
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

    // Форматируем контент в зависимости от роли и режима
    val displayContent = if (isUser || message.isSystemMessage) {
        // Для пользователя и системных сообщений выводим как есть
        message.content
    } else {
        // Для сообщений от LLM форматируем через chatManager
        chatManager.formatResponse(message.content)
    }

    // Извлекаем изображения только для сообщений от LLM
    val images = if (!isUser && !message.isSystemMessage) {
        chatManager.extractImages(message.content)
    } else {
        emptyList()
    }

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
                    text = if (isUser) "Вы" else "🍳 Кулинарный Помощник",
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

                // Отображение изображений
                if (images.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    images.forEach { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Изображение рецепта",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .padding(vertical = 4.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

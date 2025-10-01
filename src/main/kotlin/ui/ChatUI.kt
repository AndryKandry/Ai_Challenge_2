package org.kozyrev.ui

import androidx.compose.foundation.background
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
import kotlinx.coroutines.launch
import org.kozyrev.claude.ChatManager
import org.kozyrev.claude.Message

@Composable
fun ChatScreen(chatManager: ChatManager) {
    var messageText by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp)
        ) {
            // Заголовок
            Text(
                text = "Claude AI Chat",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Область сообщений
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
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
                    placeholder = { Text("Введите сообщение...") },
                    enabled = !isLoading,
                    maxLines = 3
                )

                Button(
                    onClick = {
                        if (messageText.isNotBlank() && !isLoading) {
                            val userMessage = messageText.trim()
                            messageText = ""
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
fun MessageBubble(message: Message) {
    val isUser = message.role == "user"
    val backgroundColor = if (isUser) Color(0xFF2196F3) else Color(0xFFE0E0E0)
    val textColor = if (isUser) Color.White else Color.Black
    val alignment = if (isUser) Alignment.BottomEnd else Alignment.BottomStart

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = backgroundColor,
            modifier = Modifier
                .widthIn(max = 400.dp)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = if (isUser) "Вы" else "Claude",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    color = textColor,
                    fontSize = 14.sp
                )
            }
        }
    }
}

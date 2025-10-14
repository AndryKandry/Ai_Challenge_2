package org.kozyrev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import org.kozyrev.mcp.*

@Composable
fun MCPToolsScreen() {
    var serverUrl by remember { mutableStateOf("http://127.0.0.1:8000/mcp") }
    var tools by remember { mutableStateOf<List<MCPTool>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var serverInfo by remember { mutableStateOf<String?>(null) }
    var useMockServer by remember { mutableStateOf(false) }
    var selectedTool by remember { mutableStateOf<MCPTool?>(null) }
    var toolResult by remember { mutableStateOf<String?>(null) }
    var mcpClient by remember { mutableStateOf<MCPClientInterface?>(null) }
    var ttoolsInfo by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Заголовок
        Text(
            text = "MCP Tools Explorer",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Поле ввода URL сервера
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("MCP Server URL") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Чекбокс для выбора Mock сервера
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = useMockServer,
                onCheckedChange = { useMockServer = it }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Использовать демо-сервер (Mock)",
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Кнопка подключения
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        serverInfo = null
                        tools = emptyList()
                        selectedTool = null
                        toolResult = null
                        ttoolsInfo = null

                        // Функция для попытки подключения
                        suspend fun tryConnectWithClient(client: MCPClientInterface): Boolean {
                            return try {
                                val initResult = client.initialize()
                                if (initResult.isSuccess) {
                                    val info = initResult.getOrNull()
                                    serverInfo = "Сервер: ${info?.serverInfo?.name} v${info?.serverInfo?.version}"

                                    val toolsResult = client.listTools()
                                    if (toolsResult.isSuccess) {
                                        tools = toolsResult.getOrNull() ?: emptyList()
                                        mcpClient = client

                                        // Пробуем вызвать ttools для получения детальной информации
                                        try {
                                            val ttoolsResult = client.callTool("ttools", emptyMap())
                                            if (ttoolsResult.isSuccess) {
                                                val content = ttoolsResult.getOrNull()?.content
                                                ttoolsInfo = content?.firstOrNull()?.text
                                                println("✓ ttools вызван успешно")
                                            } else {
                                                println("⚠️ ttools недоступен: ${ttoolsResult.exceptionOrNull()?.message}")
                                            }
                                        } catch (e: Exception) {
                                            println("⚠️ ttools недоступен: ${e.message}")
                                        }

                                        true
                                    } else {
                                        errorMessage = "Ошибка получения инструментов: ${toolsResult.exceptionOrNull()?.message}"
                                        false
                                    }
                                } else {
                                    errorMessage = "Ошибка инициализации: ${initResult.exceptionOrNull()?.message}"
                                    false
                                }
                            } catch (e: Exception) {
                                errorMessage = "Ошибка: ${e.message}"
                                false
                            }
                        }

                        try {
                            if (useMockServer) {
                                // Mock клиент
                                val mockClient = MCPMockClient()
                                tryConnectWithClient(mockClient)
                            } else {
                                // Определяем, какой клиент использовать
                                val client: MCPClientInterface = when {
                                    serverUrl.contains("deepwiki.com") -> {
                                        println("🔄 Используем специальный DeepWiki клиент...")
                                        MCPDeepWikiClient()
                                    }
                                    serverUrl.contains("127.0.0.1") || serverUrl.contains("localhost") -> {
                                        println("🔄 Локальный сервер обнаружен, используем Python FastMCP клиент...")
                                        MCPPythonClient(serverUrl)
                                    }
                                    else -> {
                                        println("🔄 Попытка подключения через SSE клиент...")
                                        MCPSSEClient(serverUrl)
                                    }
                                }

                                val result = tryConnectWithClient(client)

                                if (!result && !serverUrl.contains("deepwiki.com") &&
                                    !(serverUrl.contains("127.0.0.1") || serverUrl.contains("localhost"))) {
                                    println("⚠️ SSE клиент не сработал, пробую упрощенный клиент...")
                                    client.close()

                                    val simpleClient = MCPSimpleClient(serverUrl)
                                    val simpleResult = tryConnectWithClient(simpleClient)

                                    if (!simpleResult) {
                                        simpleClient.close()
                                        errorMessage = "Не удалось подключиться к серверу. Попробуйте Mock сервер для тестирования или проверьте URL."
                                    }
                                } else if (!result) {
                                    client.close()
                                    if (serverUrl.contains("127.0.0.1") || serverUrl.contains("localhost")) {
                                        errorMessage = "Не удалось подключиться к локальному серверу. Убедитесь, что Python MCP сервер запущен."
                                    } else {
                                        errorMessage = "Не удалось подключиться к серверу. Проверьте URL и интернет-соединение."
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = "Ошибка подключения: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isLoading && (useMockServer || serverUrl.isNotBlank())
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Подключение...")
                } else {
                    Text(if (useMockServer) "Подключиться к демо-серверу" else "Подключиться")
                }
            }

            if (mcpClient != null) {
                Button(
                    onClick = {
                        mcpClient?.close()
                        mcpClient = null
                        tools = emptyList()
                        serverInfo = null
                        selectedTool = null
                        toolResult = null
                        ttoolsInfo = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Отключиться")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Информация о сервере
        serverInfo?.let { info ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = info,
                    modifier = Modifier.padding(12.dp),
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Детальная информация из ttools
        ttoolsInfo?.let { info ->
            TToolsInfoCard(
                info = info,
                onDismiss = { ttoolsInfo = null }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Сообщение об ошибке
        errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Результат вызова инструмента
        toolResult?.let { result ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Результат:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        TextButton(onClick = { toolResult = null }) {
                            Text("Закрыть")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 200.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Список инструментов
        if (tools.isNotEmpty()) {
            Text(
                text = "Найдено инструментов: ${tools.size}",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tools) { tool ->
                    ToolCard(
                        tool = tool,
                        isSelected = selectedTool == tool,
                        onSelect = { selectedTool = if (selectedTool == tool) null else tool },
                        onExecute = { args ->
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                toolResult = null
                                try {
                                    val result = mcpClient?.callTool(tool.name, args)
                                    if (result?.isSuccess == true) {
                                        val content = result.getOrNull()?.content
                                        toolResult = content?.joinToString("\n") { c ->
                                            c.text ?: c.data ?: "Нет данных"
                                        } ?: "Пустой результат"
                                    } else {
                                        errorMessage = "Ошибка вызова: ${result?.exceptionOrNull()?.message}"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Ошибка: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        isExecuting = isLoading
                    )
                }
            }
        } else if (!isLoading && errorMessage == null && serverUrl.isNotBlank()) {
            // Показываем подсказку, если еще не подключались
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Подключитесь к MCP серверу",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Введите URL сервера и нажмите кнопку подключения для получения списка доступных инструментов.\n\nПримеры серверов:\n\n🏠 Локальный Python MCP сервер:\nhttp://127.0.0.1:8000/mcp\n",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
fun TToolsInfoCard(info: String, onDismiss: () -> Unit) {
    // Парсим JSON массив инструментов ДО Composable блока
    data class ParsedTool(val name: String, val signature: String, val doc: String)

    val parsedTools = remember(info) {
        try {
            val json = Json { ignoreUnknownKeys = true; isLenient = true }
            val toolsArray = json.decodeFromString<JsonArray>(info)
            toolsArray.mapNotNull { element ->
                try {
                    val toolObj = element.jsonObject
                    ParsedTool(
                        name = toolObj["name"]?.jsonPrimitive?.content ?: "unknown",
                        signature = toolObj["signature"]?.jsonPrimitive?.content ?: "",
                        doc = toolObj["doc"]?.jsonPrimitive?.content ?: ""
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Детальная информация об инструментах:",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                TextButton(onClick = onDismiss) {
                    Text("Скрыть")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (parsedTools != null && parsedTools.isNotEmpty()) {
                // Отображаем распарсенные инструменты
                parsedTools.forEachIndexed { index, tool ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = tool.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (tool.signature.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = tool.signature,
                                    fontSize = 12.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }

                            if (tool.doc.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = tool.doc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    if (index < parsedTools.size - 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            } else {
                // Если не удалось распарсить JSON, показываем как есть
                Text(
                    text = info,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 150.dp),
                    fontSize = 12.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun ToolCard(
    tool: MCPTool,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onExecute: (Map<String, Any>) -> Unit,
    isExecuting: Boolean
) {
    var toolParams by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Название инструмента
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tool.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = onSelect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.secondary
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(if (isSelected) "Свернуть" else "Вызвать")
                }
            }

            // Описание
            tool.description?.let { description ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Форма для ввода параметров (если выбран)
            if (isSelected) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // Парсим схему и показываем поля ввода
                val properties = tool.inputSchema?.get("properties") as? JsonObject
                val required = (tool.inputSchema?.get("required") as? JsonArray)?.map {
                    (it as JsonPrimitive).content
                } ?: emptyList()

                if (properties != null) {
                    properties.forEach { (paramName, paramSchema) ->
                        val schema = paramSchema as? JsonObject
                        val description = (schema?.get("description") as? JsonPrimitive)?.content ?: ""
                        val isRequired = required.contains(paramName)

                        OutlinedTextField(
                            value = toolParams[paramName] ?: "",
                            onValueChange = { toolParams = toolParams + (paramName to it) },
                            label = { Text("$paramName ${if (isRequired) "*" else ""}") },
                            placeholder = { Text(description) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = {
                            val args = toolParams.mapValues { it.value as Any }
                            onExecute(args)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isExecuting && required.all { toolParams.containsKey(it) && toolParams[it]?.isNotBlank() == true }
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Выполнение...")
                        } else {
                            Text("Выполнить")
                        }
                    }
                } else {
                    Text(
                        text = "Этот инструмент не требует параметров",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onExecute(emptyMap()) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isExecuting
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Выполнение...")
                        } else {
                            Text("Выполнить")
                        }
                    }
                }
            }

            // Схема входных данных (свернутая форма)
            if (!isSelected) {
                tool.inputSchema?.let { schema ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Input Schema:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = schema.toString().take(100) + "...",
                            fontSize = 11.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

package org.kozyrev.mcp

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Модели данных для Model Context Protocol (MCP)
 */

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MCPRequest(
    @EncodeDefault val jsonrpc: String = "2.0",
    val id: Int,
    val method: String,
    @EncodeDefault val params: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class MCPResponse(
    val jsonrpc: String,
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: MCPError? = null
)

@Serializable
data class MCPError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

@Serializable
data class MCPTool(
    val name: String,
    val description: String? = null,
    val inputSchema: JsonObject? = null
)

@Serializable
data class MCPToolsList(
    val tools: List<MCPTool>
)

@Serializable
data class MCPServerInfo(
    val name: String,
    val version: String
)

@Serializable
data class MCPInitializeResult(
    val protocolVersion: String,
    val serverInfo: MCPServerInfo,
    val capabilities: JsonObject? = null
)

@Serializable
data class MCPToolCallResult(
    val content: List<MCPContent>,
    val isError: Boolean? = null
)

@Serializable
data class MCPContent(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null
)

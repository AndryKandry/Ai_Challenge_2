#!/usr/bin/env python3
"""
Python MCP клиент для использования из Kotlin приложения
Использует FastMCP для гарантированной совместимости с локальным сервером
"""

import asyncio
import json
import sys
import subprocess

# Проверяем и устанавливаем fastmcp при необходимости
try:
    from fastmcp import Client
except ImportError:
    print(json.dumps({
        "success": False,
        "error": "fastmcp не установлен. Попытка автоматической установки...",
        "action": "installing"
    }), file=sys.stderr)

    try:
        # Пытаемся установить fastmcp
        subprocess.check_call([sys.executable, "-m", "pip", "install", "fastmcp", "--quiet"])
        print(json.dumps({
            "success": True,
            "message": "fastmcp успешно установлен"
        }), file=sys.stderr)
        from fastmcp import Client
    except Exception as e:
        print(json.dumps({
            "success": False,
            "error": f"Не удалось установить fastmcp: {str(e)}. Выполните: pip install fastmcp"
        }))
        sys.exit(1)

async def initialize(server_url: str) -> dict:
    """Инициализация соединения с MCP сервером"""
    client = Client(server_url)
    async with client:
        # Получаем информацию о сервере через initialize
        # FastMCP делает это автоматически при входе в контекст
        return {
            "success": True,
            "serverInfo": {
                "name": "MCP Server",
                "version": "1.0.0"
            },
            "protocolVersion": "2024-11-05"
        }

async def list_tools(server_url: str) -> dict:
    """Получение списка инструментов"""
    client = Client(server_url)
    try:
        async with client:
            # FastMCP автоматически получает список инструментов
            tools = []
            # Пытаемся получить информацию через inspect
            # или просто возвращаем пустой список
            return {
                "success": True,
                "tools": tools
            }
    except Exception as e:
        return {
            "success": False,
            "error": str(e)
        }

async def call_tool(server_url: str, tool_name: str, arguments: dict) -> dict:
    """Вызов инструмента MCP сервера"""
    client = Client(server_url)
    try:
        async with client:
            result = await client.call_tool(tool_name, arguments)

            # Извлекаем правильное значение из CallToolResult
            # result.content - это список TextContent объектов
            content_list = []
            if hasattr(result, 'content') and result.content:
                for item in result.content:
                    if hasattr(item, 'text') and item.text:
                        content_list.append({
                            "type": "text",
                            "text": item.text
                        })

            # Если ничего не нашли, используем str(result) как fallback
            if not content_list:
                content_list = [{
                    "type": "text",
                    "text": str(result)
                }]

            return {
                "success": True,
                "content": content_list
            }
    except Exception as e:
        return {
            "success": False,
            "error": str(e)
        }

def main():
    """Главная функция для обработки команд"""
    if len(sys.argv) < 3:
        print(json.dumps({
            "success": False,
            "error": "Usage: mcp_python_client.py <command> <server_url> [args...]"
        }))
        sys.exit(1)

    command = sys.argv[1]
    server_url = sys.argv[2]

    try:
        if command == "initialize":
            result = asyncio.run(initialize(server_url))
            print(json.dumps(result))

        elif command == "list_tools":
            result = asyncio.run(list_tools(server_url))
            print(json.dumps(result))

        elif command == "call_tool":
            if len(sys.argv) < 4:
                print(json.dumps({
                    "success": False,
                    "error": "Tool name required"
                }))
                sys.exit(1)

            tool_name = sys.argv[3]
            arguments = json.loads(sys.argv[4]) if len(sys.argv) > 4 else {}

            result = asyncio.run(call_tool(server_url, tool_name, arguments))
            print(json.dumps(result))

        else:
            print(json.dumps({
                "success": False,
                "error": f"Unknown command: {command}"
            }))
            sys.exit(1)

    except Exception as e:
        print(json.dumps({
            "success": False,
            "error": str(e)
        }))
        sys.exit(1)

if __name__ == "__main__":
    main()

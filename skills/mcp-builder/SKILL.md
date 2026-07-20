---
name: mcp-builder
description: Build MCP (Model Context Protocol) servers that give Claude new capabilities. Use when user wants to create an MCP server, add tools to Claude, or integrate external services.
---

# MCP Server Building Skill

You now have expertise in building MCP (Model Context Protocol) servers. MCP enables Claude to interact with external services through a standardized protocol.

## What is MCP?

MCP servers expose:
- **Tools**: Functions Claude can call
- **Resources**: Data Claude can read
- **Prompts**: Pre-built prompt templates

## Quick Start: Python MCP Server

```python
from mcp.server import Server
from mcp.server.stdio import stdio_server

server = Server("my-server")

@server.tool()
async def hello(name: str) -> str:
    return f"Hello, {name}!"
```

## TypeScript MCP Server

```typescript
import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";

const server = new Server({ name: "my-server", version: "1.0.0" });
const transport = new StdioServerTransport();
server.connect(transport);
```

## Best Practices

1. **Clear tool descriptions**
2. **Input validation**
3. **Meaningful error handling**
4. **Async for I/O**
5. **Security first**
6. **Idempotent tools when possible**

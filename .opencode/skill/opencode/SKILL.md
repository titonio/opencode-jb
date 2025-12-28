---
name: opencode
description: Expertise for using OpenCode (an open-source AI coding agent) and building client applications that interact with the OpenCode HTTP server. Use this skill when the user asks about OpenCode features, how to use it, or how to build tools/clients that integrate with the OpenCode server API.
---

# OpenCode

## Overview

OpenCode is an open source AI coding agent that runs in your terminal, IDE, or desktop. It provides a server mode that exposes an OpenAPI specification, allowing developers to build custom clients and integrations.

## Using OpenCode

For general information about OpenCode features, privacy, and ecosystem, see [references/overview.md](references/overview.md).

## Building Client Applications

To build a client application that interacts with OpenCode, you should communicate with the OpenCode HTTP server.

### Quick Start
See `scripts/simple_client.py` for a complete Python example of connecting, creating a session, and sending a message.

### Client Development Guide
For detailed patterns on session management, messaging loops, and event streaming, see [references/client_guide.md](references/client_guide.md).

### Server Connection

The server typically runs on `http://127.0.0.1:4096`.
You can start it with `opencode serve`.

### API Reference

For a complete list of available endpoints, see [references/api.md](references/api.md).

The API covers:
- **Sessions**: Create, manage, and interact with coding sessions.
- **Messages**: Send prompts and receive responses.
- **Files**: Read and search files in the workspace.
- **Projects**: Manage projects.
- **TUI Control**: Control the Terminal UI programmatically.

### Common Workflows

#### 1. Connecting to a Session
To interact with an existing session or create a new one:
- List sessions: `GET /session`
- Create session: `POST /session`

#### 2. Sending a Message
To send a prompt to the agent:
- `POST /session/:id/message` with the prompt content.

#### 3. Reading Files
To read file content:
- `GET /file/content?path=<path>`

## Resources

### scripts/
- [simple_client.py](scripts/simple_client.py): A Python script demonstrating basic client interactions (connect, create session, send message).

### references/
- [overview.md](references/overview.md): General overview of OpenCode features and ecosystem.
- [api.md](references/api.md): Detailed API reference for the OpenCode HTTP server.
- [client_guide.md](references/client_guide.md): Best practices and patterns for building OpenCode clients.

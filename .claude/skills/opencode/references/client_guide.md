# Building OpenCode Clients

This guide provides best practices and patterns for building client applications that interact with the OpenCode server.

## Connection & Authentication

The OpenCode server runs locally by default on `http://127.0.0.1:4096`.
- **Local Development**: No authentication is typically required for the local server started by `opencode serve` or the TUI.
- **Check Connection**: A simple `GET /doc` or `GET /config` can verify if the server is reachable.

## Session Management

Interactions happen within **Sessions**.
1.  **List Sessions**: `GET /session` to see active sessions.
2.  **Create Session**: `POST /session` to start a new context. You can optionally provide a `title`.
3.  **Resume Session**: Store the `session_id` to continue a conversation later.

## Messaging Loop

The core interaction loop is:
1.  **Send Message**: `POST /session/:id/message`
    *   Body requires `parts` array.
    *   Example: `{"parts": [{"type": "text", "text": "Your prompt here"}]}`
2.  **Receive Response**: The API call typically blocks until the response is complete (unless using `prompt_async`).
    *   The response object contains `info` (Message metadata) and `parts` (the content).

## Streaming & Events

For real-time updates, use the Event Stream:
- **Endpoint**: `GET /event`
- **Format**: Server-Sent Events (SSE)
- **Usage**: Connect to this stream to receive updates about session changes, new messages, or status updates without polling.

## File Operations

Clients can read the workspace context:
- **Read File**: `GET /file/content?path=/absolute/path/to/file`
- **Search**: `GET /find?pattern=search_term`

## Error Handling

- **404 Not Found**: Session ID might be invalid or expired.
- **500 Internal Server Error**: The agent might have crashed or encountered an unrecoverable error.
- **Connection Refused**: Ensure `opencode serve` is running.

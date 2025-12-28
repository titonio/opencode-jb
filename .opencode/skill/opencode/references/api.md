# OpenCode Server API Reference

The OpenCode server exposes an OpenAPI 3.1 spec endpoint.
Base URL: `http://<hostname>:<port>` (Default: `http://127.0.0.1:4096`)
Spec URL: `http://<hostname>:<port>/doc`

## Global Events

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/global/event` | Get global events (SSE stream) | Event stream |

## Project

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/project` | List all projects | `Project[]` |
| `GET` | `/project/current` | Get the current project | `Project` |

## Path & VCS

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/path` | Get the current path | `Path` |
| `GET` | `/vcs` | Get VCS info for the current project | `VcsInfo` |

## Instance

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `POST` | `/instance/dispose` | Dispose the current instance | `boolean` |

## Config

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/config` | Get config info | `Config` |
| `PATCH` | `/config` | Update config | `Config` |
| `GET` | `/config/providers` | List providers and default models | `{ providers: Provider[], default: { [key: string]: string } }` |

## Provider

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/provider` | List all providers | `{ all: Provider[], default: {...}, connected: string[] }` |
| `GET` | `/provider/auth` | Get provider authentication methods | `{ [providerID: string]: ProviderAuthMethod[] }` |
| `POST` | `/provider/{id}/oauth/authorize` | Authorize a provider using OAuth | `ProviderAuthAuthorization` |
| `POST` | `/provider/{id}/oauth/callback` | Handle OAuth callback for a provider | `boolean` |

## Sessions

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/session` | List all sessions | `Session[]` |
| `POST` | `/session` | Create a new session | body: `{ parentID?, title? }`, returns `Session` |
| `GET` | `/session/status` | Get session status for all sessions | `{ [sessionID: string]: SessionStatus }` |
| `GET` | `/session/:id` | Get session details | `Session` |
| `DELETE` | `/session/:id` | Delete a session and all its data | `boolean` |
| `PATCH` | `/session/:id` | Update session properties | body: `{ title? }`, returns `Session` |
| `GET` | `/session/:id/children` | Get a session’s child sessions | `Session[]` |
| `GET` | `/session/:id/todo` | Get the todo list for a session | `Todo[]` |
| `POST` | `/session/:id/init` | Analyze app and create `AGENTS.md` | body: `{ messageID, providerID, modelID }`, returns `boolean` |
| `POST` | `/session/:id/fork` | Fork an existing session at a message | body: `{ messageID? }`, returns `Session` |
| `POST` | `/session/:id/abort` | Abort a running session | `boolean` |
| `POST` | `/session/:id/share` | Share a session | `Session` |
| `DELETE` | `/session/:id/share` | Unshare a session | `Session` |
| `GET` | `/session/:id/diff` | Get the diff for this session | query: `messageID?`, returns `FileDiff[]` |
| `POST` | `/session/:id/summarize` | Summarize the session | body: `{ providerID, modelID }`, returns `boolean` |
| `POST` | `/session/:id/revert` | Revert a message | body: `{ messageID, partID? }`, returns `boolean` |
| `POST` | `/session/:id/unrevert` | Restore all reverted messages | `boolean` |
| `POST` | `/session/:id/permissions/:permissionID` | Respond to a permission request | body: `{ response, remember? }`, returns `boolean` |

## Messages

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/session/:id/message` | List messages in a session | query: `limit?`, returns `{ info: Message, parts: Part[] }[]` |
| `POST` | `/session/:id/message` | Send a message and wait for response | body: `{ messageID?, model?, agent?, noReply?, system?, tools?, parts }`, returns `{ info: Message, parts: Part[] }` |
| `GET` | `/session/:id/message/:messageID` | Get message details | `{ info: Message, parts: Part[] }` |
| `POST` | `/session/:id/prompt_async` | Send a message asynchronously (no wait) | body: same as `/session/:id/message`, returns `204 No Content` |
| `POST` | `/session/:id/command` | Execute a slash command | body: `{ messageID?, agent?, model?, command, arguments }`, returns `{ info: Message, parts: Part[] }` |
| `POST` | `/session/:id/shell` | Run a shell command | body: `{ agent, model?, command }`, returns `{ info: Message, parts: Part[] }` |

## Commands

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/command` | List all commands | `Command[]` |

## Files

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/find?pattern=<pat>` | Search for text in files | Array of match objects |
| `GET` | `/find/file?query=<q>` | Find files by name | `string[]` (file paths) |
| `GET` | `/find/symbol?query=<q>` | Find workspace symbols | `Symbol[]` |
| `GET` | `/file?path=<path>` | List files and directories | `FileNode[]` |
| `GET` | `/file/content?path=<p>` | Read a file | `FileContent` |
| `GET` | `/file/status` | Get status for tracked files | `File[]` |

## Tools (Experimental)

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/experimental/tool/ids` | List all tool IDs | `ToolIDs` |
| `GET` | `/experimental/tool?provider=<p>&model=<m>` | List tools with JSON schemas for a model | `ToolList` |

## LSP, Formatters & MCP

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/lsp` | Get LSP server status | `LSPStatus[]` |
| `GET` | `/formatter` | Get formatter status | `FormatterStatus[]` |
| `GET` | `/mcp` | Get MCP server status | `{ [name: string]: MCPStatus }` |
| `POST` | `/mcp` | Add MCP server dynamically | body: `{ name, config }`, returns MCP status object |

## Agents

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/agent` | List all available agents | `Agent[]` |

## Logging

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `POST` | `/log` | Write log entry | Body: `{ service, level, message, extra? }`, returns `boolean` |

## TUI

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `POST` | `/tui/append-prompt` | Append text to the prompt | `boolean` |
| `POST` | `/tui/open-help` | Open the help dialog | `boolean` |
| `POST` | `/tui/open-sessions` | Open the session selector | `boolean` |
| `POST` | `/tui/open-themes` | Open the theme selector | `boolean` |
| `POST` | `/tui/open-models` | Open the model selector | `boolean` |
| `POST` | `/tui/submit-prompt` | Submit the current prompt | `boolean` |
| `POST` | `/tui/clear-prompt` | Clear the prompt | `boolean` |
| `POST` | `/tui/execute-command` | Execute a command | Body: `{ command }`, returns `boolean` |
| `POST` | `/tui/show-toast` | Show toast | Body: `{ title?, message, variant }`, returns `boolean` |
| `GET` | `/tui/control/next` | Wait for the next control request | Control request object |
| `POST` | `/tui/control/response` | Respond to a control request | Body: `{ body }`, returns `boolean` |

## Auth

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `PUT` | `/auth/:id` | Set authentication credentials | Body must match provider schema, returns `boolean` |

## Events

| Method | Endpoint | Description | Return Type |
| :--- | :--- | :--- | :--- |
| `GET` | `/event` | Server-sent events stream | Server-sent events stream |

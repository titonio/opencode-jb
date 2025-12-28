# OpenCode Overview

OpenCode is an open source AI coding agent that helps you write code in your terminal, IDE, or desktop.

## Key Features

- **LSP enabled**: Automatically loads the right LSPs for the LLM.
- **Multi-session**: Start multiple agents in parallel on the same project.
- **Share links**: Share a link to any session for reference or to debug.
- **Any model**: Supports 75+ LLM providers through Models.dev, including local models.
- **Any editor**: Available as a terminal interface, desktop app, and IDE extension.
- **Privacy first**: Does not store any of your code or context data.

## Server Mode

OpenCode has a server mode (`opencode serve`) that exposes an OpenAPI 3.1 spec. This allows you to interact with OpenCode programmatically and build custom clients.

The server starts a headless HTTP server that exposes an OpenAPI endpoint.
Default URL: `http://127.0.0.1:4096`

## Ecosystem

- **Plugins**: Extend functionality (e.g., auth, tools, notifications).
- **Themes**: Customize the look and feel.
- **Agents**: Specialized agents for different tasks.
- **Projects**: Community projects built on top of OpenCode.

See [Awesome OpenCode](https://github.com/awesome-opencode/awesome-opencode) and [OpenCode Cafe](https://www.opencode.cafe/) for more resources.

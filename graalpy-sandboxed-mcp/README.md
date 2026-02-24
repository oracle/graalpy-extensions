# GraalPy Sandboxed MCP Server

This MCP server provides a tool `eval_python` that can evaluate arbitrary Python code in a configurable sandboxed
environment. It supports the full Python language including the standard library and third-party packages without
native (C/rust) extensions.

# Sandboxing

- Each tool invocation is ran as a separate GraalVM isolate, with an independent heap. The maxiumum heap size is limited
  by default to 512MB and can be changed using `--max-memory` option.
- The CPU time is limited to 20s by default. This can be changed using `--max-cpu-time` option.
- Filesystem access is denied by default. The option `--allow-read-fs` enables read-only access to the filesystem. More
  fine-grained controls like allowing access to specific directories are currently not implemented.
- Subprocess creation is not allowed. Sockets are not allowed.
- Environment variables are not propagated unless `--allow-read-env` is specified.
- Native extensions are not allowed. That includes a few modules in the standard library, notably `ctypes`, `pyexpat`
  (used by the `xml.*` modules) and `sqlite3`.

# Virtual environments and third-party package support

It is possible to make the MCP server use a virtual environment with third-party packages that don't contain native
extensions. The virtual environment must be created using a GraalPy standalone distribution of the same version as this
MCP server. The path to the virtual environment can then be passed to the `--virtualenv` option.

# Example MCP configuration for Cline

```json
{
  "mcpServers": {
    "graalpy-sandboxed-mcp": {
      "transport": "stdio",
      "command": "/path/to/graalpy-sandboxed-mcp",
      "args": [
        "--allow-read-fs",
        "--cwd",
        "/path/to/my-project"
      ],
      "autoApprove": [
        "eval_python"
      ]
    }
  }
}
```

# Building from source

Use `mvn -Dpackaging=native-image clean verify`, the resulting binary will be in `target/target/graalpy-sandboxed-mcp`.

# GraalPy Sandboxed CLI

`graalpy-sandboxed` is a sandboxed Python interpreter suitable for executing by AI agents if they are forbidden to
execute other commands. The sandbox can be configured using a properties file.

The CLI supports the full Python language including the standard library and third-party packages
without native (C/Rust) extensions.

## Sandbox Characteristics

- Each invocation runs in a separate GraalVM isolate with an independent heap.
- The maximum heap size is limited to `512MB` by default and can be changed with `max-memory`.
- CPU time is limited to `20s` by default and can be changed with `max-cpu-time`.
- Filesystem access is denied by default. Set `allow-read-fs=true` to enable read-only filesystem access.
- Environment variables are not propagated unless `allow-read-env=true` is specified.
- Subprocess creation is not allowed. Sockets are not allowed.
- Native extensions are not allowed. This includes some standard library modules such as `ctypes`,
  `pyexpat` (used by the `xml.*` modules), and `sqlite3`.

## Supported invocation modes

- `graalpy-sandboxed -c "<code>" [args...]`
- `graalpy-sandboxed -m <module> [args...]`
- `graalpy-sandboxed <script.py> [args...]` (only when read filesystem access is enabled)
- `echo "print(1+1)" | graalpy-sandboxed`

Interactive REPL mode is intentionally not supported. If the command is run without arguments while stdin is a terminal,
it exits with an error.

## Virtual Environments and Third-Party Packages

It is possible to use a virtual environment with third-party packages that do not contain native extensions.
The virtual environment must be created using a GraalPy standalone distribution that matches the version of
the CLI. Configure the virtual environment path with the `virtualenv` or `venv` property.

## Configuration via properties file

At startup, the CLI looks for `graalpy-sandboxed.properties` in the current working directory. If present, it loads
sandbox options from there.

Supported properties:

- `allow-read-fs` (`true`/`false`, default `false`)
- `allow-read-env` (`true`/`false`, default `false`)
- `virtualenv` or `venv` (path, optional; requires `allow-read-fs=true`)
- `max-cpu-time` (default `20s`)
- `max-memory` (default `512MB`)

Example `graalpy-sandboxed.properties`:

```properties
allow-read-fs=true
allow-read-env=false
max-cpu-time=20s
max-memory=512MB
```

## Building from Source

Download [GraalVM 25](https://www.graalvm.org/downloads/) and set it as your JDK.
Build the project with `mvn -Dpackaging=native-image clean verify`.
The resulting binary will be in `target/target/graalpy-sandboxed`.

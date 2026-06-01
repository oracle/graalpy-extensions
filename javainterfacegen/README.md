# Java Interface Generator

A tool that generates Java interfaces from Python source code, enabling seamless interoperability between Java and Python through [GraalPy](https://www.graalvm.org/python/).

## Overview

The Java Interface Generator analyzes Python code using [MyPy](http://mypy-lang.org/) and produces idiomatic Java interfaces that wrap Python classes, functions, and modules. This allows Java developers to interact with Python libraries using familiar Java patterns while maintaining type safety.

## Documentation

- [Configuration Quick Start](doc/configuration-quickstart.md) - Get started with YAML configuration
- [Configuration Reference](doc/configuration-reference.md) - Complete configuration options
- [Usage Examples](doc/usage-examples.md) - Detailed examples with Python and Java code
- [Architecture Overview](doc/architecture.md) - System design and component descriptions
- [MyPy Integration](doc/mypy-integration.md) - How MyPy is used for type extraction
- [Python to Java Type Translation](doc/python-to-java-type-translation.md) - Type mapping rules

## Quick Start

### 1. Build the Tool

```bash
mvn package -DskipTests
```

If Maven fails with `Could not find artifact org.graalvm.python:graalpy-maven-plugin:...-SNAPSHOT`,
build/install the plugin first from the parent `graalpy-extensions` project root:

```bash
mvn -pl graalpy-maven-plugin -am install -DskipTests
mvn -pl javainterfacegen -am package -DskipTests
```

### 2. Create a Configuration File

Create a YAML configuration file (e.g., `myproject.yml`):

```yaml
# Input Python files or directories
files:
  - path: ./mypackage

# Output configuration
target_folder: ./generated-sources
interface_package: com.example.mypackage

# Cache directories (speeds up subsequent runs)
generator_cache_folder: ./cache/generator
```

### 3. Run the Generator

```bash
# Standard invocation (available in all builds)
mvn exec:java \
  -Dexec.mainClass="org.graalvm.python.javainterfacegen.Main" \
  -Dexec.args="./myproject.yml"

# Optional executable (only if you have built/installed a launcher for your environment)
# javainterfacegen ./myproject.yml
```

Use the Maven command as the default documented path. The `javainterfacegen` executable is environment/build dependent and may not be produced in a standard local Maven flow.

## Example

**Python source (`calculator.py`):**
```python
def add(a: int, b: int) -> int:
    """Add two numbers."""
    return a + b
```

**Configuration (`calculator.yml`):**
```yaml
files:
  - path: ./calculator.py
target_folder: ./generated
interface_package: com.example.calculator
generator_cache_folder: ./cache/generator
```

**Generated Java interface:**
```java
package com.example.calculator;

public interface Calculator {
    long add(long a, long b);

    static Calculator fromContext(Context context) { ... }
}
```

**Using the generated interface:**
```java
try (Context context = Context.newBuilder("python").build()) {
    Calculator calc = Calculator.fromContext(context);
    long sum = calc.add(5, 3);  // Returns 8
}
```

For more examples including class generation, type factories, and advanced configurations, see [Usage Examples](doc/usage-examples.md).

## Configuration Reference

For complete configuration documentation, see:
- [Configuration Quick Start](doc/configuration-quickstart.md) - Common configuration patterns
- [Configuration Reference](doc/configuration-reference.md) - All options with defaults

### Key Settings

| Property | Default | Description |
|----------|---------|-------------|
| `files` | *(required)* | Python files or directories to process |
| `target_folder` | *(required)* | Output directory for generated Java code |
| `interface_package` | `org.mycompany.api` | Java package for interfaces |
| `generator_cache_folder` | *(must be set explicitly)* | Cache folder currently used for MyPy cache path and serialized AST cache |
| `generate_type_interface` | `false` | Generate `*Type` factory interfaces |
| `whitelist` | *(none)* | Only generate listed classes/functions |
| `ignore` | *(none)* | Exclude listed names |
| `type_mappings` | *(none)* | Custom Python-to-Java type mappings |

## Debugging

### Increase Stack Size

For large Python codebases with deep class hierarchies:

```bash
MAVEN_OPTS="-Xss64M" mvn exec:java \
  -Dexec.mainClass="org.graalvm.python.javainterfacegen.Main" \
  -Dexec.args="./myproject.yml"
```

### Enable Debug Comments

```yaml
generate_log_comments: true
```

This adds comments showing:
- Python type information
- Why certain code paths were chosen
- Unresolved type placeholders

### Debug with Maven

```bash
MAVEN_OPTS="-Xss64M" mvnDebug exec:java \
  -Dexec.mainClass="org.graalvm.python.javainterfacegen.Main" \
  -Dexec.args="./myproject.yml"
```

Then attach a debugger to port 8000.

## Building from Source

### Prerequisites

- JDK 21 or later
- Maven 3.6+
- GraalPy (or run within GraalVM)

### Build Commands

```bash
# Compile
mvn compile

# Run tests
mvn test

# Package (skip tests for faster build)
mvn package -DskipTests

# Run directly
mvn exec:java \
  -Dexec.mainClass="org.graalvm.python.javainterfacegen.Main" \
  -Dexec.args="./path/to/config.yml"
```

## License

Universal Permissive License (UPL), Version 1.0

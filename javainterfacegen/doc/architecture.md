# Java Interface Generator Architecture

This document describes the architecture and design of the Java Interface Generator tool.

## Overview

The Java Interface Generator automatically creates Java interfaces from Python source code, enabling Java applications to interoperate with Python libraries through GraalPy. The tool leverages MyPy's type system to extract accurate type information and generates idiomatic Java interfaces.

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Python Source  │────▶│  MyPy Analysis  │────▶│  Java Interface │
│   (.py/.pyi)    │     │   (Type AST)    │     │   Generation    │
└─────────────────┘     └─────────────────┘     └─────────────────┘
        │                       │                       │
        ▼                       ▼                       ▼
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  YAML Config    │     │  Cached JSON    │     │  .java Files    │
│  (input spec)   │     │  (parsed AST)   │     │  (output)       │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

## Core Components

### 1. Entry Point

[`Main`](../src/main/java/org/graalvm/python/javainterfacegen/Main.java) orchestrates the generation pipeline:

1. Loads YAML configuration
2. Resolves input Python file paths
3. Invokes MyPy parsing (or loads from cache)
4. Generates Javadoc from docstrings
5. Generates Java interfaces via [`TransformerVisitor`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TransformerVisitor.java)
6. Handles unresolved types
7. Exports type mappings and native-image proxy configuration

### 2. Configuration System

The configuration system ([`Configuration`](../src/main/java/org/graalvm/python/javainterfacegen/configuration/Configuration.java)) provides:

- **YAML-based configuration** via [`YamlConfigurationLoader`](../src/main/java/org/graalvm/python/javainterfacegen/configuration/YamlConfigurationLoader.java)
- **Hierarchical property resolution** (global → file → class → function)
- **Customizable generators** for functions, types, Javadoc, and naming

Key configuration properties:

| Property | Description |
|----------|-------------|
| `files` | Python files/directories to process |
| `target_folder` | Output directory for generated Java code |
| `interface_package` | Java package for generated interfaces |
| `type_mappings` | Custom Python→Java type mappings |
| `whitelist` | Explicit list of classes/functions to include |
| `ignore` | Classes/functions to exclude |

See [`DefaultConfigurationLoader`](../src/main/java/org/graalvm/python/javainterfacegen/configuration/DefaultConfigurationLoader.java) for all defaults.

### 3. MyPy Integration

[`MypyHook`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/MypyHook.java) bridges Java and Python MyPy:

- Invokes MyPy through GraalPy's polyglot interface
- Serializes/deserializes parsed AST to JSON cache
- Extracts docstrings from Python source

The Python-side implementation lives in [`analyzePath.py`](../src/main/resources/GRAALPY-VFS/org.graalvm.python/javainterfacegen/src/analyzePath.py).

See [MyPy Integration](./mypy-integration.md) for detailed documentation.

### 4. AST Node Wrappers

The `mypy.nodes` package provides Java wrappers for MyPy's AST nodes:

| Node Type | Java Interface | Description |
|-----------|----------------|-------------|
| `MypyFile` | [`MypyFile`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/nodes/MypyFile.java) | Python module |
| `ClassDef` | [`ClassDef`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/nodes/ClassDef.java) | Class definition |
| `FuncDef` | [`FuncDef`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/nodes/FuncDef.java) | Function/method definition |
| `Var` | [`Var`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/nodes/Var.java) | Variable/attribute |
| `TypeInfo` | [`TypeInfo`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/nodes/TypeInfo.java) | Class metadata (bases, MRO, etc.) |

### 5. Type System Wrappers

The `mypy.types` package wraps MyPy's type representations:

| Type | Java Interface | Description |
|------|----------------|-------------|
| `Instance` | [`Instance`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/Instance.java) | Class instance type |
| `UnionType` | [`UnionType`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/UnionType.java) | Union of types |
| `CallableType` | [`CallableType`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/CallableType.java) | Function signature |
| `TupleType` | [`TupleType`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/TupleType.java) | Tuple type |
| `TypeVarType` | [`TypeVarType`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/TypeVarType.java) | Type variable |

### 6. Code Generation

#### TransformerVisitor

[`TransformerVisitor`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TransformerVisitor.java) implements the visitor pattern to traverse MyPy's AST and generate Java code:

```
MypyFile.visit() ──▶ Generate module interface
    │
    ├── ClassDef.visit() ──▶ Generate class interface
    │       │
    │       ├── FuncDef.visit() ──▶ Generate method signature
    │       └── Var.visit() ──▶ Generate getter/setter
    │
    └── FuncDef.visit() ──▶ Generate module-level function
```

#### GeneratorContext

[`GeneratorContext`](../src/main/java/org/graalvm/python/javainterfacegen/generator/GeneratorContext.java) maintains generation state:

- Current AST node being processed
- Import tracking
- Indentation level
- Java FQN of generated class
- Configuration property resolution

#### TypeManager

[`TypeManager`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java) handles Python→Java type translation:

- Maintains registered type mappings
- Resolves MyPy types to Java types
- Tracks unresolved types for later patching
- Supports primitive boxing for generics

See [Python to Java Type Translation](./python-to-java-type-translation.md) for detailed rules.

### 7. Generator Plugins

The tool uses a plugin architecture for customizable generation:

| Interface | Purpose | Default Implementation |
|-----------|---------|------------------------|
| [`FunctionGenerator`](../src/main/java/org/graalvm/python/javainterfacegen/generator/FunctionGenerator.java) | Method signature generation | [`JustInterfacesGeneratorImpl`](../src/main/java/org/graalvm/python/javainterfacegen/generator/impl/JustInterfacesGeneratorImpl.java) |
| [`TypeGenerator`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeGenerator.java) | Complex type generation | [`TypeInterfaceGeneratorImpl`](../src/main/java/org/graalvm/python/javainterfacegen/generator/impl/TypeInterfaceGeneratorImpl.java) |
| [`NameGenerator`](../src/main/java/org/graalvm/python/javainterfacegen/generator/NameGenerator.java) | Java naming conventions | [`NameGeneratorImpl`](../src/main/java/org/graalvm/python/javainterfacegen/generator/impl/NameGeneratorImpl.java) |
| [`JavadocGenerator`](../src/main/java/org/graalvm/python/javainterfacegen/generator/JavadocGenerator.java) | Javadoc from docstrings | [`JavadocGeneratorImpl`](../src/main/java/org/graalvm/python/javainterfacegen/generator/impl/JavadocGeneratorImpl.java) |

Plugins are discovered via Java ServiceLoader from `META-INF/services/`.

## Data Flow

### 1. Parsing Phase

```
Python files ──▶ MyPy ──▶ Typed AST ──▶ JSON cache
                  │
                  └── Type checking and inference
```

### 2. Generation Phase

```
JSON cache ──▶ MypyFile objects ──▶ TransformerVisitor
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    ▼                     ▼                     ▼
              Interface.java      InterfaceType.java    type-classes/
              (instance API)      (static API/factory)  (complex types)
```

### 3. Post-Processing Phase

```
Generated .java files ──▶ TypeManager.handleUnresolved()
                                │
                                ├── Replace type placeholders
                                ├── Add TODO comments for unknown types
                                └── Export type mappings
```

## Generated Code Structure

For a Python class `mymodule.MyClass`:

```java
// MyClass.java - Instance interface
public interface MyClass extends GuestValue {
    // Instance methods
    String getName();
    void setName(String value);

    // Type checking
    static boolean isInstance(Object object) { ... }
    static MyClass cast(Object o) { ... }
}

// MyClassType.java - Static/factory interface (optional)
public interface MyClassType {
    // Factory method
    static MyClassType getPythonType(Context context) { ... }

    // Constructor wrapper
    default MyClass newInstance(String name) { ... }

    // Static methods and class attributes
    static Value CLASS_CONSTANT = ...;
}
```

## Caching and Performance

### Why Caching Matters

Running MyPy on GraalVM is slower than on standard CPython. The tool caches parsed results to speed up subsequent runs.

Current runtime behavior uses one configured cache folder:

1. **Generator cache** (`generator_cache_folder`): Serialized AST as JSON, and also the folder passed to MyPy as its cache directory

Cache behavior:
- **First run**: Full MyPy analysis (may take minutes for large codebases)
- **Subsequent runs**: Load from cache / deserialize JSON (seconds)

### Cache Management

Caches contain absolute file paths and should **not** be committed to version control.

Example `.gitignore` entries:

```gitignore
# .gitignore
generator_cache/
*.json  # If storing cache as JSON
```

Regenerate caches when:
- Python source files change
- Files are moved or renamed
- Working directory changes
## Extension Points

### Custom Type Mappings

```yaml
type_mappings:
  mymodule.CustomType: com.example.JavaCustomType
  numpy.ndarray: org.graalvm.polyglot.Value
```

### Custom Generators

Implement the generator interface and register in `META-INF/services/`:

```java
public class CustomFunctionGenerator implements FunctionGenerator {
    @Override
    public String createSignature(FuncDef funcDef, GeneratorContext context) {
        // Custom signature generation
    }
}
```

### Whitelist/Blacklist

```yaml
whitelist:
  - class: mymodule.ImportantClass
  - function: mymodule.important_function

ignore:
  - internal_helper
  - _private_class
```

## Error Handling

- **Unresolved types**: Placeholder markers in generated code, patched post-generation
- **Missing docstrings**: TODO comments or empty Javadoc
- **Invalid Python**: Propagated MyPy errors

## Performance Considerations

1. **Initial parse**: MyPy on GraalVM is slow; use caching
2. **Large codebases**: Process incrementally by module
3. **Memory**: Increase stack size for deep AST traversal (`-Xss64M`)

## Related Documentation

- [Python to Java Type Translation](./python-to-java-type-translation.md)
- [MyPy Integration](./mypy-integration.md)

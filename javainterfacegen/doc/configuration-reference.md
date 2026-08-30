# Configuration Reference

Complete reference for all YAML configuration options in the Java Interface Generator.

## Configuration File Structure

Configuration files use YAML format. The generator merges user-provided values with defaults, but current runtime behavior requires a few key properties to be set explicitly.

```yaml
# Required for current runtime
files:
  - path: ./module.py
generator_cache_folder: ./cache/generator

# Commonly set
target_folder: ./output
interface_package: com.example.api
```

Unless stated otherwise, property snippets below are partial examples and may omit other required settings.

---

## Required Properties

### `files`

**Type:** String, or list of file configurations
**Required:** Yes

Python files or directories to process.

**List of paths (current runtime-compatible list form):**
```yaml
files:
  - path: ./module.py
  - path: ./package/ # includes all python files from the dir
```

**Single file:**
```yaml
files: ./single_module.py
```

**File-specific configuration:**
```yaml
files:
  - path: ./core.py
    interface_package: com.example.core

  - path: ./utils.py
    interface_package: com.example.utils
    ignore:
      - debug_function
```

### `target_folder`

**Type:** String (path)
**Required:** Yes
**Default:** `./target/generated-sources/src`

Output directory for generated Java source files. Relative paths are resolved from the configuration file's location.

```yaml
target_folder: ./generated-sources
```

### `generator_cache_folder`

**Type:** String (path)
**Required for current runtime:** Yes

Cache directory currently used for both serialized AST cache and the MyPy cache path passed to the runtime hook.

```yaml
generator_cache_folder: ./cache/generator
```

---

## Package Configuration

### `interface_package`

**Type:** String
**Default:** `org.mycompany.api`

Java package for generated interfaces.

```yaml
interface_package: com.example.myproject.api
```

### `implementation_package`

**Type:** String
**Default:** `org.mycompany.implementation`

Java package for generated implementation classes.

```yaml
implementation_package: com.example.myproject.impl
```

### `type_package`

**Type:** String
**Default:** `org.mycompany.api.types`

Java package for generated `*Type` factory interfaces.

```yaml
type_package: com.example.myproject.types
```

### `base_interface_package`

**Type:** String
**Default:** Same as `interface_package`

Package for base interfaces (`GuestValue`, etc.).

```yaml
base_interface_package: com.example.common
```

### `base_interface_name`

**Type:** String
**Default:** `GraalValueBase`

Name of the base interface that all generated interfaces extend.

```yaml
base_interface_name: PythonValue
```

### `strip_python_package_prefix`

**Type:** String
**Default:** (none)

Python package prefix to strip when generating Java package names.

```yaml
strip_python_package_prefix: myproject.internal
```

---

## Caching

Caching significantly speeds up subsequent runs by avoiding repeated MyPy analysis.

### `generator_cache_folder`

**Type:** String (path)
**Default:** (none - set explicitly in config)

Directory for serialized AST cache. Current runtime also passes this folder to `set_mypy_cache_folder(...)`.

```yaml
generator_cache_folder: ./cache/generator
```

**Important:** Cache directories can contain absolute paths. Do not commit them to version control:

```gitignore
generator_cache/
cache/
```

---

## Generation Options

### `generate_type_interface`

**Type:** Boolean
**Default:** `false`

Generate `*Type` factory interfaces for creating Python objects from Java.

```yaml
generate_type_interface: true
```

When enabled, for a Python class `Rectangle`, generates:
- `Rectangle` - Instance interface with methods
- `RectangleType` - Factory interface with `newInstance()` method

### `generate_base_classes`

**Type:** Boolean
**Default:** `false`

Generate base implementation classes.

```yaml
generate_base_classes: true
```

### `generate_log_comments`

**Type:** Boolean
**Default:** `false`

Add debug comments showing type resolution decisions.

```yaml
generate_log_comments: true
```

Useful for debugging type mapping issues.

### `generate_timestamps`

**Type:** Boolean
**Default:** `true`

Add generation timestamps to output files.

```yaml
generate_timestamps: false
```

### `generate_location`

**Type:** Boolean
**Default:** `true`

Add source file location comments to generated code.

```yaml
generate_location: true
```

### `indentation`

**Type:** Integer
**Default:** `4`

Number of spaces for indentation in generated code.

```yaml
indentation: 2
```

### `implementation_name_suffix`

**Type:** String
**Default:** `Impl`

Suffix for implementation class names.

```yaml
implementation_name_suffix: Implementation
```

---

## Filtering

### `whitelist`

**Type:** List of class/function specifications
**Default:** (none - include everything)

Only generate interfaces for specified items.

```yaml
whitelist:
  # By class name
  - class: MyClass

  # By fully qualified name
  - class: mymodule.MyClass

  # By function name
  - function: process_data

  # Simple string (matches any type)
  - PublicAPI
```

### `ignore`

**Type:** List of strings
**Default:** (none)

Exclude items by name. Matched against simple names.

```yaml
ignore:
  - _private_helper
  - DebugClass
  - test_function
```

---

## Type Mappings

### `type_mappings`

**Type:** Map of Python type to Java type
**Default:** (none)

Override default Python-to-Java type translations.

```yaml
type_mappings:
  numpy.ndarray: org.graalvm.polyglot.Value
  pandas.DataFrame: com.example.DataFrameWrapper
  mymodule.CustomType: com.example.JavaCustomType
```

### `any_java_type`

**Type:** String
**Default:** `org.graalvm.polyglot.Value`

Java type used for Python's `Any` type and unresolved types.

```yaml
any_java_type: java.lang.Object
```

### `excluded_imports`

**Type:** List of strings
**Default:** `["java.lang.*"]`

Import patterns to exclude from generated imports.

```yaml
excluded_imports:
  - java.lang.*
  - java.util.List
```

---

## Native Image Support

### `generate_proxy_config`

**Type:** Boolean
**Default:** `true`

Generate GraalVM Native Image `proxy-config.json` for interface proxies.

```yaml
generate_proxy_config: true
```

### `path_proxy_config`

**Type:** String (path)
**Default:** `proxy-config.json`

Output path for the proxy configuration file.

```yaml
path_proxy_config: ./META-INF/native-image/proxy-config.json
```

---

## Type Export

### `export_types`

**Type:** Boolean
**Default:** `true`

Export type mappings to a file for documentation or tooling.

```yaml
export_types: true
```

### `export_files`

**Type:** String (path)
**Default:** `exportedTypes.txt`

Output path for exported type mappings.

```yaml
export_files: ./docs/type-mappings.txt
```

### `export_include`

**Type:** String
**Default:** Same as `interface_package`

Package prefix to include in export.

```yaml
export_include: com.example.api
```

### `export_exclude`

**Type:** List of strings
**Default:** `[]`

Packages to exclude from type export.

```yaml
export_exclude:
  - com.example.internal
```

---

## Javadoc

### `javadoc_folder`

**Type:** String (path)
**Default:** `./javadoc-cache`

Cache directory for extracted Python docstrings.

```yaml
javadoc_folder: ./cache/javadoc
```

### `javadoc_generators`

**Type:** List of class names
**Default:** `["org.graalvm.python.javainterfacegen.generator.impl.JavadocGeneratorImpl"]`

Javadoc generator implementations to use.

```yaml
javadoc_generators:
  - org.graalvm.python.javainterfacegen.generator.impl.JavadocGeneratorImpl
```

### `javadoc_storage_manager`

**Type:** String (class name)
**Default:** `org.graalvm.python.javainterfacegen.generator.impl.JavadocStorageManagerYaml`

Storage manager for javadoc cache.

```yaml
javadoc_storage_manager: org.graalvm.python.javainterfacegen.generator.impl.JavadocStorageManagerYaml
```

---

## Licensing

### `license_file`

**Type:** String (path)
**Default:** (none)

License header file to include in generated sources.

```yaml
license_file: ./LICENSE_HEADER.txt
```

---

## Advanced: Per-Item Configuration

### File-Specific Settings

Override settings for specific files:

```yaml
files:
  - path: ./core.py
    interface_package: com.example.core
    generate_type_interface: true

  - path: ./utils.py
    interface_package: com.example.utils
    ignore:
      - debug_function

target_folder: ./generated
interface_package: com.example  # Default for unspecified files
generator_cache_folder: ./cache/generator
```

### Class-Specific Settings

Override settings for specific classes:

```yaml
files:
  - path: ./module.py

target_folder: ./generated
interface_package: com.example
generator_cache_folder: ./cache/generator

classes:
  MyClass:
    overrides:
      get_data:
        return_type: com.example.CustomData
      process:
        return_type: java.util.List
```

### Function-Specific Settings

Override settings for specific functions:

```yaml
files:
  - path: ./module.py

target_folder: ./generated
interface_package: com.example
generator_cache_folder: ./cache/generator

functions:
  calculate:
    overrides:
      return_type: java.math.BigDecimal
```

---

## Generator Plugins

### `function_generators`

**Type:** List of class names
**Default:** `["org.graalvm.python.javainterfacegen.generator.impl.JustInterfacesGeneratorImpl"]`

Function generator implementations.

```yaml
function_generators:
  - org.graalvm.python.javainterfacegen.generator.impl.JustInterfacesGeneratorImpl
```

### `name_generator`

**Type:** String (class name)
**Default:** `org.graalvm.python.javainterfacegen.generator.impl.NameGeneratorImpl`

Name generator for Java identifiers.

```yaml
name_generator: org.graalvm.python.javainterfacegen.generator.impl.NameGeneratorImpl
```

### `type_generator`

**Type:** String (class name)
**Default:** `org.graalvm.python.javainterfacegen.generator.impl.TypeInterfaceGeneratorImpl`

Type interface generator implementation.

```yaml
type_generator: org.graalvm.python.javainterfacegen.generator.impl.TypeInterfaceGeneratorImpl
```

---

## Complete Example

```yaml
# Input
files:
  - path: ./src/core
    interface_package: com.example.core
  - path: ./src/utils
    interface_package: com.example.utils
    ignore:
      - _debug

# Output
target_folder: ./generated-sources/java
interface_package: com.example.api
implementation_package: com.example.impl
type_package: com.example.types

# Caching
generator_cache_folder: ./build/cache/generator
javadoc_folder: ./build/cache/javadoc

# Generation
generate_type_interface: true
generate_base_classes: false
generate_log_comments: false
generate_timestamps: true
generate_location: true

# Type mappings
type_mappings:
  numpy.ndarray: org.graalvm.polyglot.Value
  pandas.DataFrame: com.example.DataFrameWrapper

# Filtering
whitelist:
  - class: PublicAPI
  - class: DataModel
  - function: process

ignore:
  - _internal
  - Test

# Native Image
generate_proxy_config: true
path_proxy_config: ./META-INF/native-image/proxy-config.json

# Export
export_types: true
export_files: ./docs/types.txt

# License
license_file: ./LICENSE_HEADER.txt
```

---

## See Also

- [Configuration Quick Start](configuration-quickstart.md) - Get started quickly
- [Python to Java Type Translation](python-to-java-type-translation.md) - Type mapping details
- [Architecture Overview](architecture.md) - System design

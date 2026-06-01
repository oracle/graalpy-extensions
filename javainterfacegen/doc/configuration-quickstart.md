# Configuration Quick Start

This guide gets you started with YAML configuration files for the Java Interface Generator.

## Minimal Configuration

Create a file named `myproject.yml`:

```yaml
files:
  - path: ./src/python

target_folder: ./generated-sources
interface_package: com.example.myapi
generator_cache_folder: ./cache/generator
```

Run the generator (default path via Maven):

```bash
mvn exec:java \
  -Dexec.mainClass="org.graalvm.python.javainterfacegen.Main" \
  -Dexec.args="./myproject.yml"
```

Optional launcher (environment/build dependent, not always produced in a standard Maven flow):

```bash
javainterfacegen ./myproject.yml
```

That's it! The generator will analyze all Python files in `./src/python` and output Java interfaces to `./generated-sources`.

## Common Configurations

### Single Python File

```yaml
files:
  - path: ./calculator.py

target_folder: ./generated
interface_package: com.example.calculator
generator_cache_folder: ./cache/generator
```

### Multiple Files and Directories

```yaml
files:
  - path: ./core.py
  - path: ./utils/
  - path: ./models/

target_folder: ./generated
interface_package: com.example
generator_cache_folder: ./cache/generator
```

### With Caching (Recommended for Large Projects)

Caching dramatically speeds up subsequent runs:

```yaml
files:
  - path: ./mypackage

target_folder: ./generated
interface_package: com.example.mypackage

# Cache directories
generator_cache_folder: ./cache/generator
```

### Generate Type Factory Interfaces

Enable `*Type` interfaces for creating Python objects from Java:

```yaml
files:
  - path: ./shapes.py

target_folder: ./generated
interface_package: com.example.shapes
generator_cache_folder: ./cache/generator
generate_type_interface: true
```

This generates both `Rectangle` (instance interface) and `RectangleType` (factory interface).

### Filter What Gets Generated

Include only specific classes or functions:

```yaml
files:
  - path: ./large_module.py

target_folder: ./generated
interface_package: com.example.api
generator_cache_folder: ./cache/generator

whitelist:
  - class: PublicAPI
  - class: DataModel
  - function: process_data
```

Or exclude internal items:

```yaml
files:
  - path: ./module.py

target_folder: ./generated
interface_package: com.example
generator_cache_folder: ./cache/generator

ignore:
  - _internal_helper
  - DebugClass
  - test_function
```

### Custom Type Mappings

Map Python types to specific Java types:

```yaml
files:
  - path: ./data_module.py

target_folder: ./generated
interface_package: com.example.data
generator_cache_folder: ./cache/generator

type_mappings:
  numpy.ndarray: org.graalvm.polyglot.Value
  pandas.DataFrame: com.example.DataFrameWrapper
```

## Next Steps

- See [Configuration Reference](configuration-reference.md) for all available options
- See [Python to Java Type Translation](python-to-java-type-translation.md) for type mapping details
- See [Architecture Overview](architecture.md) for how the generator works

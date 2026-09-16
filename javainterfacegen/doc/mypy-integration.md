# MyPy Integration

This document explains how the Java Interface Generator integrates with MyPy to extract type information from Python source code.

## What is MyPy?

[MyPy](http://mypy-lang.org/) is a static type checker for Python. It analyzes Python code with type annotations and produces a typed Abstract Syntax Tree (AST) with resolved type information. The Java Interface Generator uses MyPy's analysis results rather than implementing its own Python parser.

## Why MyPy?

1. **Accurate type inference**: MyPy resolves complex type expressions, generics, and type aliases
2. **Standard compliance**: Supports PEP 484 type hints and `.pyi` stub files
3. **Mature ecosystem**: Handles edge cases, inheritance, and Python's dynamic features
4. **Maintained**: Active development and wide adoption

## Integration Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Java Side                                │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────────┐  │
│  │    Main     │───▶│  MypyHook   │───▶│  Node/Type Wrappers │  │
│  │             │    │  (interface)│    │  (Java interfaces)  │  │
│  └─────────────┘    └──────┬──────┘    └─────────────────────┘  │
│                            │ GraalPy                             │
│                            │ Polyglot                            │
├────────────────────────────┼────────────────────────────────────┤
│                            ▼                                     │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │                     Python Side                              ││
│  │  ┌─────────────────┐    ┌──────────────────────────────┐   ││
│  │  │  analyzePath.py │───▶│  mypy.build.build()          │   ││
│  │  │  (entry point)  │    │  (MyPy API)                  │   ││
│  │  └─────────────────┘    └──────────────────────────────┘   ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

## Java-Side Components

### MypyHook Interface

[`MypyHook`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/MypyHook.java) defines the Java interface to MyPy functionality:

```java
public interface MypyHook {
    // Parse Python files and cache results
    Map<String, Value> serialize_result(List<String> inputPaths, String cacheFile);

    // Load previously cached parse results
    Map<String, Value> load_result(String serializedData);

    // Extract docstrings from Python source
    Map<String, String> extract_docstrings(String path, String moduleFQN);

    // Set MyPy cache directory
    void set_mypy_cache_folder(String path);
}
```

The interface is implemented via GraalPy's polyglot proxy mechanism:

```java
public static MypyHook fromContext(Context context) {
    Value pythonBindings = context.getBindings("python");
    pythonBindings.putMember("mypyhook_main",
        context.eval("python", "import analyzePath"));
    Value mypyHook = pythonBindings.getMember("mypyhook_main")
        .getMember("analyzePath");
    return mypyHook.as(MypyHook.class);
}
```

### Node Wrappers

The `mypy.nodes` package contains Java interfaces that wrap MyPy's AST nodes. Each wrapper accesses the underlying `Value` object from GraalPy:

```java
public interface MypyFile extends Node {
    String FQN = "mypy.nodes.MypyFile";

    String getName();           // Module name
    String getPath();           // File path
    String getFullname();       // Fully qualified module name
    SymbolTable getNames();     // Exported symbols
    List<Statement> getDefs();  // Top-level definitions

    class MypyFileImpl extends GuestValueDefaultImpl implements MypyFile {
        // Delegates to Value.getMember() calls
    }
}
```

Key node types:

| MyPy Node | Java Interface | Key Methods |
|-----------|----------------|-------------|
| `MypyFile` | [`MypyFile`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/nodes/MypyFile.java) | `getNames()`, `getDefs()` |
| `ClassDef` | [`ClassDef`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/nodes/ClassDef.java) | `getFullname()`, `getInfo()` |
| `FuncDef` | [`FuncDef`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/nodes/FuncDef.java) | `getType()`, `getArguments()` |
| `TypeInfo` | [`TypeInfo`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/nodes/TypeInfo.java) | `getBases()`, `getMro()`, `getNames()` |
| `Var` | [`Var`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/nodes/Var.java) | `getType()`, `isProperty()` |

### Type Wrappers

The `mypy.types` package wraps MyPy's type representations:

```java
public interface Instance extends Type {
    TypeInfo getType();      // Class being instantiated
    List<Type> getArgs();    // Generic type arguments
}

public interface CallableType extends FunctionLike {
    List<Type> getArgTypes();    // Parameter types
    List<ArgKind> getArgKinds(); // ARG_POS, ARG_OPT, etc.
    List<String> getArgNames();  // Parameter names
    Type getRetType();           // Return type
}
```

Key type classes:

| MyPy Type | Java Interface | Represents |
|-----------|----------------|------------|
| `Instance` | [`Instance`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/Instance.java) | `MyClass`, `List[int]` |
| `UnionType` | [`UnionType`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/UnionType.java) | `Union[str, int]`, `Optional[T]` |
| `CallableType` | [`CallableType`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/CallableType.java) | `Callable[[int], str]` |
| `TupleType` | [`TupleType`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/TupleType.java) | `Tuple[int, str]` |
| `TypeVarType` | [`TypeVarType`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/TypeVarType.java) | `T` in `Generic[T]` |
| `AnyType` | [`AnyType`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/AnyType.java) | `Any` |
| `NoneType` | [`NoneType`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/NoneType.java) | `None` |

## Python-Side Components

### analyzePath.py

[`analyzePath.py`](../src/main/resources/GRAALPY-VFS/org.graalvm.python/javainterfacegen/src/analyzePath.py) is the Python entry point that invokes MyPy:

```python
def extract_type_info(file_paths: list[str]):
    """Run MyPy analysis on the given files."""
    sources, options = process_options(file_paths)

    # Configure MyPy for our use case
    options.incremental = False
    options.export_types = True
    options.preserve_asts = True
    options.include_docstrings = True
    options.cache_dir = mypy_cache_dir

    # Run MyPy build
    result = build(sources, options=options)
    return result

def serialize_result(paths: list[str], cache_file_path: str):
    """Parse files and serialize AST to JSON cache."""
    result = extract_type_info(paths)

    # Serialize each module's AST
    ast_dict = {file: result.files[file].serialize()
                for file in result.files}

    with open(cache_file_path, 'w') as f:
        json.dump(ast_dict, f, default=str, indent=2)

    return result.files

def load_result(json_filename: str) -> dict[str, MypyFile]:
    """Load previously cached AST from JSON."""
    with open(json_filename, 'r') as f:
        ast_data = json.load(f)

    # Deserialize each module
    ast_nodes = {file: MypyFile.deserialize(ast)
                 for file, ast in ast_data.items()}

    # Fix up cross-references between modules
    for file, mypy_file in ast_nodes.items():
        fixup_module(mypy_file, ast_nodes, True)

    return ast_nodes
```

### MyPy Configuration

The tool configures MyPy with specific options:

| Option | Value | Purpose |
|--------|-------|---------|
| `incremental` | `False` | Full analysis each time |
| `export_types` | `True` | Preserve type information |
| `preserve_asts` | `True` | Keep full AST for serialization |
| `include_docstrings` | `True` | Extract documentation |
| `follow_imports` | `"silent"` | Process imports without warnings |
| `strict_optional` | `False` | Lenient Optional handling |

## Parsing Flow

### First Run (No Cache)

```
1. Main.parseWithMypy()
   │
   ├── 2. MypyHook.serialize_result(paths, cacheFile)
   │      │
   │      ├── 3. extract_type_info() ── MyPy builds typed AST
   │      │
   │      ├── 4. Serialize AST to JSON
   │      │
   │      └── 5. Return Map<String, MypyFile>
   │
   └── 6. Process MypyFile objects for code generation
```

### Subsequent Runs (With Cache)

```
1. Main.deserializeMypy()
   │
   ├── 2. MypyHook.load_result(cacheFile)
   │      │
   │      ├── 3. Load JSON from disk
   │      │
   │      ├── 4. MypyFile.deserialize() for each module
   │      │
   │      └── 5. fixup_module() to restore cross-references
   │
   └── 6. Return Map<String, MypyFile>
```

## Argument Kinds

MyPy represents function parameter kinds via [`ArgKind`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/ArgKind.java):

| Kind | Python Syntax | Description |
|------|---------------|-------------|
| `ARG_POS` | `def f(x)` | Required positional |
| `ARG_OPT` | `def f(x=1)` | Optional positional |
| `ARG_STAR` | `def f(*args)` | Variadic positional |
| `ARG_NAMED` | `def f(*, x)` | Required keyword-only |
| `ARG_NAMED_OPT` | `def f(*, x=1)` | Optional keyword-only |
| `ARG_STAR2` | `def f(**kwargs)` | Variadic keyword |

These are initialized from Python constants:

```java
public static void initFromContext(Context context) {
    ARG_POS = context.eval("python", "import mypy.nodes; mypy.nodes.ARG_POS");
    ARG_OPT = context.eval("python", "import mypy.nodes; mypy.nodes.ARG_OPT");
    // ...
}
```

## Docstring Extraction

For `.pyi` stub files (which lack docstrings), the tool can extract docstrings from corresponding `.py` files:

```python
def extract_docstrings(file_path: str, moduleFQN: str) -> dict[str, str]:
    """Extract docstrings using Python's ast module."""
    with open(file_path, "r") as file:
        source_code = file.read()

    tree = ast.parse(source_code)
    docstrings = {}

    def process_node(node, parent_name=moduleFQN):
        qualified_name = get_qualified_name(node, parent_name)
        if qualified_name:
            docstring = ast.get_docstring(node)
            if docstring:
                docstrings[qualified_name] = docstring
        for child in ast.iter_child_nodes(node):
            process_node(child, qualified_name)

    process_node(tree)
    return docstrings
```

## Caching

### Why Cache?

Running MyPy on GraalVM is significantly slower than on standard CPython due to GraalVM's JIT compilation overhead for Python code. Caching parsed results dramatically speeds up subsequent runs.

### Cache Format

The cache is a JSON file containing serialized MyPy AST:

```json
{
  "mymodule": {
    ".class": "MypyFile",
    "name": "mymodule",
    "path": "/path/to/mymodule.py",
    "fullname": "mymodule",
    "names": { ... },
    "defs": [ ... ]
  },
  "mymodule.submodule": { ... }
}
```

### Cache Location

Configure via YAML:

```yaml
generator_cache_folder: ./gen_cache    # Current runtime: used for MyPy cache path and serialized AST JSON
```

### Cache Invalidation

Caches contain **absolute file paths** and must be regenerated when:
- Source files change
- Files are moved
- Working directory changes

**Do not commit caches to version control.**

## Troubleshooting

### Stack Overflow

Deep AST traversal may exhaust stack space:

```bash
MAVEN_OPTS="-Xss64M" mvn exec:java ...
```

### Slow First Run

First run invokes full MyPy analysis. Subsequent runs use cached results. For large codebases, expect:
- First run: Minutes
- Cached run: Seconds

### Missing Type Information

If types show as `Any` or `Value`:

1. Check Python source has type annotations
2. Verify `.pyi` stub files are accessible
3. Check `generator_cache_folder` permissions

### Import Errors

MyPy must be able to resolve imports. Ensure:
- Virtual environment is activated
- Dependencies are installed
- `PYTHONPATH` includes necessary directories

## Advanced Usage

### Processing Multiple Modules

```yaml
files:
  - path: ./mypackage
  - path: ./mypackage/submodule.py
```

The tool finds the common root and processes all modules together.

### Handling Stubs

For libraries with `.pyi` stubs:

```yaml
files:
  - path: /path/to/typeshed/stdlib/json/__init__.pyi
```

Docstrings are extracted from corresponding `.py` files when available.

### Custom Type Mappings

Override MyPy's type resolution:

```yaml
type_mappings:
  numpy.ndarray: org.graalvm.polyglot.Value
  pandas.DataFrame: com.example.DataFrameWrapper
```

## Related Documentation

- [Architecture Overview](./architecture.md)
- [Python to Java Type Translation](./python-to-java-type-translation.md)
- [MyPy Documentation](https://mypy.readthedocs.io/)

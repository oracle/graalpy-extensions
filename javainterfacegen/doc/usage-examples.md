# Usage Examples

This document provides detailed examples of using the Java Interface Generator.

## Basic Module Generation

The simplest use case: generate Java interfaces from Python functions.

**Python source (`calculator.py`):**
```python
def add(a: int, b: int) -> int:
    """Add two numbers."""
    return a + b

def multiply(a: float, b: float) -> float:
    """Multiply two numbers."""
    return a * b
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
    /**
     * Add two numbers.
     */
    long add(long a, long b);

    /**
     * Multiply two numbers.
     */
    double multiply(double a, double b);

    static Calculator fromContext(Context context) {
        // Factory method to create instance from GraalPy context
    }
}
```

**Using the generated interface:**
```java
try (Context context = Context.newBuilder("python").build()) {
    Calculator calc = Calculator.fromContext(context);
    long sum = calc.add(5, 3);        // Returns 8
    double product = calc.multiply(2.5, 4.0);  // Returns 10.0
}
```

---

## Class Generation

Generate interfaces for Python classes with type factory interfaces.

**Python source (`shapes.py`):**
```python
class Rectangle:
    """A rectangle shape."""

    def __init__(self, width: float, height: float):
        self.width = width
        self.height = height

    def area(self) -> float:
        """Calculate the area."""
        return self.width * self.height

    def perimeter(self) -> float:
        """Calculate the perimeter."""
        return 2 * (self.width + self.height)
```

**Configuration:**
```yaml
files:
  - path: ./shapes.py
target_folder: ./generated
interface_package: com.example.shapes
generator_cache_folder: ./cache/generator
generate_type_interface: true  # Generate factory interfaces
```

**Generated interfaces:**
```java
// Rectangle.java - Instance interface
public interface Rectangle extends GuestValue {
    double getWidth();
    double getHeight();
    double area();
    double perimeter();

    static boolean isInstance(Object object) { ... }
    static Rectangle cast(Object o) { ... }
}

// RectangleType.java - Factory interface
public interface RectangleType {
    static RectangleType getPythonType(Context context) { ... }
    default Rectangle newInstance(double width, double height) { ... }
}
```

**Usage:**
```java
import org.graalvm.polyglot.Context;

try (Context context = Context.newBuilder("python").build()) {
    // Bind to the generated module/class interface for module-level functions and values
    Shapes shapes = Shapes.fromContext(context);

    // Use the generated *Type factory to construct Python class instances
    RectangleType rectType = RectangleType.getPythonType(context);
    Rectangle rect = rectType.newInstance(10.0, 5.0);

    System.out.println(rect.area());       // 50.0
    System.out.println(rect.perimeter());  // 30.0
}
```

### Factory Method Patterns (`fromContext` vs `getPythonType`)

Generated interfaces expose two common static entry points:

- `fromContext(Context)` on module/namespace interfaces (for example, `Shapes.fromContext(context)`):
  - Binds that generated interface to values/functions exported by the Python module in the provided `Context`.
  - Use this when you want to call module-level functions or access module-level objects.

- `getPythonType(Context)` on generated `*Type` interfaces (for example, `RectangleType.getPythonType(context)`):
  - Binds to a specific Python class/type object in the same `Context`.
  - Use this when you want to construct new Python objects (`newInstance(...)`) or otherwise interact with the Python type itself.

Both methods require `org.graalvm.polyglot.Context` and should be used with the same active GraalPy context that loaded your Python code.

---

## Selective Generation with Whitelist

Generate interfaces only for specific classes or functions.

**Configuration:**
```yaml
files:
  - path: ./large_module.py

target_folder: ./generated
interface_package: com.example.api
generator_cache_folder: ./cache/generator

# Only generate these specific items
whitelist:
  - class: ImportantClass
  - class: AnotherClass
  - function: main_function

# Exclude internal helpers
ignore:
  - _internal_helper
  - DebugClass
```

---

## Custom Type Mappings

Map Python types to specific Java types.

**Configuration:**
```yaml
files:
  - path: ./data_processing.py

target_folder: ./generated
interface_package: com.example.data
generator_cache_folder: ./cache/generator

# Map Python types to custom Java types
type_mappings:
  numpy.ndarray: org.graalvm.polyglot.Value
  pandas.DataFrame: com.example.DataFrameWrapper
  mymodule.CustomType: com.example.JavaCustomType
```

---

## File-Specific Configuration

Apply different settings to different files.

**Configuration:**
```yaml
files:
  - path: ./core.py
    interface_package: com.example.core

  - path: ./utils.py
    interface_package: com.example.utils
    ignore:
      - debug_function

target_folder: ./generated
interface_package: com.example  # Default package
generator_cache_folder: ./cache/generator
```

---

## Class-Specific Configuration

Override return types or other settings for specific classes.

**Configuration:**
```yaml
files:
  - path: ./module.py

target_folder: ./generated
interface_package: com.example
generator_cache_folder: ./cache/generator

classes:
  MyClass:
    # Override return types
    overrides:
      get_data:
        return_type: com.example.CustomData

functions:
  process:
    # Function-specific settings
```

---

## See Also

- [Configuration Quick Start](configuration-quickstart.md) - Common configuration patterns
- [Configuration Reference](configuration-reference.md) - All options
- [Python to Java Type Translation](python-to-java-type-translation.md) - Type mapping details

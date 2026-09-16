# Python to Java Type Translation

This document explains how Python types are translated to Java types in this repository.

The core implementation lives in [TypeManager](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java), especially [`resolveJavaType(...)`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:241), with additional generated helper type support in [TypeGeneratorImpl](../src/main/java/org/graalvm/python/javainterfacegen/generator/impl/TypeGeneratorImpl.java).

## Overview

Type translation is based on mypy's typed model (not direct runtime conversion). During generation:

1. The tool reads mypy type nodes.
2. It resolves each type to a Java type string.
3. It emits imports/signatures.
4. Unknown types are tracked as unresolved placeholders and later patched.

Main entry point:
- [`TypeManager.resolveJavaType(...)`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:241)

## Default built-in mappings

The default mappings are initialized in [`TypeManager.init()`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:147).

| Python type | Java type |
|---|---|
| `None` | `void` (or `Object` for args, see below) |
| `Any` | `org.graalvm.polyglot.Value` |
| `builtins.int` | `long` |
| `builtins.float` | `double` |
| `builtins.bool` | `boolean` |
| `builtins.str` | `java.lang.String` |
| `builtins.list` | `java.util.List` |
| `builtins.tuple` | `java.util.List` (base mapping for `Instance`-based resolution) |
| `builtins.dict` | `java.util.Map` |
| `builtins.set` | `java.util.Set` |
| `Union` | `org.graalvm.polyglot.Value` (fallback for complex unions) |

Special-case override in instance resolution:
- `typing.Coroutine` is translated directly to the default fallback Java type (typically `Value`) in [`resolveJavaType(...)`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:247).

Source:
- [`registerType(...)` calls inside init](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:160)

## Configurable mappings

Custom mappings can be provided via configuration (`type_mappings`) and registered by [`TypeManager.registerTypes(...)`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:196).

This lets users override/add mappings for project-specific Python FQNs.

## Resolution rules by type kind

### 1) Instance types (`Instance`)

Handled in [`resolveJavaType(...)`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:242).

Behavior:

- Lookup Python FQN in registry.
- If unknown, create unresolved placeholder (`pythonFQN#defaultJavaFQN`) using [`addUnresolvedType(...)`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:471).
- If generic args exist and mapped Java raw type is one of:
  - `java.util.List`
  - `java.util.Set`
  - `java.util.Map`
  then recursively translate generic arguments.

Supported generic raw types are defined by [`javaTypeWithGenerics`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:121).

### 2) Primitive boxing inside generics

When a primitive appears in generics, it is boxed via [`javaPrimitiveToWrapper(...)`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:417), e.g.:

- `long` → `Long`
- `boolean` → `Boolean`
- `double` → `Double`

Example:
- Python `list[int]` → Java `List<Long>`

### 3) Union types (`UnionType`)

Handled in [`resolveJavaType(...)`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:300).

Rules:

- If all members are `Literal[...]` with same fallback type: collapse to fallback type.
- If union is exactly `Union[T, None]` (or reversed): return `T`, boxed when needed.
- Otherwise: return default Java fallback (typically `Value`).

This is a deliberate simplification for broad unions.

### 4) Any type (`AnyType`)

Returns default Java fallback type (usually `Value`):
- [`AnyType branch`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:353)

### 5) Type aliases (`TypeAliasType`)

Alias is transparent:
- resolve alias target recursively.
- [`TypeAliasType branch`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:356)

### 6) Tuple types (`TupleType`)

Handled in [`TupleType branch`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:358).

Behavior:

- If fallback is absent or `builtins.tuple`:
  - heterogeneous/empty items → `Value[]` (the default Java fallback)
  - homogeneous items → `<ResolvedElementType>[]`
- If fallback exists and is not `builtins.tuple`, resolve fallback instance type instead.

Note: this means concrete [`TupleType`](../src/main/java/org/graalvm/python/javainterfacegen/mypy/types/TupleType.java) nodes resolve to arrays, while the base mapping `builtins.tuple -> java.util.List` applies to instance-based translation paths.

### 7) None type (`NoneType`)

Special handling in [`NoneType branch`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:385):

- return type position: `void`
- argument position (`isArg=true`): `Object`

### 8) Type variables (`TypeVarType`)

Handled in [`TypeVarType branch`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:387):

- `Self` resolves to current generated Java type name.
- other type vars currently degrade to default fallback (`Value`).

### 9) Literal types (`LiteralType`)

Literal resolves to its fallback/base type:
- [`LiteralType branch`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:399)

### 10) TypeType (`type[T]`)

Currently resolves as inner item type `T`:
- [`TypeType branch`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:403)

### 11) Not fully supported kinds

These currently become unresolved/defaulted:

- `CallableType`
- `Overloaded`
- `TypedDictType`
- `UninhabitedType`

See:
- [`CallableType branch`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:396)
- [`Overloaded branch`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:406)
- [`TypedDictType branch`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:409)
- [`UninhabitedType branch`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:382)

## Unresolved type mechanism

When a type is unknown during first pass, a placeholder form is emitted:
- `pythonFQN#defaultJavaFQN`

Later, [`handleUnresolved()`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:501) revisits generated files to:

1. Replace placeholder with resolved mapped type, if available.
2. Otherwise replace with default fallback and add TODO comments via [`addUnresolvedTypeComment(...)`](../src/main/java/org/graalvm/python/javainterfacegen/generator/TypeManager.java:563).

## Generated helper classes for complex types

For selected complex cases (notably some unions/lists), the generator may emit helper Java classes using [TypeGeneratorImpl](../src/main/java/org/graalvm/python/javainterfacegen/generator/impl/TypeGeneratorImpl.java). These classes typically contain:

- `Optional<T>` fields for alternatives
- constructors per alternative (+ empty constructor for `None` cases)
- `isXxx()` checks
- getters
- `toString()`

Relevant method:
- [`TypeGeneratorImpl.getBody(...)`](../src/main/java/org/graalvm/python/javainterfacegen/generator/impl/TypeGeneratorImpl.java:312)

## Where type translation is consumed

Function and property signatures use this translation in [FunctionGeneratorImpl](../src/main/java/org/graalvm/python/javainterfacegen/generator/impl/FunctionGeneratorImpl.java), especially:

- return type resolution in [`create(FuncDef, ...)`](../src/main/java/org/graalvm/python/javainterfacegen/generator/impl/FunctionGeneratorImpl.java:108) (resolution statement at line 116)
- argument type resolution in [`create(FuncDef, ...)`](../src/main/java/org/graalvm/python/javainterfacegen/generator/impl/FunctionGeneratorImpl.java:193)

## Representative examples

- `builtins.str` → `String`
- `builtins.int` → `long`
- `builtins.set[builtins.int]` → `Set<Long>`
- `builtins.dict[builtins.str, Any]` → `Map<String, Value>`
- `Union[builtins.str, None]` → `String`
- `Union[builtins.str, builtins.int]` → `Value` (fallback)
- Unknown `module1.Unknown` → unresolved placeholder, then patched/defaulted

## Tests validating behavior

Primary tests are in:

- [TypeManagerTest](../src/test/java/org/graalvm/python/javainterfacegen/generator/TypeManagerTest.java)
- [TypeGeneratorImplTest](../src/test/java/org/graalvm/python/javainterfacegen/generator/impl/TypeGeneratorImplTest.java)

Notable assertions include:
- builtins mappings ([basic test](../src/test/java/org/graalvm/python/javainterfacegen/generator/TypeManagerTest.java:152))
- list/set/map generic translation ([list test](../src/test/java/org/graalvm/python/javainterfacegen/generator/TypeManagerTest.java:174), [dict test](../src/test/java/org/graalvm/python/javainterfacegen/generator/TypeManagerTest.java:205))
- union fallback/optional behavior ([union test](../src/test/java/org/graalvm/python/javainterfacegen/generator/TypeManagerTest.java:242), [union+None test](../src/test/java/org/graalvm/python/javainterfacegen/generator/TypeManagerTest.java:252))
- alias/literal/typevar handling ([alias test](../src/test/java/org/graalvm/python/javainterfacegen/generator/TypeManagerTest.java:228), [literal union test](../src/test/java/org/graalvm/python/javainterfacegen/generator/TypeManagerTest.java:288), [typevar test](../src/test/java/org/graalvm/python/javainterfacegen/generator/TypeManagerTest.java:272))

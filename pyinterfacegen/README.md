# pyinterfacegen

This is a JavaDoc and Gradle plugin that generates Python modules designed for binding Java libraries to GraalPy. The Python module consists of:

- `.pyi` stubs used at development time to supply API documentation and types to IDEs, type checkers and API documentation renderers. These are analogous to header files in C.
- A runtime `__init__.py` per package that imports Java types using GraalPy's `java.type()`.

This allows Java libraries to be used more naturally from Python source code.

## Dependencies

JavaDoc requires Java dependencies to be available to process code correctly. The Gradle plugin has a task that resolves whole dependency graphs and converts them at once, so you don't necessarily have to modify the build of the upstream library if you wish to make bindings independently.

Often your Java library API will use types from other libraries that don't have Python type definitions (e.g. JDK classes). The doclet can be configured with a set of globs/regexes to identify which packages and classes are expected to have Python bindings available as well. The pyi stubs generated for your library will then contain Python imports and references to those other types. If a Java type isn't matched by the globs or regexes, then it'll be emitted as an untyped fully dynamic object.

## Direct javadoc invocation (no Gradle)

The doclet assembles a Python package by default, so flags are optional. If your sources reference types from other modules or jars, pass them on the `-classpath` so types resolve and correct imports are emitted:

```bash
javadoc \
  -docletpath path/to/j2pyi-doclet-all-jars \
  -doclet org.graalvm.python.pyinterfacegen.J2PyiDoclet \
  -d build/pyi/main \
  -classpath path/to/dependency1.jar:path/to/dependency-classes \
  -Xj2pyi-moduleName mymodule \
  -Xj2pyi-moduleVersion 0.1.0 \
  -Xj2pyi-packageMap com.example=example \
  -Xj2pyi-assumedTypedPackageGlobs 'com.example.otherproject.**,com.example.utils.**' \
  -Xj2pyi-assumedTypedPackageRegexes 'com\.(foo|bar)\..*' \
  -sourcepath src/main/java \
  com.example
```

Results (unified output directory):
- Module root: `<-d>` (contains .pyi stubs, runtime package `__init__.py` files, and `pyproject.toml`)

Key doclet options:
- `-d <dir>`: unified module root (stubs + runtime files + packaging)
- `-Xj2pyi-moduleName <name>`: distribution/module name
- `-Xj2pyi-moduleVersion <ver>`: version in `pyproject.toml`
- `-Xj2pyi-packageMap <javaPkg=pyPkg[,more]>`: map Java package prefixes to Python package prefixes
- `-Xj2pyi-assumedTypedPackageGlobs`: patterns that match Java package names (before mapping) which will be processed separately and should be assumed to be properly Python typed.

## Gradle plugin

The plugin offers two tasks:

- `J2PyiTask`, which is useful to convert an in-project module.
- `PyiFromDependencySources`, which is useful to convert any set of modules that publishes source JARs to a Maven repository.

In-project usage:

```kotlin
val graalPyBindingsMain by tasks.register<J2PyiTask>("graalPyBindingsMain") {
    source = fileTree("src/main/java") { include("**/*.java") }
    classpath = files()
    setDestinationDir(layout.buildDirectory.dir("pymodule/${project.name}").get().asFile)

    // Optional: customize package assembly
    moduleName.set(project.name)
    moduleOutDir.set(layout.buildDirectory.dir("pymodule").get().asFile.absolutePath)
    moduleVersion.set("0.1.0")
}
```

Then link in the generated Python module to a venv and do a quick workaround for a PyCharm bug (not specific to this project, it's for linking local dev modules):

```shell
PROJECT_NAME=my-project
GENERATED_MODULE=/path/to/$PROJECT_NAME/build/pymodule/$PROJECT_NAME/
python3 -m pip install -e $GENERATED_MODULE
echo GENERATED_MODULE >$VIRTUAL_ENV/lib/python3.12/site-packages/$PROJECT_NAME.pth
```

You should now be able to point your IDE at that venv and import the Java module.

## Generate stubs from dependency sources

The Gradle plugin also provides a task that resolves a whole dependency graph to source JARs, merges them into a temporary source tree, and runs the doclet over the combined sources to produce a single Python module.

Example usage:

```kotlin
val commons by configurations.registering {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    commons("org.apache.commons:commons-lang3:3.14.0")
}

// Register the task
val pyi by tasks.registering(org.graalvm.python.pyinterfacegen.PyiFromDependencySources::class) {
    group = "verification"
    description = "Generate Python stubs from dependency sources in 'depStubs'"
    // Provide the configuration object directly so task inputs track changes correctly
    configuration.set(commons.get())
    // Where the unified Python module will be assembled by the doclet
    destinationDir.set(layout.buildDirectory.dir("pymodule"))
    // Optional: rename/move Java packages to nicer Python package names
    packageMap.set("org.apache.commons.lang3=commons_lang3")
    moduleName.set("commons-lang3-stubs")
    moduleVersion.set("0.1.0")
}
```

Then run:

```bash
./gradlew pyi
```

You'll find a PEP 561 stub-only package at `build/pymodule` containing `.pyi` files and runtime `__init__.py` files for GraalPy imports.

## GraalPy integration check

This build includes a convenience task that downloads a matching GraalPy distribution for your OS/arch, generates stubs and a Python package, then runs a short Python script under GraalPy to import and use the generated bindings:

```bash
./gradlew graalPyIntegrationTest
```

Notes:
- Override the GraalPy version with `-PgraalPyVersion=25.0.1` if needed.
- The task uses the GraalPy community JVM distribution and sets `CLASSPATH` to your compiled classes so Java types are available at runtime.

## Optional: type-check generated stubs

You can run a Python type checker over the generated `.pyi` output to sanity-check internal consistency. A Gradle task type
`TypeCheckPyiTask` is provided by the plugin. This project registers an example task:

```bash
./gradlew typecheckGraalPyStubs
```

By default it runs mypy via `python3 -m mypy`. If mypy isn't installed, the task logs and skips. To use pyright instead:

```kotlin
tasks.named<TypeCheckPyiTask>("typecheckGraalPyStubs") {
    typeChecker.set("pyright")
    // extraArgs.set(listOf("--verifytypes", "your_root_package"))
}
```

Tip: install tools as needed:
 - mypy: `python3 -m pip install mypy`
 - pyright: `npm i -g pyright`

### Namespace packages and mypy

Generated modules may omit intermediate `__init__.py` files to allow multiple distributions to share a namespace
(e.g., generating `foo.bar` and `foo.baz` separately without them conflicting on `foo/__init__.py`). This relies on
[PEP 420] namespace packages.

Mypy needs to be told to treat such directories as packages. The plugin does this automatically by passing:
 - `--namespace-packages`: opt-in to PEP 420 package discovery.
 - `--explicit-package-bases`: interpret the provided paths as package roots, improving resolution for PEP 420 trees.

If you run mypy yourself, enable the same in your config:

```ini
# mypy.ini or pyproject.toml [tool.mypy]
namespace_packages = true
explicit_package_bases = true
```

For code that imports from namespace fragments installed in different locations, ensure mypy sees all fragments in its
search path (e.g., by activating the venv where they’re installed, or by setting `MYPYPATH` to include those site dirs).

[PEP 420]: https://peps.python.org/pep-0420/

# j2pyi

This is a JavaDoc and Gradle plugin that generates a Python module designed for use with GraalPy. The Python module consists of:

- `.pyi` stubs used at development time to supply API documentation and types to IDEs, type checkers and API documentation renderers.
- A runtime `__init__.py` per package that imports Java types using GraalPy's `java.type()`.

This allows Java libraries to be used more naturally from Python source code.

You can either invoke the doclet directly (no Gradle required) or use the Gradle plugin. The plugin lets you resolve whole dependency graphs and convert them at once. That's necessary because the process requires the source code of all a libraries dependencies to be available for conversion as well.

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
import org.graalvm.python.pyinterfacegen.PyiFromDependencySources

val commons by configurations.registering {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    commons("org.apache.commons:commons-lang3:3.14.0")
}

// Register the task
val pyi by tasks.registering(PyiFromDependencySources::class) {
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
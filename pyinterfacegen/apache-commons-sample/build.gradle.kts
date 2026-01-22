import org.graalvm.python.pyinterfacegen.PyiFromDependencySources
import org.graalvm.python.pyinterfacegen.TypeCheckPyiTask

plugins {
    // Apply the plugin without a version; version resolution is handled in settings.gradle.kts
    id("org.graalvm.python.pyinterfacegen")
    // Optional, but common for lifecycle tasks like 'clean'; not strictly required.
    base
}

repositories {
    // Resolve dependencies and the plugin/doclet locally if needed
    mavenCentral()
    mavenLocal()
}

// A resolvable configuration of dependencies to generate stubs for
val commons by configurations.registering {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    // Multiple libraries so we can verify the merge across dependencies
    commons("org.apache.commons:commons-lang3:3.14.0")
    commons("org.apache.commons:commons-collections4:4.4")
}

// Register the task that merges sources and runs the doclet
val pyi by tasks.registering(PyiFromDependencySources::class) {
    group = "verification"
    description = "Generate Python stubs from some Apache Commons libraries"
    // Provide the configuration directly so inputs are tracked robustly
    configuration.set(commons.get())
    // Unified Python module output assembled by the doclet (root directory)
    destinationDir.set(layout.buildDirectory.dir("pymodule"))

    // Map Java package prefixes to nicer Python packages for both libraries
    packageMap.set(
        "org.apache.commons.lang3=commons.lang," +
        "org.apache.commons.collections4=commons.collections"
    )
    moduleName.set("apache-commons")
    moduleVersion.set("0.1.0")
}

// Optional: type check the generated stubs (not part of default lifecycle)
val typecheckApacheCommonsStubs by tasks.registering(TypeCheckPyiTask::class) {
    description = "Run mypy or pyright over the generated Apache Commons stubs"
    // The doclet assembles the module in build/pymodule (root), so point to that.
    moduleDir.set(layout.buildDirectory.dir("pymodule"))
    dependsOn(pyi)
}

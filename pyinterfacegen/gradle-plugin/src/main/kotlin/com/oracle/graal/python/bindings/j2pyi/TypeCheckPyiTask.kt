package org.graalvm.python.pyinterfacegen

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Runs a Python type checker over a generated Python module directory (containing .pyi files and runtime __init__.py files).
 * The task is intended to be optional and not wired into the standard build lifecycle.
 *
 * By default it tries to run 'python3 -m mypy'. Alternatively, set [typeChecker] to "pyright" to use pyright if installed.
 *
 * Typical usage:
 *   tasks.register<TypeCheckPyiTask>("typecheckGraalPyStubs") {
 *       moduleDir.set(layout.buildDirectory.dir("pymodule/${project.name}"))
 *       dependsOn(tasks.named("graalPyBindingsMain"))
 *   }
 */
abstract class TypeCheckPyiTask @Inject constructor(
    private val execOperations: ExecOperations
) : DefaultTask() {

    /**
     * Root directory of the generated Python module (the doclet's -d output).
     * This directory should contain subpackages with .pyi and runtime __init__.py files.
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val moduleDir: DirectoryProperty

    /**
     * Which checker to run. Supported: "mypy" (default) or "pyright".
     */
    @get:Input
    @get:Optional
    abstract val typeChecker: Property<String>

    /**
     * Extra arguments to pass to the checker.
     * Example (mypy): ["--strict"]
     * Example (pyright): ["--verifytypes", "package_name"]
     */
    @get:Input
    @get:Optional
    abstract val extraArgs: ListProperty<String>

    /**
     * If true, the task will not fail when the checker executable isn't found.
     * It will log a lifecycle message and skip instead.
     */
    @get:Input
    @get:Optional
    abstract val skipIfNotFound: Property<Boolean>

    init {
        group = "verification"
        description = "Run a Python type checker (mypy/pyright) over the generated .pyi module directory"
        typeChecker.convention("mypy")
        extraArgs.convention(emptyList())
        skipIfNotFound.convention(true)
    }

    @TaskAction
    fun run() {
        val dir = moduleDir.asFile.get()
        if (!dir.exists()) {
            throw GradleException("Module directory does not exist: $dir. Did you run the stub generation task?")
        }
        if (dir.listFiles()?.isEmpty() != false) {
            throw GradleException("Module directory is empty: $dir. Nothing to type-check.")
        }

        val checker = typeChecker.get().lowercase()
        val args = when (checker) {
            "mypy" -> {
                // Use 'python3 -m mypy' to be resilient to PATH launcher names.
                mutableListOf("python3", "-m", "mypy").apply {
                    addAll(defaultMypyArgs())
                    addAll(extraArgs.get())
                    add(dir.absolutePath)
                }
            }
            "pyright" -> {
                mutableListOf("pyright").apply {
                    addAll(extraArgs.get())
                    add(dir.absolutePath)
                }
            }
            else -> throw GradleException("Unsupported typeChecker: '$checker'. Use 'mypy' or 'pyright'.")
        }

        // Perform a minimal availability check with clear handling of "not found".
        when (checker) {
            "mypy" -> {
                try {
                    val available = checkMypyAvailable()
                    if (!available) {
                        val msg = "Type checker 'mypy' is not available. Install it (e.g. 'python3 -m pip install mypy') and re-run."
                        if (skipIfNotFound.get()) {
                            logger.lifecycle("$msg Skipping type checking.")
                            return
                        } else {
                            throw GradleException(msg)
                        }
                    }
                } catch (e: Exception) {
                    if (skipIfNotFound.get()) {
                        logger.lifecycle("Failed to probe 'mypy' availability (${e.message}). Skipping type checking.")
                        return
                    } else {
                        throw GradleException("Failed to probe 'mypy' availability.", e)
                    }
                }
            }
            "pyright" -> {
                try {
                    val res = execOperations.exec {
                        it.executable = "pyright"
                        it.args = listOf("--version")
                        it.isIgnoreExitValue = false
                    }
                    res.rethrowFailure() // ensure non-zero would throw
                } catch (e: Exception) {
                    if (skipIfNotFound.get()) {
                        logger.lifecycle("Type checker 'pyright' not found or not runnable. Install it (e.g. 'npm i -g pyright'). Skipping type checking.")
                        return
                    } else {
                        throw GradleException("Type checker 'pyright' not found or not runnable. Install it (e.g. 'npm i -g pyright').", e)
                    }
                }
            }
        }

        logger.lifecycle("Running $checker on $dir ...")
        val result = execOperations.exec {
            it.executable = args.first()
            it.args = args.drop(1)
            it.isIgnoreExitValue = true
        }
        val exit = result.exitValue
        if (exit != 0) {
            throw GradleException("Type checker '$checker' reported issues (exit code $exit). See output above.")
        }
    }

    private fun defaultMypyArgs(): List<String> = listOf(
        "--hide-error-codes",
        "--no-error-summary",
        // We don't want imports to explode on missing deps. Stubs are internally consistent within the tree.
        "--follow-imports=skip",
        // Namespace packages are common in generated layouts.
        "--namespace-packages",
        // Treat the provided paths as package roots. This improves module resolution for PEP 420 layouts
        // when checking a directory tree (no intermediate __init__.py files).
        "--explicit-package-bases"
    )

    /**
     * Checks mypy availability without suppressing unexpected errors.
     * Returns true if 'python3' is present and can import the 'mypy' module.
     * If python3 is missing or the import fails, returns false (to allow controlled skip).
     */
    private fun checkMypyAvailable(): Boolean {
        val result = execOperations.exec {
            it.executable = "python3"
            it.args = listOf("-c", "import importlib.util, sys; sys.exit(0 if importlib.util.find_spec('mypy') else 1)")
            it.isIgnoreExitValue = true
        }
        return result.exitValue == 0
    }
}

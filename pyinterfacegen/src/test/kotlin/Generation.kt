package com.oracle.labs

import java.nio.file.Files
import java.nio.file.Path

fun ensureMainGenerated() {
    val moduleBase = mainModuleBase()
    check(Files.isDirectory(moduleBase)) {
        "Generated module should exist at $moduleBase; the test task must depend on graalPyBindingsMain."
    }
}

fun mainModuleBase(): Path = Path.of("build/pymodule/j2pyi/com/example")

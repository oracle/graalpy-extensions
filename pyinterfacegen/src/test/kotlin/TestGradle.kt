package com.oracle.labs

import java.nio.file.Path

object TestGradle {
    fun runGradle(task: String) {
        val cmd = listOf("./gradlew", "-q", "--no-daemon", task)
        val pb = ProcessBuilder(cmd)
            .redirectErrorStream(true)
        val p = pb.start()
        val out = p.inputStream.readAllBytes().toString(Charsets.UTF_8)
        val code = p.waitFor()
        if (code != 0) error("Gradle task failed ($task):\n$out")
    }
}

object Generation {
    @Volatile private var mainGenerated: Boolean = false

    @Synchronized fun ensureMainGenerated() {
        if (!mainGenerated) {
            TestGradle.runGradle("graalPyBindingsMain")
            mainGenerated = true
        }
    }

    fun mainModuleBase(): Path = Path.of("build/pymodule/j2pyi/com/example")
}

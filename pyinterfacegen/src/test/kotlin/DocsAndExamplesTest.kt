package com.oracle.labs

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class DocsAndExamplesTest {
    @Test
    fun quickStartGeneratesExampleStubs() {
        Generation.ensureMainGenerated()
        val box = Generation.mainModuleBase().resolve("Box.pyi")
        assertTrue(Files.exists(box), "Box.pyi should be generated at: $box")
        val text = box.readText()
        assertTrue(text.contains("class Box[T]"), "Quick start should yield PEP 695 inline type parameter on class header:\n$text")
    }
}

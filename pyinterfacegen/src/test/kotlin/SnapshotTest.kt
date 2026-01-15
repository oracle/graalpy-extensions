package com.oracle.labs

import kotlin.io.path.readText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private fun normalize(text: String): String =
    text.replace("\r\n", "\n").trimEnd()

class SnapshotTest {
    @BeforeTest
    fun prepare() {
        Generation.ensureMainGenerated()
    }

    @Test
    fun boxSnapshot() {
        val actual = normalize(Generation.mainModuleBase().resolve("Box.pyi").readText())
        val expected = normalize(object {}.javaClass.getResource("/snapshots/Box.pyi")!!.readText())
        assertEquals(expected, actual, "Box.pyi should match snapshot")
    }

    @Test
    fun boundedSnapshot() {
        val actual = normalize(Generation.mainModuleBase().resolve("Bounded.pyi").readText())
        val expected = normalize(object {}.javaClass.getResource("/snapshots/Bounded.pyi")!!.readText())
        assertEquals(expected, actual, "Bounded.pyi should match snapshot")
    }

    @Test
    fun useSiteSnapshot() {
        val actual = normalize(Generation.mainModuleBase().resolve("UseSite.pyi").readText())
        val expected = normalize(object {}.javaClass.getResource("/snapshots/UseSite.pyi")!!.readText())
        assertEquals(expected, actual, "UseSite.pyi should match snapshot")
    }

    @Test
    fun helloSnapshot() {
        val actual = normalize(Generation.mainModuleBase().resolve("Hello.pyi").readText())
        val expected = normalize(object {}.javaClass.getResource("/snapshots/Hello.pyi")!!.readText())
        assertEquals(expected, actual, "Hello.pyi should match snapshot")
    }

    @Test
    fun packageInitExports() {
        val text = normalize(Generation.mainModuleBase().resolve("__init__.pyi").readText())
        // Expect stable re-exports for the public API of the package
        val expectedLines = listOf(
            "from .Bounded import Bounded as Bounded",
            "from .Box import Box as Box",
            "from .Hello import Hello as Hello",
            "from .UseSite import UseSite as UseSite",
        )
        for (line in expectedLines) {
            kotlin.test.assertTrue(text.contains(line), "__init__.pyi should contain: '$line'\n$text")
        }
    }
}

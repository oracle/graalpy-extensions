package com.oracle.labs

import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class GenericsGenerationTest {
    @Test
    fun emitsTypeVarsAndGenericBases() {
        Generation.ensureMainGenerated()
        val box = Generation.mainModuleBase().resolve("Box.pyi").readText()
        assertTrue(box.contains("class Box[T]:"), "Box should declare inline type parameter T on the class header:\n$box")
        assertTrue(box.contains("def get(self) -> T"), "Box.get should return T:\n$box")
        assertTrue(box.contains("def set(self, v: T) -> None"), "Box.set should accept T:\n$box")
    }

    @Test
    fun emitsBoundedTypeVars() {
        Generation.ensureMainGenerated()
        val bounded = Generation.mainModuleBase().resolve("Bounded.pyi").readText()
        assertTrue(bounded.contains("from numbers import Number"), "Bounded should import Number:\n$bounded")
        assertTrue(bounded.contains("class Bounded[T: Number]:"), "Bounded should declare T bound inline in the header:\n$bounded")
        assertTrue(bounded.contains("def id(self, x: T) -> T"), "Bounded.id should use T in parameter and return:\n$bounded")
    }

    @Test
    fun mapsWildcardsAndRawToAny() {
        Generation.ensureMainGenerated()
        val use = Generation.mainModuleBase().resolve("UseSite.pyi").readText()
        assertTrue(use.contains("def anyList(self) -> list[Any]"), "Wildcard list should map to list[Any]:\n$use")
        assertTrue(use.contains("def mapWild(self) -> dict[Any, str]"), "Map<?, String> should map to dict[Any, str]:\n$use")
        assertTrue(use.contains("def listRaw(self, raw: list[Any]) -> list[str]"), "Raw list param should map to list[Any]:\n$use")
    }
}

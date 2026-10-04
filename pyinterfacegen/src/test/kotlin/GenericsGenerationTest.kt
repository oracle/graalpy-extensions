package com.oracle.labs

import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class GenericsGenerationTest {
    @Test
    fun emitsTypeVarsAndGenericBases() {
        ensureMainGenerated()
        val box = mainModuleBase().resolve("Box.pyi").readText()
        assertTrue(
            box.contains("class Box(Generic[T]):"),
            "Box should declare a PEP 484 Generic[T] base on the class header:\n$box"
        )
        // Return-only TypeVars are replaced with Any to satisfy type checkers.
        assertTrue(box.contains("def get(self) -> Any"), "Box.get should return Any:\n$box")
        // Java methods are exposed by GraalPy with their original names.
        assertTrue(box.contains("def set(self, v: T) -> None"), "Box.set should accept T:\n$box")
    }

    @Test
    fun emitsBoundedTypeVars() {
        ensureMainGenerated()
        val bounded = mainModuleBase().resolve("Bounded.pyi").readText()
        assertTrue(bounded.contains("from numbers import Number"), "Bounded should import Number:\n$bounded")
        // Bounded now uses a PEP 484 TypeVar with a bound and Generic[T] base rather than inline PEP 695 syntax.
        assertTrue(bounded.contains("T = TypeVar(\"T\", bound=Number)"), "Bounded should declare TypeVar T bound to Number:\n$bounded")
        assertTrue(bounded.contains("class Bounded(Generic[T]):"), "Bounded should declare Generic[T] base on the class header:\n$bounded")
        assertTrue(bounded.contains("def id(self, x: T) -> T"), "Bounded.id should use T in parameter and return:\n$bounded")
    }

    @Test
    fun mapsWildcardsAndRawToAny() {
        ensureMainGenerated()
        val use = mainModuleBase().resolve("UseSite.pyi").readText()
        assertTrue(use.contains("def anyList(self) -> list[Any]"), "Wildcard list should map to list[Any]:\n$use")
        assertTrue(use.contains("def mapWild(self) -> dict[Any, str]"), "Map<?, String> should map to dict[Any, str]:\n$use")
        assertTrue(use.contains("def listRaw(self, raw: list[Any]) -> list[str]"), "Raw list param should map to list[Any]:\n$use")
    }
}

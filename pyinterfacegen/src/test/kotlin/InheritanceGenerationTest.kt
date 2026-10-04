package com.oracle.labs

import kotlin.io.path.readText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class InheritanceGenerationTest {
    @BeforeTest
    fun prepare() {
        ensureMainGenerated()
    }

    @Test
    fun emitsSuperclassAndIndependentInterface() {
        val text = mainModuleBase().resolve("NamedHello.pyi").readText()

        assertTrue(text.contains("class NamedHello(Hello, Nameable):"), "Expected superclass and interface:\n$text")
        assertTrue(
            text.contains("# Java member 'name' omitted to preserve the inherited Python signature."),
            "Expected inherited interface contract explanation:\n$text"
        )
        assertTrue(!text.contains("def name("), "Interface method should be inherited rather than redeclared:\n$text")
    }

    @Test
    fun skipsInterfaceAlreadyInheritedThroughSuperclass() {
        val text = mainModuleBase().resolve("EnthusiasticHello.pyi").readText()

        assertTrue(text.contains("class EnthusiasticHello(NamedHello):"), "Expected only the effective superclass:\n$text")
        assertTrue(!text.contains("class EnthusiasticHello(NamedHello, Nameable)"), "Nameable must not be duplicated:\n$text")
        assertTrue(
            text.contains("# Java member 'greet' omitted to preserve the inherited Python signature."),
            "Expected overridden member explanation:\n$text"
        )
    }

    @Test
    fun preservesGenericSuperclassArguments() {
        val text = mainModuleBase().resolve("StringBox.pyi").readText()

        assertTrue(text.contains("class StringBox(Box[str]):"), "Expected parameterized generic superclass:\n$text")
    }
}

package org.graalvm.python.pyinterfacegen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for platform package handling.
 * Verifies:
 *  - Default JDK/platform packages map to builtins.object (no Ref/import emission).
 *  - User can extend platform set via -Xj2pyi-extraPlatformPackages.
 */
class PlatformPackagesTest {
    @Test
    fun `default JDK types map to builtins_object`() {
        // The method returns java.util.Optional<java.lang.String> and takes org.w3c.dom.Document
        val src = """
            import java.util.Optional;
            import org.w3c.dom.Document;
            public class UsesJdk {
                public Optional<String> maybe() { return Optional.empty(); }
                public Document dom(Document d) { return d; }
            }
        """.trimIndent()
        val pyi = DocletTestUtil.runDocletWithArgs(
            src,
            packageName = "com.example",
            extraArgs = listOf("-Xj2pyi-include", "com.example")
        )
        // Optional<String> becomes str | None in return position (Iterable mapping is elsewhere)
        assertTrue(pyi.contains("def maybe(self) -> str | None:"), "Expected Optional mapping to union with None:\n$pyi")
        // org.w3c.* should be treated as platform -> mapped to builtins.object
        assertTrue(pyi.contains("def dom(self, d: builtins.object) -> builtins.object:"), "Expected platform type mapped to builtins.object:\n$pyi")
        // Ensure we didn't emit a reference import for org.w3c.dom.Document in the .pyi
        assertFalse(pyi.contains("from org.w3c.dom.Document import Document"), "Should not emit import for platform package type:\n$pyi")
    }

    @Test
    fun `user extended platform packages are respected`() {
        // Create a fake external package type reference and ensure it's mapped as builtins.object
        val extSrc = """
            package ext.lib;
            public class External { }
        """.trimIndent()
        val userSrc = """
            package com.example;
            import ext.lib.External;
            public class UsesExternal {
                public External get() { return null; }
            }
        """.trimIndent()
        val destDir = DocletTestUtil.runDocletMultiWithArgs(
            arrayOf(
                "ext.lib" to extSrc.removePrefix("package ext.lib;").trim(),
                "com.example" to userSrc.removePrefix("package com.example;").trim()
            ),
            extraArgs = listOf(
                "-Xj2pyi-include", "com.example,ext.lib",
                "-Xj2pyi-extraPlatformPackages", "ext.lib"
            )
        )
        val pyi = java.io.File(destDir, "com/example/UsesExternal.pyi").readText()
        // Return type from 'ext.lib' should be mapped to builtins.object due to extraPlatformPackages
        assertTrue(pyi.contains("def get(self) -> builtins.object:"), "Expected return type mapped to builtins.object:\n$pyi")
        // And there should be no import for ext.lib.External
        assertFalse(pyi.contains("from ext.lib.External import External"), "Should not emit import for user-extended platform package type:\n$pyi")
    }
}

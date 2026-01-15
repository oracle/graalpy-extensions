package org.graalvm.python.pyinterfacegen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class M10ConfigTest {
    @Test
    fun metadataHeader_emitted_whenEnabled() {
        val java = """
            public class Meta {
                public Meta() {}
                public String name() { return "x"; }
            }
        """.trimIndent()
        val text = DocletTestUtil.runDocletWithArgs(
            java,
            packageName = "com.example",
            extraArgs = listOf("-Xj2pyi-emitMetadataHeader", "true")
        )
        assertTrue(text.lines().first().startsWith("# javadoc2pyi:"), "Expected metadata header at top:\n$text")
    }

    @Test
    fun includeExclude_filters_packages() {
        val dest = DocletTestUtil.runDocletMultiWithArgs(
            arrayOf(
                "com.a" to """
                    public class A {
                        public A() {}
                    }
                """.trimIndent(),
                "com.b" to """
                    public class B {
                        public B() {}
                    }
                """.trimIndent()
            ),
            extraArgs = listOf("-Xj2pyi-include", "com.a")
        )
        val aFile = File(dest, "com/a/A.pyi")
        val bFile = File(dest, "com/b/B.pyi")
        assertTrue(aFile.isFile, "Expected included class generated: ${aFile.absolutePath}")
        assertFalse(bFile.exists(), "Expected excluded class not generated: ${bFile.absolutePath}")
    }

    @Test
    fun interfaceAsProtocol_false_renders_plain_class() {
        val java = """
            public interface I {
                String a();
            }
        """.trimIndent()
        val text = DocletTestUtil.runDocletWithArgs(
            java,
            packageName = "com.example",
            extraArgs = listOf("-Xj2pyi-noInterfaceProtocol")
        )
        // Should not import Protocol; header should be 'class I:' not 'class I(Protocol):'
        assertFalse(text.contains("from typing import Protocol"), "Should not import Protocol when disabled:\n$text")
        assertTrue(text.contains("\nclass I:\n") || text.startsWith("class I:\n"), "Header should be plain class:\n$text")
    }

    @Test
    fun visibility_public_plus_package_includes_pkgPrivate_members() {
        val java = """
            public class V {
                int count; // package-private
                public V() {}
            }
        """.trimIndent()
        val text = DocletTestUtil.runDocletWithArgs(
            java,
            packageName = "com.example",
            extraArgs = listOf("-Xj2pyi-visibility", "public+package")
        )
        assertTrue(text.contains("count: int"), "Package-private field should be included under public+package:\n$text")
    }
}

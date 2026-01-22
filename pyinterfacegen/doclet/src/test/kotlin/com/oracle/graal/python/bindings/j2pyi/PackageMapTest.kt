package org.graalvm.python.pyinterfacegen

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.readText
import kotlin.test.assertTrue

class PackageMapTest {
    @Test
    fun packagePrefixMapping_remaps_output_and_imports() {
        // Two simple classes in related packages that reference each other
        val dest = DocletTestUtil.runDocletMultiWithArgs(
            arrayOf(
                "com.a" to """
                    public class A {
                        public com.a.sub.B make() { return null; }
                    }
                """.trimIndent(),
                "com.a.sub" to """
                    public class B {
                        public com.a.A ref() { return null; }
                    }
                """.trimIndent()
            ),
            extraArgs = listOf("-Xj2pyi-packageMap", "com.a=pyA")
        )
        // Expect files under pyA and pyA/sub
        val aFile = File(dest, "pyA/A.pyi")
        val bFile = File(dest, "pyA/sub/B.pyi")
        assertTrue(aFile.isFile, "Expected remapped A.pyi at ${aFile.absolutePath}")
        assertTrue(bFile.isFile, "Expected remapped B.pyi at ${bFile.absolutePath}")
        // Import in A.pyi should reference from pyA.sub.B import B
        val aText = aFile.toPath().readText()
        assertTrue(aText.contains("from pyA.sub.B import B"), "Expected mapped absolute import in A.pyi:\n$aText")
        // Import in B.pyi should reference from pyA.A import A
        val bText = bFile.toPath().readText()
        assertTrue(bText.contains("from pyA.A import A"), "Expected mapped absolute import in B.pyi:\n$bText")
        // __init__.pyi exists in both pyA and pyA/sub
        assertTrue(File(dest, "pyA/__init__.pyi").isFile, "Expected __init__.pyi in pyA")
        assertTrue(File(dest, "pyA/sub/__init__.pyi").isFile, "Expected __init__.pyi in pyA/sub")
    }
}

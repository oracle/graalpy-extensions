package org.graalvm.python.pyinterfacegen

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.readText
import kotlin.test.assertTrue

class DependencyImportTest {
    @Test
    fun references_to_external_declared_types_emit_imports() {
        // External dependency type not included in -subpackages input
        val depPkg = "dep.lib"
        val depSrc = """
            public class DepType {
                public String name() { return ""; }
            }
        """.trimIndent()
        val depClasses: File = DocletTestUtil.compileToDir(mapOf("$depPkg.DepType" to (depPkg to depSrc)))

        // Our library type that references the dependency type in a method signature
        val libPkg = "com.app"
        val libSrc = """
            import dep.lib.DepType;
            public class UseDep {
                public DepType make() { return null; }
            }
        """.trimIndent()

        val outDir = DocletTestUtil.runDocletMultiWithArgs(
            arrayOf(libPkg to libSrc),
            // No special doclet args; supply classpath so javadoc can resolve the type
            classpath = listOf(depClasses)
        )
        val libPyi = File(outDir, "${libPkg.replace('.', '/')}/UseDep.pyi").toPath().readText()

        // Expect an import for DepType (absolute import based on dependency's Java package)
        assertTrue(libPyi.contains("from dep.lib.DepType import DepType"),
            "Expected absolute import for DepType in generated stub.\n$libPyi")

        // And type appears in method return annotation
        assertTrue(libPyi.contains("def make(self) -> DepType:"))
    }
}

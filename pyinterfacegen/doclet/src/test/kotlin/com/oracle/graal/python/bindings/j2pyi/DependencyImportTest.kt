package org.graalvm.python.pyinterfacegen

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.readText
import kotlin.test.assertTrue

class DependencyImportTest {
    @Test
    fun references_to_external_declared_types_default_to_object() {
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

        // By default, only packages emitted by this doclet run are assumed to have stubs.
        // External declared types are scrubbed to builtins.object.
        assertTrue(!libPyi.contains("from dep.lib.DepType import DepType"),
            "Did not expect an import for DepType by default.\n$libPyi")
        assertTrue(libPyi.contains("def make(self) -> builtins.object:"),
            "Expected external types to be scrubbed to builtins.object.\n$libPyi")
    }

    @Test
    fun references_to_assumed_typed_external_declared_types_emit_imports() {
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
            classpath = listOf(depClasses),
            extraArgs = listOf(
                // Tell the doclet that dep.* packages are assumed to have stubs elsewhere.
                "-Xj2pyi-assumedTypedPackageGlobs", "dep/**"
            )
        )
        val libPyi = File(outDir, "${libPkg.replace('.', '/')}/UseDep.pyi").toPath().readText()

        assertTrue(libPyi.contains("from dep.lib.DepType import DepType"),
            "Expected absolute import for DepType when it is assumed typed.\n$libPyi")
        assertTrue(libPyi.contains("def make(self) -> DepType:"))
    }
}

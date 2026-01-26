package org.graalvm.python.pyinterfacegen

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.io.PrintWriter
import java.nio.file.Files
import java.util.spi.ToolProvider

/**
 * Test utilities for invoking the javadoc tool with our doclet and preparing sources.
 * Centralizing this logic avoids duplication across tests.
 */
object DocletTestUtil {
    private fun extractTypeName(src: String): String {
        val classMatch = Regex("""\bclass\s+([A-Za-z0-9_]+)""").find(src)?.groupValues?.get(1)
        if (classMatch != null) return classMatch
        val ifaceMatch = Regex("""\binterface\s+([A-Za-z0-9_]+)""").find(src)?.groupValues?.get(1)
        if (ifaceMatch != null) return ifaceMatch
        val enumMatch = Regex("""\benum\s+([A-Za-z0-9_]+)""").find(src)?.groupValues?.get(1)
        if (enumMatch != null) return enumMatch
        return "TestClass"
    }

    /**
     * Run the doclet on multiple Java source snippets with extra javadoc arguments (e.g., custom options).
     * Each pair is (packageName, javaSourceWithoutPackageLine).
     * Returns the destination directory containing generated .pyi files.
     */
    fun runDocletMultiWithArgs(
        sources: Array<out Pair<String, String>>,
        extraArgs: List<String> = emptyList(),
        classpath: List<File> = emptyList()
    ): File {
        val tmpSrc = Files.createTempDirectory("javadoc2pyi-multi-src").toFile()
        // Write all provided sources
        val basePkgs = linkedSetOf<String>()
        for ((pkg, src) in sources) {
            basePkgs += pkg
            val pkgDir = File(tmpSrc, pkg.replace('.', '/'))
            pkgDir.mkdirs()
            val className = extractTypeName(src)
            File(pkgDir, "$className.java").writeText(
                """
                    package $pkg;
                    $src
                    """.trimIndent()
            )
        }
        val destDir = Files.createTempDirectory("javadoc2pyi-multi-out").toFile()
        val javadoc = ToolProvider.findFirst("javadoc")
            .orElseThrow { IllegalStateException("javadoc tool not found in this JDK") }
        val baseArgs = mutableListOf(
            "-quiet",
            "-d", destDir.absolutePath,
            "-sourcepath", tmpSrc.absolutePath,
            "-subpackages", basePkgs.joinToString(":"),
            "-doclet", J2PyiDoclet::class.qualifiedName!!
        )
        if (classpath.isNotEmpty()) {
            baseArgs += listOf("-classpath", classpath.joinToString(File.pathSeparator) { it.absolutePath })
        }
        baseArgs.addAll(extraArgs)
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val origOut = System.out
        val origErr = System.err
        System.setOut(PrintStream(outBytes, true, "UTF-8"))
        System.setErr(PrintStream(errBytes, true, "UTF-8"))
        try {
            val code = javadoc.run(PrintWriter(outBytes, true), PrintWriter(errBytes, true), *baseArgs.toTypedArray())
            if (code != 0) {
                val stdout = outBytes.toString("UTF-8")
                val stderr = errBytes.toString("UTF-8")
                throw AssertionError("javadoc exited with $code; stderr:\n$stderr\nstdout:\n$stdout")
            }
        } finally {
            System.setOut(origOut)
            System.setErr(origErr)
        }
        return destDir
    }

    /** Compile tiny Java sources to a classes directory for use as a javadoc classpath. */
    fun compileToDir(sources: Map<String, Pair<String, String>>): File {
        // Map: fqcn -> (packageName, javaSourceWithoutPackage)
        val tmpSrc = Files.createTempDirectory("javadoc2pyi-compile-src").toFile()
        val outDir = Files.createTempDirectory("javadoc2pyi-compile-out").toFile()
        for ((_, pair) in sources) {
            val (pkg, src) = pair
            val pkgDir = File(tmpSrc, pkg.replace('.', '/'))
            pkgDir.mkdirs()
            val className = extractTypeName(src)
            File(pkgDir, "$className.java").writeText("package $pkg;\n$src\n")
        }
        // Collect all Java files under tmpSrc and compile them
        val javaFiles = tmpSrc.walkTopDown().filter { it.isFile && it.extension == "java" }.map { it.absolutePath }.toList()
        if (javaFiles.isEmpty()) return outDir
        val javac = ToolProvider.findFirst("javac")
            .orElseThrow { IllegalStateException("javac tool not found in this JDK") }
        val args = mutableListOf(
            "-d", outDir.absolutePath,
            "-source", "17",
            "-target", "17",
            "-encoding", "UTF-8"
        )
        args.addAll(javaFiles)
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val code = javac.run(PrintWriter(outBytes, true), PrintWriter(errBytes, true), *args.toTypedArray())
        if (code != 0) {
            val stdout = outBytes.toString("UTF-8")
            val stderr = errBytes.toString("UTF-8")
            throw AssertionError("javac exited with $code; stderr:\n$stderr\nstdout:\n$stdout")
        }
        return outDir
    }

    /**
     * Run the doclet on multiple Java source snippets. Each pair is (packageName, javaSourceWithoutPackageLine).
     * Returns the destination directory containing generated .pyi files so tests can inspect package structure.
     */
    fun runDocletMulti(vararg sources: Pair<String, String>): File {
        val tmpSrc = Files.createTempDirectory("javadoc2pyi-m2-src").toFile()
        // Write all provided sources
        val basePkgs = linkedSetOf<String>()
        for ((pkg, src) in sources) {
            basePkgs += pkg
            val pkgDir = File(tmpSrc, pkg.replace('.', '/'))
            pkgDir.mkdirs()
            val className = Regex("""class\s+([A-Za-z0-9_]+)""").find(src)?.groupValues?.get(1) ?: "TestClass"
            File(pkgDir, "$className.java").writeText(
                """
                package $pkg;
                $src
                """.trimIndent()
            )
        }
        val destDir = Files.createTempDirectory("javadoc2pyi-m2-out").toFile()
        val javadoc = ToolProvider.findFirst("javadoc")
            .orElseThrow { IllegalStateException("javadoc tool not found in this JDK") }
        val args = arrayOf(
            "-quiet",
            "-d", destDir.absolutePath,
            "-sourcepath", tmpSrc.absolutePath,
            "-subpackages", basePkgs.joinToString(":"),
            "-doclet", J2PyiDoclet::class.qualifiedName!!
        )
        val outBytes = ByteArrayOutputStream()
        val errBytes = ByteArrayOutputStream()
        val origOut = System.out
        val origErr = System.err
        System.setOut(PrintStream(outBytes, true, "UTF-8"))
        System.setErr(PrintStream(errBytes, true, "UTF-8"))
        try {
            val code = javadoc.run(PrintWriter(outBytes, true), PrintWriter(errBytes, true), *args)
            if (code != 0) {
                val stdout = outBytes.toString("UTF-8")
                val stderr = errBytes.toString("UTF-8")
                throw AssertionError("javadoc exited with $code; stderr:\n$stderr\nstdout:\n$stdout")
            }
        } finally {
            System.setOut(origOut)
            System.setErr(origErr)
        }
        return destDir
    }

    /**
     * Run the doclet on a single Java source with extra args and return the generated .pyi module text.
     */
    fun runDocletWithArgs(javaSource: String, packageName: String = "com.example", extraArgs: List<String> = emptyList()): String {
        val className = extractTypeName(javaSource)
        val dest = runDocletMultiWithArgs(arrayOf(packageName to javaSource), extraArgs)
        val moduleFile = File(dest, packageName.replace('.', File.separatorChar) + "/$className.pyi")
        if (!moduleFile.isFile) {
            throw AssertionError("Expected generated module not found: ${moduleFile.absolutePath}")
        }
        return moduleFile.readText().trimEnd()
    }

    /**
     * Run the doclet on a single Java source and return the generated .pyi module text.
     */
    fun runDoclet(javaSource: String, packageName: String = "com.example"): String {
        val className = extractTypeName(javaSource)
        // Delegate to the multi-source helper to avoid duplicating javadoc wiring.
        val dest = runDocletMulti(packageName to javaSource)
        val moduleFile = File(dest, packageName.replace('.', File.separatorChar) + "/$className.pyi")
        if (!moduleFile.isFile) {
            throw AssertionError("Expected generated module not found: ${moduleFile.absolutePath}")
        }
        return moduleFile.readText().trimEnd()
    }
}

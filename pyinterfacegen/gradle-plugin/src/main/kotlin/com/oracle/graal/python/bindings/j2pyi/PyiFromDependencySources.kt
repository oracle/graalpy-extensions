package org.graalvm.python.pyinterfacegen

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.jar.JarFile
import javax.inject.Inject

/**
 * Resolves the sources of a dependency configuration, merges them into a temporary source directory,
 * and then invokes the J2Pyi doclet using the JDK javadoc tool to assemble a Python module.
 */
@CacheableTask
abstract class PyiFromDependencySources : DefaultTask() {
    @get:Inject
    protected abstract val execOperations: ExecOperations

    /**
     * Option A (preferred in Kotlin DSL snippets): directly provide the Configuration object.
     */
    @get:Internal
    abstract val configuration: Property<Configuration>

    /**
     * Option B: provide a configuration name (e.g., "depStubs" or "compileClasspath").
     */
    @get:Input
    @get:Optional
    abstract val configurationName: Property<String>

    /**
     * Directory where all resolved source JARs/directories are merged.
     */
    @get:OutputDirectory
    abstract val mergedSourcesDir: DirectoryProperty

    /**
     * Root directory where the doclet assembles the Python module (contains .pyi, __init__.py and pyproject.toml).
     * The doclet writes into a subdirectory named by moduleName by default; keep that behavior for consistency.
     */
    @get:OutputDirectory
    abstract val destinationDir: DirectoryProperty

    /**
     * Coordinates of resolved modules (group:module:version) for the selected configuration.
     * Used as lightweight input to invalidate the cache when the resolution graph changes.
     */
    @get:Input
    abstract val dependencyCoordinates: ListProperty<String>

    /**
     * Source artifacts (e.g., sources jars) fetched for the resolved modules.
     * Marked as classpath so that names/paths are normalized consistently.
     */
    @get:InputFiles
    @get:Classpath
    abstract val dependencySourceArtifacts: ConfigurableFileCollection

    /**
     * Doclet classpath (the j2pyi doclet jars), participates in cache key.
     */
    @get:InputFiles
    @get:Classpath
    abstract val docletClasspathInput: ConfigurableFileCollection

    /**
     * Optional mapping from Java package prefixes to Python package prefixes.
     * Example: "com.knuddels.jtokkit=jtokkit,com.example=example"
     */
    @get:Input
    @get:Optional
    abstract val packageMap: Property<String>

    /**
     * Optional CSV of Java package prefixes to exclude from extraction and javadoc scanning.
     * Example: "io.micronaut.grpc,io.micronaut.http.netty"
     */
    @get:Input
    @get:Optional
    abstract val excludePrefixes: Property<String>

    @get:Input
    @get:Optional
    abstract val moduleName: Property<String>

    @get:Input
    @get:Optional
    abstract val moduleVersion: Property<String>

    /**
     * Whether to include only .java files (default true). Hook for future language support.
     */
    @get:Input
    @get:Optional
    abstract val javaOnly: Property<Boolean>

    init {
        group = "documentation"
        description = "Generate Python stubs from dependency sources using the j2pyi doclet (no placeholder needed)"

        // Defaults
        mergedSourcesDir.convention(project.layout.buildDirectory.dir("merged-dependency-sources/${name}"))
        destinationDir.convention(project.layout.buildDirectory.dir("pymodule"))
        javaOnly.convention(true)
        moduleName.convention(project.name)
        moduleVersion.convention("0.1.0")

        // Default configuration name to main compileClasspath if Java plugin is present.
        project.plugins.withId("java") {
            val javaExt = project.extensions.findByType(JavaPluginExtension::class.java)
            if (javaExt != null) {
                configurationName.convention("${SourceSet.MAIN_SOURCE_SET_NAME}CompileClasspath")
            }
        }

        // Expose the doclet configuration (created by the plugin) as a cache input.
        @Suppress("UNCHECKED_CAST")
        val docletConf = project.extensions.findByName("j2pyiDocletClasspath") as? Configuration
            ?: project.configurations.maybeCreate("j2pyiDoclet")
        docletClasspathInput.from(project.provider { docletConf })

        // Pick whichever configuration the user provided: direct Configuration or by name.
        val confProvider: Provider<Configuration> =
            configuration.orElse(configurationName.map { project.configurations.getByName(it) })

        // Capture resolved module coordinates for cache key.
        dependencyCoordinates.convention(
            confProvider.map { conf ->
                conf.incoming.resolutionResult.allComponents
                    .mapNotNull { it.id as? ModuleComponentIdentifier }
                    .filter { !it.version.isNullOrBlank() }
                    .map { "${it.group}:${it.module}:${it.version}" }
                    .sorted()
            }
        )

        // Capture the set of source artifacts for cache fingerprinting.
        dependencySourceArtifacts.from(
            confProvider.map { binaryConf ->
                // Limit to real library components, skip platforms/BOMs and other non-jar artifacts.
                val components = binaryConf.incoming.resolutionResult.allComponents
                    .mapNotNull { it.id as? ModuleComponentIdentifier }
                    .distinctBy { Triple(it.group, it.module, it.version) }
                    .filterNot { it.version.isNullOrBlank() }
                    // Exclude common BOM/platform coordinates (e.g., io.micronaut.platform:micronaut-platform)
                    .filterNot { mcid ->
                        val m = mcid.module.lowercase()
                        val g = mcid.group.lowercase()
                        g.endsWith(".platform") || m.contains("bom") || m.contains("platform")
                    }

                val deps = components
                    .map { mcid ->
                        val notation = "${mcid.group}:${mcid.module}:${mcid.version}:sources@jar"
                        project.dependencies.create(notation)
                    }
                    // This stub JAR has no code in it so also no source jar.
                    .filterNot { it.group == "com.google.guava" && it.name == "listenablefuture" && it.version == "9999.0-empty-to-avoid-conflict-with-guava" }
                val sourcesConf = project.configurations.detachedConfiguration(*deps.toTypedArray()).apply {
                    isTransitive = false
                    isCanBeResolved = true
                    isCanBeConsumed = false
                }
                sourcesConf.incoming.artifactView {
                    // If lenient = true we can tolerate some sources being missing. However, this tool is designed
                    // for open source projects where source jars should always be available. Being unlenient means
                    // there will be an exception if any sources are missing.
                    it.isLenient = false
                }.files
            }
        )
    }

    @TaskAction
    fun run() {
        val binaryConf: Configuration = configuration.orNull
            ?: configurationName.orNull?.let { project.configurations.getByName(it) }
            ?: throw IllegalStateException("Either 'configuration' or 'configurationName' must be set")

        // Resolve sources via the lazily-defined input for consistency with cache key.
        val sourcesFiles = dependencySourceArtifacts.files.toList()

        // Merge sources into the output dir
        val outDir: File = mergedSourcesDir.get().asFile
        project.delete(outDir)
        outDir.mkdirs()

        // Copy sources without attempting to set POSIX file modes, to be robust on non-POSIX filesystems.
        val javaOnlyFlag = javaOnly.get()

        // If a package map is provided, limit extracted sources to those Java package prefixes.
        val includePkgPrefixes: List<String> = packageMap.orNull
            ?.split(',', ';')
            ?.mapNotNull {
                val kv = it.trim().split('=', limit = 2)
                if (kv.size == 2) kv[0].trim().replace('.', '/') + "/" else null
            }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        // Package prefixes to exclude
        val excludePkgPrefixes: List<String> = excludePrefixes.orNull
            ?.split(',', ';')
            ?.map { it.trim().replace('.', '/') + "/" }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        fun shouldIncludePath(relPath: String): Boolean {
            if (!javaOnlyFlag || relPath.lowercase().endsWith(".java")) {
                if (includePkgPrefixes.isEmpty()) {
                    // No include filter: just ensure we don't match any exclude
                    return excludePkgPrefixes.none { relPath.startsWith(it) }
                }
                // Include only if under requested packages and not excluded
                return includePkgPrefixes.any { relPath.startsWith(it) } &&
                        excludePkgPrefixes.none { relPath.startsWith(it) }
            }
            return false
        }

        sourcesFiles.forEach { f ->
            if (!f.exists()) return@forEach
            if (f.isDirectory) {
                // Walk and copy .java files only
                f.walkTopDown()
                    .filter { it.isFile && shouldIncludePath(it.relativeTo(f).invariantSeparatorsPath) }
                    .forEach { src ->
                        val rel = src.relativeTo(f).invariantSeparatorsPath
                        val dest = File(outDir, rel)
                        if (!dest.exists()) {
                            dest.parentFile.mkdirs()
                            src.inputStream().use { input ->
                                dest.outputStream().use { output -> input.copyTo(output) }
                            }
                        }
                    }
            } else if (f.name.endsWith(".jar", ignoreCase = true) || f.name.endsWith(".zip", ignoreCase = true)) {
                JarFile(f).use { jar ->
                    val entries = jar.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (e.isDirectory) continue
                        if (!shouldIncludePath(e.name)) continue
                        val dest = File(outDir, e.name)
                        if (dest.exists()) continue // honor EXCLUDE duplicates strategy
                        dest.parentFile.mkdirs()
                        jar.getInputStream(e).use { input ->
                            dest.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
        }

        if (!outDir.exists() || outDir.listFiles()?.isEmpty() != false) {
            throw GradleException("No source files resolved for configuration '${binaryConf.name}' so could not generate a Python module.")
        }

        // Gather all .java files into an argfile to avoid command line length limits.
        val sources = project.fileTree(outDir) { it.include("**/*.java") }.files.sortedBy { it.absolutePath }
        if (sources.isEmpty()) {
            throw GradleException("Merged sources directory contains no .java files: $outDir")
        }
        val tmpDir = project.layout.buildDirectory.dir("tmp/${name}").get().asFile.also { it.mkdirs() }
        val argFile = File(tmpDir, "sources.txt")
        Files.write(
            argFile.toPath(),
            sources.joinToString(System.lineSeparator()) { it.absolutePath }.toByteArray(Charsets.UTF_8),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE
        )

        // Build classpath strings
        val classpathStr = binaryConf.resolve().joinToString(File.pathSeparator) { it.absolutePath }
        val docletCpStr = docletClasspathInput.files.joinToString(File.pathSeparator) { it.absolutePath }

        // Resolve javadoc executable from current JDK
        val javaHome = System.getProperty("java.home")
        val javadocExe = File(javaHome, "bin/javadoc" + if (System.getProperty("os.name").lowercase().contains("win")) ".exe" else "")
        require(javadocExe.exists()) { "javadoc executable not found at: $javadocExe (JAVA_HOME: $javaHome)" }

        val args = mutableListOf(
            "-doclet", "org.graalvm.python.pyinterfacegen.J2PyiDoclet",
            "-docletpath", docletCpStr,
            "-d", destinationDir.get().asFile.absolutePath,
            "-classpath", classpathStr
        )

        // Reduce noise; unresolved symbols are handled by the doclet.
        args += listOf("-quiet")

        // If the user provided a packageMap, pass it to the doclet and also constrain javadoc scanning
        // to those Java package prefixes using -sourcepath and -subpackages for reliable inclusion.
        val pkgMapSpec: String? = packageMap.orNull?.takeIf { it.isNotBlank() }
        if (pkgMapSpec != null) {
            args += listOf("-Xj2pyi-packageMap", pkgMapSpec)
        }
        excludePrefixes.orNull?.takeIf { it.isNotBlank() }?.let { args += listOf("-Xj2pyi-exclude", it) }
        run {
            val includePkgs: List<String> = pkgMapSpec
                ?.split(',', ';')
                ?.map {
                    val kv = it.trim().split('=', limit = 2)
                    if (kv.size == 2) kv[0].trim() else throw IllegalArgumentException("Entry in package map is not in k=v form: $it")
                }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val usingSubpackages = includePkgs.isNotEmpty()
            if (usingSubpackages) {
                // Also tell the doclet to include only these java package prefixes
                args += listOf("-Xj2pyi-include", includePkgs.joinToString(","))
                // Constrain the Javadoc analysis to only the specified subpackages from the merged sources.
                args += listOf(
                    "-sourcepath", outDir.absolutePath,
                    "-subpackages", includePkgs.joinToString(":")
                )
            }
            // If we're using -subpackages, do NOT pass the explicit list of source files to javadoc.
            // Javadoc will discover sources from the sourcepath. Otherwise, pass the argfile.
            if (!usingSubpackages) {
                args += "@${argFile.absolutePath}"
            }
        }
        moduleName.orNull?.takeIf { it.isNotBlank() }?.let { args += listOf("-Xj2pyi-moduleName", it) }
        moduleVersion.orNull?.takeIf { it.isNotBlank() }?.let { args += listOf("-Xj2pyi-moduleVersion", it) }

        // Execute javadoc
        // Ensure destination directory is clean before generation to avoid stale files affecting cache or success checks.
        project.delete(destinationDir.get().asFile)
        project.logger.lifecycle("Running javadoc doclet to generate Python stubs (sources: ${sources.size})")
        execOperations.exec {
            it.executable = javadocExe.absolutePath
            it.args = args
            // Fail the task on any non-zero exit to prevent incorrect cache entries/hits.
            it.isIgnoreExitValue = false
        }
    }
}

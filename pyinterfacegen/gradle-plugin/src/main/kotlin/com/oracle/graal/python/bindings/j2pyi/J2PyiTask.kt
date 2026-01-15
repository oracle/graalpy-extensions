package org.graalvm.python.pyinterfacegen

import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Property
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import java.io.File

/**
 * Converts a single set of sources to a Python module.
 */
abstract class J2PyiTask : Javadoc() {
    @get:Internal
    protected val docletClasspath: Configuration
        get() = (project.extensions.findByName("j2pyiDocletClasspath") as Configuration)

    /**
     * Optional mapping from Java package prefixes to Python package prefixes.
     * Example: "com.knuddels.jtokkit=jtokkit,com.example=example"
     */
    @get:Input
    @get:Optional
    val packageMap: Property<String> = project.objects.property(String::class.java)

    @get:Input
    @get:Optional
    val moduleName: Property<String> = project.objects.property(String::class.java)


    @get:Input
    @get:Optional
    val moduleVersion: Property<String> = project.objects.property(String::class.java)

    /**
     * Ensures the doclet classpath participates in up-to-date checks and the build cache key.
     */
    @get:Classpath
    abstract val docletClasspathInput: ConfigurableFileCollection

    init {
        group = "documentation"
        description = "Generate Python .pyi stubs using the custom Javadoc doclet"
        // If the doclet/javadoc reports errors, fail the task so the build cache won't record a successful run.
        isFailOnError = true

        // Ensure the doclet artifact is built/resolved before running the Javadoc task.
        dependsOn(docletClasspath)

        val opts = options as StandardJavadocDocletOptions
        opts.noTimestamp.value = false
        opts.doclet = "org.graalvm.python.pyinterfacegen.J2PyiDoclet"

        // Defer resolution of the doclet classpath and also expose it as a cache input.
        docletClasspathInput.from(project.provider { docletClasspath })

        moduleName.convention(project.name)
        moduleVersion.convention("0.1.0")

        // Default output to build/pymodule/<project-name> to match repo convention
        // Users can still override via setDestinationDir in their task configuration.
        destinationDir = project.layout.buildDirectory.dir("pymodule/${project.name}").get().asFile

        // Provide sensible defaults for source and classpath so dependent types resolve.
        // Users can override in their task configuration if needed.
        project.plugins.withId("java") {
            val javaExt = project.extensions.findByType(JavaPluginExtension::class.java)
            if (javaExt != null) {
                val main = javaExt.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                // Default Java sources if none explicitly set
                if (this@J2PyiTask.source.isEmpty) {
                    this@J2PyiTask.source = main.allJava.matching { it.include("**/*.java") }
                }
                // Default classpath to main compileClasspath so referenced types in dependencies are resolved
                if (this@J2PyiTask.classpath.isEmpty) {
                    this@J2PyiTask.classpath = main.compileClasspath
                }
            }
        }
    }

    override fun generate() {
        // Resolve the docletpath at execution time so Gradle can fingerprint inputs lazily.
        (options as StandardJavadocDocletOptions).docletpath = docletClasspathInput.files.toList()
        packageMap.orNull?.let { spec ->
            if (spec.isNotBlank()) {
                // NOTE: StandardJavadocDocletOptions.addStringOption expects the option name WITHOUT the leading dash.
                // Passing "-Xj2pyi-packageMap" would result in an incorrectly formatted option that the doclet won't see.
                // Use "Xj2pyi-packageMap" here so Gradle emits "-Xj2pyi-packageMap <value>" to the javadoc tool.
                (options as StandardJavadocDocletOptions).addStringOption("Xj2pyi-packageMap", spec)
            }
        }
        moduleName.orNull?.let { (options as StandardJavadocDocletOptions).addStringOption("Xj2pyi-moduleName", it) }
        moduleVersion.orNull?.let { (options as StandardJavadocDocletOptions).addStringOption("Xj2pyi-moduleVersion", it) }
        super.generate()
    }
}

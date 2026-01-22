package org.graalvm.python.pyinterfacegen

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

open class J2PyiExtension {
    /**
     * Coordinate of the published doclet used by tasks. Override if you publish under a different version.
     */
    var docletCoordinate: String = "org.graalvm.python.pyinterfacegen:j2pyi-doclet:1.3-SNAPSHOT"
}

class J2PyiPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // User-configurable extension
        val ext = project.extensions.create("j2pyi", J2PyiExtension::class.java)

        // Configuration holding the doclet artifact
        val docletConf: Configuration = project.configurations.maybeCreate("j2pyiDoclet").apply {
            isCanBeConsumed = false
            isCanBeResolved = true
            // Provide a reasonable default dependency; users can add more via this configuration if needed
            defaultDependencies { deps ->
                val localDoclet = project.findProject(":doclet")
                if (localDoclet != null) {
                    deps.add(project.dependencies.project(mapOf("path" to ":doclet")))
                } else {
                    deps.add(project.dependencies.create(ext.docletCoordinate))
                }
            }
        }

        // Make the configuration easy to find from tasks
        project.extensions.add("j2pyiDocletClasspath", docletConf)
    }
}

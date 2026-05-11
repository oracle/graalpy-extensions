package org.graalvm.python.pyinterfacegen.build

import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.io.File

fun RepositoryHandler.mavenBundleRepository(startDir: File) {
    generateSequence(startDir.absoluteFile) { it.parentFile }
        .map { it.resolve(".mvn/maven-bundle") }
        .firstOrNull { it.exists() }
        ?.let { bundledRepo ->
            maven {
                name = "mavenBundle"
                url = bundledRepo.toURI()
            }
        }
}

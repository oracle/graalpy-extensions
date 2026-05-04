plugins {
    `kotlin-dsl`
}

fun org.gradle.api.artifacts.dsl.RepositoryHandler.mavenBundleRepository(startDir: File) {
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

repositories {
    mavenBundleRepository(rootDir)
    gradlePluginPortal()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

pluginManagement {
    includeBuild("../build-logic")
    repositories {
        generateSequence(settingsDir.absoluteFile) { it.parentFile }
            .map { it.resolve(".mvn/maven-bundle") }
            .firstOrNull { it.exists() }
            ?.let { bundledRepo ->
                maven {
                    name = "mavenBundle"
                    url = bundledRepo.toURI()
                }
            }
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "j2pyi-gradle-plugin"

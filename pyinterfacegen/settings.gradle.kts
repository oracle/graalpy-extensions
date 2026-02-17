pluginManagement {
    includeBuild("build-logic")
    includeBuild("gradle-plugin")
    repositories {
        // Allow resolving the plugin from local Maven when published
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
    // Ensure requests for our plugin id resolve to the included gradle-plugin module,
    // and pin the version to the repository root POM version to avoid any hard-coded fallback.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.graalvm.python.pyinterfacegen") {
                // Read version once from the repository root pom.xml (../pom.xml from this settings file)
                val rootPom = file("../pom.xml")
                val rootVersion: String = kotlin.run {
                    if (!rootPom.exists()) return@run "unspecified"
                    // Falls back to <properties><revision> if used as the effective version.
                    val text = rootPom.readText()
                    // Prefer explicit <version> under <project>
                    val verRegex = Regex("<project[\\s\\S]*?<version>\\s*([^<\\s]+)\\s*</version>", RegexOption.DOT_MATCHES_ALL)
                    val revRegex = Regex("<properties>[\\s\\S]*?<revision>\\s*([^<\\s]+)\\s*</revision>", RegexOption.DOT_MATCHES_ALL)
                    val ver = verRegex.find(text)?.groupValues?.getOrNull(1)
                    ver ?: (revRegex.find(text)?.groupValues?.getOrNull(1) ?: "unspecified")
                }
                // Map the plugin id to the implementation module provided by the included build;
                // include the version string to satisfy Gradle's notation requirements.
                val ver = if (rootVersion != "unspecified") rootVersion else "0.0.0-DEV"
                useModule("org.graalvm.python.pyinterfacegen:gradle-plugin:$ver")
            }
        }
    }
}

rootProject.name = "j2pyi"

include(":doclet")
include(":apache-commons-sample")
// Include the plugin project as a composite build so its tasks (e.g., publishToMavenLocal) are invokable.
includeBuild("gradle-plugin")

pluginManagement {
    // Resolve our plugin from the included build instead of requiring it to be pre-published.
    includeBuild("gradle-plugin")
    repositories {
        // Allow resolving the plugin from local Maven when published
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
    // Ensure requests for our plugin id resolve to the included gradle-plugin module
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.graalvm.python.pyinterfacegen") {
                useModule("org.graalvm.python.pyinterfacegen:gradle-plugin:${requested.version ?: "1.3-SNAPSHOT"}")
            }
        }
    }
}

rootProject.name = "j2pyi"

include(":doclet")
include(":apache-commons-sample")
// Include the plugin project as a composite build so its tasks (e.g., publishToMavenLocal) are invokable.
includeBuild("gradle-plugin")

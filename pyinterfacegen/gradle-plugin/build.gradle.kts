import org.gradle.api.publish.maven.MavenPublication
import org.graalvm.python.pyinterfacegen.build.readRootPomMetadata

plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.2.10"
    `maven-publish`
    id("j2pyi.convention")
}

group = "org.graalvm.python"

// Derive the version and metadata from the repository root pom.xml so this included build aligns with the parent build.
val rootPomMeta = readRootPomMetadata(rootProject)
version = rootPomMeta.version

repositories {
    mavenCentral()
    // For resolving the doclet dependency when using the plugin locally
    mavenLocal()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    // Use the host JDK (21) to avoid toolchain download in this environment
    jvmToolchain(21)
}

// Ensure the plugin JAR manifest contains the Implementation-Version so plugin code can discover its own version.
tasks.withType<Jar>().configureEach {
    manifest {
        attributes(mapOf("Implementation-Title" to project.name, "Implementation-Version" to project.version))
    }
}

gradlePlugin {
    plugins {
        create("jarsToGraalPyBindings") {
            id = "org.graalvm.python.pyinterfacegen"
            displayName = "JARs to GraalPy bindings plugin"
            description = "Provides a PyiFromDependencySources task that runs a Javadoc-based doclet to emit a Python module containing .pyi stubs"
            implementationClass = "org.graalvm.python.pyinterfacegen.J2PyiPlugin"
            tags.set(listOf("graalpy", "python", "pyi", "doclet", "javadoc"))
        }
    }
}

// Configure publishing. With `java-gradle-plugin` applied, a `pluginMaven` publication is created automatically.
// We also publish the main Java component for convenience.
publishing {
    publications {
        // Conventional Java publication of the plugin JAR
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
        // `pluginMaven` is created by the java-gradle-plugin; no need to define it explicitly here.
    }
    repositories {
        val localRepoUrl = (project.findProperty("localRepoUrl") as String?)?.trim()?.takeIf { it.isNotEmpty() }
        if (localRepoUrl != null) {
            maven {
                name = "local"
                url = uri(localRepoUrl)
            }
        } else {
            mavenLocal()
        }
    }
}

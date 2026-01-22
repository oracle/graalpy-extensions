plugins {
    `java-gradle-plugin`
    kotlin("jvm") version "2.2.10"
    `maven-publish`
}

group = "org.graalvm.python.pyinterfacegen"
version = "1.3-SNAPSHOT"

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

publishing {
    // java-gradle-plugin sets up marker + plugin publications; just add mavenLocal as a target
    repositories {
        mavenLocal()
    }
}

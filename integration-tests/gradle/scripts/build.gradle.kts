plugins {
    application
    id("org.graalvm.python") version "$VERSION$"
    id("org.graalvm.buildtools.native") version "0.10.2"
}

repositories {
    mavenLocal()
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of($JAVA_LANGUAGE_VERSION$))
    }
}

application {
    // Define the main class for the application.
    mainClass = "org.example.GraalPy"
}

val r = tasks.run.get()
r.enableAssertions = true
r.outputs.upToDateWhen {false}

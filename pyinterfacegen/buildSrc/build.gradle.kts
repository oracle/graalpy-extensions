plugins {
    // Sets up Kotlin + Gradle Kotlin DSL for buildSrc
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

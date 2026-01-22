plugins {
    kotlin("jvm") version "2.2.10"
    `maven-publish`
}

repositories {
    mavenCentral()
}

group = "org.graalvm.python.pyinterfacegen"

dependencies {
    // Kotlin stdlib is brought in by the Kotlin plugin.
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            // Publish a clearer artifact name
            artifactId = "j2pyi-doclet"
        }
    }
    repositories {
        mavenLocal()
    }
}

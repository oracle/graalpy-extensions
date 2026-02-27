plugins {
    kotlin("jvm") version "2.2.10"
    java
    `maven-publish`
}

repositories {
    mavenCentral()
}

group = "org.graalvm.python"

java {
    withSourcesJar()
    withJavadocJar()
}

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

            pom {
                name.set("J2PyI doclet")
                description.set("A JavaDoc doclet that emits Python .pyi stub modules for use with GraalPy")
            }
        }
    }
    // Allow publishing to a specific local repository via -PlocalRepoUrl=...
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

import org.graalvm.python.pyinterfacegen.J2PyiTask
import org.graalvm.python.pyinterfacegen.TypeCheckPyiTask
import org.gradle.internal.os.OperatingSystem
import org.gradle.api.publish.maven.MavenPublication
import org.graalvm.python.pyinterfacegen.build.readRootPomMetadata
import java.net.URI
import java.util.*

plugins {
    kotlin("jvm") version "2.2.10"
    java
    // Use the locally included plugin (see settings.gradle.kts pluginManagement). Version is supplied there.
    id("org.graalvm.python.pyinterfacegen")
    id("j2pyi.convention")  // Local build logic.
}

// Read metadata and version from the repository root pom.xml
val rootPomMeta = readRootPomMetadata(rootProject)

allprojects {
    group = "org.graalvm.python"
    version = rootPomMeta.version
}

// When developing locally, always use the local doclet project for any requests to the published module,
// regardless of version requested by the plugin.
allprojects {
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.graalvm.python.pyinterfacegen:j2pyi-doclet"))
                .using(project(":doclet"))
        }
    }
}

// For projects that publish, project the POM data from the root pom.xml into their publications.
subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    url.set(rootPomMeta.url)
                    version = this@subprojects.version.toString()
                    licenses {
                        rootPomMeta.licenses.forEach { lic ->
                            license {
                                name.set(lic.name)
                                url.set(lic.url)
                            }
                        }
                    }
                    developers {
                        rootPomMeta.developers.forEach { d ->
                            developer {
                                name.set(d.name)
                                email.set(d.email)
                                d.organization?.let { organization.set(it) }
                                d.organizationUrl?.let { organizationUrl.set(it) }
                            }
                        }
                    }
                    scm {
                        url.set(rootPomMeta.scm.url)
                        connection.set(rootPomMeta.scm.connection)
                        developerConnection.set(rootPomMeta.scm.developerConnection)
                        rootPomMeta.scm.tag?.let { tag.set(it) }
                    }
                }
            }
        }
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    // Use JDK 21 present on this host; adjust as needed in other environments
    jvmToolchain(21)
}

// ------- GraalPy Integration Test Support -------
// Allow overriding the GraalPy version at invocation time: -PgraalPyVersion=25.0.1
val graalPyVersion: String = providers.gradleProperty("graalPyVersion").orNull ?: "25.0.1"

data class GraalPyDist(val url: String, val archiveName: String, val isZip: Boolean)

fun resolveGraalPyDist(version: String): GraalPyDist {
    val os = OperatingSystem.current()
    val (osToken, isZip) = when {
        os.isWindows -> "windows" to true
        os.isMacOsX -> "macos" to false
        os.isLinux -> "linux" to false
        else -> error("Unsupported OS for GraalPy: ${System.getProperty("os.name")}")
    }
    val archProp = System.getProperty("os.arch").lowercase(Locale.ROOT)
    val archToken = when {
        archProp.contains("aarch64") || archProp.contains("arm64") -> "aarch64"
        archProp.contains("x86_64") || archProp.contains("amd64") -> "amd64"
        else -> error("Unsupported arch for GraalPy: $archProp")
    }
    // Use community-jvm distribution (runs on JVM; suitable for our needs)
    val baseName = "graalpy-community-jvm-$version-$osToken-$archToken"
    val ext = if (isZip) ".zip" else ".tar.gz"
    val archiveName = baseName + ext
    val tag = "graal-$version"
    val url = "https://github.com/oracle/graalpython/releases/download/$tag/$archiveName"
    return GraalPyDist(url, archiveName, isZip)
}

val graalPyDir = layout.buildDirectory.dir("graalpy").get().asFile
val distDir = graalPyDir.resolve("dist").apply { mkdirs() }
val extractDir = graalPyDir.resolve("extracted")

val downloadGraalPy by tasks.registering {
    group = "verification"
    description = "Download GraalPy distribution for the host OS/arch"
    val dist = resolveGraalPyDist(graalPyVersion)
    val destFile = distDir.resolve(dist.archiveName)
    outputs.file(destFile)
    doLast {
        if (!destFile.exists()) {
            logger.lifecycle("Downloading GraalPy ${graalPyVersion} from ${dist.url}")
            destFile.outputStream().use { out ->
                URI(dist.url).toURL().openStream().use { ins -> ins.copyTo(out) }
            }
        } else {
            logger.lifecycle("GraalPy archive already present: ${destFile}")
        }
    }
}

val extractGraalPy by tasks.registering(Copy::class) {
    group = "verification"
    description = "Extract the downloaded GraalPy archive"
    dependsOn(downloadGraalPy)
    val dist = resolveGraalPyDist(graalPyVersion)
    val archive = distDir.resolve(dist.archiveName)
    from(if (dist.isZip) zipTree(archive) else tarTree(resources.gzip(archive)))
    into(extractDir)
}

// Instance 1: run on this project’s Java sources
val graalPyBindingsMain by tasks.register<J2PyiTask>("graalPyBindingsMain") {
    source = fileTree("src/main/java") { include("**/*.java") }
    classpath = files()
    setDestinationDir(layout.buildDirectory.dir("pymodule/${project.name}").get().asFile)
}

// Optional verification: run a Python type checker over the generated module.
// Not wired into the standard 'check' lifecycle; invoke explicitly.
tasks.register<TypeCheckPyiTask>("typecheckGraalPyStubs") {
    description = "Run mypy (or pyright) over the generated .pyi module to detect internal inconsistencies"
    moduleDir.set(layout.buildDirectory.dir("pymodule/${project.name}"))
    // Ensure stubs are generated before checking them
    dependsOn(graalPyBindingsMain)
    // Default checker is mypy; customize via:
    //   typeChecker.set("pyright")
    //   extraArgs.set(listOf("--strict"))
}
// Execute a simple GraalPy run that imports the generated module and calls a method
val graalPyIntegrationTest by tasks.registering {
    group = "verification"
    description = "Generate stubs, compile Java, and verify import/call under GraalPy"
    dependsOn("classes", graalPyBindingsMain, extractGraalPy)

    doLast {
        // Locate graalpy executable under the extracted directory
        val home = extractDir.listFiles()?.firstOrNull { it.isDirectory } ?: error("No GraalPy directory found under $extractDir")
        val exe = if (OperatingSystem.current().isWindows) home.resolve("bin/graalpy.exe") else home.resolve("bin/graalpy")
        require(exe.exists()) { "graalpy executable not found at: $exe" }

        // Generated module root (doclet assembles by default via the Gradle task)
        val moduleRoot = layout.buildDirectory.dir("pymodule").get().asFile.resolve(project.name)
        require(moduleRoot.exists()) { "Generated Python module not found at: $moduleRoot. Run graalPyBindingsMain first." }

        // Write a tiny Python script for the verification
        val scriptDir = graalPyDir.resolve("integration").apply { mkdirs() }
        val script = scriptDir.resolve("verify_import.py")
        script.writeText(
            """
            |import os, sys
            |# Make the generated module importable
            |sys.path.insert(0, os.path.abspath(${"\"" + moduleRoot.absolutePath.replace("\\", "\\\\") + "\""}))
            |from com.example import Hello
            |h = Hello()
            |s = h.greet("GraalPy")
            |# Print a known line so Gradle can assert success heuristically
            |print("GREETING:", s)
            |# Basic sanity assertion
            |assert "Hello, GraalPy!" == str(s)
            |print("OK: import and call worked")
            |""".trimMargin()
        )

        // Construct environment for JVM interop: ensure our compiled classes are on the classpath
        val classpath = listOf(
            layout.buildDirectory.dir("classes/java/main").get().asFile.absolutePath,
            layout.buildDirectory.dir("classes/kotlin/main").get().asFile.absolutePath,
        ).filter { File(it).exists() }
             .joinToString(File.pathSeparator)

        val cmd = mutableListOf(exe.absolutePath)
        // Enable JVM mode for Java interop and pass the host JVM classpath
        cmd += listOf("--jvm")
        if (classpath.isNotBlank()) {
            cmd += listOf("--vm.classpath=$classpath")
        }
        cmd += script.absolutePath
        val pb = ProcessBuilder(cmd)
            .directory(project.projectDir)
            .redirectErrorStream(true)
        // Also set CLASSPATH for completeness; some tooling honors it.
        if (classpath.isNotBlank()) pb.environment()["CLASSPATH"] = classpath
        val proc = pb.start()
        val out = proc.inputStream.readAllBytes().toString(Charsets.UTF_8)
        val code = proc.waitFor()
        logger.lifecycle(out)
        if (code != 0) error("GraalPy integration run failed ($code). See output above.")
        if (!out.contains("OK: import and call worked")) error("GraalPy run did not report success. Output:\n$out")
    }
}

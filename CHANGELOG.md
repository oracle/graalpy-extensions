# GraalPy Extensions Changelog

This changelog summarizes user-observable changes in GraalPy Extensions
artifacts released to Maven Central.

For releases prior to 25.0.0, GraalPy Extensions were part of
GraalPy itself; those changes are recorded in the
[GraalPy changelog](https://github.com/oracle/graalpython/blob/master/CHANGELOG.md)
under the "GraalPy Embedding" section.

## 25.1.0

* API reference documentation for the embedding module is now published at
[oracle.github.io/graalpy-extensions](https://oracle.github.io/graalpy-extensions/latest/org.graalvm.python.embedding/module-summary.html).

* GraalPy Maven plugin supports configuration of Python dependencies via
external `requirements.txt` file as an alternative to specifying those
dependencies in `pom.xml` (#30). See the [documentation](https://github.com/oracle/graalpython/blob/e41e01aa69144b9d9adf5526cd96ffedc6d502c9/docs/user/Embedding-Build-Tools.md#using-requirementstxt)
for more details.

* Fixed unnecessary eager reads in `VirtualFileSystem`: files are no longer
read into memory unless required (#49). This avoids unnecessary
`OutOfMemoryError`s when working with large files.

* Fixed runtime use of Python files precompiled during package installation by
the Maven and Gradle plugins (#51). Previously, a modification-time check caused
those files to be ignored at runtime.

## 25.0.2

* `VirtualFileSystem` now supports directory layouts that do not include a
`venv` directory (#48). This is intended for advanced users who manually create
the virtual file system or use custom tools instead of the official GraalPy
Maven and Gradle plugins.

* `VirtualFileSystem` no longer installs its own
`java.util.logging.ConsoleHandler` (#48). Users should configure
`java.util.logging` through the standard Java logging mechanisms.

## 25.0.1

* GraalPy Extensions artifacts now target Java 17.

## 25.0.0

* Initial standalone GraalPy Extensions release. The embedding library, Maven
plugin, Gradle plugin, Maven archetype, and JBang integration moved out of the
GraalPy repository into this project.

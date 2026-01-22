# GraalPy Extensions Changelog

This changelog summarizes major changes in GraalPy Extensions.
The main focus is on user-observable behavior.

For releases prior to 25.0.0, GraalPy Extensions were part of
GraalPy itself; those changes are recorded in the
[GraalPy changelog](https://github.com/oracle/graalpython/blob/master/CHANGELOG.md)
under "GraalPy Embedding".

## 25.1.0

* `VirtualFileSystem` now supports directory layouts that do not
include a `venv` directory. This feature is intended for advanced
users who manually create the virtual file system or use custom tools
other than the official GraalPy Maven/Gradle plugins.

* `VirtualFileSystem` no longer configures its own `java.util.logging.ConsoleHandler`.
Users should configure the `java.util.logging` framework using standard mechanisms.

* GraalPy Maven plugin supports configuration of Python dependencies via
external `requirements.txt` file as an alternative to specifying those
dependencies in `pom.xml`. See the [documentation](https://github.com/oracle/graalpython/blob/e41e01aa69144b9d9adf5526cd96ffedc6d502c9/docs/user/Embedding-Build-Tools.md#using-requirementstxt)
for more details.

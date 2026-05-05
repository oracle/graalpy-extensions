## How to develop against the latest GraalPy, GraalVM SDK, and Truffle:

The top-level `pom.xml` property `revision` is the source of truth for the
project version. The version of GraalPy/GraalVM dependencies is determined
by the property `project.polyglot.version`, which defaults to `${revision}`.

Some pre-release versions of GraalPy/GraalVM dependencies require a local Maven
bundle. The repository convention is:

```text
.mvn/maven-bundle -> .mvn/maven-bundle-{revision}
```

Maven automatically adds `.mvn/maven-bundle` as an additional repository only
when that path exists. If `.mvn/maven-bundle` is absent, the build continues to
use the standard configured Maven repositories.

Maven, recursive Maven invocations, helper scripts, pre-commit hooks, and GitHub
Actions rely on the default `.mvn/maven-bundle` convention. You should not need
to pass custom Maven settings or local repository properties for the bundle.

Gradle builds also use this convention for normal dependency resolution. Because
some Gradle projects live in subdirectories, their repository setup scans upward
from the Gradle root for `.mvn/maven-bundle`. Maven-to-Gradle invocations and
standalone Gradle builds should work without extra repository arguments.

### Option 1: Use pre-built Maven bundle

Install the bundle for the current `pom.xml` revision with:

```
./scripts/maven-bundle-setup.sh
```

This downloads into `.mvn/maven-bundle-{revision}` and updates the default
`.mvn/maven-bundle` symlink.

Download a bundle for another version with:

```
./scripts/maven-bundle-setup.sh --version 25.0.0-SNAPSHOT
```

When `--version` is used, the script downloads into
`.mvn/maven-bundle-25.0.0-SNAPSHOT` and deliberately does not update the
default `.mvn/maven-bundle` symlink.

### Option 2: Build and install GraalPy/Truffle from sources

Check out and build [GraalPy](https://github.com/oracle/graalpython) by
following the instructions in its documentation. We need to build all necessary
dependencies. Make sure that you use the latest supported Java version;
otherwise, multi-release JARs will not be complete.

```
mx --dy /tools,/sdk,/truffle build
```

Run `mx maven-deploy` (full command below) from the `graal/vm` directory.
Replace the version with the current development version and keep the
`-SNAPSHOT` suffix for a better development experience.

Note: `-SNAPSHOT` versions are always re-checked and the newest snapshot is
always pulled from the repository by Maven, unlike regular versions, which are
assumed to be "immutable" and can be cached with no way to invalidate the cache
other than manually removing the artifacts from `~/.m2/repository/...`.

```
mx --dy /tools,/truffle,/graalpython maven-deploy --all-suites \
            --version-string 25.0.0-SNAPSHOT \
            --validate none
```

Since we did not specify a repository, this installs the Maven artifacts into
your local repository (e.g., `~/.m2/repository`) and Maven projects can use the
version `25.0.0-SNAPSHOT` without any extra configuration. Installation to the
local repository also omits the license checks.

## Structure

This repository is structured as a Maven multi-module project. There is also a Gradle project
for the Gradle plugin, `org.graalvm.python.gradle.plugin`, and for the `pyinterface` tool.

A Maven project and `pom.xml` exist for the Gradle plugin, but solely to delegate most of the
lifecycle tasks to Gradle. This allows you to run those tasks with a single Maven command.

Some subprojects contain standard JUnit tests, and there are also Python-driven integration tests.

To package, clean, install, test, or otherwise build only one module, use, for example:

```
mvn -pl org.graalvm.python.gradle.plugin -am clean
```

### Integration tests

tl;dr:

```
mvn install exec:java@integration-tests -Dintegration.test.args="test_maven_plugin.py" -Dgradle.java.home=...
```

The integration tests are driven by Python and implemented using the unittest framework, which is
part of the Python standard library. The tests expect that the relevant Maven artifacts are available,
which can be achieved, for example, by `mvn install`. They can also run on released artifacts
published in Maven Central or a snapshot repository configured in Maven settings.

The whole execution of the tests is wrapped in Maven goal `exec:java@integration-tests`, which passes
some necessary arguments to the test driver Python script. You can pass additional arguments for the
unittest framework using the system property `integration.test.args`, for example, tests to execute
or verbosity level.

## Changing version

- Update the top-level `pom.xml` property `revision`; everything else should be derived from it.

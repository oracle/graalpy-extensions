## How to develop against the latest GraalPy, GraalVM SDK, and Truffle:

### Option 1: Use pre-built Maven bundle

There is script that downloads the latest Maven bundle from GraalVM GitHub EA releases page and unpacks it to
`.mvn/maven-bundle-${revision}`. The script also updates `.mvn/maven-bundle` to point to that versioned
directory. Maven automatically uses `.mvn/maven-bundle` as an additional repository when it exists. If the
bundle is not present the build ignores it and uses only the standard repositories.

```
./scripts/maven-bundle-setup.sh
./mvnw ...
```

To download a bundle for another version without changing the default `.mvn/maven-bundle` symlink, use:

```
./scripts/maven-bundle-setup.sh --version 25.0.0-SNAPSHOT
```

### Option 2: Build and install GraalPy/Truffle from sources

Checkout out and build the [GraalPy](https://github.com/oracle/graalpython)
following instructions in its documentation. We need to build all the necessary
dependencies. Make sure that you use the latest Java version that is supported,
otherwise multi-release jars will not be complete.

```
mx --dy /tools,/sdk,/truffle build
```

Run the `mx maven-deploy` (full command below) from the `graal/vm` directory.
Make sure to replace the version with what is the current development version
and keep the `-SNAPSHOT` suffix for better development experience.

Note: `-SNAPSHOT` versions are always re-checked and the newest snapshot is
always pulled from the repository by Maven unlike regular version, which are
assumed to be "immutable" and can be cached with no way to invalidate the cache
other than manually removing the artifacts from `~/.m2/repository/...`.

```
mx --dy /tools,/truffle,/graalpython maven-deploy --all-suites \
            --version-string 25.0.0-SNAPSHOT \
            --validate none
```

Since we did not specify repository, it will install the Maven artifacts into
your local repository (e.g., `~/.m2/repository`) and Maven projects can just
use the version `25.0.0-SNAPSHOT` without any extra configuration. Installation
to the local repository also omits the license checks.

## Structure

This repository is structured as a Maven multi-module project. There is also a Gradle project
for the Gradle plugin: `org.graalvm.python.gradle.plugin`, and for the `pyinterface` tool.

A Maven project and pom.xml exist for the Gradle plugin, but solely to delegate most of the
lifecycle tasks to Gradle. This allows you to run those tasks with a single Maven command.

Some subprojects contain standard JUnit tests, and there are also Python-driven integration tests.

To package/clean/install/test/etc. only one module, use, for example:

```
mvn -pl org.graalvm.python.gradle.plugin -am clean
```

### Integration tests

tl;dr:

```
mvn install exec:java@integration-tests -Dintegration.test.args="test_maven_plugin.py" -Dgradle.java.home=...
```

The integration tests are driven by Python and implemented using unittest framework, which is
part of Python standard library. The tests expect that the relevant Maven artifacts are available,
which can be achieved, for example, by `mvn install`. But they can also run on released artifacts
published in Mavencentral or some snapshot repository configured in Maven settings.

The whole execution of the tests is wrapped in Maven goal `exec:java@integration-tests`, which passes
some necessary arguments to the test driver Python script. One can pass additional arguments for the
unittest framework using system property `integration.test.args`, for example, tests to execute or
verbosity level.


## Changing version

- just update property `revision` in top level `pom.xml`, everything else should be derived from it

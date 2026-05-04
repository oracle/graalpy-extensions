# Agent Notes

## Maven Bundle Repository

The top-level `pom.xml` property `revision` is the source of truth for the
project version. The GraalPy/GraalVM dependency property
`project.polyglot.version` defaults to `${revision}`.

Some `revision` values are released to Maven Central. Pre-release versions may
instead require a downloaded Maven bundle. The repository convention is:

```text
.mvn/maven-bundle -> .mvn/maven-bundle-{revision}
```

Maven adds `.mvn/maven-bundle` as an additional repository only when that path
exists. If `.mvn/maven-bundle` is absent, the build must continue to use the
standard configured Maven repositories, so do not make scripts fail just because
the bundle is missing.

Install the bundle for the current `pom.xml` revision with:

```sh
./scripts/maven-bundle-setup.sh
```

This downloads into `.mvn/maven-bundle-{revision}` and updates the default
`.mvn/maven-bundle` symlink.

Download a bundle for another version with:

```sh
./scripts/maven-bundle-setup.sh --version 25.0.0-SNAPSHOT
```

When `--version` is used, the script downloads into
`.mvn/maven-bundle-25.0.0-SNAPSHOT` and deliberately does not update the
default `.mvn/maven-bundle` symlink.

Maven, recursive Maven invocations, helper scripts, pre-commit hooks, and GitHub
Actions should rely on the default `.mvn/maven-bundle` convention.

Gradle build and settings scripts should also use this convention. Because some
Gradle projects live in subdirectories their repository setup should scan upward
from the Gradle root or settings directory for `.mvn/maven-bundle`. Maven-to-Gradle
invocations and standalone Gradle builds should work without extra repository
arguments.

## Troubleshooting Stale Bundle Symlinks

If the build cannot resolve GraalPy/GraalVM artifacts, first check whether the
requested version is available from Maven Central. If it is not, check whether
`.mvn/maven-bundle` points at a bundle for a different `revision` than the
top-level `pom.xml` currently requests.

Check the requested version:

```sh
./mvnw help:evaluate -Dexpression=revision -q -DforceStdout
```

Check the default bundle link:

```sh
ls -l .mvn/maven-bundle
```

If the link target does not match `maven-bundle-{revision}`, rerun:

```sh
./scripts/maven-bundle-setup.sh
```

If the correct versioned bundle directory already exists and only the link is
wrong, repair it from the repository root:

```sh
rm -f .mvn/maven-bundle
ln -s maven-bundle-25.1.0-SNAPSHOT .mvn/maven-bundle
```

If `.mvn/maven-bundle` is a real non-empty directory rather than a symlink, move
it aside or remove it intentionally before rerunning the setup script. The setup
script should not silently replace a non-empty directory.

## Git Worktrees

For Git worktrees, reuse a versioned bundle directory from the primary checkout
or another shared location. In each worktree, `.mvn/maven-bundle` should be a
symlink to the appropriate `.mvn/maven-bundle-{revision}` directory.

If the shared bundle for a different version does not exist yet, download it
from a normal checkout using `--version` so the checkout's default symlink is
not changed:

```sh
./scripts/maven-bundle-setup.sh --version 25.1.0-SNAPSHOT
```

Avoid downloading duplicate GraalPy/GraalVM Maven bundles inside individual
worktrees.

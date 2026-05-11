# Agent Notes

## Development Documentation

General development guidance is documented in [DEVELOPMENT.md](DEVELOPMENT.md),
including Maven bundle setup, building GraalPy/GraalVM dependencies from
source, project structure, integration tests, and version changes.

Follow the `.mvn/maven-bundle` convention from
[DEVELOPMENT.md](DEVELOPMENT.md#option-1-use-pre-built-maven-bundle) for Maven,
Gradle, helper scripts, pre-commit hooks, and CI changes.

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

#!/bin/bash
if [ $# -eq 0 ]; then
    echo "Deploys the artifacts to a local repository"
    echo "Usage: $0 local-repository-path [additional Maven options]"
    exit 1
fi

source="${BASH_SOURCE[0]}"
while [ -h "$source" ] ; do
    prev_source="$source"
    source="$(readlink "$source")";
    if [[ "$source" != /* ]]; then
        # if the link was relative, it was relative to where it came from
        dir="$( cd -P "$( dirname "$prev_source" )" && pwd )"
        source="$dir/$source"
    fi
done
project_root="$( cd -P "$( dirname "$source" )/.." && pwd )"

cd "${project_root}"

set -ex
repo=$(readlink -f "$1")
shift
# Extract optional -Dgradle.java.home property from Maven args to forward to Gradle.
gradle_java_home=""
for arg in "$@"; do
  case "$arg" in
    -Dgradle.java.home=*)
      gradle_java_home="${arg#-Dgradle.java.home=}"
      ;;
  esac
done
./mvnw "$@" -DskipJavainterfacegen -DskipTests -DdeployAtEnd=true \
    -DaltDeploymentRepository=local::default::file:${repo} \
    deploy

# Also publish the pyinterfacegen artifacts (doclet and Gradle plugin) to the same local repo.
# We honor the same Java home used for Gradle, if provided in the environment.
gradle_java_arg=()
if [[ -n "${gradle_java_home}" ]]; then
  gradle_java_arg=(-Dorg.gradle.java.home="${gradle_java_home}")
elif [[ -n "${GRADLE_JAVA_HOME:-}" ]]; then
  gradle_java_arg=(-Dorg.gradle.java.home="${GRADLE_JAVA_HOME}")
elif [[ -n "${JAVA_HOME:-}" ]]; then
  gradle_java_arg=(-Dorg.gradle.java.home="${JAVA_HOME}")
fi
local_repo_url="file://${repo}"

pushd pyinterfacegen >/dev/null
  # Publish both the pyinterfacegen subprojects and the included gradle-plugin build
  ./gradlew "${gradle_java_arg[@]}" -PlocalRepoUrl="${local_repo_url}" publish gradle-plugin:publish -x test
popd >/dev/null

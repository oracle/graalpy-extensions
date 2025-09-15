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
repo=$(realpath "$1")
shift
./mvnw "$@" -DskipJavainterfacegen -DskipTests -DdeployAtEnd=true \
    -DaltDeploymentRepository=local::default::file:${repo} \
    deploy

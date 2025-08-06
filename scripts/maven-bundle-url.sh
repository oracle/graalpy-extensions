#!/bin/bash
set -xe
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

revision="$(mvn -f "${project_root}/pom.xml" help:evaluate -Dexpression=revision -q -DforceStdout)"
revision="${revision%-SNAPSHOT}" # remove -SNAPSHOT
revision_quoted_for_jq="${revision//./\\\\.}"

echo "Trying to find the release for revision: ${revision}"
curl -sSL "https://api.github.com/repos/graalvm/oracle-graalvm-ea-builds/releases" -o github_releases.json

echo "Downloaded releases JSON from GitHub, head:"
head -n 20 github_releases.json
echo "==========================================="

asset_url=$(cat github_releases.json \
    | jq -r 'map(select(.tag_name | test("'${revision_quoted_for_jq}'"))) | max_by(.published_at) | .assets[] | select(.name | test("^maven-resource-.*\\.zip$")) | .browser_download_url')
if [[ -z "$asset_url" ]]; then
  echo "Failed to find a maven-resource-bundle zip" >&2
  exit 1
fi
echo $asset_url
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

echo "Trying to find the release for revision: ${revision}"
curl -sSL "https://api.github.com/repos/graalvm/oracle-graalvm-ea-builds/releases" -o github_releases.json

echo "Downloaded releases JSON from GitHub, head:"
head -n 20 github_releases.json
echo "==========================================="

# Find the newest maven-resource-bundle ZIP whose name contains the desired revision.
# Scan all releases and their assets, guard against nulls, and pick the latest by published_at.
asset_url=$(
  jq -r --arg rev "$revision" '
    [ .[] as $rel
      | ($rel.assets // [])[]
      | select(
          (.name | startswith("maven-resource-bundle-")) and
          (.name | contains($rev)) and
          (.name | endswith(".zip"))
        )
      | {published_at: $rel.published_at, url: .browser_download_url}
    ]
    | sort_by(.published_at)
    | last?
    | .url // empty
  ' github_releases.json
)

rm github_releases.json
if [[ -z "$asset_url" ]]; then
  echo "Failed to find a maven-resource-bundle zip for revision ${revision}" >&2
  exit 1
fi
echo $asset_url

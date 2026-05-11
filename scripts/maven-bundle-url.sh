#!/bin/bash
set -euo pipefail
if [[ "${1:-}" == "--help" || "${1:-}" == "help" ]]; then
  echo "Usage: $0 [bundle-version]"
  echo "Prints the URL for the latest Maven bundle matching the given version."
  echo "When omitted, bundle-version defaults to revision from the top-level pom.xml."
  exit 0
fi
if [[ $# -gt 1 ]]; then
    echo "Usage: $0 [bundle-version]" >&2
    exit 1
fi
set -x
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

read_project_revision() {
    local pom_file="$1/pom.xml"
    sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' "${pom_file}" | head -n 1
}

if [[ -n "${1:-}" ]]; then
    revision="$1"
else
    revision="$(read_project_revision "${project_root}")"
    if [[ -z "${revision}" ]]; then
        echo "Failed to read <revision> from ${project_root}/pom.xml" >&2
        exit 1
    fi
fi
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

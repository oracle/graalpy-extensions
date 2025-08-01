#!/bin/bash
set -xe
if [[ $# -eq 0 || "$1" == "--help" || "$1" == "help" ]]; then
  echo "Usage: $0 <directory-where-to-download-the-bundle> [maven-bundle-url]"
  echo "Downloads and unpacks the latest Maven bundle according to the revision in top level pom.xml"
  echo "Generates settings.xml in the project root for the downloaded bundle (overriding any existing settings.xml)"
  echo "After that Maven can be run with the settings.xml using: mvn -s settings.xml ..."
  exit 0
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

revision="$(mvn -f "${project_root}/pom.xml" help:evaluate -Dexpression=revision -q -DforceStdout)"
revision="${revision%-SNAPSHOT}" # remove -SNAPSHOT
revision_quoted_for_jq="${revision//./\\\\.}"

echo "Trying to find the release for revision: ${revision}"
curl -sSL "https://api.github.com/repos/graalvm/oracle-graalvm-ea-builds/releases" -o github_releases.json

echo "Downloaded releases JSON from GitHub, head:"
head -n 20 github_releases.json
echo "==========================================="

if [ -z "$2" ]; then
    asset_url=$("${project_root}/scripts/maven-bundle-url" | tail -n 1)
else
    echo "Using given Maven bundle URL: $2"
    asset_url="$2"
fi

echo "Downloading: $asset_url"
curl -L -o maven-resource-bundle.zip "$asset_url"
unzip -q -o maven-resource-bundle.zip -d "$1"
rm maven-resource-bundle.zip
rm github_releases.json
"${project_root}/scripts/maven-bundle-create-settings.sh" "$(realpath "$1")"
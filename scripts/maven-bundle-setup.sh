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

if [ -z "$2" ]; then
    asset_url=$("${project_root}/scripts/maven-bundle-url.sh" | tail -n 1)
else
    echo "Using given Maven bundle URL: $2"
    asset_url="$2"
fi

echo "Downloading: $asset_url"
curl -L -o maven-resource-bundle.zip "$asset_url"
unzip -q -o maven-resource-bundle.zip -d "$1"
rm maven-resource-bundle.zip
"${project_root}/scripts/maven-bundle-create-settings.sh" "$(readlink -f "$1")"

#!/bin/bash
set -euo pipefail
usage() {
  echo "Usage: $0"
  echo "       $0 <bundle-version> [maven-bundle-url]"
  echo "       $0 --version <bundle-version> [maven-bundle-url]"
  echo "       $0 <maven-bundle-url>"
  echo "Downloads and unpacks a Maven bundle."
  echo "Without a bundle version, the version defaults to revision from top-level pom.xml,"
  echo "and .mvn/maven-bundle is updated to point to .mvn/maven-bundle-\${revision}."
  echo "With a bundle version, the bundle is unpacked to .mvn/maven-bundle-\${bundle-version},"
  echo "and .mvn/maven-bundle is not changed."
}

looks_like_bundle_url() {
    local value="$1"
    [[ "${value}" == *"://"* || "${value}" == *.zip || "${value}" == *.zip\?* ]]
}

read_project_revision() {
    local pom_file="$1/pom.xml"
    sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' "${pom_file}" | head -n 1
}

if [[ "${1:-}" == "--help" || "${1:-}" == "help" ]]; then
  usage
  exit 0
fi

bundle_version=""
asset_url=""
update_default_bundle_link=1
positionals=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --version)
            if [[ $# -lt 2 || -z "$2" ]]; then
                echo "--version requires a bundle version" >&2
                exit 1
            fi
            bundle_version="$2"
            update_default_bundle_link=0
            shift 2
            ;;
        --version=*)
            bundle_version="${1#--version=}"
            if [[ -z "${bundle_version}" ]]; then
                echo "--version requires a bundle version" >&2
                exit 1
            fi
            update_default_bundle_link=0
            shift
            ;;
        -*)
            echo "Unknown option: $1" >&2
            usage >&2
            exit 1
            ;;
        *)
            positionals+=("$1")
            shift
            ;;
    esac
done

if [[ "${#positionals[@]}" -gt 2 ]]; then
    usage >&2
    exit 1
fi

if [[ "${#positionals[@]}" -eq 1 ]]; then
    if [[ -n "${bundle_version}" ]] || looks_like_bundle_url "${positionals[0]}"; then
        asset_url="${positionals[0]}"
    else
        bundle_version="${positionals[0]}"
        update_default_bundle_link=0
    fi
elif [[ "${#positionals[@]}" -eq 2 ]]; then
    if [[ -n "${bundle_version}" ]]; then
        echo "Bundle version was specified more than once." >&2
        usage >&2
        exit 1
    fi
    if looks_like_bundle_url "${positionals[0]}"; then
        echo "When two positional arguments are provided, use bundle-version first and maven-bundle-url second." >&2
        usage >&2
        exit 1
    fi
    bundle_version="${positionals[0]}"
    update_default_bundle_link=0
    asset_url="${positionals[1]}"
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
if [[ -z "${bundle_version}" ]]; then
    bundle_version="$(read_project_revision "${project_root}")"
    if [[ -z "${bundle_version}" ]]; then
        echo "Failed to read <revision> from ${project_root}/pom.xml" >&2
        exit 1
    fi
fi
default_bundle_link="${project_root}/.mvn/maven-bundle"
default_bundle_dir="${default_bundle_link}-${bundle_version}"
mkdir -p "${default_bundle_dir}"
bundle_dir="$( cd -P "${default_bundle_dir}" && pwd )"

tmp_files=()
cleanup() {
    rm -f "${tmp_files[@]}"
}
trap cleanup EXIT

if [ -z "${asset_url}" ]; then
    asset_url=$("${project_root}/scripts/maven-bundle-url.sh" "${bundle_version}" | tail -n 1)
else
    echo "Using given Maven bundle URL: ${asset_url}"
fi

zip_file="$(mktemp "${TMPDIR:-/tmp}/maven-resource-bundle.XXXXXX.zip")"
tmp_files+=("${zip_file}")

echo "Downloading: $asset_url"
curl -L -o "${zip_file}" "$asset_url"
unzip -q -o "${zip_file}" -d "${bundle_dir}"

if [[ "${update_default_bundle_link}" -eq 1 ]]; then
    if [[ -e "${default_bundle_link}" && ! -L "${default_bundle_link}" ]]; then
        if ! rmdir "${default_bundle_link}" 2>/dev/null; then
            echo "Cannot update ${default_bundle_link}: it exists and is not a symlink." >&2
            echo "Move or remove it and rerun this script." >&2
            exit 1
        fi
    fi
    rm -f "${default_bundle_link}"
    ln -s "$(basename "${default_bundle_dir}")" "${default_bundle_link}"
fi

echo "Maven bundle unpacked to: ${bundle_dir}"
if [[ "${update_default_bundle_link}" -eq 1 ]]; then
    echo "Updated ${default_bundle_link} -> $(basename "${default_bundle_dir}")"
    echo "Maven will use ${default_bundle_link} automatically."
else
    echo "Did not update ${default_bundle_link}; a bundle version was specified."
fi

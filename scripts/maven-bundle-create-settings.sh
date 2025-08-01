#!/bin/bash
set -xe
if [[ $# -eq 0 || "$1" == "--help" || "$1" == "help" ]]; then
  echo "Usage: $0 <directory-with-maven-bundle>"
  echo "Creates settings.xml in the project root that configures given bundle as additional Maven repository"
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
cat > "${project_root}/settings.xml" <<EOF
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              https://maven.apache.org/xsd/settings-1.0.0.xsd">
  <profiles>
    <profile>
      <id>local-repo-profile</id>
      <pluginRepositories>
        <pluginRepository>
          <id>local-repo</id>
          <name>local-repo</name>
          <url>file://$1</url>
        </pluginRepository>
      </pluginRepositories>
      <repositories>
        <repository>
          <id>local-repo</id>
          <name>local-repo</name>
          <url>file://$1</url>
          <releases>
            <enabled>true</enabled>
          </releases>
          <snapshots>
            <enabled>true</enabled>
          </snapshots>
        </repository>
      </repositories>
    </profile>
  </profiles>
  <activeProfiles>
    <activeProfile>local-repo-profile</activeProfile>
  </activeProfiles>
</settings>
EOF
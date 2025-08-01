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

cd "${project_root}"
classpath="$(mvn -q -pl org.graalvm.python.embedding exec:exec -Dexec.executable="echo" -Dexec.args='%classpath')"
exec_args="-BootCP -Static -Mode bin -FileName ./org.graalvm.python.embedding/snapshot.sigtest -ClassPath ${classpath} -b -PackageWithoutSubpackages org.graalvm.python.embedding"
rc=0
mvn exec:java@sigtest-tool -Dexec.args="${exec_args}" || rc=$?
echo "Exit code from sigtest tool: ${rc}"
if [ $rc -ne 95 ]; then
  exit 1
else
  exit 0
fi
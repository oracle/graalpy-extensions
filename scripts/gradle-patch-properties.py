import sys
import os
import shutil
import subprocess

if len(sys.argv) < 2:
    print("Usage: python gradle-patch-properties.py <path_to_gradle.properties> [<path2>, ...]")
    print("Uses mx urlrewrites to patches distributionUrl in given Gradle properties file.")
    print("If mx is not available or no urlrewrite rule applies, does nothing.")
    sys.exit(1)

for properties_path in sys.argv[1:]:
    mx_exec = 'mx.cmd' if os.name == 'nt' else 'mx'
    if shutil.which(mx_exec) is None:
        print(f"mx executable not found, not rewriting distributionUrl in {properties_path}")
        sys.exit(0)

    try:
        with open(properties_path, 'r') as f:
            lines = f.readlines()
    except IOError:
        print(f"Error reading file: {properties_path}, not rewriting distributionUrl")
        sys.exit(1)

    found = False
    updated = False
    new_url = None
    for i, line in enumerate(lines):
        if line.strip().startswith('distributionUrl='):
            found = True
            old_url = line.split('=', 1)[1].strip()
            try:
                new_url = subprocess.check_output([mx_exec, 'urlrewrite', old_url]).decode('utf-8').strip()
                if new_url != old_url:
                    lines[i] = f'distributionUrl={new_url}\n'
                    updated = True
                else:
                    print(f"distributionUrl in {properties_path} is already set to {new_url}")
            except subprocess.CalledProcessError as e:
                print(f"Command `{mx_exec} urlrewrite {old_url}` failed: {e}")
                sys.exit(1)
            break

    if updated:
        try:
            with open(properties_path, 'w') as f:
                f.writelines(lines)
            print(f"Patched distributionUrl in '{properties_path}' to '{new_url}'")
            print("Do not commit this change")
        except IOError:
            print(f"Error writing file: {properties_path}, distributionUrl not updated according to mx urlrewrites")
            sys.exit(1)
    elif not found:
        print(f"Did not find 'distributionUrl' in {properties_path}")
        sys.exit(1)

# Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import os
import shutil
import subprocess
import sys
import unittest
import tempfile
from abc import ABC, abstractmethod
from typing import Optional

MAVEN_VERSION = "3.9.8"
GLOBAL_MVN_CMD = [shutil.which('mvn'), "--batch-mode"]

GRADLE_VERSION = "8.9"

DEFAULT_VFS_PREFIX = "org.graalvm.python.vfs"

graalvmVersion = None
jbang_graalpy_version = None
long_running_test_disabled = False
no_clean = False
test_native_image = True

def long_running_test(func):
    return unittest.skipIf(long_running_test_disabled, "passed option --skip-long-running")(func)

class TemporaryTestDirectory():
    def __init__(self):
        if no_clean:
            self.ctx = None
            self.name = tempfile.mkdtemp()
            print(f"Running test in {self.name}")
        else:
            self.ctx = tempfile.TemporaryDirectory()
            self.name = self.ctx.name

    def __enter__(self):
        return self.name

    def __exit__(self, exc_type, exc_val, exc_tb):
        if self.ctx:
            self.ctx.__exit__(exc_type, exc_val, exc_tb)

class LoggerBase(ABC):
    def log_block(self, name, text):
        self.log("=" * 80)
        self.log(f"==> {name}:")
        self.log(text)
        self.log("=" * 80)

    @abstractmethod
    def log(self, msg, newline=True):
        pass

class Logger(LoggerBase):
    def __init__(self):
        self.data = ''

    def log(self, msg, newline=True):
        self.data += msg + ('\n' if newline else '')

    def __str__(self):
        two_lines = ("=" * 80 + "\n") * 2
        return two_lines + "Test execution log:\n" + self.data + "\n" + two_lines

class NullLogger(LoggerBase):
    def log(self, msg, newline=True):
        pass

class StdOutLogger(LoggerBase):
    def __init__(self, delegate:LoggerBase):
        self.delegate = delegate

    def log(self, msg, newline=True):
        print(msg)
        self.delegate.log(msg, newline=newline)


class BuildToolTestBase(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.env = os.environ.copy()
        cls.env["PYLAUNCHER_DEBUG"] = "1"

        cls.archetypeGroupId = "org.graalvm.python"
        cls.archetypeArtifactId = "graalpy-archetype-polyglot-app"
        cls.pluginArtifactId = "graalpy-maven-plugin"
        cls.graalvmVersion = get_graalvm_version()


def run_cmd(cmd, env, cwd=None, print_out=False, logger:LoggerBase=NullLogger()):
    if print_out:
        logger = StdOutLogger(logger)
    out = []
    out.append(f"Executing:\n    {cmd=}\n")
    
    logger.log(f"Executing command: {' '.join(cmd)}")
    process = subprocess.Popen(cmd, env=env, cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, text=True, errors='backslashreplace')
    for line in iter(process.stdout.readline, ""):
        out.append(line)
    process.stdout.close()
    out_str = "".join(out)
    logger.log_block("output", out_str)
    return out_str, process.wait()


def check_ouput(txt, out, contains=True, logger: Optional[LoggerBase] =None):
    # if logger is passed, we assume that it already contains the output
    if contains and txt not in out:
        if not logger:
            print_output(out, f"expected '{txt}' in output")
        assert False, f"expected '{txt}' in output. \n{logger}"
    elif not contains and txt in out:
        if not logger:
            print_output(out, f"did not expect '{txt}' in output")
        assert False, f"did not expect '{txt}' in output. {logger}"


def print_output(out, err_msg=None):
    print("============== output =============")
    for line in out:
        print(line, end="")
    print("\n========== end of output ==========")
    if err_msg:
        print("", err_msg, "", sep="\n")


def get_mvn_wrapper(project_dir, env):
    cmd = "mvnw" if 'win32' != sys.platform else "mvnw.cmd"
    mvn_cmd = [os.path.join(project_dir, cmd),  "--batch-mode"]
    cmd = mvn_cmd + ["--version"]
    out, return_code = run_cmd(cmd, env, cwd=project_dir)
    check_ouput(MAVEN_VERSION, out)
    return mvn_cmd


def get_gradle_wrapper(project_dir, env, verbose=True):
    gradle_cmd = [os.path.abspath(os.path.join(project_dir, "gradlew" if 'win32' != sys.platform else "gradlew.bat"))]
    cmd = gradle_cmd + ["--version"]
    out, return_code = run_cmd(cmd, env, cwd=project_dir)
    check_ouput(GRADLE_VERSION, out)
    if verbose:
        return gradle_cmd + ["-i"]
    else:
        return gradle_cmd


def get_graalvm_version():
    return graalvmVersion


def get_executable(file):
    if os.path.isfile(file):
        return file
    exe = f"{file}.exe"
    if os.path.isfile(exe):
        return exe
    exe = f"{file}.cmd"
    if os.path.isfile(exe):
        return exe
    return None


def replace_in_file(file, str, replace_str):
    with open(file, "r") as f:
        contents = f.read()
    assert str in contents, f"cannot find '{str}' in file '{file}' with contents:\n {contents}\n------"
    with open(file, "w") as f:
        f.write(contents.replace(str, replace_str))


def replace_main_body(filename, new_main_body):
    with open(filename, "r") as f:
        lines = f.readlines()
    with open(filename, "w") as f:
        for l in lines:
            f.write(l)
            if 'public static void main(String[] args) {' in l:
                break
        f.write(new_main_body)
        f.write('    }\n')
        f.write('}\n')


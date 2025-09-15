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

import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import unittest

import util

# whole folder will be deleted after the tests finished
WORK_DIR = os.path.join(tempfile.gettempdir(),tempfile.mkdtemp())
JBANG_CMD = [os.environ.get('JBANG_CMD', 'jbang'), '--verbose']
ENV = os.environ.copy()
USE_SHELL = 'win32' == sys.platform

def run_cmd(cmd, cwd=None):
    print(f"\nExecuting: {cmd=}")
    env = os.environ.copy()
    env['GRAALPY_VERSION'] = util.jbang_graalpy_version
    process = subprocess.Popen(cmd, cwd=cwd, env=env, shell=USE_SHELL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, text=True, errors='backslashreplace')
    out = []
    print("============== output =============")
    for line in iter(process.stdout.readline, ""):
        print(line, end="")
        out.append(line)
    print("========== end of output ==========")
    return "".join(out), process.wait()

class TestJBangIntegration(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.clearCache()
        cls.catalog_file = cls.getCatalogFile()

    @classmethod
    def tearDownClass(cls):
        try:
            shutil.rmtree(WORK_DIR)
        except Exception as e:
            print(f"The test run correctly but problem during removing workdir: {e}")

    def setUp(self):
        self.tmpdir = tempfile.mkdtemp()

    def tearDown(self):
        try:
            shutil.rmtree(self.tmpdir)
        except Exception as e:
            print(f"The test run correctly but problem during removing workdir: {e}")

    @staticmethod
    def clearCache():
        command = JBANG_CMD + ["cache", "clear"]
        run_cmd(command)

    @staticmethod
    def getCatalogFile():
        catalog_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        return os.path.join(catalog_dir, 'jbang-catalog.json')

    def getCatalogData(self, catalog_file):
        try:
            with open(catalog_file, 'r') as json_file:
                json_data = json.load(json_file)

        except FileNotFoundError:
            self.fail(f"Catalog {catalog_file} was not found.")
        except json.JSONDecodeError:
            self.fail(f"Error during readinj JSON catalog {catalog_file}.")
        except Exception as e:
            self.fail(f"Error during reading catalog: {e}")
        return json_data

    def prepare_hello_example(self, work_dir):
        hello_java_file = os.path.join(work_dir, "hello.java")
        hello_template = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "org.graalvm.python.jbang", "catalog", "examples", "hello.java")
        self.prepare_template(hello_template, hello_java_file)
        return hello_java_file

    def prepare_template(self, template, target):
        shutil.copyfile(template, target)

    def test_register_catalog(self):
        alias = "graalpy_test_catalog_" + str(int(time.time()))

        # jbang checks catalog file sanity when adding
        command = JBANG_CMD + ["catalog", "add", "--name", alias, self.catalog_file]
        out, result = run_cmd(command, cwd=WORK_DIR)
        if result != 0:
            self.fail(f"Problem during registering catalog")

        command = JBANG_CMD + ["catalog", "remove", alias]
        out, result = run_cmd(command, cwd=WORK_DIR)
        if result != 0:
            self.fail(f"Problem during removing catalog")

    def test_catalog(self):
        json_data = self.getCatalogData(self.catalog_file)
        for alias in json_data.get("aliases", {}).values():
            script_ref = alias.get("script-ref")
            script_path = os.path.normpath(os.path.join(os.path.dirname(self.catalog_file), script_ref))
            self.assertTrue(os.path.isfile(script_path), f"The path defined in catalog is not found: {script_path}")

        for template in json_data.get("templates", {}).values():
            for file_ref in template.get("file-refs", {}).values():
                file_path = os.path.normpath(os.path.join(os.path.dirname(self.catalog_file), file_ref))
                self.assertTrue(os.path.isfile(file_path), f"The path definied in catalog is not found: {file_path}")

    def test_graalpy_template(self):
        template_name = "graalpy"
        test_file = "graalpy_test.java"
        work_dir = self.tmpdir

        command = JBANG_CMD + ["init", f"--template={template_name}@{self.catalog_file}" , test_file]
        out, result = run_cmd(command, cwd=work_dir)
        self.assertTrue(result == 0, f"Creating template {template_name} failed")

        test_file_path = os.path.join(work_dir, test_file)
        tested_code = "from termcolor import colored; print(colored('hello java', 'red', attrs=['reverse', 'blink']))"
        command = JBANG_CMD + [ test_file_path, tested_code]
        out, result = run_cmd(command, cwd=work_dir)

        self.assertTrue(result == 0, f"Execution failed with code {result}\n    command: {command}\n    stdout: {out}\n")
        self.assertIn("Successfully installed termcolor", out)
        self.assertIn("hello java", out)

    @util.skip_on_windows("Currently the jbang native image on Win gate fails.")
    def test_graalpy_template_native(self):
        if not util.native_image_all():
            self.skipTest("native-image tests disabled in smoke mode")

        template_name = "graalpy"
        test_file = "graalpy_test.java"
        work_dir = self.tmpdir

        command = JBANG_CMD + ["init", f"--template={template_name}@{self.catalog_file}" , test_file]
        out, result = run_cmd(command, cwd=work_dir)
        self.assertTrue(result == 0, f"Creating template {template_name} failed")

        test_file_path = os.path.join(work_dir, test_file)
        tested_code = "from termcolor import colored; print(colored('hello java', 'red', attrs=['reverse', 'blink']))"
        command = JBANG_CMD + ["--native", test_file_path, tested_code]
        out, result = run_cmd(command, cwd=work_dir)

        self.assertEqual(0, result, f"command: {command}\n    stdout: {out}")
        self.assertIn("Successfully installed termcolor", out)
        self.assertIn("hello java", out)

    def test_hello_example(self):
        work_dir = self.tmpdir
        hello_java_file = self.prepare_hello_example(work_dir)

        tested_code = "print('hello java')"
        command = JBANG_CMD + [hello_java_file, tested_code]
        out, result = run_cmd(command, cwd=work_dir)

        self.assertEqual(0, result, f"command: {command}\n    stdout: {out}")
        self.assertIn("Successfully installed termcolor", out)
        self.assertIn("hello java", out)

        if not 'win32' in sys.platform and util.native_image_smoke():
            command = JBANG_CMD + ["--native", hello_java_file, tested_code]
            out, result = run_cmd(command, cwd=work_dir)

            self.assertEqual(0, result, f"command: {command}\n    stdout: {out}")
            self.assertIn("Successfully installed termcolor", out)
            self.assertIn("hello java", out)

    @util.skip_on_windows("ujson installation broken on Windows (GR-69683)")
    def test_external_dir(self):
        work_dir = self.tmpdir
        hello_java_file = self.prepare_hello_example(work_dir)

        # patch hello.java file to use external dir for resources
        resources_dir = os.path.join(work_dir, "python-resources")
        src_dir = os.path.join(resources_dir, "src")
        os.makedirs(src_dir, exist_ok=True)
        with open(os.path.join(src_dir, "hello.py"), "w", encoding="utf-8") as f:
            f.writelines("""
from termcolor import colored
def hello():
    print(print(colored('hello java', 'red', attrs=['reverse', 'blink'])))
                             """)
        util.replace_in_file(hello_java_file,
                "//PIP termcolor==2.2",
                f"//PIP termcolor==2.2\n//PYTHON_RESOURCES_DIRECTORY {resources_dir}")
        rd = resources_dir.replace("\\", "\\\\")
        util.replace_in_file(hello_java_file,
                "GraalPyResources.createContext()",
                f"GraalPyResources.contextBuilder(java.nio.file.Path.of(\"{rd}\")).build()")

        tested_code = "import hello; hello.hello()"
        command = JBANG_CMD + [hello_java_file, tested_code]
        out, result = run_cmd(command, cwd=work_dir)

        self.assertEqual(0, result, f"command: {command}\n    stdout: {out}")
        self.assertIn("Successfully installed termcolor", out)
        self.assertIn("hello java", out)

        # add ujson to PIP comment
        util.replace_in_file(hello_java_file,
                "//PIP termcolor==2.2",
                "//PIP termcolor==2.2 ujson")
        tested_code = "import hello; hello.hello()"
        command = JBANG_CMD + [hello_java_file, tested_code]
        out, result = run_cmd(command, cwd=work_dir)

        self.assertEqual(0, result, f"command: {command}\n    stdout: {out}")
        self.assertIn("Successfully installed ujson", out)
        self.assertNotIn("Successfully installed termcolor", out)
        self.assertIn("hello java", out)

        # remove ujson from PIP comment
        util.replace_in_file(hello_java_file,
                "//PIP termcolor==2.2 ujson",
                "//PIP termcolor==2.2\n")
        tested_code = "import hello; hello.hello()"
        command = JBANG_CMD + [hello_java_file, tested_code]
        out, result = run_cmd(command, cwd=work_dir)

        self.assertEqual(0, result, f"command: {command}\n    stdout: {out}")
        self.assertNotIn("ujson", out)
        self.assertIn("Successfully installed termcolor", out)
        self.assertIn("hello java", out)

        # add ujson in additional PIP comment
        util.replace_in_file(hello_java_file,
                "//PIP termcolor==2.2",
                "//PIP termcolor==2.2\n//PIP ujson")
        tested_code = "import hello; hello.hello()"
        command = JBANG_CMD + [hello_java_file, tested_code]
        out, result = run_cmd(command, cwd=work_dir)

        self.assertEqual(0, result, f"command: {command}\n    stdout: {out}")
        self.assertIn("Successfully installed ujson", out)
        self.assertNotIn("Successfully installed termcolor", out)
        self.assertIn("hello java", out)

        if not 'win32' in sys.platform and util.native_image_all():
            command = JBANG_CMD + ["--native", hello_java_file, tested_code]
            out, result = run_cmd(command, cwd=work_dir)

            self.assertEqual(0, result, f"command: {command}\n    stdout: {out}")
            self.assertNotIn("Successfully installed ujson", out)
            self.assertNotIn("Successfully installed termcolor", out)
            self.assertIn("hello java", out)

    def check_empty_comments(self, work_dir, java_file):
        command = JBANG_CMD + [java_file]
        out, result = run_cmd(command, cwd=work_dir)
        self.assertEqual(0, result, f"command: {command}\n    stdout: {out}")
        self.assertNotIn("[graalpy jbang integration]", out)

    def test_malformed_tag_formats(self):
        jbang_templates_dir = os.path.join(os.path.dirname(__file__), "jbang")
        work_dir = self.tmpdir

        java_file = os.path.join(work_dir, "EmptyPIPComments.java")
        self.prepare_template(os.path.join(jbang_templates_dir, "EmptyPIPComments.j"), java_file)
        self.check_empty_comments(work_dir, java_file)

        java_file = os.path.join(work_dir, "EmptyPythonResourceComment.java")
        self.prepare_template(os.path.join(jbang_templates_dir, "EmptyPythonResourceComment.j"), java_file)
        self.check_empty_comments(work_dir, java_file)

        java_file = os.path.join(work_dir, "EmptyPythonResourceCommentWithBlanks.java")
        self.prepare_template(os.path.join(jbang_templates_dir, "EmptyPythonResourceCommentWithBlanks.j"), java_file)
        self.check_empty_comments(work_dir, java_file)

    def test_no_pkgs_but_resource_dir(self):
        jbang_templates_dir = os.path.join(os.path.dirname(__file__), "jbang")
        work_dir = self.tmpdir

        java_file = os.path.join(work_dir, "NoPackagesResourcesDir.java")
        self.prepare_template(os.path.join(jbang_templates_dir, "NoPackagesResourcesDir.j"), java_file)
        command = JBANG_CMD + [java_file]
        out, result = run_cmd(command, cwd=work_dir)
        self.assertEqual(0, result, f"command: {command}\n    stdout: {out}")
        self.assertNotIn("[graalpy jbang integration] python packages", out)
        self.assertIn("[graalpy jbang integration] python resources directory: python-resources", out)
        self.assertNotIn("-m ensurepip", out)
        self.assertNotIn("pip install", out)

    def test_two_resource_dirs(self):
        jbang_templates_dir = os.path.join(os.path.dirname(__file__), "jbang")
        work_dir = self.tmpdir

        java_file = os.path.join(work_dir, "TwoPythonResourceComments.java")
        self.prepare_template(os.path.join(jbang_templates_dir, "TwoPythonResourceComments.j"), java_file)
        command = JBANG_CMD + [java_file]
        out, result = run_cmd(command, cwd=work_dir)
        self.assertEqual(1, result, f"command: {command}\n    stdout: {out}")
        self.assertIn("only one //PYTHON_RESOURCES_DIRECTORY comment is allowed", out)

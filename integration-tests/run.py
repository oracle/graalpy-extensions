# Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

if __name__ == "__main__":
    import unittest
    import sys
    import os
    import argparse
    import util

    os.chdir(os.path.dirname(__file__))

    parser = argparse.ArgumentParser(description="Sets up the testing harness and then forwards remaining arguments to the stdlib unittest module.",
                                     formatter_class=argparse.RawDescriptionHelpFormatter,
                                     epilog="If executed via Maven: \n"
                                             "    Arguments are passed as `-Dintegration.tests.args='--no-clean --skip-long-running'`.\n"
                                             "    Maven passes --graalpy-version and --gradle-java-home automatically. Gradle Java home is taken from -Dgradle.java.home=...")
    parser.add_argument('--graalpy-version', help='The version of GraalPy and other Maven artifacts to use', required=True)
    parser.add_argument('--skip-native-image', action='store_true', help='Skips tests that build projects with GraalVM Native Image (TODO: not fully honored by all tests)')
    parser.add_argument('--skip-long-running', action='store_true', help='Skips long running tests')
    parser.add_argument('--no-clean', action='store_true', help='Do not clean the test temporary directories (for post-mortem debugging)')
    parser.add_argument('--jbang-graalpy-version', help='GraalPy version to use for JBang tests, overrides --graalpy-version')
    parser.add_argument('--gradle-java-home', default=os.environ['JAVA_HOME'], help='Java to be used to run Gradle (by default $JAVA_HOME)')
    parser.add_argument('--help-unittest', action='store_true', help='Print help of the stdlib unittest CLI and exits')
    args, remaining_args = parser.parse_known_args()

    if args.help_unittest:
        unittest.main(argv=[sys.argv[0], '--help'], module=None, exit=True)

    if 'JAVA_HOME' not in os.environ:
        print("WARNING: JAVA_HOME not in environment.\n")
    elif not args.skip_native_image:
        suffix = '.exe' if sys.platform == 'win32' else ''
        if not os.path.exists(os.path.join(os.environ['JAVA_HOME'], 'bin', 'native-image' + suffix)):
            print("WARNING: JAVA_HOME is not a GraalVM distribution. Tests using Native Image will fail.\n")

    util.graalvmVersion = args.graalpy_version
    util.long_running_test_disabled = args.skip_long_running
    util.no_clean = args.no_clean
    util.test_native_image = not args.skip_native_image
    util.jbang_graalpy_version = args.jbang_graalpy_version if args.jbang_graalpy_version else args.graalpy_version
    util.gradle_java_home = args.gradle_java_home

    unittest.main(argv=[sys.argv[0]] + remaining_args, module=None, exit=True)
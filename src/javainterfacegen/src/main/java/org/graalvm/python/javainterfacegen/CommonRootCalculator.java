/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.python.javainterfacegen;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CommonRootCalculator {

    public static Path findCommonRoot(List<Path> paths) {
        Path commonRoot = null;
        if (!(paths == null || paths.isEmpty())) {
            commonRoot = paths.get(0);
            for (int i = 1; i < paths.size(); i++) {
                commonRoot = getCommonPath(commonRoot, paths.get(i));
            }
        }
        return commonRoot;
    }

    private static Path getCommonPath(Path path1, Path path2) {
        int minLength = Math.min(path1.getNameCount(), path2.getNameCount());
        Path commonPath = path1.isAbsolute() ? path1.getRoot() : path2.isAbsolute() ? path2.getRoot() :Paths.get("");

        for (int i = 0; i < minLength; i++) {
            if (path1.getName(i).equals(path2.getName(i))) {
                commonPath = commonPath.resolve(path1.getName(i));
            } else {
                break;
            }
        }

        return commonPath;
    }

}

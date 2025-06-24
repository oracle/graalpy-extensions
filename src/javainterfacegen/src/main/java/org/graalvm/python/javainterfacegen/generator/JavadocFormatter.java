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
package org.graalvm.python.javainterfacegen.generator;

public class JavadocFormatter {

    public static String removeLeadingSpaces(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String[] parts = text.split("\n");
//        char ch = parts[0].charAt(0);
//        String line = parts[0];
//
//        int lineLen = line.length();
//        line = line.st
//        int lineLen2 = line
//
//
        StringBuilder result = new StringBuilder();
        boolean firstLineDone = false;
        boolean secondLineDone = false;
        String line;
        String leadingSpaces="";
        for (int i = 0; i < parts.length; i++) {
            line = parts[i];
            if (!firstLineDone) {
                line = line.trim();
                if (!line.isEmpty()) {
                    result.append(line);
                    firstLineDone = true;
                }
            } else if (!secondLineDone) {
                int origLen = line.length();
                line = line.stripLeading();
                if (!line.isEmpty()) {
                    leadingSpaces = parts[i].substring(0, origLen - line.length());
                    secondLineDone = true;
                    result.append('\n').append(line.stripTrailing());
                } else if (i < parts.length -1) {
                    result.append('\n');
                }
            } else {
                if (!leadingSpaces.isEmpty() && line.startsWith(leadingSpaces)) {
                    line = line.substring(leadingSpaces.length());
                }
                result.append('\n').append(line.stripTrailing());
            }
        }
        return result.toString().stripTrailing();
    }
}

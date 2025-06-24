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
package org.graalvm.python.javainterfacegen.generator.impl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.generator.GeneratorFactory;
import org.graalvm.python.javainterfacegen.generator.JavadocGenerator;
import org.graalvm.python.javainterfacegen.generator.JavadocStorageManager;
import org.graalvm.python.javainterfacegen.mypy.nodes.ClassDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.DefaultNodeVisitor;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.MypyFile;
import org.graalvm.python.javainterfacegen.mypy.nodes.Node;
import org.graalvm.python.javainterfacegen.mypy.nodes.Var;

public class JavadocGeneratorImpl implements JavadocGenerator {

    private static class FQNProvider extends DefaultNodeVisitor<String> {

        @Override
        protected String defaultVisit(Node node) {
            return "";
        }

        @Override
        public String visit(MypyFile file) {
            return file.getName();
        }

        @Override
        public String visit(ClassDef classDef) {
            return classDef.getFullname();
        }

        @Override
        public String visit(FuncDef funcDef) {
            return funcDef.getFullname();
        }

        @Override
        public String visit(Var v) {
            return v.fullname();
        }


    }

    @Override
    public String create(Node node, GeneratorContext context) {
        String fqn = node.accept(new FQNProvider());

        String managerClass =  context.getConfig().getJavadocStorageManager(context);
        JavadocStorageManager manager = GeneratorFactory.getJavadocStorageManager(managerClass);
        FileInputStream input;
        try {
            input = new FileInputStream(manager.getStoragePath(context).toFile());
            return format(manager.load(input).get(fqn));
        } catch (FileNotFoundException ex) {
            // no javadoc for the file?
        }

        return null;
    }


    public String format(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        boolean inCodeBlock = false;
        int whiteSpaceCount = 0;
        int possibleCodeBlockEnd = -1;
        for (String line : lines) {
            int lineLen = line.length();
            String stripLine = line.stripLeading();
            if (stripLine.startsWith(">")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    whiteSpaceCount = lineLen - stripLine.length();
                    sb.append("<pre>\n");
                }
            }
            if (inCodeBlock && !line.trim().isEmpty()) {
                if (whiteSpaceCount < lineLen && !line.substring(0, whiteSpaceCount).isBlank()) {
                    inCodeBlock = false;
                    sb.insert(possibleCodeBlockEnd, "</pre>\n");
                } else {
                    possibleCodeBlockEnd = sb.length() + lineLen + 1;
                }
            }
            if (!inCodeBlock) {
                if(line.isBlank()) {
                    sb.append("<p>\n");
                } else {
                    if (stripLine.startsWith("-")) {
                        for(int i = 0; i < lineLen - stripLine.length(); i++) {
                            sb.append("&nbsp;");
                        }
                        sb.append(line).append("<br/>\n");
                    } else {
                        sb.append(line).append("\n");
                    }
                }
            } else {
                sb.append(line).append("\n");
            }

        }
        if (inCodeBlock) {
            sb.append("\n</pre>");
        }
        return sb.toString();
    }

}

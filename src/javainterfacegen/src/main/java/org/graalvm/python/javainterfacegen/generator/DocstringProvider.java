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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.graalvm.python.javainterfacegen.mypy.nodes.ClassDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.DefaultNodeVisitor;
import org.graalvm.python.javainterfacegen.mypy.nodes.ExpressionStmt;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.MypyFile;
import org.graalvm.python.javainterfacegen.mypy.nodes.Node;
import org.graalvm.python.javainterfacegen.mypy.nodes.Statement;
import org.graalvm.python.javainterfacegen.mypy.nodes.StrExpr;
import org.graalvm.python.javainterfacegen.mypy.nodes.SymbolTableNode;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeInfo;

public class DocstringProvider extends DefaultNodeVisitor<Map<String, String>> {

    @Override
    protected Map<String, String> defaultVisit(Node node) {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, String> visit(ClassDef classDef) {
        Map<String, String> result = new HashMap();
        if (classDef.getDocstring() != null) {
            result.put(classDef.getFullname(), JavadocFormatter.removeLeadingSpaces(classDef.getDocstring()));
        }
        TypeInfo typeInfo = (TypeInfo)classDef.getInfo();
        Map<String, SymbolTableNode> symbolTable = typeInfo.getNames().getTable();
        for (Map.Entry<String, SymbolTableNode> entry : symbolTable.entrySet()) {
            String key = entry.getKey();
            if ("__init__".equals(key) || !key.startsWith("_")) {
                SymbolTableNode tableNode = entry.getValue();
                result.putAll(tableNode.getNode().accept(this));
            }
        }
        return result;
    }

    @Override
    public Map<String, String> visit(FuncDef funcDef) {
        if (funcDef.getDocstring() != null) {
            Map<String, String> result = new HashMap();
            result.put(funcDef.getFullname(), JavadocFormatter.removeLeadingSpaces(funcDef.getDocstring()));
            return result;
        }
        return Collections.EMPTY_MAP;
    }

    @Override
    public Map<String, String> visit(MypyFile file) {
        Map<String, String> result = new TreeMap();
        String fileFullName = file.getFullname();
        List<Statement> defs = file.getDefs();
        if (defs.isEmpty()) {
            return Collections.EMPTY_MAP;
        }
        System.out.println("d%%%%%%%%%%%%%%%%%%%%%%%% " + defs.get(0).toString());
        if (defs.get(0) instanceof ExpressionStmt expr) {
            if (expr.getExpr() instanceof StrExpr strExpr) {
                System.out.println(strExpr.getText());
                result.put(file.getName(), strExpr.getText());
            }
        }

        Map<String, SymbolTableNode> symbolTable = file.getNames().getTable();

        for (Map.Entry<String, SymbolTableNode> entry : symbolTable.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("_")) {
                SymbolTableNode tableNode = entry.getValue();
                if (tableNode.getFullname().startsWith(fileFullName)) {
                    if (!(tableNode.getNode() instanceof MypyFile)) {
                        result.putAll(tableNode.getNode().accept(this));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public Map<String, String> visit(TypeInfo typeInfo) {
        return typeInfo.getDefn().accept(this);
    }


}

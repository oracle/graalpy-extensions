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

package org.graalvm.python.javainterfacegen.mypy.nodes;

import java.util.List;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.python.GuestArray;
import org.graalvm.python.javainterfacegen.python.Utils;


public interface MypyFile extends SymbolNode {

    public static final String FQN = "mypy.nodes.MypyFile";

    static class MypyFileImpl extends SymbolNode.SymbolNodeImpl implements MypyFile {

        private GuestArray<Statement> defs = null;

        public MypyFileImpl(Value instance) {
            super(instance);
        }

        @Override
        public String getName() {
            Value result = getValue().getMember("name");
            if (result != null) {
                return result.asString();
            }
            System.out.println("@@@@File name is null: " + getValue().toString());
            return null;
        }

        @Override
        public String getPath() {
            Value path = getValue().getMember("path");
            if (path == null) {
                return null;
            }
            return path.asString();
        }

        @Override
        public String getFullname() {
            Value fullName = getValue().getMember("fullname");
            return fullName != null ? fullName.asString() : null;
        }

        @Override
        public <T> T accept(NodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public GuestArray<Statement> getDefs() {
            if (defs == null) {
                Value orig = getValue().getMember("defs");
                GuestArray<Statement> result = new GuestArray<>(orig, (value) -> {
                    String pythonFQN = Utils.getFullyQualifedName( value);
                    switch (pythonFQN){
                        case FuncDef.FQN:
                            return new FuncDef.FuncDefImpl(value);
                        case ClassDef.FQN:
                            return new ClassDef.ClassDefImpl(value);
                        case ExpressionStmt.FQN:
                            return new ExpressionStmt.ExpressionStmtImpl(value);
                        case Import.FQN:
                            return new Import.ImportImpl(value);
                        case ImportFrom.FQN:
                            return new ImportFrom.ImportFromImpl(value);
                        case ImportAll.FQN:
                            return new ImportAll.ImportAllImpl(value);
                        case AssignmentStmt.FQN:
                            return new AssignmentStmt.AssignmentStmtImpl(value);
                        case TryStmt.FQN:
                            return new TryStmt.TryStmtImpl(value);
                    }
                    throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
                });
                defs = result;
            }
            return defs;
        }

        @Override
        public SymbolTable getNames() {
            Value table = getValue().getMember("names");

            String pythonFQN = Utils.getFullyQualifedName(table);
            switch (pythonFQN){
                case SymbolTable.FQN :
                    return new SymbolTable.SymbolTableImpl(table);
            }
            throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
        }

        @Override
        public boolean isPackageInitFile() {
            return getValue().invokeMember("is_package_init_file").asBoolean();
        }

    }

    String getName();
    String getFullname();
    String getPath();
    List<Statement> getDefs();

    SymbolTable getNames();

    boolean isPackageInitFile();

}

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
import org.graalvm.python.javainterfacegen.python.Utils;


public interface ClassDef extends Statement {

    public static final String FQN = "mypy.nodes.ClassDef";

    static class ClassDefImpl extends Statement.StatementImpl implements ClassDef {


        public ClassDefImpl(Value instance) {
            super(instance);
            String instanceFQN = Utils.getFullyQualifedName(instance);
            if (!FQN.equals(instanceFQN)) {
                throw new UnsupportedOperationException("Can not create new ClassDefImpl from Guest instance " + instanceFQN);
            }
        }

        @Override
        public String getName() {
            return getValue().getMember("name").asString();
        }

        @Override
        public String getFullname() {
            return getValue().getMember("fullname").asString();
        }

        @Override
        public boolean isGeneric() {
            return getValue().invokeMember("is_generic").asBoolean();
        }

        @Override
        public Block getDefs() {
            return new Block.BlockImpl(getValue().getMember("defs"));
        }

        @Override
        public Value getTypeArgs() {
            return getValue().getMember("type_args");
        }

        @Override
        public Value getTypeVars() {
            return getValue().getMember("type_vars");
        }

        @Override
        public Value getBaseTypeExprs() {
            return getValue().getMember("base_type_exprs");
        }

        @Override
        public Value getRemovedBaseTypeExprs() {
            return getValue().getMember("removed_base_type_exprs");
        }

        @Override
        public TypeInfo getInfo() {

            return new TypeInfo.TypeInfoImpl(getValue().getMember("info"));
        }

        @Override
        public Value getMetaclass() {
            return getValue().getMember("metaclass");
        }

        @Override
        public Value getDecorators() {
            return getValue().getMember("decorators");
        }

        @Override
        public Value getKeywords() {
            return getValue().getMember("keywords");
        }

        @Override
        public Value getAnalyzed() {
            return getValue().getMember("analyzed");
        }

        @Override
        public boolean hasIncompatibleBaseclass() {
            return getValue().getMember("has_incompatible_baseclass").asBoolean();
        }

        @Override
        public String getDocstring() {
            return getValue().getMember("docstring").asString();
        }

        @Override
        public Value getRemovedStatements() {
            return getValue().getMember("removed_statements");
        }

        @Override
        public <T> T accept(NodeVisitor<T> visitor) {
            return visitor.visit(this);
        }
    }

    String getName();
    String getFullname();
    boolean isGeneric();
    Block getDefs();
    Value getTypeArgs();
    Value getTypeVars();
    Value getBaseTypeExprs();
    Value getRemovedBaseTypeExprs();
    TypeInfo getInfo();
    Value getMetaclass();
    Value getDecorators();
    Value getKeywords();
    Value getAnalyzed();
    boolean hasIncompatibleBaseclass();
    String getDocstring();
    Value getRemovedStatements();

}

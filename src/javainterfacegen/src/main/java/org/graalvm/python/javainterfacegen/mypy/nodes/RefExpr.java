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

import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.python.Utils;

public interface RefExpr extends Expression {

    public static final String FQN = "mypy.nodes.RefExpr";

    static class RefExprImpl extends Expression.ExpressionImpl implements RefExpr {


        public RefExprImpl(Value instance) {
            super(instance);
        }

        @Override
        public Integer getKind() {
            Value value = getValue().getMember("kind");
            if (value != null) {
                return value.asInt();
            }
            return null;
        }

        @Override
        public SymbolNode getNode() {
            Value orig = getValue().getMember("node");

            String pythonFQN = Utils.getFullyQualifedName(orig);
            switch (pythonFQN){
                case Var.FQN:
                    return new Var.VarImpl(orig);
            }
            throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
        }

        @Override
        public String getFullName() {
            return getValue().getMember("_fullname").asString();
        }

        @Override
        public boolean isNewDef() {
            return getValue().getMember("is_new_def").asBoolean();
        }

        @Override
        public boolean isInferredDef() {
            return getValue().getMember("is_inferred_def").asBoolean();
        }

        @Override
        public boolean isAliasRvalue() {
            return getValue().getMember("is_alias_rvalue").asBoolean();
        }

        @Override
        public Value getTypeGuard() {
            return getValue().getMember("type_guard");
        }

        @Override
        public Value getTypeIs() {
            return getValue().getMember("type_is");
        }
    }

    Integer getKind();
    SymbolNode getNode();
    String getFullName();
    boolean isNewDef();
    boolean isInferredDef();
    boolean isAliasRvalue();
    Value getTypeGuard();
    Value getTypeIs();

}

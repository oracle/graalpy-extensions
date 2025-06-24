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
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.ProperType;
import org.graalvm.python.javainterfacegen.python.GuestValueDefaultImpl;
import org.graalvm.python.javainterfacegen.python.Utils;


public interface FuncDef extends FuncItem, SymbolNode, Statement {
    public static final String FQN = "mypy.nodes.FuncDef";

    static class FuncDefImpl extends FuncItem.FuncItemImpl implements FuncDef {

        public FuncDefImpl(Value instance) {
            super(instance);
            if (instance == null) {
                throw new UnsupportedOperationException("Can not create new FuncDefImpl from Guest instance null");
            }
            String instanceFQN = Utils.getFullyQualifedName(instance);
            if (!FQN.equals(instanceFQN)) {
                throw new UnsupportedOperationException("Can not create new FuncDefImpl from Guest instance " + instanceFQN);
            }
        }

        @Override
        public String getName() {
            return getValue().getMember("name").asString();
        }

        @Override
        public boolean isDecorated() {
            return getValue().getMember("is_decorated").asBoolean();
        }


        @Override
        public List<Argument> getArguments() {
            return FuncItem.getArgumentsImpl(getValue());
        }

        @Override
        public List<String> getArgNames() {
            return FuncItem.getArgNames(getValue());
        }



        @Override
        public List<TypeParam> getTypeArgs() {
            return FuncItem.getTypeArgs(getValue());
        }



        @Override
        public ProperType getType() {
            return FuncBase.getTypeImpl(this);
        }



        @Override
        public <T> T accept(NodeVisitor<T> visitor) {
            return visitor.visit(this);
        }


        @Override
        public String fullname() {
            // TODO this is wrong, fullname is defined in SymbolNode and we should
            // share the implementation
            return getValue().getMember("fullname").asString();
//            System.out.println(Utils.dir(getValue()));
//            return "ahoj";
        }

        @Override
        public boolean isConditional() {
            return getValue().getMember("is_conditional").asBoolean();
        }

        @Override
        public Value getAbstractStatus() {
            return getValue().getMember("abstract_status");
        }

        @Override
        public Value getOriginalDef() {
            return getValue().getMember("original_def");
        }

        @Override
        public boolean isTrivialBody() {
            return getValue().getMember("is_trivial_body").asBoolean();
        }

        @Override
        public boolean isMypyOnly() {
            return getValue().getMember("is_mypy_only").asBoolean();
        }

        @Override
        public Value getDataclassTransformSpec() {
            return getValue().getMember("dataclass_transform_spec");
        }

        @Override
        public String getDocstring() {
            return getValue().getMember("docstring").asString();
        }

    }

    String getName();
    boolean isDecorated();
    boolean isConditional();
    Value getAbstractStatus();
    Value getOriginalDef();
    boolean isTrivialBody();
    boolean isMypyOnly();
    Value getDataclassTransformSpec();
    String getDocstring();


}

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
import org.graalvm.python.javainterfacegen.mypy.types.AnyType;
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.Instance;
import org.graalvm.python.javainterfacegen.mypy.types.NoneType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.AnyTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.CallableTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.InstanceImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.NoneTypeImpl;
import org.graalvm.python.javainterfacegen.python.GuestValue;
import org.graalvm.python.javainterfacegen.python.GuestValueDefaultImpl;
import org.graalvm.python.javainterfacegen.python.Utils;

public interface SymbolTableNode extends GuestValue {

    public static final String FQN = "mypy.nodes.SymbolTableNode";

    static class SymbolTableNodeImpl extends GuestValueDefaultImpl implements SymbolTableNode {

        public SymbolTableNodeImpl(Value instance) {
            super(instance);
        }

        @Override
        public int getKind() {
            return getValue().getMember("kind").asInt();
        }

        @Override
        public SymbolNode getNode() {
            Value node = getValue().getMember("node");

            String pythonFQN = Utils.getFullyQualifedName(node);
            switch (pythonFQN){
                case Var.FQN:
                    return new Var.VarImpl(node);
                case TypeInfo.FQN:
                    return new TypeInfo.TypeInfoImpl(node);
                case MypyFile.FQN:
                    return new MypyFile.MypyFileImpl(node);
                case FuncDef.FQN:
                    return new FuncDef.FuncDefImpl(node);
                case TypeAlias.FQN:
                    return new TypeAlias.TypeAliasImpl(node);
                case TypeVarExpr.FQN:
                    return new TypeVarExpr.TypeVarExprImpl(node);
                case Decorator.FQN:
                    return new Decorator.DecoratorImpl(node);
                case OverloadedFuncDef.FQN:
                    return new OverloadedFuncDef.OverloadedFuncDefImpl(node);

            }
            throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
        }

        @Override
        public boolean isModulePublic() {
            return getValue().getMember("module_public").asBoolean();
        }

        @Override
        public boolean isModuleHidden() {
            return getValue().getMember("module_hidden").asBoolean();
        }

        @Override
        public Value getCrossRef() {
            return getValue().getMember("cross_ref");
        }

        @Override
        public Value getImplicit() {
            return getValue().getMember("implicit");
        }

        @Override
        public boolean isPluginGenerated() {
            return getValue().getMember("plugin_generated").asBoolean();
        }

        @Override
        public boolean noSerialize() {
            return getValue().getMember("no_serailize").asBoolean();
        }

        @Override
        public String getFullname() {
            return getValue().getMember("fullname").asString();
        }

        @Override
        public Type getType() {
            Value type = getValue().getMember("type");

            String pythonFQN = Utils.getFullyQualifedName(type);
            switch (pythonFQN){
                case AnyType.FQN:
                    return new AnyTypeImpl(type);
                case NoneType.FQN:
                    return new NoneTypeImpl(type);
                case CallableType.FQN:
                    return new CallableTypeImpl(type);
                case Instance.FQN:
                    return new InstanceImpl(type);
            }
            throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
        }

    }


    int getKind();
    SymbolNode getNode();
    boolean isModulePublic();
    boolean isModuleHidden();
    Value getCrossRef();
    Value getImplicit();
    boolean isPluginGenerated();
    boolean noSerialize();
    String getFullname();
    Type getType();
}

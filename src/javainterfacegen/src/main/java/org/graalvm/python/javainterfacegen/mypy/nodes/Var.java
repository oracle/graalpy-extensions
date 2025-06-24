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
import org.graalvm.python.javainterfacegen.mypy.types.LiteralType;
import org.graalvm.python.javainterfacegen.mypy.types.NoneType;
import org.graalvm.python.javainterfacegen.mypy.types.Overloaded;
import org.graalvm.python.javainterfacegen.mypy.types.ParamSpecType;
import org.graalvm.python.javainterfacegen.mypy.types.Parameters;
import org.graalvm.python.javainterfacegen.mypy.types.TupleType;
import org.graalvm.python.javainterfacegen.python.Utils;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.mypy.types.TypeAliasType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVarType;
import org.graalvm.python.javainterfacegen.mypy.types.UninhabitedType;
import org.graalvm.python.javainterfacegen.mypy.types.UnionType;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.AnyTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.CallableTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.InstanceImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.LiteralTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.NoneTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.OverloadedImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.ParamSpecTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.ParametersImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.TupleTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.TypeAliasTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.TypeTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.TypeVarTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.TypesFactory;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.UninhabitedTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.UnionTypeImpl;

public interface Var extends SymbolNode {

    public static final String FQN = "mypy.nodes.Var";

    static class VarImpl extends SymbolNode.SymbolNodeImpl implements Var {

        public VarImpl(Value instance) {
            super(instance);
            String instanceFQN = Utils.getFullyQualifedName(instance);
            if (!Var.FQN.equals(instanceFQN)) {
                throw new UnsupportedOperationException("Can not create new VarImpl from Guest instance " + instanceFQN);
            }
        }

        @Override
        public String getName() {
            if (getValue().hasMember("name")) {
                return getValue().getMember("name").asString();
            }
            return null;
        }

        @Override
        public String getFullname() {
            if (getValue().hasMember("fullname")) {
                return getValue().getMember("fullname").asString();
            }
            return null;
        }

        @Override
        public Value getInfo() {
            if (getValue().hasMember("info")) {
                return getValue().getMember("info");
            }
            return null;
        }

        @Override
        public Type getType() {

            if (getValue().hasMember("type")) {
                Value type = getValue().getMember("type");
                return TypesFactory.createType(type);
            }
            return null;
        }

        @Override
        public <T> T accept(NodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Value getFinalValue() {
            return getValue().getMember("final_value");
        }

        @Override
        public boolean isSelf() {
            return getValue().getMember("is_self").asBoolean();
        }

        @Override
        public boolean isCls() {
            return getValue().getMember("is_cls").asBoolean();
        }

        @Override
        public boolean isReady() {
            return getValue().getMember("is_ready").asBoolean();
        }

        @Override
        public boolean isInferred() {
            return getValue().getMember("is_inferred").asBoolean();
        }

        @Override
        public boolean isInitializedInClass() {
            return getValue().getMember("is_initialized_in_class").asBoolean();
        }

        @Override
        public boolean isStaticMethod() {
            return getValue().getMember("is_staticmethod").asBoolean();
        }

        @Override
        public boolean isClassMethod() {
            return getValue().getMember("is_classmethod").asBoolean();
        }

        @Override
        public boolean isProperty() {
            return getValue().getMember("is_property").asBoolean();
        }

        @Override
        public boolean isSettableProperty() {
            return getValue().getMember("is_settable_property").asBoolean();
        }

        @Override
        public boolean isClassVar() {
            return getValue().getMember("is_classvar").asBoolean();
        }

        @Override
        public boolean isAbstractVar() {
            return getValue().getMember("is_abstract_var").asBoolean();
        }

        @Override
        public boolean isFinal() {
            return getValue().getMember("is_final").asBoolean();
        }

        @Override
        public boolean isFinalUnsetInClass() {
            return getValue().getMember("final_unset_in_class").asBoolean();
        }

        @Override
        public boolean isFinalSetInInit() {
            return getValue().getMember("final_set_in_init").asBoolean();
        }

        @Override
        public boolean isSuppressedImport() {
            return getValue().getMember("is_suppressed_import").asBoolean();
        }

        @Override
        public boolean isExplicitSelfType() {
            return getValue().getMember("explicit_self_type").asBoolean();
        }

        @Override
        public boolean isFromModuleGetAttr() {
            return getValue().getMember("from_module_getattr").asBoolean();
        }

        @Override
        public boolean hasExplicitValue() {
            return getValue().getMember("has_explicit_value").asBoolean();
        }

        @Override
        public boolean allowIncompatibleOverride() {
            return getValue().getMember("allow_incompatible_override").asBoolean();
        }

        @Override
        public boolean isInvalidPartialType() {
            return getValue().getMember("invalid_partial_type").asBoolean();
        }
    }

    String getName();

    String getFullname();

    Value getInfo();

    Type getType();

    Value getFinalValue();

    boolean isSelf();

    boolean isCls();

    boolean isReady();

    boolean isInferred();

    boolean isInitializedInClass();

    boolean isStaticMethod();

    boolean isClassMethod();

    boolean isProperty();

    boolean isSettableProperty();

    boolean isClassVar();

    boolean isAbstractVar();

    boolean isFinal();

    boolean isFinalUnsetInClass();

    boolean isFinalSetInInit();

    boolean isSuppressedImport();

    boolean isExplicitSelfType();

    boolean isFromModuleGetAttr();

    boolean hasExplicitValue();

    boolean allowIncompatibleOverride();

    boolean isInvalidPartialType();
}

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
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.NoneType;
import org.graalvm.python.javainterfacegen.mypy.types.ProperType;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.CallableTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.NoneTypeImpl;
import org.graalvm.python.javainterfacegen.python.Utils;

public interface FuncBase extends Node {

    public static ProperType getTypeImpl(FuncBase funcBase) {
        Value type = funcBase.getValue().getMember("type");
        String typeFQN = Utils.getFullyQualifedName(type);
        switch(typeFQN) {
            case CallableType.FQN:
                return new CallableTypeImpl(type);
            case NoneType.FQN:
            case "NoneType":
                return new NoneTypeImpl(type);
        }
        throw new UnsupportedOperationException(typeFQN + " doesn't fit");
    }

    static class FuncBaseImpl extends Node.NodeImpl implements FuncBase {

        public FuncBaseImpl(Value instance) {
            super(instance);
        }

        @Override
        public ProperType getType() {
            return getTypeImpl(this);
        }

        @Override
        public Value getUnanalyzed_type() {
            return getValue().getMember("unanalyzed_type");
        }

        @Override
        public Value getInfo() {
            return getValue().getMember("info");
        }

        @Override
        public boolean isProperty() {
            return getValue().getMember("is_property").asBoolean();
        }

        @Override
        public boolean isClass() {
            return getValue().getMember("is_class").asBoolean();
        }

        @Override
        public boolean isStatic() {
            return getValue().getMember("is_static").asBoolean();
        }

        @Override
        public boolean isFinal() {
            return getValue().getMember("is_final").asBoolean();
        }

        @Override
        public boolean isExplicitOverride() {
            return getValue().getMember("is_explicit_override").asBoolean();
        }

        @Override
        public boolean isTypeCheckOnly() {
            return getValue().getMember("is_type_check_only").asBoolean();
        }

        @Override
        public String getFullname() {
            return getValue().getMember("fullname").asString();
        }



    }

    ProperType getType();
    Value getUnanalyzed_type();
    Value getInfo();
    boolean isProperty();
    boolean isClass();
    boolean isStatic();
    boolean isFinal();
    boolean isExplicitOverride();
    boolean isTypeCheckOnly();
    String getFullname();
}

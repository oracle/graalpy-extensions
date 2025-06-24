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
import org.graalvm.python.javainterfacegen.mypy.types.TupleType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.mypy.types.TypeAliasType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVarType;
import org.graalvm.python.javainterfacegen.mypy.types.UninhabitedType;
import org.graalvm.python.javainterfacegen.mypy.types.UnionType;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.AnyTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.CallableTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.InstanceImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.NoneTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.TupleTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.TypeAliasTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.TypeVarTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.TypesFactory;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.UninhabitedTypeImpl;
import org.graalvm.python.javainterfacegen.mypy.types.defaultImpl.UnionTypeImpl;
import org.graalvm.python.javainterfacegen.python.Utils;

public interface TypeAlias extends SymbolNode {

    public static final String FQN = "mypy.nodes.TypeAlias";

    static class TypeAliasImpl extends SymbolNode.SymbolNodeImpl implements TypeAlias {

        public TypeAliasImpl(Value instance) {
            super(instance);
        }

        @Override
        public <T> T accept(NodeVisitor<T> visitor) {
            return visitor.visit(this);
        }

        @Override
        public Type getTarget() {
            Value value = getValue().getMember("target");
            return TypesFactory.createType(value);
        }

        @Override
        public boolean noArgs() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public String getName() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        @Override
        public String getFullName() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

    }

    public Type getTarget();
    public boolean noArgs();
    public String getName();
    public String getFullName();

}

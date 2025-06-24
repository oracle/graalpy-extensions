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

import java.util.List;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeAlias;
import org.graalvm.python.javainterfacegen.mypy.types.AnyType;
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.ExtraAttrs;
import org.graalvm.python.javainterfacegen.mypy.types.Instance;
import org.graalvm.python.javainterfacegen.mypy.types.LiteralType;
import org.graalvm.python.javainterfacegen.mypy.types.NoneType;
import org.graalvm.python.javainterfacegen.mypy.types.Overloaded;
import org.graalvm.python.javainterfacegen.mypy.types.ParamSpecType;
import org.graalvm.python.javainterfacegen.mypy.types.Parameters;
import org.graalvm.python.javainterfacegen.mypy.types.TupleType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.mypy.types.TypeAliasType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVarType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVisitor;
import org.graalvm.python.javainterfacegen.mypy.types.TypedDictType;
import org.graalvm.python.javainterfacegen.mypy.types.UninhabitedType;
import org.graalvm.python.javainterfacegen.mypy.types.UnionType;


public class PythonFQNResolver {

    private static TypeVisitor<String> nameVisitor = new TypeVisitor<String>() {
        @Override
        public String visit(Instance instance) {
            try {
                return instance.getType().getFullname();
            } catch (PolyglotException e) {
                return instance.getValue().getMember("type_ref").asString();
            }
        }

        @Override
        public String visit(CallableType callableType) {
            return callableType.getName();
        }

        @Override
        public String visit(NoneType noneType) {
            return NoneType.FQN;
        }

        @Override
        public String visit(AnyType anyType) {
            return AnyType.FQN;
        }

        @Override
        public String visit(UnionType unionType) {
            List<Type> items = unionType.getItems();
            if (items.size() == 2 ) {
                if (items.get(0) instanceof NoneType) {
                    return items.get(1).accept(this);
                }
                if (items.get(1) instanceof NoneType) {
                    return items.get(0).accept(this);
                }
            }
            return AnyType.FQN;
        }

        @Override
        public String visit(TypeVarType typeVarType) {
            // TODO if we wan to support generics somehow ...
            return AnyType.FQN;
        }

        @Override
        public String visit(TypeAliasType typeAliasType) {
            TypeAlias alias = typeAliasType.getAlias();
            if (alias != null) {
                return alias.fullname();
            }
            return TypeAliasType.FQN;
        }

        @Override
        public String visit(TupleType tupleType) {
            return TupleType.FQN;
        }

        @Override
        public String visit(UninhabitedType uType) {
            return UninhabitedType.FQN;
        }

        @Override
        public String visit(LiteralType literalType) {
            return visit(literalType.getFallback());
        }

        @Override
        public String visit(TypeType typeType) {
            return TypeType.FQN + "[" + typeType.getItem().accept(this) + "]";
        }

        @Override
        public String visit(Overloaded overloaded) {
            return Overloaded.FQN;
        }

        @Override
        public String visit(ParamSpecType paramSpec) {
            return ParamSpecType.FQN;
        }

        @Override
        public String visit(Parameters parameters) {
            return Parameters.FQN;
        }

        @Override
        public String visit(TypedDictType typedDict) {
            return TypedDictType.FQN;
        }


    };

    public static String findPythonFQN(Type type) {
        return type.accept(nameVisitor);
//        if (type instanceof Instance) {
//            return ((Instance)type).accept(nameVisitor);
//        }
//        if (type instanceof CallableType) {
//            return ((CallableType)type).accept(nameVisitor);
//        }
//        throw new UnsupportedOperationException("Unknown Python type " + Utils.getFullyQualifedName(type.getValue()) + " to map to Java type.");
    }
}

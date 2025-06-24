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
package org.graalvm.python.javainterfacegen.mypy.types.defaultImpl;

import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.mypy.nodes.Argument;
import org.graalvm.python.javainterfacegen.mypy.nodes.Decorator;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.SymbolNode;
import org.graalvm.python.javainterfacegen.mypy.types.AnyType;
import org.graalvm.python.javainterfacegen.mypy.types.ArgKind;
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.FormalArgument;
import org.graalvm.python.javainterfacegen.mypy.types.Instance;
import org.graalvm.python.javainterfacegen.mypy.types.LiteralType;
import org.graalvm.python.javainterfacegen.mypy.types.NoneType;
import org.graalvm.python.javainterfacegen.mypy.types.Overloaded;
import org.graalvm.python.javainterfacegen.mypy.types.ParamSpecType;
import org.graalvm.python.javainterfacegen.mypy.types.TupleType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.mypy.types.TypeAliasType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVarLikeType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVarType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVisitor;
import org.graalvm.python.javainterfacegen.mypy.types.UninhabitedType;
import org.graalvm.python.javainterfacegen.mypy.types.UnionType;
import org.graalvm.python.javainterfacegen.python.GuestArray;
import org.graalvm.python.javainterfacegen.python.Utils;

public class CallableTypeImpl extends FunctionLikeImpl implements CallableType {

    public CallableTypeImpl(Value instance) {
        super(instance);
    }

    @Override
    public List<Type> getArgTypes() {
        // TODO factoryMethod for this.
        Value argTypes = getValue().getMember("arg_types");
        GuestArray<Type> result = new GuestArray<>(argTypes, (value) -> {
            String pythonFQN = Utils.getFullyQualifedName(value);
            switch (pythonFQN) {
                case CallableType.FQN:
                    return new CallableTypeImpl(value);
                case Instance.FQN:
                    return new InstanceImpl(value);
                case NoneType.FQN:
                    return new NoneTypeImpl(value);
                case AnyType.FQN:
                    return new AnyTypeImpl(value);
                case UnionType.FQN:
                    return new UnionTypeImpl(value);
                case TypeVarType.FQN:
                    return new TypeVarTypeImpl(value);
                case TypeAliasType.FQN:
                    return new TypeAliasTypeImpl(value);
                case TupleType.FQN:
                    return new TupleTypeImpl(value);
                case UninhabitedType.FQN:
                    return new UninhabitedTypeImpl(value);
                case LiteralType.FQN:
                    return new LiteralTypeImpl(value);
                case TypeType.FQN:
                    return new TypeTypeImpl(value);
                case Overloaded.FQN:
                    return new OverloadedImpl(value);
            }
            throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
        });
        return result;
    }

    @Override
    public Type getRetType() {
        Value retType = getValue().getMember("ret_type");
        return TypesFactory.createType(retType);
    }

    @Override
    public void setRetType(Type type) {
        getValue().putMember("ret_type", type.getValue());
    }

    @Override
    public <T> T accept(TypeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public List<String> getArgNames() {
        Value names = getValue().getMember("arg_names");
        List<String> result = new ArrayList((int) names.getArraySize());
        Value iterator = names.getIterator();

        while (iterator.hasIteratorNextElement()) {
            result.add(iterator.getIteratorNextElement().asString());
        }
        return result;
    }

    @Override
    public List<ArgKind> getArgKinds() {
        Value kinds = getValue().getMember("arg_kinds");
        List<ArgKind> result = new ArrayList((int) kinds.getArraySize());
        Value iterator = kinds.getIterator();

        while (iterator.hasIteratorNextElement()) {
            Value kind = iterator.getIteratorNextElement();
            result.add(ArgKind.valueOf(kind.getMember("name").asString()));
        }
        return result;
    }

    @Override
    public CallableType withNormalizedVarArgs() {
        return new CallableTypeImpl(getValue().invokeMember("with_normalized_var_args"));
    }

    @Override
    public int getMinArgs() {
        return getValue().getMember("min_args").asInt();
    }

    @Override
    public SymbolNode getDefinition() {
        Value value = getValue().getMember("definition");
        String pythonFQN = Utils.getFullyQualifedName(value);
        switch (pythonFQN) {
            case FuncDef.FQN:
                return new FuncDef.FuncDefImpl(value);
            case Decorator.FQN:
                return new Decorator.DecoratorImpl(value);
            case NoneType.FQN:
                return null;
        }
        throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");

    }

    @Override
    public List<TypeVarLikeType> getVariables() {
        Value items = getValue().getMember("variables");
        GuestArray<TypeVarLikeType> result = new GuestArray<>(items, (value) -> {
            String pythonFQN = Utils.getFullyQualifedName(value);
            switch (pythonFQN) {
                case ParamSpecType.FQN:
                    return new ParamSpecTypeImpl(value);
            }
            throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
        });
        return result;
    }

    @Override
    public int maxPossiblePositionalArgs() {
        return getValue().invokeMember("max_possible_positional_args").asInt();
    }

    @Override
    public List<FormalArgument> getFormalArguments(boolean includeStarArgs) {
        return getValue().invokeMember("formal_arguments", includeStarArgs).as(FormalArgument.LIST_OF_FormalArgument);
    }

    @Override
    public FormalArgument argumentByName(String name) {
        return getValue().invokeMember("argument_by_name", name).as(FormalArgument.class);
    }

    @Override
    public FormalArgument argumentByPosition(int position) {
        return getValue().invokeMember("argument_by_position", position).as(FormalArgument.class);
    }

}

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.mypy.types.ArgKind;
import org.graalvm.python.javainterfacegen.python.GuestArray;
import org.graalvm.python.javainterfacegen.python.Utils;

public interface FuncItem extends FuncBase {

    public static List<Argument> getArgumentsImpl(Value instance) {
        Value orig = instance.getMember("arguments");
        if (orig == null) {
            System.out.println("!!!!!!!!!!!!!No argumets");
            return Collections.EMPTY_LIST;
        }
        GuestArray<Argument> result = new GuestArray<>(orig, (value) -> {
            String pythonFQN = Utils.getFullyQualifedName(value);
            switch (pythonFQN) {
                case "mypy.nodes.Argument":
                    return new Argument.ArgumentImpl(value);
            }
            throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
        });
        return result;
    }

    public static List<TypeParam> getTypeArgs(Value instance) {
        Value params = instance.getMember("type_args");
        GuestArray<TypeParam> result = new GuestArray<>(params, (value) -> {
            String pythonFQN = Utils.getFullyQualifedName(value);
            switch (pythonFQN) {
                case TypeParam.FQN:
                    return new TypeParam.TypeParamImpl(value);
            }
            throw new UnsupportedOperationException("Unknown Python type " + pythonFQN + " to map to Java type.");
        });
        return result;
    }

    public static List<String> getArgNames(Value instance) {
        Value names = instance.getMember("arg_names");
        List<String> result = new ArrayList((int) names.getArraySize());
        Value iterator = names.getIterator();

        while (iterator.hasIteratorNextElement()) {
            result.add(iterator.getIteratorNextElement().asString());
        }
        return result;
    }

    static class FuncItemImpl extends FuncBase.FuncBaseImpl implements FuncItem {

        public FuncItemImpl(Value instance) {
            super(instance);
        }

        @Override
        public List<Argument> getArguments() {
            return getArgumentsImpl(getValue());
        }

        @Override
        public List<String> getArgNames() {
            return FuncItem.getArgNames(getValue());
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
        public int getMinArgs() {
            return getValue().getMember("min_args").asInt();
        }

        @Override
        public int getMaxPos() {
            return getValue().getMember("max_pos").asInt();
        }

        @Override
        public List<TypeParam> getTypeArgs() {
            return FuncItem.getTypeArgs(getValue());
        }

        @Override
        public boolean isOverloaded() {
            return getValue().getMember("is_overload").asBoolean();
        }

        @Override
        public boolean isGenerator() {
            return getValue().getMember("is_generator").asBoolean();
        }

        @Override
        public boolean isCoroutine() {
            return getValue().getMember("is_coroutine").asBoolean();
        }

        @Override
        public boolean isAsyncGenerator() {
            return getValue().getMember("is_async_generator").asBoolean();
        }

        @Override
        public boolean isAwaitableCroutine() {
            return getValue().getMember("is_awaitable_coroutine").asBoolean();
        }

        @Override
        public Value getExpanded() {
            return getValue().getMember("expanded");
        }

        @Override
        public int maxFixedArgc() {
            return getValue().invokeMember("max_fixed_argc").asInt();
        }

        @Override
        public boolean isDynamic() {
            return getValue().invokeMember("is_dynamic").asBoolean();
        }

    }

    List<Argument> getArguments();

    List<String> getArgNames();

    List<ArgKind> getArgKinds();

    int getMinArgs();

    int getMaxPos();

    List<TypeParam> getTypeArgs();

    boolean isOverloaded();

    boolean isGenerator();

    boolean isCoroutine();

    boolean isAsyncGenerator();

    boolean isAwaitableCroutine();

    Value getExpanded();

    int maxFixedArgc();

    boolean isDynamic();

}

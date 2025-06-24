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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.mypy.types.ArgKind;
import org.graalvm.python.javainterfacegen.mypy.types.Parameters;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVarLikeType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVisitor;


public class ParametersImpl extends TypeImpl implements Parameters {

    public ParametersImpl(Value instance) {
        super(instance);
    }

    @Override
    public List<Value> formalArguments() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<Value> formalArguments(boolean include_star_args) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Set<TypeVarLikeType> getVariables() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Value varArg() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getMinArgs() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean impreciseArgKinds() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Value argumentByPosition() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Value argumentByPosition(int position) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isEllipsisArgs() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<ArgKind> getArgKinds() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<String> getArgNames() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Value trySynthesizingArgFromVararg() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Value trySynthesizingArgFromVararg(int position) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Value kwArg() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Value argumentByName(String name) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Value trySynthesizingArgFromKwarg(String name) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<Type> getArgTypes() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Parameters copyModified() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Parameters copyModified(CopyModifiedArgs optionalArgs) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public <T> T accept(TypeVisitor<T> visitor) {
        return visitor.visit(this);
    }

//    public Value<TypeVarLikeType> getVariables() {
//        return getValue().getMember("variables"); // TODO handle Value<TypeVarLikeType>
//    }
//
//
//    public int getMinArgs() {
//        return getValue().getMember("min_args"); // TODO handle int
//    }
//    @Override
//    public boolean impreciseArgKinds() {
//        return getValue().getMember("imprecise_arg_kinds"); // TODO handle boolean
//    }
//
//
//    public boolean isEllipsisArgs() {
//        return getValue().getMember("is_ellipsis_args"); // TODO handle boolean
//    }
//
//
//    public List<ArgKind> getArgKinds() {
//        return getValue().getMember("arg_kinds"); // TODO handle List<ArgKind>
//    }
//    @Override
//    public List<Optional<String>> getArgNames() {
//        return getValue().getMember("arg_names"); // TODO handle List<Optional<String>>
//    }
//
//    public List<Type> getArgTypes() {
//        return getValue().getMember("arg_types"); // TODO handle List<Type>
//    }

}

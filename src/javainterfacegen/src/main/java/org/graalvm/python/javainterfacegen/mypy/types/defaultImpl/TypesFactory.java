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
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.mypy.types.TypeAliasType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVarType;
import org.graalvm.python.javainterfacegen.mypy.types.TypedDictType;

import org.graalvm.python.javainterfacegen.mypy.types.UninhabitedType;
import org.graalvm.python.javainterfacegen.mypy.types.UnionType;
import org.graalvm.python.javainterfacegen.python.Utils;

public class TypesFactory {

    public static Type createType(Value valeu) {
        String typeFQN = Utils.getFullyQualifedName(valeu);
        switch (typeFQN) {
            case CallableType.FQN:
                return new CallableTypeImpl(valeu);
            case Instance.FQN:
                return new InstanceImpl(valeu);
            case NoneType.FQN:
            case "NoneType":
                return new NoneTypeImpl(valeu);
            case AnyType.FQN:
                return new AnyTypeImpl(valeu);
            case UnionType.FQN:
                return new UnionTypeImpl(valeu);
            case TypeVarType.FQN:
                return new TypeVarTypeImpl(valeu);
            case TypeAliasType.FQN:
                return new TypeAliasTypeImpl(valeu);
            case TupleType.FQN:
                return new TupleTypeImpl(valeu);
            case UninhabitedType.FQN:
                return new UninhabitedTypeImpl(valeu);
            case LiteralType.FQN:
                return new LiteralTypeImpl(valeu);
            case TypeType.FQN:
                return new TypeTypeImpl(valeu);
            case Overloaded.FQN:
                return new OverloadedImpl(valeu);
            case Parameters.FQN:
                return new ParametersImpl(valeu);
            case ParamSpecType.FQN:
                return new ParamSpecTypeImpl(valeu);
            case TypedDictType.FQN:
                return new TypedDictTypeImpl(valeu);
        }
        throw new UnsupportedOperationException("Unknown Python type " + typeFQN + " to map to Java type.");
    }

}

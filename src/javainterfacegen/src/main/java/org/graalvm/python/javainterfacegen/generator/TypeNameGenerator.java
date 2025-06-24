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
import java.util.Set;
import java.util.TreeSet;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeInfo;
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
import org.graalvm.python.javainterfacegen.mypy.types.TypeVisitor;
import org.graalvm.python.javainterfacegen.mypy.types.TypedDictType;
import org.graalvm.python.javainterfacegen.mypy.types.UninhabitedType;
import org.graalvm.python.javainterfacegen.mypy.types.UnionType;

public class TypeNameGenerator implements TypeVisitor<String> {

    private boolean firstLevel = true;

    public static String createName(Type type) {
        String name = type.accept(new TypeNameGenerator());
        while(name.charAt(name.length() - 1) == '_') {
            name = name.substring(0, name.length() - 1);
        }
        return name;
    }

    @Override
    public String visit(AnyType anyType) {
        return "Any";
    }

    @Override
    public String visit(CallableType callableType) {
        // TODO
        return "Callable";
    }

    @Override
    public String visit(Instance instance) {
        List<Type> args = instance.getArgs();

        TypeInfo info = instance.getType();
        String pythonName = GeneratorUtils.convertToJavaClassName(info.getName());
        if (args.size() == 0) {
            return pythonName;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(pythonName).append("Of");
        for (int i = 0; i < args.size(); i++) {
            sb.append(args.get(i).accept(this));
        }
        sb.append("_");
        return sb.toString();

    }

    @Override
    public String visit(NoneType noneType) {
        return "None";
    }

    @Override
    public String visit(UnionType unionType) {
        Set<String> parts = new TreeSet();
        List<Type> items = unionType.getItems();

        boolean hasNone = false;
        boolean wasFirstLevel = firstLevel;
        firstLevel = false;
        for (int i = 0; i < items.size(); i++) {
            Type typeItem = items.get(i);
            if (typeItem instanceof NoneType) {
                hasNone = true;
            } else {
                String name = typeItem.accept(this);
                parts.add(name);
            }
        }
        StringBuilder sb = new StringBuilder();
        if (!wasFirstLevel) {
            sb.append("UnionOf");
        }
        boolean first = true;
        for (String part : parts) {
            if (first) {
                first = false;
            } else {
                sb.append("Or");
            }
            sb.append(part);
        }
        if (hasNone) {
            sb.append("OrNone");
        }
        if (!wasFirstLevel) {
            sb.append("_");
        } else {
            // remove '_' at the end
            int index = sb.length();
            while (sb.charAt(--index) == '_') {
                sb.deleteCharAt(index);
            }
        }

        return sb.toString();
    }

    @Override
    public String visit(UninhabitedType uType) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String visit(TupleType tupleType) {
        return tupleType.getPartialFallback().accept(this);
    }

    @Override
    public String visit(TypeAliasType typeAliasType) {
        return typeAliasType.getAlias().getTarget().accept(this);
    }

    @Override
    public String visit(TypeVarType typeVarType) {
        return "Value";
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String visit(LiteralType literalType) {
        return literalType.getFallback().accept(this);
    }

    @Override
    public String visit(TypeType typeType) {
        // TODO
        return "TypeType";
    }

    @Override
    public String visit(Overloaded overloaded) {
        // TODO
        return "Overloaded";
    }

    @Override
    public String visit(ParamSpecType paramSpec) {
        // TODO
        return "ParamSpecType";
    }

    @Override
    public String visit(Parameters parameters) {
        return "Parameters";
    }

    @Override
    public String visit(TypedDictType typedDict) {
        return "TypedDictType";
    }

}

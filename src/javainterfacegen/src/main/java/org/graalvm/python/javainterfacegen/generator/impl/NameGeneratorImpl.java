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
package org.graalvm.python.javainterfacegen.generator.impl;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.generator.GeneratorUtils;
import org.graalvm.python.javainterfacegen.mypy.nodes.ClassDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.MypyFile;
import org.graalvm.python.javainterfacegen.generator.NameGenerator;
import org.graalvm.python.javainterfacegen.generator.PythonFQNResolver;
import org.graalvm.python.javainterfacegen.generator.TypeManager;
import org.graalvm.python.javainterfacegen.mypy.nodes.DefaultNodeVisitor;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.Node;
import org.graalvm.python.javainterfacegen.mypy.nodes.Var;
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;

public class NameGeneratorImpl implements NameGenerator {

    private static final String IMPLEMENTATION_POSFIX = "Impl";

    @Override
    public String packageForInterface(Node node, GeneratorContext context) {
        if (node instanceof MypyFile mypyFile) {
            return packageForInterface(mypyFile, context);
        } else if (node instanceof ClassDef classDef) {
            return packageForInterface(classDef, context);
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String packageForImplementation(Node node, GeneratorContext context) {
        if (node instanceof MypyFile mypyFile) {
            return packageForImplementation(mypyFile, context);
        }  else if (node instanceof ClassDef classDef) {
            return packageForImplementation(classDef, context);
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public String packageForInterface(MypyFile mypyFile, GeneratorContext context) {
        Map<String, Object> config = context.getConfiguration();
        String targetName = config.get(Configuration.P_TARGET_INTERFACE_PACKAGE).toString();
        return targetName;
    }

    public String packageForImplementation(MypyFile mypyFile, GeneratorContext context) {
        Map<String, Object> config = context.getConfiguration();
        String targetName = config.get(Configuration.P_TARGET_IMPLEMENTATION_PACKAGE).toString();
        return targetName;
    }

    public String packageForInterface(ClassDef classDef, GeneratorContext context) {
        Map<String, Object> config = context.getConfiguration();
        String targetName = config.get(Configuration.P_TARGET_INTERFACE_PACKAGE).toString();
        targetName = targetName + '.' + checkPackageName(config, classDef.getFullname());
        targetName = targetName.substring(0, targetName.lastIndexOf('.'));
        return targetName;
    }

    public String packageForImplementation(ClassDef classDef, GeneratorContext context) {
        Map<String, Object> config = context.getConfiguration();
        String targetName = config.get(Configuration.P_TARGET_IMPLEMENTATION_PACKAGE).toString();
        targetName = targetName + '.' + checkPackageName(config, classDef.getFullname());
        targetName = targetName.substring(0, targetName.lastIndexOf('.'));
        return targetName;
    }

    @Override
    public String interfaceName(MypyFile mypyFile, GeneratorContext context) {
        String name = mypyFile.getName();
        if (name == null) {
            System.out.println("####path: " + mypyFile.getPath());
            System.out.println("####fullname: " + mypyFile.getFullname());
            name = "UknownFileName_" + mypyFile.getValue().toString();
        }
        return GeneratorUtils.convertToJavaClassName(name) + "Module";
    }

    @Override
    public String interfaceName(ClassDef classDef, GeneratorContext context) {
        String name = classDef.getName();
        return GeneratorUtils.convertToJavaClassName(name);
    }

    @Override
    public String classImplementationName(MypyFile mypyFile, GeneratorContext context) {
        String name = mypyFile.getName();
        return GeneratorUtils.convertToJavaClassName(name) + IMPLEMENTATION_POSFIX;
    }

    @Override
    public String classImplementationName(ClassDef classDef, GeneratorContext context) {
        String name = classDef.getName();
        return GeneratorUtils.convertToJavaClassName(name) + IMPLEMENTATION_POSFIX;
    }

    @Override
    public String methodName(FuncDef funcDef, GeneratorContext context) {
        return GeneratorUtils.convertToJavaIdentifierName(funcDef.getName());
    }

    @Override
    public String getterName(Node node, GeneratorContext context) {

        String name = GeneratorUtils.convertToJavaIdentifierName((new NameVisitor()).getName(node));
        String returnPythonType = PythonFQNResolver.findPythonFQN((new ReturnTypeFinder().getType(node)));
        String returnJavaType = TypeManager.get().getJavaType(returnPythonType);
        if (!"boolean".equals(returnJavaType)) {
            name = "get" + GeneratorUtils.uppercaseFirstLetter(name);
        }
        return name;
    }

    private String checkPackageName(Map<String, Object> config, String packageName) {
        boolean changed = false;
        Object stripPrefix = config.get(Configuration.P_TARGET_PACKAGE_STRIP);
        if (stripPrefix != null) {
            packageName = packageName.replace(stripPrefix.toString(), "");
            if (packageName.startsWith(".")) {
                packageName = packageName.substring(1);
            }
            changed = true;
        }
        String[] parts = packageName.split("\\.");
        for (int i = 0; i < parts.length ; i++) {
            String part = parts[i];
            if (GeneratorUtils.JAVA_KEYWORDS.contains(part)
                    || GeneratorUtils.JAVA_PRIMITIVE_TYPES.contains(part)) {
                parts[i] = part + "_";
                changed = true;
            }
        }

        if (changed) {
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                sb.append(part).append(".");
            }
            sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        }
        return packageName;
    }

    private static class NameVisitor extends DefaultNodeVisitor<String> {

        public String getName(Node node) {
            return node.accept(this);
        }

        @Override
        protected String defaultVisit(Node node) {
            throw new UnsupportedOperationException("Not implemented visit method in NameVisitor for " + node.getClass().getName());
        }

        @Override
        public String visit(Var v) {
            return v.getName();
        }

        @Override
        public String visit(FuncDef funcDef) {
            return funcDef.getName();
        }

    }

    private static class ReturnTypeFinder extends DefaultNodeVisitor<Type> {

        public Type getType(Node node) {
            return node.accept(this);
        }

        @Override
        protected Type defaultVisit(Node node) {
            throw new UnsupportedOperationException("Not implemented visit method in ReturnTypeFinder for " + node.getClass().getName());
        }

        @Override
        public Type visit(Var v) {
            return v.getType();
        }

        @Override
        public Type visit(FuncDef funcDef) {
            if (funcDef.getType() instanceof CallableType ct) {
                return ct.getRetType();
            }
            return funcDef.getType();
        }

    }
}

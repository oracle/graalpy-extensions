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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.generator.GeneratorUtils;
import org.graalvm.python.javainterfacegen.generator.PythonFQNResolver;
import org.graalvm.python.javainterfacegen.generator.TypeManager;
import org.graalvm.python.javainterfacegen.mypy.nodes.ClassDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.types.ArgKind;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_NAMED;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_NAMED_OPT;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_OPT;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_POS;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_STAR;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_STAR2;
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;

public class OverloadArgsGenerator {

    public static final String ARGS_NAME = "{{args}}";
    public static final String KEYWORD_ARGS_NAME = "{{kwargs}}";
    public static final String KEYWORDS_ARGS_BUILDER = "{{builder}}";
    // TODO should be configurable. 
    public static final int MAX_OPTIONAL_ARGS = 2;

    private static final String KW_ARGS_JAVADOC_START = """
                            {{indent}} *        represents the keyword arguments (`**kwargs`) passed to a Python 
                            {{indent}} *        function.This parameter encapsulates a set of named arguments.
                            """;
    private static final String KW_ARGS_JAVADOC_INCLUDE = """
                            {{indent}} *        <p>
                            {{indent}} *        The set include the following keys:<br><br>""";
    private static final String KW_ARGS_JAVADOC_KEYS = """
                            {{indent}} *        - {{required_optional}}:
                            {{indent}} *        <ul>
                            {{keys}}
                            {{indent}} *        </ul>""";
    private static final String KW_ARGS_JAVADOC_KEY = 
                           "{{indent}} *        {{indent}}<li><b>{{name}}</b> {{type}}{{description}}</li>";
    private static final String STAR_ARGS_JAVADOC = """ 
                            {{indent}} *        represents the positional arguments (`*args`) passed to a Python
                            {{indent}} *        function.
                            {{indent}} *        <p>
                            {{indent}} *        This parameter encapsulates a variable number of arguments 
                            {{indent}} *        in the order they were provided.
                            {{indent}} *        Use {@link PositionalArguments#fromArray(java.lang.Object...) }
                            {{indent}} *        or {@link PositionalArguments#fromList(java.util.List) }
                            {{indent}} *        to construct this parameter.""";
    private static final String KW_ARGS_JAVADOC_PYTHON_TYPE = "Python type: {@code {{type}}}";
    private static final String KW_ARGS_JAVADOC_HOW_CREATE_SIMPLE = """
                            {{indent}} *        <p>
                            {{indent}} *        Use {@link KeywordArguments#from(Map)} or one of the {@link KeywordArguments#of} 
                            {{indent}} *        methods to construct this parameter.""";
    private static final String KW_ARGS_JAVADOC_HOW_CREATE = """
                            {{indent}} *        <p>
                            {{indent}} *        The {@code kwArg} parameter can be constructed using the generated
                            {{indent}} *        builder {@link {{builder_name}}} for better type safety.
                            {{indent}} *        Alternatively, use {@link KeywordArguments#from(Map)} or one 
                            {{indent}} *        of the {@link KeywordArguments#of} methods to construct this
                            {{indent}} *        parameter.""";
    public static class Argument {

        private final String name;
        private final Type type;
        private final ArgKind kind;

        public Argument(java.lang.String name, Type type, ArgKind kind) {
            this.name = name;
            this.type = type;
            this.kind = kind;
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public ArgKind getKind() {
            return kind;
        }
        
    }

    public static class KwArgument extends Argument {

        private final Argument[] required;
        private final Argument[] optional;

        public KwArgument(String name, Type type, ArgKind kind, Argument[] required, Argument[] optional) {
            super(name, type, kind);
            this.required = required;
            this.optional = optional;
        }

        public boolean hasRequired() {
            return required.length > 0;
        }

        public boolean hasOptional() {
            return optional.length > 0;
        }
    }

    /**
     *
     * @param context
     * @param fn
     * @return name of arg, python type or null.
     */
    public static List<List<Argument>> generateVariations(GeneratorContext context, FuncDef fn) {
        List<ArgKind> argKinds = fn.getArgKinds();
        List<Type> argTypes = null;
        List<String> argNames = fn.getArgNames();
        if (fn.getType() instanceof CallableType ct) {
            argTypes = ct.getArgTypes();
        }

        List<Argument> positional = new ArrayList();
        List<Argument> positionalOpt = new ArrayList();
        List<Argument> named = new ArrayList();
        List<Argument> namedOpt = new ArrayList();

        Argument starArgs = null;
        Argument kwArgs = null;

        boolean skipFirst = false;
        if (argTypes != null && !argTypes.isEmpty()) {

            skipFirst = hasSelfArg(fn, context) || fn.isClass();
        } else {
            skipFirst = fn.isClass() || (argKinds.size() > 0 && argKinds.get(0) == ARG_POS && "self".equals(argNames.get(0)));
        }

        for (int i = skipFirst ? 1 : 0; i < argKinds.size(); i++) {
            ArgKind kind = argKinds.get(i);
            String argName = GeneratorUtils.toValidJavaIdentifier(argNames.get(i));
            switch (kind) {
                case ARG_POS:
                    positional.add(new Argument(argName, argTypes == null ? null : argTypes.get(i), ARG_POS));
                    break;
                case ARG_OPT: 
                    positionalOpt.add(new Argument(argName, argTypes == null ? null : argTypes.get(i), ARG_OPT));
                    break;
                case ARG_NAMED:
                    named.add(new Argument(argName, argTypes == null ? null : argTypes.get(i), ARG_NAMED));
                    break;
                case ARG_NAMED_OPT:
                    namedOpt.add(new Argument(argName, argTypes == null ? null : argTypes.get(i), ARG_NAMED_OPT));
                    break;
                case ARG_STAR:
                    starArgs = new Argument(argName, null, ARG_STAR);
                    break;
                case ARG_STAR2:
                    kwArgs = new KwArgument(argName, null, ARG_STAR2,
                            named.stream().toArray(Argument[]::new), namedOpt.stream().toArray(Argument[]::new));
                    break;
            }
        }

        return createVariation(positional, positionalOpt, starArgs, named, namedOpt, kwArgs);
    }

    private static String getName(Argument arg, int position) {
        return arg.name != null ? arg.name : "arg" + position;
    }

    private static List<List<Argument>> createVariation(List<Argument> pos, List<Argument> posOpt, Argument args,
            List<Argument> named, List<Argument> namedOpt, Argument kwArgs) {
        List<List<Argument>> result = new ArrayList<>();

        if (kwArgs == null && (!named.isEmpty() || !namedOpt.isEmpty())) {
            kwArgs = new KwArgument("kwArgs", null, ARG_STAR2, named.stream().toArray(Argument[]::new), namedOpt.stream().toArray(Argument[]::new));
        } else if (kwArgs == null && !posOpt.isEmpty()) {
            kwArgs = new KwArgument("kwArgs", null, ARG_STAR2, posOpt.stream().toArray(Argument[]::new), namedOpt.stream().toArray(Argument[]::new));
        }

        if (pos.isEmpty() && named.isEmpty()) {
            // possible call without args
            result.add(Collections.EMPTY_LIST);
        }
        if (!pos.isEmpty() && named.isEmpty()) {
            result.add(pos);
        }
        result.addAll(createVariatioWithArgsAndKwArgs(pos, args, named, kwArgs));

        if (!posOpt.isEmpty()) {
            List<List<Argument>> variations = createOptionalVariations(posOpt);
            for (List<Argument> variation : variations) {
                List<Argument> newVariation = new ArrayList(pos);
                newVariation.addAll(variation);
                if (named.isEmpty()) {
                    result.add(newVariation);
                }
                result.addAll(createVariatioWithArgsAndKwArgs(newVariation, args, named, kwArgs));
            }
        }

        return result;
    }

    private static List<List<Argument>> createOptionalVariations(List<Argument> optional) {
        if (optional.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        List<List<Argument>> result = new ArrayList();
        List<Argument> variation = Collections.EMPTY_LIST;
        for (Argument arg : optional) {
            variation = new ArrayList(variation);
            variation.add(arg);
            result.add(variation);
        }
        return result;
    }

    private static List<List<Argument>> createVariatioWithArgsAndKwArgs(List<Argument> variant, Argument args, List<Argument> named, Argument kwArgs) {
        if (args == null && kwArgs == null) {
            return Collections.EMPTY_LIST;
        }
        List<List<Argument>> result = new ArrayList();
        List<Argument> variation;
        if (args != null && named.isEmpty()) {
            variation = new ArrayList(variant);
            variation.add(args);
            result.add(variation);
        }
        if (kwArgs != null) {
            variation = new ArrayList(variant);
            variation.add(kwArgs);
            result.add(variation);
        }
        if (args != null && kwArgs != null) {
            variation = new ArrayList(variant);
            variation.add(args);
            variation.add(kwArgs);
            result.add(variation);
        }
        return result;
    }

    public static String createArgsText(GeneratorContext context, List<Argument> args) {
        StringBuilder argsText = new StringBuilder();
        int argIndex = 0;
        for (Argument arg : args) {
            argIndex++;
            if (argsText.length() > 0) {
                argsText.append(", ");
            }

            if (arg.type != null) {
                String javaType = TypeManager.get().resolveJavaType(context, arg.type, true, "java.lang.Object");
                argsText.append(javaType).append(' ').append(getName(arg, argIndex));
            } else {
                switch (arg.kind) {
                    case ARG_STAR:
                        context.useJavaType("org.graalvm.python.embedding.PositionalArguments");
                        argsText.append("PositionalArguments ").append(arg.name != null ? arg.name : "args");
                        break;
                    case ARG_STAR2:
                        context.useJavaType("org.graalvm.python.embedding.KeywordArguments");
                        argsText.append("KeywordArguments ").append(arg.name != null ? arg.name : "kwargs");
                        break;
                    default:
                        argsText.append("Object ").append(getName(arg, argIndex));
                        break;
                }

            }
        }
        return argsText.toString();
    }

    public static String createJavadocParams(GeneratorContext context, FuncDef fn, List<Argument> args) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        for (Argument arg : args) {
            index++;
            result.append("\n{{indent}} * @param ").append(getName(arg, index)).append('\n');
            switch (arg.kind) {
                case ARG_STAR:
                    result.append(STAR_ARGS_JAVADOC);
                    break;
                case ARG_STAR2:
                    result.append(KW_ARGS_JAVADOC_START);
                    KwArgument kwArgs = (KwArgument) arg;
                    if (kwArgs.hasRequired() || kwArgs.hasOptional()) {
                        result.append(KW_ARGS_JAVADOC_INCLUDE);
                        if (kwArgs.hasRequired()) {
                            result.append(createKwArgList("Required", kwArgs.required));
                        }
                        if (kwArgs.hasOptional()) {
                            result.append(createKwArgList("Optional (has a default value)", kwArgs.optional));
                        }
                        result.append('\n');
                        result.append(KW_ARGS_JAVADOC_HOW_CREATE
                                .replace("{{builder_name}}", KwArgBuilderGenerator.getBuilderName(context, fn)));
                    } else {
                        result.append(KW_ARGS_JAVADOC_HOW_CREATE_SIMPLE);
                    }
                    break;
                case ARG_POS:
                case ARG_OPT:
                    result.append("{{indent}} *        ")
                            .append(createJavadocPythonType(arg.type));
                    break;
            }
        }
        return result.toString();
    }

    private static String createKwArgList(String title, Argument[] args) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Argument arg : args) {
            if (first) {
                first = false;
            } else {
                sb.append('\n');
            }
            sb.append(KW_ARGS_JAVADOC_KEY
                    .replace("{{name}}", arg.name)
                    .replace("{{type}}", createJavadocPythonType(arg.type))
                    .replace("{{description}}", ""));
        }

        return "\n" + KW_ARGS_JAVADOC_KEYS
                .replace("{{required_optional}}", title)
                .replace("{{keys}}", sb.toString());
    }

    public static String createJavadocPythonType(Type type) {
        return KW_ARGS_JAVADOC_PYTHON_TYPE.replace("{{type}}", type == null ? "Any" : type.toString());
    }
    
    public static String nameArgs(GeneratorContext context, List<Argument> args) {
        StringBuilder argNamesText = new StringBuilder();
        for (Argument arg : args) {
            String argName = arg.name;

            if (argNamesText.length() > 0) {
                argNamesText.append(", ");
            }
            argNamesText.append(argName);
        }
        return argNamesText.toString();
    }

    public static boolean hasSelfArg(FuncDef fn, GeneratorContext context) {
        GeneratorContext parent = context.getParent();
        if ("__init__".equals(fn.getName())) {
            return true;
        }
        if (parent != null && parent.getCurrentNode() instanceof ClassDef) {
            ClassDef classDef = (ClassDef) parent.getCurrentNode();
            List<String> argNames = fn.getArgNames();
            if (!argNames.isEmpty() && "self".equals(argNames.get(0))) {
                return true;
            }
            if (fn.getType() instanceof CallableType ct) {
                List<Type> types = ct.getArgTypes();
                if (types != null && !types.isEmpty()) {
                    String typeFQN = PythonFQNResolver.findPythonFQN(types.get(0));
                    if (typeFQN.equals(classDef.getFullname())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

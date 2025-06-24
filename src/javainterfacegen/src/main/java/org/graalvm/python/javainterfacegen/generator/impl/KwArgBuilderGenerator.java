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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.generator.GeneratorUtils;
import org.graalvm.python.javainterfacegen.generator.TypeManager;
import org.graalvm.python.javainterfacegen.mypy.nodes.ClassDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.types.ArgKind;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_NAMED;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_NAMED_OPT;
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;

public class KwArgBuilderGenerator {
    private static final String KW_ARGS_BUILDER_CLASS = """
                            {{javadoc_class}}
                            {{indent}}public static final class {{builder_name}} {
                            {{indent+1}}private final Map<String, Object> values = new HashMap();
                            
                            {{javadoc_constructor}}
                            {{indent+1}}public {{builder_name}}({{mandatory_args}}) {
                            {{constructor_body}}
                            {{indent+1}}}
                            
                            {{field_methods}}
                            
                            {{indent+1}}/**
                            {{indent+1}} * Adds custom arguments by specifying the key and value.
                            {{indent+1}} * 
                            {{indent+1}} * @param key the name of the argument; must not be null
                            {{indent+1}} * @param value value of the argument; can be null, which is translated 
                            {{indent+1}} *        to the Python value {@code None}
                            {{indent+1}} * @return the builder instance, allowing for method chaining
                            {{indent+1}} */
                            {{indent+1}}public {{builder_name}} add(String key, Object value) {
                            {{indent+2}}values.put(key, value); return this;
                            {{indent+1}}}
                            
                            {{indent+1}}/**
                            {{indent+1}} * Builds and returns a {@link KeywordArguments} instance containing 
                            {{indent+1}} * the specified arguments.
                            {{indent+1}} * 
                            {{indent+1}} * @return a new instance of {@link KeywordArguments} with the provided arguments
                            {{indent+1}} */
                            {{indent+1}}public KeywordArguments build() {
                            {{indent+2}}return KeywordArguments.from(values);
                            {{indent+1}}}
                            {{indent}}}
                            """;

    private static final String KW_ARGS_BUILDER_METHOD = """
                            {{indent+1}}/**
                            {{indent+1}} * Sets the predefined optional argument '{@code {{field_name}}}'.
                            {{indent+1}} * 
                            {{indent+1}} * @param {{field_name}}
                            {{indent+1}} *        the value of the argument; can be null, which translates 
                            {{indent+1}} *        to the Python value {@code None}<br>
                            {{indent+1}} *        {{python_type}}                             
                            {{indent+1}} * @return the builder instance, allowing for method chaining
                            {{indent+1}} */                             
                            {{indent+1}}public {{builder_name}} {{field_name}}({{type}} {{field_name}})  {
                            {{indent+2}}values.put("{{field_name}}", {{field_name}}); return this;
                            {{indent+1}}}
                            """;
    
    private static final String KW_ARGS_BUILDER_CONSTRUCTOR_ITEM = "{{indent+2}}values.put(\"{{field_name}}\", {{field_name}});";
    
    private static final String KW_ARGS_BUILDER_JAVADOC_CLASS_HEAD = """
                            {{indent}}/**
                            {{indent}} * A builder class for constructing {@link KeywordArguments} with predefined
                            {{indent}} * and custom keyword arguments. This builder is tailored for Python function
                            {{indent}} * with specific required and optional keyword arguments.
                            {{indent}} * <p>
                            """;
    private static final String KW_ARGS_BUILDER_JAVADOC_CLASS_CONSTRUCTOR = """
                            {{indent}} * The constructor initializes the builder with the required keyword arguments, 
                            {{indent}} * which must always be provided.
                            """;
    private static final String KW_ARGS_BUILDER_JAVADOC_CLASS_OPTIONAL = """
                            {{indent}} * Optional keyword arguments can be set using their corresponding methods, 
                            {{indent}} * and any additional arguments can be added using 
                            {{indent}} * the {@link #add(String, Object)} method.
                            {{indent}} *
                            {{indent}} * @see KeywordArguments
                            {{indent}} */""";
    private static final String KW_ARGS_BUILDER_JAVADOC_CONSTRUCTOR = """
                            {{indent+1}}/**
                            {{indent+1}} * The constructor enforces the initialization of all required named
                            {{indent+1}} * arguments for the associated Python function.
                            {{indent+1}} *
                            {{params}}
                            {{indent+1}} */""";
    
    private static final String KW_ARGS_BUILDER_JAVADOC_PARAM = """
                            {{indent+1}} * @param {{arg_name}} 
                            {{indent+1}} *        the value of the argument; can be null, which translates 
                            {{indent+1}} *        to the Python value {@code None}
                                     """;
    
    public static String getBuilderName(GeneratorContext context, FuncDef fn) {
        String name = fn.getName();
        if ("__init__".equals(fn.getName())) {
            ClassDef classDef = null;
            if (context.getCurrentNode() instanceof ClassDef cd) {
                classDef = cd;
            } else {
                GeneratorContext classContext =  context.getOuterClassContext();
                if (classContext != null && classContext.getCurrentNode() instanceof ClassDef cd) {
                    classDef = cd;
                }
            }
            if (classDef != null) {
                name = classDef.getName() + name;
            }
        }
                
        return GeneratorUtils.convertToJavaClassName(name) + "KwArgsBuilder";
    }
    
    private static final Map<String, String> generatedGenerators = new HashMap();
    
    public static boolean isAlreadyGenerated(FuncDef fn) {
        return generatedGenerators.containsKey(fn.fullname());
    }
    
    public static String getJavaBuilderFQN(FuncDef fn) {
        return generatedGenerators.get(fn.fullname());
    }
    
    public static String createKwArgsBuilder(GeneratorContext context, FuncDef fn) {
        
        List<ArgKind> argKinds = fn.getArgKinds();

        List<Type> argTypes = fn.getType() instanceof CallableType ct ? ct.getArgTypes() : null;
        List<String> argNames = fn.getArgNames();

        StringBuilder fieldMethods = new StringBuilder();
        StringBuilder constructorBody = new StringBuilder();
        StringBuilder constructorArgs = new StringBuilder();
        boolean hasRequired = false;
        boolean hasOptional = false;
        String type;
        String name;
        StringBuilder javadocConstructorParams = new StringBuilder();
        String pythonType;
        for (int i = 0; i < argKinds.size(); i++) {
            if (argKinds.get(i) == ARG_NAMED || argKinds.get(i) == ARG_NAMED_OPT || argKinds.get(i) == ArgKind.ARG_OPT) {
                type = argTypes == null ? "Object" : TypeManager.get().resolveJavaType(context, argTypes.get(i), true, "java.lang.Object");
                name = argNames.get(i);
                pythonType = OverloadArgsGenerator.createJavadocPythonType(argTypes == null ? null : argTypes.get(i));
                if ("Value".equals(type)) {
                    type = "Object";
                }
                
                if (argKinds.get(i) == ARG_NAMED_OPT || argKinds.get(i) == ArgKind.ARG_OPT) {
                    hasOptional = true;
                    fieldMethods.append(KW_ARGS_BUILDER_METHOD
                            .replace("{{field_name}}", name)
                            .replace("{{type}}", type)
                            .replace("{{python_type}}", pythonType));
                    fieldMethods.append("\n"); 
                } else {
                    javadocConstructorParams.append("{{indent+1}} * @param ")
                            .append(name).append('\n');
                    javadocConstructorParams.append("{{indent+1}} *        ")
                            .append(pythonType);
                    if (!hasRequired) {
                        hasRequired = true;
                    } else {
                        constructorArgs.append(", ");
                    }
                    constructorArgs.append(type).append(' ').append(name);
                    constructorBody.append(KW_ARGS_BUILDER_CONSTRUCTOR_ITEM
                            .replace("{{field_name}}", name)).append('\n');
                }
            } 
        }
        
        if (!fieldMethods.isEmpty()) {
            context.useJavaType("java.util.Map");
            context.useJavaType("java.util.HashMap");
            StringBuilder javadocClass = new StringBuilder(KW_ARGS_BUILDER_JAVADOC_CLASS_HEAD);
            String javadocConstructor =  "";
            if (hasRequired) {
                javadocClass.append(KW_ARGS_BUILDER_JAVADOC_CLASS_CONSTRUCTOR);
                javadocConstructor = KW_ARGS_BUILDER_JAVADOC_CONSTRUCTOR
                        .replace("{{params}}", javadocConstructorParams.toString());
            }
            javadocClass.append(KW_ARGS_BUILDER_JAVADOC_CLASS_OPTIONAL);
            String result = KW_ARGS_BUILDER_CLASS
                    .replace("{{field_methods}}", fieldMethods.toString().trim());
            result = result.replace("{{javadoc_class}}", javadocClass.toString());
            result = result.replace("{{javadoc_constructor}}", javadocConstructor);
            result = result.replace("{{mandatory_args}}", constructorArgs.toString());
            result = result.replace("{{constructor_body}}", constructorBody.toString().trim());
            String builderName = getBuilderName(context, fn);
            result = result.replace("{{builder_name}}", builderName);
            generatedGenerators.put(fn.getFullname(), context.getJavaFQN() + "." + builderName);
            return result;
        }
        return "";
    }
}

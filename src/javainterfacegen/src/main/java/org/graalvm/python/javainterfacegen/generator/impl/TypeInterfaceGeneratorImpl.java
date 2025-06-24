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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.generator.GeneratorUtils;
import org.graalvm.python.javainterfacegen.generator.PythonFQNResolver;
import org.graalvm.python.javainterfacegen.generator.TransformerVisitor;
import org.graalvm.python.javainterfacegen.generator.TypeGenerator;
import org.graalvm.python.javainterfacegen.generator.TypeManager;
import org.graalvm.python.javainterfacegen.generator.TypeNameGenerator;
import org.graalvm.python.javainterfacegen.mypy.types.Instance;
import org.graalvm.python.javainterfacegen.mypy.types.NoneType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.mypy.types.UnionType;

public class TypeInterfaceGeneratorImpl implements TypeGenerator {

    private static final String TEMPLATE_INTERFACE = """
            {{license}}

            {{generatedinfo}}

            package {{package}};

            {{imports}}

            {{javadoc}}
            public interface {{name}} extends {{extends}} {
            {{type_literals}}
            {{content}}
            }
            """;

    private static final String TEMPLATE_IS = """
            {{indent}}static boolean is{{type_name}}(Object object) {
            {{indent+1}}Value value = Value.asValue(object);                      
            {{indent+1}}Value metaObject = value.getMetaObject();
            {{indent+1}}String moduleName = metaObject.getMember("__module__").asString();
            {{indent+1}}String typeName = metaObject.getMember("__name__").asString();
            {{indent+1}}String fqn = moduleName + "." + typeName;
            {{indent+1}}return "{{python_fqn}}".equals(fqn);
            {{indent}}}
            """;

    private static final String TEMPLATE_CAST = """
            {{indent}}static {{java_type}} cast(Object object) {
            {{indent+1}}if (is{{type_name}}(object)) {
            {{indent+2}}Value v = Value.asValue(object);
            {{indent+2}}return v.as({{as_java_type}});
            {{indent+1}}}
            {{indent+1}}throw new ClassCastException();
            {{indent}}}
            """;

    private static final String TEMPLATE_TYPELITERAL
            = "{{indent}}static TypeLiteral<{{java_type}}> {{name}} = new TypeLiteral<{{java_type}}>(){};";

    @Override
    public String createType(Type type, GeneratorContext context, String javaPackage, String name) {
        String template = TEMPLATE_INTERFACE;
        template = template.replace("{{license}}", GeneratorUtils.getLicense(context));

        template = template.replace("{{generatedinfo}}", context.addTimestamp() ? GeneratorUtils.generateTimeStamp() : "");

        template = template.replace("{{package}}", javaPackage);

        Configuration config = context.getConfig();

        GeneratorContext fileContext = new GeneratorContext(null, config, null, true);
        fileContext.setJavaFQN(javaPackage + "." + name);
        fileContext.addImport("org.graalvm.polyglot.Value");

        template = template.replace("{{javadoc}}", GeneratorUtils.createJavaDoc("Generated from Python type: " + type.toString()));
        template = template.replace("{{name}}", name);

        template = template.replace("{{extends}}", createExtendsExpr(type, fileContext));
        fileContext.increaseIndentLevel();

        List<String> typeLiterals = new ArrayList();
        template = template.replace("{{content}}", createBody(type, fileContext, typeLiterals));

        template = template.replace("{{imports}}", GeneratorUtils.generateImports(fileContext.getImports()));

        if (typeLiterals.isEmpty()) {
            template = template.replace("{{type_literals}}", "");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String typeLiteral : typeLiterals) {
                sb.append(typeLiteral).append("\n");
            }
            template = template.replace("{{type_literals}}", sb.toString());
        }
        template = GeneratorUtils.indentTemplate(fileContext, template);
        fileContext.decreaseIndentLevel();
        try {
            GeneratorUtils.saveFile(context, javaPackage, name, template);
        } catch (IOException ex) {
            Logger.getLogger(TransformerVisitor.class.getName()).log(Level.SEVERE, null, ex);
        }
        return template;
    }

    private static final Set<String> primitiveTypes = new HashSet<>(Arrays.asList(
            "boolean", "byte", "char", "short", "int", "long", "float", "double"));
    
    private String createExtendsExpr(Type type, GeneratorContext context) {
        StringBuilder sb = new StringBuilder();
        if (type instanceof UnionType ut) {
            List<Type> items = ut.getItems();
            for (int i = 0; i < items.size(); i++) {
                Type eType = items.get(i);
                if (!(eType instanceof NoneType)){
                    String javaType = TypeManager.get().resolveJavaType(context, eType, false, context.getDefaultJavaType());
                    if (!primitiveTypes.contains(javaType)) {
                        if (sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(javaType);
                    }
                }
            }
        } else if (type instanceof Instance instance && isSupported(instance)) {
            String javaType = TypeManager.get().resolveJavaType(context, instance, false, context.getDefaultJavaType());
            sb.append(javaType);  
        } else {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        return sb.toString();
    }

    private static boolean isSupported(Instance instance) {
        return "builtins.list".equals(instance.getType().getFullname());
    }
    
    private static String createBody(Type type, GeneratorContext context, List<String> typeLiterals) {
        StringBuilder sb = new StringBuilder();
        if (type instanceof UnionType ut) {
            List<Type> items = ut.getItems();
            for (int i = 0; i < items.size(); i++) {
                Type eType = items.get(i);
                sb.append(createIsMethod(eType, context));
                sb.append("\n");
                if (!(eType instanceof NoneType)) {
                    sb.append(createCastMethod(eType, context, typeLiterals));
                    sb.append("\n");
                }
            }
        } else if (type instanceof Instance instance && isSupported(instance)) {
            sb.append(createIsMethod(instance, context));
                sb.append("\n");
                if (!(instance instanceof NoneType)) {
                    sb.append(createCastMethod(instance, context, typeLiterals));
                    sb.append("\n");
                }
        } else {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        return sb.toString();
    }

    private static String createJavaName(String pythonFQN, Type type) {
        String javaName;
        if ("builtins.list".equals(pythonFQN)) {
            javaName = "List";
        } else {
            javaName = TypeNameGenerator.createName(type);
        }
        while (javaName.endsWith("_")) {
            javaName = javaName.substring(0, javaName.length() - 1);
        }
        return javaName;
    }
    
    private static String createIsMethod(Type type, GeneratorContext context) {
        String template = TEMPLATE_IS;
        String pythonFQN = PythonFQNResolver.findPythonFQN(type);
        String javaName = createJavaName(pythonFQN, type);
        
        template = template.replace("{{type_name}}", javaName);
        template = template.replace("{{python_fqn}}", pythonFQN);
        return template;
    }

    private static String createCastMethod(Type type, GeneratorContext context, List<String> typeLiterals) {
        String template = TEMPLATE_CAST;
        
        String javaType = TypeManager.get().resolveJavaType(context, type, false, "org.graalvm.polyglot.Value");
        String pythonFQN = PythonFQNResolver.findPythonFQN(type);
        String javaName = createJavaName(pythonFQN, type);
        
        template = template.replace("{{type_name}}", javaName);
        template = template.replace("{{java_type}}", javaType);

        String asJavaType;
        switch (pythonFQN) {
            case "builtins.list":
            case "builtins.dict":
                context.addImport("org.graalvm.polyglot.TypeLiteral");
                String typeLiteral = TEMPLATE_TYPELITERAL;
                typeLiteral = typeLiteral.replace("{{java_type}}", javaType);
//                String javaNameUpper = javaName.toUpperCase();
                typeLiteral = typeLiteral.replace("{{name}}", "TYPE_LITERAL");
                typeLiterals.add(typeLiteral);
                asJavaType = "TYPE_LITERAL";
                break;
            default:
                asJavaType = javaType + ".class";
        }
        template = template.replace("{{as_java_type}}", asJavaType);
        return template;
    }

}

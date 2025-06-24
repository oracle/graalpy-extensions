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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.generator.GeneratorFactory;
import org.graalvm.python.javainterfacegen.generator.GeneratorUtils;
import org.graalvm.python.javainterfacegen.generator.NameGenerator;
import org.graalvm.python.javainterfacegen.generator.PythonFQNResolver;
import org.graalvm.python.javainterfacegen.generator.TypeManager;
import org.graalvm.python.javainterfacegen.mypy.nodes.ClassDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.Node;
import org.graalvm.python.javainterfacegen.mypy.nodes.Var;
import org.graalvm.python.javainterfacegen.mypy.types.ArgKind;
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.python.Utils;

public class FunctionGeneratorImpl implements org.graalvm.python.javainterfacegen.generator.FunctionGenerator {

    private static final String TEMPLATE = "public {{returntype}} {{functionname}}({{args}})";
    private static final String TEMPLATE_RECORD
            = """
{{indent}}public static record {{recordname}} ({{args}}) {
{{indent+1}}public {{recordname}}() {
{{indent+2}}this({{null}});
{{indent+1}}}
{{indent}}}

""";

    private static final String TEMPLATE_BUILDER_CLASS
            = """
{{indent}}public static final class {{buildername}} {

{{fieldmethods}}
{{indent+1}}public {{recordname}} build() {
{{indent+2}}return new {{recordname}}({{fields}});
{{indent+1}}}
{{indent}}}

""";

    private static final String TEMPLATE_BUILDER_FIELD
            = """
{{indent+1}}private {{type}} {{fieldname}} = null;
{{indent+1}}public {{buildername}} {{fieldname}}({{type}} {{fieldname}}) {
{{indent+2}}this.{{fieldname}} = {{fieldname}};
{{indent+2}}return this;
{{indent+1}}}
              
""";

    @Override
    public String createSignature(Node node, GeneratorContext context) {
        if (node instanceof FuncDef funcDef) {
            return create(funcDef, context);
        }
        if (node instanceof Var v) {
            return create(v, context);
        }
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    public String create(FuncDef funcDef, GeneratorContext context) {
        if (!(funcDef.getType() instanceof CallableType)) {
            System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! funkce s ne callable typem?????");
            System.out.println(funcDef.getType().toString());
            System.out.println(funcDef.getName() + "   " + funcDef.getFullname());
            return "";
        }
        CallableType ct = (CallableType)funcDef.getType();
        String returnType = TypeManager.get().resolveJavaType(context, ct.getRetType(), false, context.getDefaultJavaType());
        if (funcDef.isProperty()) {
            // property defined through @property 
            String nameGeneratorClassName = (String) context.getConfiguration().get(Configuration.P_NAME_GENERATOR);
            NameGenerator nameGen = GeneratorFactory.createNameGenerator(nameGeneratorClassName);
            String functionName = nameGen.getterName(funcDef, context);
            StringBuilder sb = new StringBuilder();
            
            String javadoc = GeneratorUtils.getJavadoc(funcDef, context);
            if (javadoc != null && !javadoc.isEmpty()) {
                sb.append(javadoc).append('\n');
            } else if (context.getConfig().addLogComments(context)) {
                sb.append(GeneratorUtils.getMissingJavadocComment(funcDef.getFullname(), context));
            }
            sb.append("{{indent}}").append(createSignature(context, returnType, functionName, ""));
            return sb.toString();
        }
//        System.out.println("funcDef.getType(): " + funcDef.getType().getValue().toString());

//        System.out.println("Return type: " + Utils.getFullyQualifedName(funcDef.getType().getRetType().getValue()));
//        System.out.println("class: " + funcDef.getType().getRetType().getClass().getName());
//        System.out.println("python type: " + returnPythonType);

//        String returnType = context.useType(returnPythonType);
//        System.out.println("java type: " + returnType);

        String nameGeneratorClassName = (String) context.getConfiguration().get(Configuration.P_NAME_GENERATOR);
        NameGenerator nameGen = GeneratorFactory.createNameGenerator(nameGeneratorClassName);
        String functionName = nameGen.methodName(funcDef, context);

        // we need the info obtain from the type, due to performance
        
        List<String> argNames = ct.getArgNames();
        List<Type> argTypes = ct.getArgTypes();
        List<ArgKind> argKinds = ct.getArgKinds();

        List<Integer> posArgs = new ArrayList();
        List<Integer> optArgs = new ArrayList();

        for (int i = 0; i < argKinds.size(); i++) {
            ArgKind kind = argKinds.get(i);
            switch (kind) {
                case ARG_POS:
                    posArgs.add(i);
                    break;
                case ARG_OPT:
                    optArgs.add(i);
                    break;
                case ARG_NAMED:
                case ARG_NAMED_OPT:
                    optArgs.add(i);
                    break;
                case ARG_STAR:
                case ARG_STAR2:
                    // TODO
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported kind of args: " + kind
                            + " PythonType: " + Utils.getFullyQualifedName(kind.getItem().getValue())
                            + " " + kind.getItem().getValue());

            }
        }

        StringBuilder argsText = new StringBuilder();

        for (int i = 0; i < posArgs.size(); i++) {
            String argName = argNames.get(posArgs.get(i));
            Type argType = argTypes.get(posArgs.get(i));
            String argFQNType = PythonFQNResolver.findPythonFQN(argType);
            if (!isSelfArg(argFQNType, context)) {
                if (argsText.length() > 0) {
                    argsText.append(", ");
                }
                argsText.append(TypeManager.get().resolveJavaType(context, argType, true, "java.lang.Object")).append(' ').append(argName);
            } else {
//                System.out.println("name: " + argName);
//                System.out.println("type: " + argType.getValue().toString());
//                System.out.println(context.getParent().getCurrentNode());
//                System.out.println(((ClassDef) context.getParent().getCurrentNode()).getFullname());
            }
        }

        StringBuilder result = new StringBuilder();

        String javadoc = GeneratorUtils.getJavadoc(funcDef, context);
        if (javadoc != null && !javadoc.isEmpty()) {
            result.append(javadoc).append('\n');
        } else if (context.getConfig().addLogComments(context)) {
            result.append(GeneratorUtils.getMissingJavadocComment(funcDef.getFullname(), context));
        }
        result.append("{{indent}}").append(createSignature(context, returnType, functionName, argsText.toString()));
        
        if (!optArgs.isEmpty()) {
            if (argsText.length() > 0) {
                argsText.append(", ");
            }
            if (optArgs.size() == 1) {
                // TODO this has to be configurable and more clever
                // we need to realize, if we want to make more signatures from it, then it can not be with the same type

                String argName = argNames.get(optArgs.get(0));
                Type argType = argTypes.get(optArgs.get(0));
                String argFQNType = PythonFQNResolver.findPythonFQN(argType);
                argsText.append(context.useType(argFQNType)).append(' ').append(argName);

            } else {

                String recordName = GeneratorUtils.uppercaseFirstLetter(functionName) + "Args";
                result.insert(0, createBuilder(context, recordName + "Builder", recordName, argNames, argTypes, optArgs));
                result.insert(0, createRecord(context, recordName, argNames, argTypes, optArgs));
                argsText.append(recordName).append(" optionalArgs");
            }

            result.append(";\n{{indent}}").append(createSignature(context, returnType, functionName, argsText.toString()));
//            result.append("\n");
        }

//        
        return result.toString();

    }

    public String create(Var v, GeneratorContext context) {

        Type type = v.getType();
//        String returnPythonType = PythonFQNResolver.findPythonFQN(type);
        String returnType = TypeManager.get().resolveJavaType(context, type, false, context.getDefaultJavaType());
//        if (type instanceof Instance) {
//            List<Type> args = ((Instance) type).getArgs();
//            if (args.size() > 0) {
//
//                if (returnType.equals("List") || returnType.equals("Set")) {
//                    boolean first = true;
//                    if (args.size() == 1) {
//                        String pythonType = PythonFQNResolver.findPythonFQN(args.get(0));
//                        String javaType = context.useType(pythonType);
//                        if ("int".equals(javaType)) {
//                            javaType = "Integer";
//                        }
//                        returnType = returnType + "<" + javaType + ">";
//                    } else {
//                        System.out.println("@@@@@@@@@@@@@@unhanled more args of type");
//                    }
//
//                } else {
//                    System.out.println("unprocessed");
//                    for (int i = 0; i < args.size(); i++) {
//                        System.out.println("args: " + args.get(i).toString());
//                    }
//                }
//            }
//        }
        String nameGeneratorClassName = (String) context.getConfiguration().get(Configuration.P_NAME_GENERATOR);
        NameGenerator nameGen = GeneratorFactory.createNameGenerator(nameGeneratorClassName);
        String functionName = nameGen.getterName(v, context);

        StringBuilder sb = new StringBuilder();
        String javadoc = GeneratorUtils.getJavadoc(v, context);
        if (javadoc != null && !javadoc.isEmpty()) {
            sb.append(javadoc);
        } else if (context.getConfig().addLogComments(context)) {
            sb.append(GeneratorUtils.getMissingJavadocComment(v.fullname(), context));
        }
        sb.append("{{indent}}").append(createSignature(context, returnType, functionName, ""));
        return sb.toString();
    }

    private boolean isSelfArg(String argFQNType, GeneratorContext context) {
        GeneratorContext parent = context.getParent();
        if (parent != null && parent.getCurrentNode() instanceof ClassDef) {
            ClassDef classDef = (ClassDef) parent.getCurrentNode();
            return argFQNType != null && argFQNType.endsWith(classDef.getFullname());
        }
        return false;
    }

    private String createSignature(GeneratorContext context, String returnType, String name, String args) {
        String sig = TEMPLATE;
        sig = sig.replace("{{indent}}", GeneratorUtils.indent(context));
        sig = sig.replace("{{returntype}}", returnType);
        sig = sig.replace("{{functionname}}", name);
        sig = sig.replace("{{args}}", args);
        return sig;
    }

    private String createRecord(GeneratorContext context, String recordName, List<String> argNames, List<Type> argTypes, List<Integer> optArgs) {
        String result = TEMPLATE_RECORD;

        // TODO indent, but should be solved by a defined formatter. 
        result = GeneratorUtils.indentTemplate(context, result);
        result = result.replace("{{recordname}}", recordName);

        StringBuilder argsText = new StringBuilder();
        for (int i = 0; i < optArgs.size(); i++) {
            String argName = GeneratorUtils.convertToJavaIdentifierName(argNames.get(optArgs.get(i)));
            Type argType = argTypes.get(optArgs.get(i));
            String argFQNType = PythonFQNResolver.findPythonFQN(argType);
//            System.out.println(argType.getValue() + "->" + argFQNType);
            if (!isSelfArg(argFQNType, context)) {
                if (argsText.length() > 0) {
                    argsText.append(", ");
                }
                argsText.append(checkOrdinaryTypes(context.useType(argFQNType))).append(' ').append(argName);

            } else {
//                System.out.println("name: " + argName);
//                System.out.println("type: " + argType.getValue().toString());
//                System.out.println(context.getParent().getCurrentNode());
//                System.out.println(((ClassDef) context.getParent().getCurrentNode()).getFullname());
            }
        }

        result = result.replace("{{args}}", argsText.toString());

        String nullText = "null, ";
        nullText = nullText.repeat(optArgs.size() - 1);
        nullText = nullText + "null";

        result = result.replace("{{null}}", nullText);
        return result;
    }

    private Map<String, String> ordToObject = null;

    private String checkOrdinaryTypes(String type) {
        if (ordToObject == null) {
            ordToObject = new HashMap<>();
            ordToObject.put("boolean", "Boolean");
            ordToObject.put("int", "Integer");
            ordToObject.put("double", "Double");
        }

        if (ordToObject.containsKey(type)) {
            return ordToObject.get(type);
        }
        return type;
    }

    private String createBuilder(GeneratorContext context, String builderName, String recordName, List<String> argNames, List<Type> argTypes, List<Integer> optArgs) {
        String result = TEMPLATE_BUILDER_CLASS;

        StringBuilder fieldsMethodsText = new StringBuilder();
        StringBuilder fieldsText = new StringBuilder();
        for (int i = 0; i < optArgs.size(); i++) {

            String argName = GeneratorUtils.convertToJavaIdentifierName(argNames.get(optArgs.get(i)));
            if (fieldsText.length() > 0) {
                fieldsText.append(", ");
            }
            fieldsText.append(argName);
            Type argType = argTypes.get(optArgs.get(i));
            String argFQNType = PythonFQNResolver.findPythonFQN(argType);

            String fieldText = TEMPLATE_BUILDER_FIELD;
            fieldText = fieldText.replace("{{fieldname}}", argName);
            fieldText = fieldText.replace("{{type}}", checkOrdinaryTypes(context.useType(argFQNType)));

            fieldsMethodsText.append(fieldText);
        }

        result = result.replace("{{fieldmethods}}", fieldsMethodsText.toString());
        result = GeneratorUtils.indentTemplate(context, result);
        result = result.replace("{{args}}", fieldsMethodsText.toString());
        result = result.replace("{{buildername}}", builderName);
        result = result.replace("{{recordname}}", recordName);
        result = result.replace("{{fields}}", fieldsText.toString());

        return result;
    }

    @Override
    public String createImplementation(Node node, GeneratorContext context) {
        if (node instanceof Var v) {
            return createGetter(v, context);
        }

        return "{{indent}}// TODO provide function implementation from " + node.toString();
    }

    protected String createGetter(Var v, GeneratorContext context) {
        // TODO this should be configurable
        boolean generateFieldGetters = true;
        StringBuilder sb = new StringBuilder();
        if (generateFieldGetters) {

            sb.append("{{indent}}@Override\n");
            String text = createSignature(v, context);
            sb.append("{{indent}}").append(text);
            String returnType = TypeManager.get().resolveJavaType(context, v.getType(), false, context.getDefaultJavaType());
            StringBuilder body = new StringBuilder();
            if (!TypeManager.isCollection(returnType)) {
                body.append("return getValue().getMember(\"");
                body.append(v.getName());
                body.append("\")");
                switch (returnType) {
                    case TypeManager.PYTHON_BOOLEAN:
                        body.append(".asBoolean();");
                        break;
                    case TypeManager.PYTHON_INT:
                        body.append(".asInt();");
                        break;
                    case TypeManager.PYTHON_STR:
                        body.append(".asString();");
                        break;
                    case "mypy.types.AnyType":
                        body.append(";");
                        break;
                    default:
                        body.append("; // TODO handle ").append(returnType);
                }
            } else {
                
            }
            sb.append(" {\n{{indent+1}}").append(body.toString());
            sb.append("\n{{indent}}}\n");
        }

        return sb.toString();
    }
}

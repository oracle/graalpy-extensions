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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.python.javainterfacegen.generator.FunctionGenerator;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.generator.GeneratorUtils;
import org.graalvm.python.javainterfacegen.generator.PythonFQNResolver;
import org.graalvm.python.javainterfacegen.generator.TypeManager;
import org.graalvm.python.javainterfacegen.mypy.nodes.Decorator;
import org.graalvm.python.javainterfacegen.mypy.nodes.DefaultNodeVisitor;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncBase;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.MypyFile;
import org.graalvm.python.javainterfacegen.mypy.nodes.Node;
import org.graalvm.python.javainterfacegen.mypy.nodes.OverloadedFuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.SymbolNode;
import org.graalvm.python.javainterfacegen.mypy.nodes.SymbolTableNode;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeInfo;
import org.graalvm.python.javainterfacegen.mypy.nodes.Var;
import org.graalvm.python.javainterfacegen.mypy.types.ArgKind;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_NAMED;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_NAMED_OPT;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_POS;
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.NoneType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.mypy.types.UnionType;

public class JustInterfacesGeneratorImpl implements FunctionGenerator {

    private static String TEMPLATE_FUNCTION_SIGNATURE = """
            {{javadoc}}
            {{indent}}{{return_type}} {{name}}({{args}});""";
    
    @Override
    public String createSignature(Node node, GeneratorContext context) {
        if (node instanceof OverloadedFuncDef of) {
            return process(of, context);
        }
        if (node instanceof FuncDef funcDef) {
////            if (!funcDef.isOverloaded()) {
////                if (hasAllDefaultArgs(funcDef)) {
////                    String result = create(funcDef, context, false);
////                    
////                    if (funcDef.getType() instanceof CallableType ct) {
////                        List<ArgKind> argKinds = ct.getArgKinds();
////                        boolean hasInterestingArgs = false;
////                        for (int i = 1; i < argKinds.size(); i++) {
////                            ArgKind kind = argKinds.get(i);
////                            if (kind != ARG_STAR && kind != ARG_STAR2 ) {
////                                hasInterestingArgs = true;
////                                break;
////                            }
////                        }
////                        if (hasInterestingArgs) {
////                            result = result + "\n\n" + create(funcDef, context);
////                        }
////                    }
////                    return result;
////                }
////                
//            }
            return create(funcDef, context);
        }
        if (node instanceof Var v) {
            return create(v, context);
        }
        if (node instanceof MypyFile mypyFile) {
            return create(mypyFile, context);
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String createImplementation(Node node, GeneratorContext context) {
        return "// no implementation";
    }

    private String create(FuncDef funcDef, GeneratorContext context) {
        return create(funcDef, context, null, true, null);
    }
    
    
    private String create(FuncDef funcDef, GeneratorContext context, boolean addArgs) {
        return create(funcDef, context, null, addArgs, null);
    }
    
    private String create(FuncDef funcDef, GeneratorContext context, List<List<OverloadArgsGenerator.Argument>> existingVariations) {
        return create(funcDef, context, null, true,  existingVariations);
    }
    
    private String create(FuncDef funcDef, GeneratorContext context, boolean addArgs, List<List<OverloadArgsGenerator.Argument>> existingVariations) {
        return create(funcDef, context, null, addArgs,  existingVariations);
    }
    
    private String create(FuncDef funcDef, GeneratorContext context, Type returnType, List<List<OverloadArgsGenerator.Argument>> existingVariations) {
        return create(funcDef, context, null, true, existingVariations);
    }
    
    private String create(FuncDef funcDef, GeneratorContext context, Type returnType, boolean addArgs, List<List<OverloadArgsGenerator.Argument>> existingVariations) {
        CallableType ct = null; 
        
        if (funcDef.getType() instanceof CallableType) {
            ct = (CallableType) funcDef.getType();
        } else if (funcDef.isProperty() && context.getParent().getCurrentNode() instanceof Decorator decorator) {
            if (decorator.getType() instanceof CallableType) {
                ct = (CallableType)decorator.getType(); 
                if (ct.getArgKinds().size() == 1) {
                    addArgs = false;
                }
            }
        }
        
        returnType = returnType == null ? ct == null ? TypeConstants.ANY_TYPE : ct.getRetType() : returnType;
        GeneratorContext classContext = context.getOuterClassContext();
        if (classContext != null) {
            // we need to find all methods, from mro
            
            if (classContext.getParent() != null && classContext.getParent().getCurrentNode() instanceof TypeInfo typeInfo){
                List<FuncBase> sameFunctions = typeInfo.accept(new MethodFinder(funcDef.getName()));
                
                for (FuncBase fnParent : sameFunctions) {
                    Type returnTypeInParent = fnParent.getType() instanceof CallableType ctp? ctp.getRetType() : TypeConstants.ANY_TYPE;
                    if (!returnType.toString().equals(returnTypeInParent.toString())) {
                        // TODO
                        // 1. probably we should check the overloaded functions as well
                        // 2. we should retrun Value in both functions, but we are no
                        // able to change the type in already generated file
                        // 3. should we generate it at all?
                        returnType = returnTypeInParent;
                        break;
                    }
                }
            }
        }

        String returnTypeText = context.overridedReturnType(funcDef.getName());
        if (returnTypeText == null) {
            returnTypeText = context.overridedReturnType(funcDef.getFullname());
        }
        if (returnTypeText == null) {
            returnTypeText = TypeManager.get().resolveJavaType(context, returnType, false, context.getDefaultJavaType());
            returnTypeText = TypeManager.javaWrapperToPrimitive(returnTypeText);
        }
        String result = TEMPLATE_FUNCTION_SIGNATURE;
        String javadoc = GeneratorUtils.getJavadoc(funcDef, context);
        if (javadoc.isEmpty() && context.getConfig().addLogComments(context)) {
            String log = (GeneratorUtils.getMissingJavadocComment(funcDef.getFullname(), context));
            log = log + "\n{{indent}}{{javadoc}}";
            result = result.replace("{{javadoc}}", log);
        }
        
        result = result.replace("{{return_type}}", returnTypeText);
        result = result.replace("{{name}}", funcDef.getName());
        if (addArgs) {
            List<List<OverloadArgsGenerator.Argument>> argsVariations = OverloadArgsGenerator.generateVariations(context, funcDef);
            StringBuilder overloadedFn = new StringBuilder();
            boolean isFirst = true;
            for (List<OverloadArgsGenerator.Argument> argsVariation : argsVariations) {
                if (!(existingVariations != null && existingVariations.contains(argsVariation))) {
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        overloadedFn.append("\n");
                    }
                    overloadedFn.append(result
                            .replace("{{javadoc}}", 
                                    composeJavadoc(context, javadoc, 
                                            OverloadArgsGenerator.createJavadocParams(context, funcDef,  argsVariation), returnType))
                            .replace("{{args}}", OverloadArgsGenerator.createArgsText(context, argsVariation)));
                    overloadedFn.append("\n");
                    if (existingVariations != null) {
                        existingVariations.add(argsVariation);
                    }
                }
            }
            if (!overloadedFn.isEmpty()) {
                result = overloadedFn.toString().substring(0, overloadedFn.length() - 2);
            }
        } else {
            if (!(existingVariations != null && existingVariations.contains(Collections.EMPTY_LIST))) {
                result = result.replace("{{args}}", "")
                        .replace("{{javadoc}}", javadoc);
                if (existingVariations != null) {
                    existingVariations.add(Collections.EMPTY_LIST);
                }
            } else {
                return "";
            }
        }
        if (!KwArgBuilderGenerator.isAlreadyGenerated(funcDef)) {
            result = KwArgBuilderGenerator.createKwArgsBuilder(context, funcDef) + result;
        }
        return result;
    }
    
    
    private String create(Var v, GeneratorContext context) {
        Type type = v.getType();
        String result = "";
        String returnType = context.overridedReturnType(v.getName());
        if (returnType == null) {
            returnType = context.overridedReturnType(v.getFullname());
        }
        if (returnType == null) {
            returnType = TypeManager.get().resolveJavaType(context, type, false, context.getDefaultJavaType());
        } else {
            if (context.getConfig().addLogComments(context)) {
                result = "{{indent}}// result type is overrided with config to: " + returnType + "\n";
            }
        }
        
        result = result + TEMPLATE_FUNCTION_SIGNATURE;
        
        StringBuilder javadoc = new StringBuilder(GeneratorUtils.getJavadoc(v, context));
        if (javadoc.isEmpty() && context.getConfig().addLogComments(context)) {
            javadoc.append(GeneratorUtils.getMissingJavadocComment(v.getFullname(), context));
        }
        
        result = result.replace("{{javadoc}}", javadoc.toString());
        if (GeneratorUtils.isInvalidJavaIdentifier(v.getName())) {
            result = result.replace("{{return_type}}", "default " + returnType);
            result = result.replace("{{name}}", "get" + GeneratorUtils.uppercaseFirstLetter(v.getName()));
            result = result.replace("{{args}}", "");
            String baseGetter = String.format("Value.asValue(this).getMember(\"%s\")", v.getName());
            if (returnType.equals("Value")) {
                result = result.replace(";", String.format("{ return %s; }", baseGetter));
            } else if (GeneratorUtils.JAVA_PRIMITIVE_TYPES.contains(returnType)) {
                result = result.replace(";", String.format("{ return %s.as%s(); }", baseGetter, GeneratorUtils.uppercaseFirstLetter(returnType)));
            } else {
                result = result.replace(";", String.format("{ return %s.as(%s.class); }", baseGetter, returnType));
            }
        } else {
            result = result.replace("{{return_type}}", returnType);
            result = result.replace("{{name}}", v.getName());
            result = result.replace("{{args}}", "");
        }
        return result;
    }

    private String create(MypyFile mypyFile, GeneratorContext context) {
        return "nejaky bla\n {{content}} \n zase nejaky bla";
    }
    
    private String process(OverloadedFuncDef oFnDef, GeneratorContext context) {
        StringBuilder sb = new StringBuilder();
        List<SymbolNode> items = oFnDef.items(); 

        // Key - signature withough return type
        List<List<FuncDef>> functions = new ArrayList();
        List<FuncDef> noArgFns = new ArrayList();
        for (int i = 0; i < items.size(); i++) {
            SymbolNode item = items.get(i);
            FuncDef fn = null;
            if (item instanceof Decorator dec) {
                fn = dec.getFunc();
            } else if (item instanceof FuncDef fd) {
                fn = fd;
            } else {
                sb.append("\n{{indent}}// TODO handle OverloadedFuncDef ");
                sb.append(oFnDef.name()).append(" item: ").append(item.toString());
            }
            if (fn != null) {
                if (hasAllDefaultArgs(fn)) {
                    noArgFns.add(fn);
                }
                boolean alreadyIsHere = false;
                for (List<FuncDef> fns : functions) {
                    if (sameArguments(fns.get(0), fn)) {
                        fns.add(fn);
                        alreadyIsHere = true;
                        break;
                    }
                }
                if (!alreadyIsHere) {
                    List<FuncDef> fns = new ArrayList();
                    fns.add(fn);
                    functions.add(fns);
                }

            }
        }
        Set<Type> returnTypes = new HashSet();
        Set<String> returnTypesText = new HashSet();
        List<List<OverloadArgsGenerator.Argument>> generatedVariation = new ArrayList<>();
        for (List<FuncDef> fns : functions) {
            sb.append("\n");
            if (fns.size() > 1) {
                for (FuncDef fn : fns) {
                    Type type = fn.getType();
                    if (type instanceof CallableType ct) {
                        Type retType = ct.getRetType();
                        String returnTypeText = retType.toString();
                        if (!returnTypesText.contains(returnTypeText)) {
                            returnTypesText.add(returnTypeText);
                            if (retType instanceof UnionType union) {
                                List<Type> unionItems = union.getItems();
                                for(int i = 0; i < unionItems.size(); i++) {
                                    returnTypes.add(unionItems.get(i));
                                }
                            } else {
                                returnTypes.add(retType);
                            }
                        }
                        if (ct.getMinArgs() == 0) {
                            sb.append(create(fn, context, false, generatedVariation)).append("\n\n");
//                        System.out.println("^^^^^0 " + ct.getRetType() + " " + ct.getName() + "()");
//                        System.out.println("   ::" + fn.getType().toString());
                        }
                    }
                }
//                System.out.println("^^^^^+ " + returnTypes + " " + fns.get(0).getName() + "( nejake parametery)");
                Type rt = returnTypes.size() == 1 ? returnTypes.iterator().next() : UnionType.createUnionType(List.copyOf(returnTypes));
                sb.append(create(fns.getLast(), context, rt, generatedVariation)).append(";\n\n");
//                for (FuncDef fn : fns) {
//                    System.out.println("   ::" + fn.getType().toString());
//                }
            } else {
                CallableType ct = (CallableType)fns.get(0).getType();
                List<ArgKind> argKinds = ct.getArgKinds();
                // ignore function where args like (*, .....)
                // TODO ignore also return types???
                if(argKinds.isEmpty() || argKinds.get(0) != ARG_NAMED_OPT) {
                    sb.append(create(fns.get(0), context, generatedVariation)).append(";\n\n");
//                    System.out.println("^^^^^- " + ct.getRetType() + " " + fns.get(0).getName() + "( nejake parametery)");
//                    System.out.println("   ::" + ct.toString());
                }
            }
            
        }
        if (!sb.isEmpty()) {
            sb.delete(sb.length() - 1, sb.length());
        } else {
            sb.append("\n{{indent}}// WARNING nothing generated for overloaded function.\n");
            sb.append("{{indent}}// Mypy reports "). append(oFnDef.items().size());
            sb.append(" items for function ").append(oFnDef.getFullname()).append(".\n");
        }
        return sb.toString();
    }

    private boolean hasAllDefaultArgs(FuncDef fn) {
        if (fn.getType() instanceof CallableType ct) {
        
            List<ArgKind> argKinds = ct.getArgKinds();
            if (argKinds.isEmpty()) {
                return true;
            }
            boolean isMandatory = false;
            
            for (int i = 0; i < argKinds.size(); i++) {
                ArgKind kind = argKinds.get(i);
                if (kind == ARG_POS || kind == ARG_NAMED) {
                    if (!(i == 0 && !fn.isClass()) || i > 0 ) {
                        isMandatory = true;
                        break;
                    }
                }
            }
            return !isMandatory;
        }
        return false;
    }
    
    private boolean sameArguments(FuncDef fn1, FuncDef fn2) {
        if(fn1.getType() instanceof NoneType && fn2.getType() instanceof NoneType) {
            return true;
        } else if ((fn1.getType() instanceof CallableType && fn2.getType() instanceof NoneType)
                || (fn1.getType() instanceof NoneType && fn2.getType() instanceof CallableType)) {
            return false;
        }
        CallableType ct1 = (CallableType) fn1.getType();
        CallableType ct2 = (CallableType) fn2.getType();

        List<Type> argTypes1 = ct1.getArgTypes();
        List<Type> argTypes2 = ct2.getArgTypes();

        boolean areSame = true;
        if (argTypes1.size() != argTypes2.size()) {
            areSame = false;
        } else if (argTypes1.size() != 0) {
            List<ArgKind> argKinds1 = ct1.getArgKinds();
            List<ArgKind> argKinds2 = ct2.getArgKinds();
            for (int i = 0; i < argKinds1.size(); i++) {
//                ArgKind argKind1 = argKinds1.get(i);
//                ArgKind argKind2 = argKinds2.get(i);
//                if (!argKind1.equals(argKind2)) {
//                    areSame = false;
//                    break;
//                }
                Type argType1 = argTypes1.get(i);
                Type argType2 = argTypes2.get(i);
                // TODO this is not very nice method, how to find if the types are the same
                String pythonFQN1 = PythonFQNResolver.findPythonFQN(argType1);
                String pythonFQN2 = PythonFQNResolver.findPythonFQN(argType2);
                if (pythonFQN1 != null && !pythonFQN1.equals(pythonFQN2)) {
                    areSame = false;
                    break;
                }
            }
        }

        return areSame;
    }

    private String createJavadocReturnTag(GeneratorContext context, Type returnType) {
        String returnTypeText = TypeManager.get().resolveJavaType(context, returnType, false, context.getDefaultJavaType());
        if ("void".equals(returnTypeText)) {
            return "";
        }
        returnTypeText = TypeManager.javaWrapperToPrimitive(returnTypeText);
        StringBuilder javadoc = new StringBuilder();
        javadoc.append("\n{{indent}} * @return ");
        javadoc.append("a {@link ").append(returnTypeText).append("} object");
        if ("Value".endsWith(returnTypeText)) {
            if(returnType instanceof UnionType union) {
                javadoc.append(" that represent");
                int len = union.getItems().size();
                for (int i = 0; i < len; i++) {
                    if (i > 0 && i < len -2 ) {
                        javadoc.append(",");
                    }
                    if (i == (len - 1)) {
                        javadoc.append(" or");
                    }
                    Type item = union.getItems().get(i);
                    javadoc.append(" {@link ");
                    String text = TypeManager.get().resolveJavaType(context, item, false, context.getDefaultJavaType());
                    text = text.replace("<", "}{@literal <}{@link ");
                    text = text.replace(">", "}{@literal >");
                    javadoc.append(text);
                    javadoc.append("}");
                    
                }
            }
        }
        return javadoc.toString();
    }
    
    private String composeJavadoc(GeneratorContext context, String pythonDoc, String params, Type returnType) {
        String returnTag = createJavadocReturnTag(context, returnType);
        if (returnTag.isEmpty() && params.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int endIndex = pythonDoc.indexOf("*/");
        
        if (endIndex > 0) {
            result.append(pythonDoc.substring(0, endIndex));
            result.append("*\n");
        } else {
            result.append("/**\n");
            result.append("{{indent}} *");
        }
        
        result.append(params);
        result.append(createJavadocReturnTag(context, returnType));
        result.append("\n{{indent}} */");
        return result.toString();
    }
    
    private static class  MethodFinder extends DefaultNodeVisitor<List<FuncBase>> {

        private final String name;
        
        public MethodFinder(String name) {
            this.name = name;
        }
        
        @Override
        protected List<FuncBase> defaultVisit(Node node) {
            return Collections.EMPTY_LIST;
        }

        @Override
        public List<FuncBase> visit(TypeInfo typeInfo) {
            List<FuncBase> result = new ArrayList<>();
            List<TypeInfo> typeInfos = typeInfo.getMro();
            for (int i = 1; i < typeInfos.size(); i++) {
                TypeInfo ti = typeInfos.get(i);
                Map<String, SymbolTableNode> symbolTable = ti.getNames().getTable();
                for (Map.Entry<String, SymbolTableNode> entry : symbolTable.entrySet()) {
                    String key = entry.getKey();
                    if (name.equals(key)) {
                        SymbolTableNode tableNode = entry.getValue();
                        result.addAll(tableNode.getNode().accept(this));
                    }
                }
                result.addAll(ti.accept(this));
            }
            return result;
        }

        @Override
        public List<FuncBase> visit(FuncDef funcDef) {
            return List.of(funcDef);
        }
    }
    
}

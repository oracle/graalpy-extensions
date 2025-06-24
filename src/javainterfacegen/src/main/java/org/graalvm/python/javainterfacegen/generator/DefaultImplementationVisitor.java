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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.mypy.nodes.Argument;
import org.graalvm.python.javainterfacegen.mypy.nodes.AssignmentStmt;
import org.graalvm.python.javainterfacegen.mypy.nodes.Block;
import org.graalvm.python.javainterfacegen.mypy.nodes.ClassDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.Decorator;
import org.graalvm.python.javainterfacegen.mypy.nodes.ExpressionStmt;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.MypyFile;
import org.graalvm.python.javainterfacegen.mypy.nodes.NameExpr;
import org.graalvm.python.javainterfacegen.mypy.nodes.NodeVisitor;
import org.graalvm.python.javainterfacegen.mypy.nodes.OverloadedFuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.StrExpr;
import org.graalvm.python.javainterfacegen.mypy.nodes.SymbolTableNode;
import org.graalvm.python.javainterfacegen.mypy.nodes.TupleExpr;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeAlias;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeInfo;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeVarExpr;
import org.graalvm.python.javainterfacegen.mypy.nodes.Var;
import org.graalvm.python.javainterfacegen.mypy.types.Instance;

public class DefaultImplementationVisitor implements NodeVisitor<String> {

    private static final String TEMPLATE_FILE = """
{{license}}
package {{package}};

{{importblock}}
           
{{javadoc}}
public class {{classname}} extends {{extends}} import {{imports}}  {
                                                
{{indent+1}}// generated from {{filepath}}

{{indent+1}}public {{classname}}(Value instance) {
{{indent+2}}super(instance);
{{indent+1}}}
                                                
{{content}}
}
""";

    private static final String TEMPLATE_IMPLEMENTATION = """
{{license}}

{{generatedinfo}}

package {{package}};
                                                     
{{imports}}

public class {{implname}} extends {{extends}} implements {{implements}} {
                                                                                
{{content}}
                                                     
}
""";
    
    

    private final Configuration configuration;
    private GeneratorContext currentContext;

    public DefaultImplementationVisitor(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String visit(MypyFile mypyFile) {

        String template = TEMPLATE_FILE;
        currentContext = new GeneratorContext(null, configuration, mypyFile);

        String nameGeneratorClassName = (String) currentContext.getConfiguration().get(Configuration.P_NAME_GENERATOR);
        NameGenerator nameGen = GeneratorFactory.createNameGenerator(nameGeneratorClassName);

        template = template.replace("{{license}}", GeneratorUtils.getLicense(currentContext));

        String packageName = nameGen.packageForImplementation(mypyFile, currentContext);
        template = template.replace("{{package}}", packageName);

        String interfaceName = nameGen.interfaceName(mypyFile, currentContext);
        String className = nameGen.classImplementationName(mypyFile, currentContext);
        String interfaceFQN = nameGen.packageForInterface(mypyFile, currentContext) + "." + interfaceName;
        template = template.replace("{{classname}}", className);

        currentContext.setJavaFQN(packageName + "." + className);
        template = template.replace("{{javadoc}}", GeneratorUtils.createJavaDoc("TODO handle JavaDoc"));

        currentContext.addImport("org.graalvm.python.javainterfacegen.GuestValue");
        currentContext.addImport("org.graalvm.polyglot.Value");

        template = template.replace("{{imports}}", currentContext.useJavaType(interfaceFQN));

        // TODO count extended classes
        template = template.replace("{{extends}}", currentContext.useJavaType("org.graalvm.python.javainterfacegen.GuestValueImpl"));
        template = template.replace("{{filepath}}", currentContext.getFileFrom());

        template = template.replace("{{importblock}}", GeneratorUtils.importBlock(currentContext.getImports()));

        StringBuilder content = new StringBuilder();

        Map<String, SymbolTableNode> symbolTable = mypyFile.getNames().getTable();
        for (Map.Entry<String, SymbolTableNode> entry : symbolTable.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("_")) {
                SymbolTableNode tableNode = entry.getValue();
                if (tableNode.getNode() instanceof MypyFile mypyFile2) {
                    System.out.println("!!!!!!!!!!!!!!!! ");
                    System.out.println("    SymbolTableNode.getFullname: " + tableNode.getFullname());
                    System.out.println("    SymbolTableNode.value: " + tableNode.getValue().toString());
                    System.out.println("    current traversed file: " + mypyFile.getPath());
                    System.out.println("    want to traverse: " + mypyFile2.getPath());
                } else {
                    content.append(tableNode.getNode().accept(this));
                }
            }
        }

        template = template.replace("{{content}}", content.toString());

        template = GeneratorUtils.indentTemplate(currentContext, template);

        try {
            GeneratorUtils.saveFile(currentContext, packageName, className, template);
        } catch (IOException ex) {
            Logger.getLogger(TransformerVisitor.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    @Override
    public String visit(ClassDef classDef) {
        GeneratorContext tmpContext = currentContext;
        if (currentContext.isIgnored(classDef.getName()) && currentContext.isIgnored(classDef.getFullname())) {
            return "";
        }

        currentContext = new GeneratorContext(currentContext, configuration, classDef, true);

        String nameGeneratorClassName = (String) currentContext.getConfiguration().get(Configuration.P_NAME_GENERATOR);
        NameGenerator nameGen = GeneratorFactory.createNameGenerator(nameGeneratorClassName);

        String packageName = nameGen.packageForImplementation(classDef, currentContext);
        String implementationName = nameGen.classImplementationName(classDef, currentContext);

        String template = TEMPLATE_IMPLEMENTATION;

        template = template.replace("{{license}}", GeneratorUtils.getLicense(currentContext));
        String generatedInfo = "";
        if (currentContext.addTimestamp()) {
            generatedInfo = GeneratorUtils.generateTimeStamp() + '\n';
        }
        if (currentContext.addLocation()) {
            generatedInfo = generatedInfo +  GeneratorUtils.generateLocation(currentContext.getFileFrom() + " class def: " + classDef.getName()) + '\n';
        }
        template = template.replace("{{generatedinfo}}", generatedInfo);
        template = template.replace("{{package}}", packageName);
        template = template.replace("{{implname}}", implementationName);

        String implJavaFQN = packageName + "." + implementationName;
        currentContext.setJavaFQN(implJavaFQN);
        TypeManager.get().registerImplementation(classDef.getFullname(), implJavaFQN);

        // implements
        String ifaceJavaFQN = nameGen.packageForInterface(classDef, currentContext) + "." + nameGen.interfaceName(classDef, currentContext);
        template = template.replace("{{implements}}", currentContext.useJavaType(ifaceJavaFQN));

        // extends 
        TypeInfo typeInfo = (TypeInfo) tmpContext.getCurrentNode();
        StringBuilder extendsExpr = new StringBuilder();
        List<Instance> bases = typeInfo.getBases();
        String guestValueFQN = configuration.getBaseImplementationPackage(currentContext) + ".GuestValueDefaultImpl";
        if (!bases.isEmpty()) {
            // I am expecting that the first extned is the latest one (first in MRO)
            String pythonType = PythonFQNResolver.findPythonFQN(bases.get(0));
            if ("builtins.object".equals(pythonType)) {
                template = template.replace("{{extends}}", currentContext.useJavaType(guestValueFQN));
            } else {
                template = template.replace("{{extends}}", currentContext.useImplementation(pythonType, guestValueFQN));
            }
        } else {
            template = template.replace("{{extends}}", currentContext.useJavaType(guestValueFQN));
        }

        // class body
        Map<String, SymbolTableNode> symbolTable = typeInfo.getNames().getTable();
        StringBuilder content = new StringBuilder();
        content.append("{{indent}}// TODO provide construtor");
        currentContext.increaseIndentLevel();
        for (Map.Entry<String, SymbolTableNode> entry : symbolTable.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("_")) {
                SymbolTableNode tableNode = entry.getValue();
                content.append(tableNode.getNode().accept(this));
            }
        }
        template = template.replace("{{content}}", GeneratorUtils.indentTemplate(currentContext, content.toString()));
        currentContext.decreaseIndentLevel();
        
        String[] imports = currentContext.getImports();
        template = template.replace("{{imports}}", GeneratorUtils.generateImports(imports));
//        template = template.replace("{{logcomment}}", "");

        try {
            GeneratorUtils.saveFile(currentContext, packageName, implementationName, GeneratorUtils.indentTemplate(currentContext, template));
        } catch (IOException ex) {
            Logger.getLogger(TransformerVisitor.class.getName()).log(Level.SEVERE, null, ex);
        }
//        System.out.println(sb.toString());
        currentContext = tmpContext;
        return "";
    }

    @Override
    public String visit(FuncDef funcDef) {
        if (currentContext.isIgnored(funcDef.getName())) {
            return "";
        }
        GeneratorContext tmpContext = currentContext;
        currentContext = new GeneratorContext(currentContext, configuration, funcDef);

        StringBuilder sb = new StringBuilder();

//        String[] fnSignatureGenerators = configuration.fnInterfaceGenerators(currentContext);
//        for (int i = 0; i < fnSignatureGenerators.length; i++) {
//            String fnInterfaceGenerator = fnSignatureGenerators[i];
//            String text = GeneratorFactory.createFnPartGenerator(fnInterfaceGenerator).create(funcDef, currentContext);
//            sb.append(text).append('\n');
//        }

        sb.append("\n\n{{indent}}// TODO provide implemetation of function: " + funcDef.getName());
        currentContext = tmpContext;
        return sb.toString();
    }

    @Override
    public String visit(Argument arg) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String visit(Var v) {
        if (currentContext.isClass()) {
            if (currentContext.isIgnored(v.getName())) {
                return "";
            }
            // TODO this should be configurable
            boolean generateFieldGetters = true;
            if (generateFieldGetters) {
                StringBuilder sb = new StringBuilder();
                String[] fnSignatureGenerators = configuration.functionGenerators(currentContext);
                for (int i = 0; i < fnSignatureGenerators.length; i++) {
                    String fnInterfaceGenerator = fnSignatureGenerators[i];
                    sb.append(GeneratorFactory.createFunctionGenerator(fnInterfaceGenerator).createImplementation(v, currentContext));        
                }
                return sb.toString();
            }
            return "";
        }
        return "{{indent}}// TODO reflect variable '" + v.getName() + "'\n";
    }

    @Override
    public String visit(Block block) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String visit(ExpressionStmt expr) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String visit(AssignmentStmt assignment) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String visit(NameExpr nameExpr) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String visit(StrExpr strExpr) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String visit(TupleExpr tuple) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String visit(TypeInfo typeInfo) {
        String result;
        GeneratorContext tmpContext = currentContext;
        currentContext = new GeneratorContext(currentContext, configuration, typeInfo, false);
        result = typeInfo.getDefn().accept(this);
        currentContext = tmpContext;
        return result;
    }

    @Override
    public String visit(Decorator decorator) {
        return "\n\n{{indent}}// TODO provide implemetation of decorator: " + decorator.getValue().toString();
    }

    @Override
    public String visit(OverloadedFuncDef oFnDef) {
        return "\n\n{{indent}}// TODO provide implemetation of decorator: " + oFnDef.getFullname();
    }

    @Override
    public String visit(TypeAlias typeAlias) {
        return "\n\n{{indent}}// TODO handle visit(TypeAlias)";
    }

    @Override
    public String visit(TypeVarExpr typeVarExpr) {
        return "\n\n{{indent}}//TODO handle TypeVarExpr " + typeVarExpr.getValue().toString();
    }

}

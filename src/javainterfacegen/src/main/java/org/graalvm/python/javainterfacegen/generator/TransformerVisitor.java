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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.generator.impl.KwArgBuilderGenerator;
import org.graalvm.python.javainterfacegen.generator.impl.OverloadArgsGenerator;
import org.graalvm.python.javainterfacegen.mypy.nodes.Argument;
import org.graalvm.python.javainterfacegen.mypy.nodes.AssignmentStmt;
import org.graalvm.python.javainterfacegen.mypy.nodes.Block;
import org.graalvm.python.javainterfacegen.mypy.nodes.ClassDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.Decorator;
import org.graalvm.python.javainterfacegen.mypy.nodes.DefaultNodeVisitor;
import org.graalvm.python.javainterfacegen.mypy.nodes.ExpressionStmt;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.MypyFile;
import org.graalvm.python.javainterfacegen.mypy.nodes.NameExpr;
import org.graalvm.python.javainterfacegen.mypy.nodes.Node;
import org.graalvm.python.javainterfacegen.mypy.nodes.NodeVisitor;
import org.graalvm.python.javainterfacegen.mypy.nodes.OverloadedFuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.StrExpr;
import org.graalvm.python.javainterfacegen.mypy.nodes.SymbolNode;
import org.graalvm.python.javainterfacegen.mypy.nodes.SymbolTable;
import org.graalvm.python.javainterfacegen.mypy.nodes.SymbolTableNode;
import org.graalvm.python.javainterfacegen.mypy.nodes.TupleExpr;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeAlias;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeInfo;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeVarExpr;
import org.graalvm.python.javainterfacegen.mypy.nodes.Var;
import org.graalvm.python.javainterfacegen.mypy.types.ArgKind;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_NAMED;
import static org.graalvm.python.javainterfacegen.mypy.types.ArgKind.ARG_POS;
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.Instance;
import org.graalvm.python.javainterfacegen.mypy.types.TupleType;
import org.graalvm.python.javainterfacegen.python.Utils;

public class TransformerVisitor implements NodeVisitor<String> {

    private static final String TEMPLATE_INTERFACE = """
            {{license}}

            {{generatedinfo}}

            package {{package}};

            {{imports}}

            {{javadoc}}
            public interface {{iname}} {{extends}} {
            {{logcomment}}

            {{factory_methods}}
            {{content}}
                      
            }
            """;

    private static final String TEMPLATE_INNER_INTERFACE = """
            {{javadoc}}
            public interface {{iname}} {{extends}} {
            {{logcomment}}

            {{factory_methods}}
            {{content}}

            }
            """;
    
    private static final String TEMPLATE_IS_METHOD = """
            {{indent}}static boolean isInstance(Object object) {
            {{indent+1}}Value value = Value.asValue(object);
            {{indent+1}}Value metaObject = value.getMetaObject();
            {{indent+1}}String moduleName = metaObject.getMember("__module__").asString();
            {{indent+1}}String typeName = metaObject.getMember("__name__").asString();
            {{indent+1}}String fqn = moduleName + "." + typeName;
            {{indent+1}}return "{{python_fqn}}".equals(fqn);
            {{indent}}}""";

    private static final String TEMPLATE_CAST_METHOD = """
            {{indent}}static {{java_type}} cast(Object o) {
            {{indent+1}}Value v = Value.asValue(o);
            {{indent+1}}if (isInstance(v)) {
            {{indent+2}}return v.as({{java_type}}.class);
            {{indent+1}}}
            {{indent+1}}throw new ClassCastException();
            {{indent}}}""";

    private static final String TEMPLATE_FROM_CONTEXT = """
                {{indent}}public static {{iname}} fromContext(Context context) {
                {{indent+1}}Value pythonBindings = context.getBindings("python");
                {{indent+1}}if (!pythonBindings.hasMember("{{liname}}_main")) {
                {{indent+2}}pythonBindings.putMember("{{liname}}_main", context.eval("python", "import {{modulename}}"));
                {{indent+1}}}
                {{indent+1}}Value {{liname}} = context.eval("python", "{{modulename}}");
                {{indent+1}}return {{liname}}.as({{iname}}.class);
                {{indent}}}
                """;

    private static final String TEMPLATE_TYPE_FROM_CONTEXT = """
                {{indent}}static {{name}}Type getPythonType(Context context) {
                {{indent+1}}return context.eval("python", "from {{python_fqn_module}} import {{python_name}}; {{python_name}}").as({{name}}Type.class);
                {{indent}}}
                """;

    private static final String TEMPLATE_NEW_INSTANCE_FROM_TYPE = """
                {{indent}}default {{name}} newInstance({{args}}) {
                {{indent+1}}return Value.asValue(this).newInstance({{arg_names}}).as({{name}}.class);
                {{indent}}}
                """;

    private static final String TEMPLATE_FACTORY_METHOD = """
                {{indent}}default {{name}} create{{name}}({{args}}) {
                {{indent+1}}return Value.asValue(this).getMember("{{name}}").newInstance({{arg_names}}).as({{name}}.class);
                {{indent}}}
                """;

    private static final String TEMPLATE_FACTORY_TYPE_METHOD = """
                {{indent}}default {{name}}Type get{{name}}Type() {
                {{indent+1}}return Value.asValue(this).getMember("{{name}}").as({{name}}Type.class);
                {{indent}}}
                """;
    
    private static final String TEMPLATE_VALUEBASE_INTERFACE = """
                {{license}}
                package {{package}};

                import org.graalvm.polyglot.Value;

                public interface {{name}} {
                
                {{indent+1}}default Value asValue() {
                {{indent+2}}return Value.asValue(this);
                {{indent+1}}}
                
                {{indent+1}}default Value getMember(String name) {
                {{indent+2}}return asValue().getMember(name);
                {{indent+1}}}

                {{indent+1}}default void putMember(String name, Object value) {
                {{indent+2}}asValue().putMember(name, value);
                {{indent+1}}}
                }
                """;

    private final Configuration configuration;
    private GeneratorContext currentContext;
    private boolean generatedBaseInterface;
    
    public TransformerVisitor(Configuration configuration) {
        this.configuration = configuration;
        this.generatedBaseInterface = false;
    }

    @Override
    public String visit(MypyFile mypyFile) {
//        System.out.println("processing file " + mypyFile.getPath());
        currentContext = new GeneratorContext(null, configuration, mypyFile);
        if (currentContext.isIgnored(mypyFile.getFullname())) {
            return "";
        }
        
        String template = TEMPLATE_INTERFACE;

        String nameGeneratorClassName = (String) currentContext.getConfiguration().get(Configuration.P_NAME_GENERATOR);
        NameGenerator nameGen = GeneratorFactory.createNameGenerator(nameGeneratorClassName);

        String packageName = nameGen.packageForInterface(mypyFile, currentContext);
        
        template = template.replace("{{license}}", GeneratorUtils.getLicense(currentContext));
        StringBuilder generatedInfo = new StringBuilder();
        if (currentContext.addTimestamp()) {
            generatedInfo.append(GeneratorUtils.generateTimeStamp());
        }
        if (currentContext.addLocation()) {
            if (!generatedInfo.isEmpty()) {
                generatedInfo.append('\n');
            }
            generatedInfo.append(GeneratorUtils.generateLocation(currentContext.getRelativePath()));
        }
        template = template.replace("{{generatedinfo}}", generatedInfo);
        template = template.replace("{{package}}", packageName);

        String interfaceName = nameGen.interfaceName(mypyFile, currentContext);
        template = template.replace("{{iname}}", interfaceName);
        currentContext.setJavaFQN(packageName + "." + interfaceName);
        String javadoc = GeneratorUtils.getJavadoc(mypyFile, currentContext);
        if ((javadoc == null || javadoc.isEmpty()) && currentContext.getConfig().addLogComments(currentContext)) {
            javadoc = GeneratorUtils.getMissingJavadocComment(mypyFile.getName(), currentContext);
        }
        if (!javadoc.isEmpty() && javadoc.charAt(javadoc.length() - 1) == '\n') {
            javadoc = javadoc.substring(0, javadoc.length() - 2);
        }
        template = template.replace("{{javadoc}}", javadoc);
        template = template.replace("{{logcomment}}\n", "");
        template = template.replace("{{extends}}", "");
//        currentContext.addImport(configuration.getBaseAPIPackage(currentContext) + ".GuestValue");
//        template = template.replace("{{extends}}", "extends GuestValue");
        currentContext.increaseIndentLevel();

        StringBuilder content = new StringBuilder();
        content.append(generateFactoryMethod2(interfaceName, mypyFile.getFullname()));
        Map<String, SymbolTableNode> symbolTable = mypyFile.getNames().getTable();
//        System.out.println(printSymbolTableNames(mypyFile.getNames(), "Symbol Table of file " + mypyFile.getName()));
        StringBuilder factoryMethods = new StringBuilder();
        Object generateTypeInterface = configuration.getProperties(currentContext).get(Configuration.P_GENERATE_TYPE_INTERFACE);
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
                } else if (!tableNode.getFullname().startsWith(mypyFile.fullname())) {
                    System.out.println("!!!!!!!!!!!!!!!!!! ignored because is not from the same namespace");
                    System.out.println("    SymbolTableNode.getFullname: " + tableNode.getFullname());
                    System.out.println("    SymbolTableNode.value: " + tableNode.getValue().toString());
                } else {
                    String textToAdd = tableNode.getNode().accept(this);

                    if (content.charAt(content.length() - 1) != '\n') {
                        content.append('\n');
                    }
                    if (!textToAdd.isEmpty() && textToAdd.charAt(0) != '\n') {
                        content.append('\n');
                    }
                    content.append(textToAdd);
                    textToAdd = createFactoryMethod(currentContext, tableNode.getNode(), TEMPLATE_FACTORY_METHOD);
                    factoryMethods.append('\n');
                    factoryMethods.append(textToAdd);
                    if (generateTypeInterface.equals(true) || GeneratorUtils.hasStaticMember(mypyFile)) {
                        textToAdd = createFactoryTypeMethod(currentContext, tableNode.getNode());
                        if (textToAdd != null) {
                            factoryMethods.append('\n');
                            factoryMethods.append(textToAdd);
                        }
                    }
                }
            }
        }
//        currentContext.decreaseIndentLevel();
        template = template.replace("{{content}}", content.toString().trim());
        template = template.replace("{{factory_methods}}", factoryMethods.toString().trim());

        String[] imports = currentContext.getImports();
        template = template.replace("{{imports}}", GeneratorUtils.generateImports(imports));

        template = GeneratorUtils.indentTemplate(currentContext, template);
        currentContext.decreaseIndentLevel();
//        System.out.println(template);
        TypeManager.get().registerModule(currentContext.getJavaFQN());
        try {
            GeneratorUtils.saveFile(currentContext, packageName, interfaceName, template);
        } catch (IOException ex) {
            Logger.getLogger(TransformerVisitor.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    @Override
    public String visit(ClassDef classDef) {
        if (currentContext.isIgnored(classDef.getFullname())
                || classDef.getName().startsWith("<subclass of")) {
            // Don't generate stuff for classes with name "<subclass of "xxx" and "yyy">"
            // These classes are created by mypy as helper classes.
            // TODO for now just ignore these classes, but should we solve it somehow
            return "";
        }
        GeneratorContext tmpContext = currentContext;
        
        
        if (currentContext.getOuterClassContext() !=  null) {
            return createInnerClass(classDef);
        }
        
        currentContext = new GeneratorContext(currentContext, configuration, classDef, true);
        currentContext.setIndentLevel(0);

        String nameGeneratorClassName = (String) currentContext.getConfiguration().get(Configuration.P_NAME_GENERATOR);

        String template = TEMPLATE_INTERFACE;

        NameGenerator nameGen = GeneratorFactory.createNameGenerator(nameGeneratorClassName);

        String packageName = nameGen.packageForInterface(classDef, currentContext);
        template = template.replace("{{license}}", GeneratorUtils.getLicense(currentContext));

        StringBuilder generatedInfo = new StringBuilder();
        if (currentContext.addTimestamp()) {
            generatedInfo.append(GeneratorUtils.generateTimeStamp());
        }
        if (currentContext.addLocation()) {
            if (!generatedInfo.isEmpty()) {
                generatedInfo.append('\n');
            }
            generatedInfo.append(GeneratorUtils.generateLocation(currentContext.getRelativePath()));
            generatedInfo.append(" class def: ").append(classDef.getName());
        }
        template = template.replace("{{generatedinfo}}", generatedInfo);

        template = template.replace("{{package}}", packageName);

        String interfaceName = nameGen.interfaceName(classDef, currentContext);
        template = template.replace("{{iname}}", interfaceName);

        String javaFQN = packageName + "." + interfaceName;
        currentContext.setJavaFQN(javaFQN);
        TypeManager.get().registerType(classDef.getFullname(), javaFQN);

        template = template.replace("{{javadoc}}", GeneratorUtils.getJavadoc(classDef, currentContext));

//        sb.append(" /* TODO extends */");
//        sb.append(" /* TODO implements */");
        TypeInfo typeInfo = (TypeInfo) tmpContext.getCurrentNode();

        StringBuilder extendsExpr = new StringBuilder();
        List<Instance> bases = Collections.EMPTY_LIST;
        boolean isTupleType = typeInfo.getTupleType() instanceof TupleType;
        if (!isTupleType) {
            bases = typeInfo.getBases();
            if (!bases.isEmpty()) {
                for (int i = 0; i < bases.size(); i++) {
                    if (!extendsExpr.isEmpty()) {
                        extendsExpr.append(", ");
                    }
                    String pythonType = PythonFQNResolver.findPythonFQN(bases.get(i));
//                    String guestValueFQN = configuration.getBaseAPIPackage(currentContext) + ".GuestValue";
                    // TODO make it configurable
                    if ("builtins.object".equals(pythonType)
                            || "builtins.type".equals(pythonType)) {
//                        currentContext.addImport(guestValueFQN);
                        if (bases.size() == 1) {
                            extendsExpr.append(getBaseInterface(currentContext));                            
                        }
                    } else {
//                        extendsExpr.append(currentContext.useType(pythonType, TypeManager.get().resolveJavaType(currentContext, bases.get(i))));
                        String defaultExtends = currentContext.getConfiguration().get(Configuration.P_BASE_INTERFACE_PACKAGE)
                                + "." + currentContext.getConfiguration().get(Configuration.P_BASE_INTERFACE_NAME);
                        String javaType = TypeManager.get().resolveJavaType(currentContext, bases.get(i), false, defaultExtends);
                        String javaFQNType = TypeManager.get().getJavaType(pythonType);
                        if (!((TypeManager.PYTHON_STR.equals(pythonType) && "java.lang.String".equals(javaFQNType))
                                ||(TypeManager.PYTHON_BOOLEAN.equals(pythonType) && "boolean".equals(javaFQNType))
                                ||(TypeManager.PYTHON_INT.equals(pythonType) && "long".equals(javaFQNType))
                                ||(TypeManager.PYTHON_FLOAT.equals(pythonType) && "double".equals(javaFQNType)))) {
                            extendsExpr.append(javaType);
                        }
                    }
                }
                if (!extendsExpr.isEmpty()) {
                    extendsExpr.insert(0, "extends ");
                    template = template.replace("{{extends}}", extendsExpr.toString());
                }
            }
            if (extendsExpr.isEmpty()) {
                currentContext.addImport(configuration.getBaseAPIPackage(currentContext) + ".GuestValue");
                template = template.replace("{{extends}}", "GuestValue");
            }
        } else {
            template = template.replace("{{extends}}", "");
        }
        // We dont use body of the class for performance reasons
        //currentContext.increaseIndentLevel();
        currentContext.increaseIndentLevel();
        if (currentContext.getConfig().addLogComments(currentContext)) {
            StringBuilder logcomment = new StringBuilder();
            logcomment.append("{{indent}}// Python def: ");
            if (!isTupleType) {
                logcomment.append(classDef.getName()).append("(");

                boolean first = true;
                for (int i = 0; i < bases.size(); i++) {
                    if (first) {
                        first = false;
                        logcomment.append(bases.get(i));
                    } else {
                        logcomment.append(", ").append(bases.get(i));
                    }
                }
                logcomment.append(")");

            } else {
                logcomment.append(typeInfo.getTupleType().toString());
            }
            template = template.replace("{{logcomment}}", logcomment.toString());
        } else {
            template = template.replace("{{logcomment}}", "{{indent}}// TypeInfo was not found for class " + classDef.getName());
        }

//        List<Statement> statements = classDef.getDefs().getBody();
//        System.out.println("Statements in class body: " + statements.size()) ;
        Map<String, SymbolTableNode> symbolTable = typeInfo.getNames().getTable();
        StringBuilder content = new StringBuilder();
        for (Map.Entry<String, SymbolTableNode> entry : symbolTable.entrySet()) {
            String key = entry.getKey();
            // TOOD this is a hack, needs to be improved
            if (isTupleType && key.endsWith("-redefinition")) {
                continue;
            }
            if (!key.startsWith("_")) {
                SymbolTableNode tableNode = entry.getValue();
                if (!GeneratorUtils.isStatic(tableNode.getNode())) {
                    content.append(tableNode.getNode().accept(this));
                } else {
                    if (GeneratorUtils.isClassAttribute(tableNode.getNode())) {
                        // we will generate access to the class fields. 
                        content.append(tableNode.getNode().accept(this));
                    }
                }
            }
        }

        // adding isInstance method
        currentContext.useJavaType("org.graalvm.polyglot.Value");
       
        content.append(TEMPLATE_IS_METHOD.replace("{{python_fqn}}", classDef.getFullname()))
                .append("\n\n");
        content.append(TEMPLATE_CAST_METHOD.replace("{{java_type}}", interfaceName));
        template = template.replace("{{content}}", GeneratorUtils.indentTemplate(currentContext, content.toString()));
        template = template.replace("{{factory_methods}}", "");
        //currentContext.decreaseIndentLevel();

//        Map<String, SymbolTableNode> symbolTable = classDef.getNames().getTable();
//        for (int i = 0; i < statements.size(); i++) {
//            sb.append(statements.get(i).accept(this));
//        }
        String[] imports = currentContext.getImports();
        template = template.replace("{{imports}}", GeneratorUtils.generateImports(imports));
        template = GeneratorUtils.indentTemplate(currentContext, template);

        try {
            GeneratorUtils.saveFile(currentContext, packageName, interfaceName, GeneratorUtils.indentTemplate(currentContext, template));
        } catch (IOException ex) {
            Logger.getLogger(TransformerVisitor.class.getName()).log(Level.SEVERE, null, ex);
        }
//        System.out.println(sb.toString());

        // solve the static stuff
        Object generateTypeInterface = configuration.getProperties(currentContext).get(Configuration.P_GENERATE_TYPE_INTERFACE);
        boolean hasStaticMember = GeneratorUtils.hasStaticMember(typeInfo);
        if (generateTypeInterface.equals(true) || hasStaticMember) {
            GeneratorContext classContext = currentContext;
            currentContext = new GeneratorContext(currentContext, configuration, classDef, true);
            currentContext.setIndentLevel(0);
            generatedInfo = new StringBuilder();
            if (currentContext.addTimestamp()) {
                generatedInfo.append(GeneratorUtils.generateTimeStamp());
            }
            if (currentContext.addLocation()) {
                if (!generatedInfo.isEmpty()) {
                    generatedInfo.append('\n');
                }
                generatedInfo.append(GeneratorUtils.generateLocation(currentContext.getRelativePath()));
                generatedInfo.append(" class def: ").append(classDef.getFullname());
            }
            template = TEMPLATE_INTERFACE;
            template = template.replace("{{license}}", GeneratorUtils.getLicense(currentContext));
            template = template.replace("{{generatedinfo}}", generatedInfo);
            template = template.replace("{{logcomment}}", "");

            String interfaceTypeName = interfaceName + "Type";
            template = template.replace("{{package}}", packageName);
            template = template.replace("{{iname}}", interfaceTypeName);
            template = template.replace("{{extends}}", "");

            javaFQN = packageName + "." + interfaceTypeName;
            currentContext.setJavaFQN(javaFQN);
            TypeManager.get().registerType(classDef.getFullname() + "Type", javaFQN);

            template = template.replace("{{javadoc}}", GeneratorUtils.wraptToJavadoc(
                    "This interface expose static fields and methods of the class.", 0, true));

            content = new StringBuilder();
            currentContext.setIndentLevel(1);
            StringBuilder factoryMethods = new StringBuilder();

            if (generateTypeInterface.equals(true) || GeneratorUtils.hasStaticMember(typeInfo)) {

                String module = classDef.getFullname();
                int dotIndex = module.indexOf('.');
                if (dotIndex > -1) {
                    module = module.substring(0, dotIndex);
                }
                factoryMethods.append(
                        TEMPLATE_TYPE_FROM_CONTEXT
                                .replace("{{name}}", interfaceName)
                                .replace("{{python_fqn_module}}", classDef.getFullname().substring(0, classDef.getFullname().lastIndexOf('.')))
                                .replace("{{python_name}}", classDef.getName()));

                factoryMethods.append("\n");
                factoryMethods.append(createFactoryMethod(currentContext, typeInfo, TEMPLATE_NEW_INSTANCE_FROM_TYPE));

                currentContext.addImport("org.graalvm.polyglot.Context");
                currentContext.addImport("org.graalvm.polyglot.Value");
                currentContext.addImport("org.graalvm.python.embedding.KeywordArguments");

                for (Map.Entry<String, SymbolTableNode> entry : symbolTable.entrySet()) {
                    String key = entry.getKey();
                    // TOOD this is a hack, needs to be improved
                    if (isTupleType && key.endsWith("-redefinition")) {
                        continue;
                    }
                    if (!key.startsWith("_")) {
                        SymbolTableNode tableNode = entry.getValue();
                        if (GeneratorUtils.isStatic(tableNode.getNode())
                                || GeneratorUtils.isStaticOrClassFunction(tableNode.getNode())) {
                            content.append(tableNode.getNode().accept(this));
                        }
                    }
                }
            }
            template = template.replace("{{content}}", GeneratorUtils.indentTemplate(currentContext, content.toString()));
            template = template.replace("{{factory_methods}}", GeneratorUtils.indentTemplate(currentContext,factoryMethods.toString()));
            currentContext.decreaseIndentLevel();

            imports = currentContext.getImports();
            template = template.replace("{{imports}}", GeneratorUtils.generateImports(imports));
            template = GeneratorUtils.indentTemplate(currentContext, template);

            
            try {
                GeneratorUtils.saveFile(currentContext, packageName, interfaceTypeName, GeneratorUtils.indentTemplate(currentContext, template));
            } catch (IOException ex) {
                Logger.getLogger(TransformerVisitor.class.getName()).log(Level.SEVERE, null, ex);
            }
            currentContext = classContext;
        }
        currentContext = tmpContext;
        return "";
    }

    @Override
    public String visit(FuncDef funcDef) {
        if (currentContext.isIgnored(funcDef.getFullname())) {
            return "";
        }
        GeneratorContext tmpContext = currentContext;
        currentContext = new GeneratorContext(currentContext, configuration, funcDef);

        StringBuilder sb = new StringBuilder();

        String[] fnSignatureGenerators = configuration.functionGenerators(currentContext);
        if (currentContext.getConfig().addLogComments(currentContext)) {
            sb.append(LogCommentGenerator.getLogComment(currentContext, funcDef));
            sb.append("\n");
        }
        for (int i = 0; i < fnSignatureGenerators.length; i++) {
            String fnInterfaceGenerator = fnSignatureGenerators[i];
            String text = GeneratorFactory.createFunctionGenerator(fnInterfaceGenerator).createSignature(funcDef, currentContext);
            sb.append(text).append(";\n");
        }
        sb.append("\n");
        currentContext = tmpContext;
        return sb.toString();
    }

    private String createInnerClass(ClassDef classDef) {
        return "{{indent}}// inner class " + classDef.getName() + "\n";
    }
    
    @Override
    public String visit(Argument arg) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String visit(Var v) {
        if (currentContext.isClass() || currentContext.isFile()) {
            if (currentContext.isIgnored(v.getName())) {
                return "";
            }
            // TODO this should be configurable
            boolean generateFieldGetters = true;
            if (generateFieldGetters) {
                StringBuilder sb = new StringBuilder();

                if (currentContext.getConfig().addLogComments(currentContext)) {
                    sb.append("{{indent}}// getter for class field '").append(v.getName());
                    sb.append("', Python type: ").append(v.getType().toString());
                    sb.append("\n{{indent}}// type class: ");
                    sb.append(Utils.getFullyQualifedName(v.getType().getValue()));
                    if (v.getType() instanceof Instance instance) {
                        sb.append(" -> ").append(Utils.getFullyQualifedName(instance.getType().getValue()));
                    }
                    sb.append("\n");
                }

                String[] fnSignatureGenerators = configuration.functionGenerators(currentContext);
                for (int i = 0; i < fnSignatureGenerators.length; i++) {
                    String fnInterfaceGenerator = fnSignatureGenerators[i];
                    String text = GeneratorFactory.createFunctionGenerator(fnInterfaceGenerator).createSignature(v, currentContext);
                    sb.append(text).append('\n');
                }
                sb.append("\n");
                return sb.toString();
            }
            return "";
        }
//        if (currentContext.isFile()) { 
//            StringBuilder sb = new StringBuilder();
//            String[] fnSignatureGenerators = configuration.functionGenerators(currentContext);
//            for (int i = 0; i < fnSignatureGenerators.length; i++) {
//                String fnInterfaceGenerator = fnSignatureGenerators[i];
//                String text = GeneratorFactory.createFunctionGenerator(fnInterfaceGenerator).createSignature(v, currentContext);
//                sb.append(text).append(";\n");
//            }
//            sb.append("\n");
//            return sb.toString();
//        }
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
//        System.out.println(printSymbolTableNames(typeInfo.getNames(), "Symbol Table of TypeInfo " + typeInfo.getFullname()));
        GeneratorContext tmpContext = currentContext;
        currentContext = new GeneratorContext(currentContext, configuration, typeInfo);
        if (typeInfo.getTupleType() instanceof TupleType tupleType) {
//            String typeGeneratorClassName = (String) currentContext.getConfig().getProperties(currentContext).get(Configuration.P_TYPE_GENERATOR);
//            TypeGenerator typeGen = GeneratorFactory.createTypeGenerator(typeGeneratorClassName);
            String pythonFQN = typeInfo.getFullname();
//            String javaPackage = 
            System.out.println("################ " + pythonFQN);
//            //typeGen.createType(tupleType, currentContext, typePackage, typeName);
////                registerType(typeName, fqnTypeName);
        }
        result = typeInfo.getDefn().accept(this);
        currentContext = tmpContext;
        return result;
    }

    @Override
    public String visit(OverloadedFuncDef oFnDef) {
        if (currentContext.isIgnored(oFnDef.name())) {
            return "";
        }
        GeneratorContext tmpContext = currentContext;
        currentContext = new GeneratorContext(currentContext, configuration, oFnDef);

        StringBuilder sb = new StringBuilder();

        String[] fnSignatureGenerators = configuration.functionGenerators(currentContext);
        if (currentContext.getConfig().addLogComments(currentContext)) {
            sb.append(LogCommentGenerator.getLogComment(currentContext, oFnDef));
        }

        for (int i = 0; i < fnSignatureGenerators.length; i++) {
            String fnInterfaceGenerator = fnSignatureGenerators[i];
            String text = GeneratorFactory.createFunctionGenerator(fnInterfaceGenerator).createSignature(oFnDef, currentContext);
            sb.append(text);
        }
        sb.append("\n");
        currentContext = tmpContext;
        return sb.toString();
    }

    @Override
    public String visit(Decorator decorator) {
        StringBuilder sb = new StringBuilder();
//        sb.append("\n{{indent}}//TODO handle Decorator correctly");
//        sb.append("\n{{indent}}// fullname: ").append(decorator.getFullname());
//        sb.append("\n{{indent}}// name: ").append(decorator.getName());
//        sb.append("\n{{indent}}// TypeInfo: ").append(decorator.getInfo().toString());
//        FuncDef funcDef = decorator.getFunc();
//        sb.append("\n{{indent}}// func: ").append(funcDef.toString());
//        sb.append("\n{{indent}}// func.name: ").append(funcDef.getName());
//        sb.append("\n{{indent}}// func.is_property: ").append(funcDef.isProperty());
//        sb.append("\n{{indent}}// func.is_static: ").append(funcDef.isStatic());
//        sb.append("\n{{indent}}// func.is_class: ").append(funcDef.isClass());
//        sb.append("\n{{indent}}// var: ").append(decorator.getVar().toString());
//        sb.append("\n{{indent}}// var.name: ").append(decorator.getVar().getName());
//        sb.append("\n{{indent}}// type: ").append(decorator.getType().toString());
//        sb.append("\n{{indent}}// docorators: ");
//        List<Expression> decorators = decorator.getDocorators();
//        for (Expression expression: decorators) {
//            sb.append("\n{{indent+1}}// type: ").append(expression.toString());
//        }
//        sb.append("\n\n");
        GeneratorContext tmpContext = currentContext;
        currentContext = new GeneratorContext(currentContext, configuration, decorator);
        FuncDef funcDef = decorator.getFunc();
        sb.append(funcDef.accept(this));
        currentContext = tmpContext;
        return sb.toString();
    }

    @Override
    public String visit(TypeAlias typeAlias) {
        return "{{indent}}// TODO handle TypeAlias " + typeAlias.getTarget().toString().replaceAll("0x[0-9a-f]+", "") + "\n";
    }

    @Override
    public String visit(TypeVarExpr typeVarExpr) {
        return "{{indent}}//TODO handle TypeVarExpr " + typeVarExpr.getValue().toString().replaceAll("0x[0-9a-f]+", "") + "\n";
    }

    private static String lowercaseFirstLetter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        char firstChar = Character.toLowerCase(text.charAt(0));
        return firstChar + text.substring(1);
    }

    private String generateFactoryMethod2(String interfaceName, String moduleName) {
        currentContext.addImport("org.graalvm.polyglot.Context");
        currentContext.addImport("org.graalvm.polyglot.Value");
        currentContext.addImport("org.graalvm.python.embedding.KeywordArguments");
        String template = TEMPLATE_FROM_CONTEXT;
        template = template.replace("{{iname}}", interfaceName);
        template = template.replace("{{liname}}", lowercaseFirstLetter(interfaceName));
        template = template.replace("{{modulename}}", moduleName);
        return template;
    }

    private String generateFactoryMethod(String interfaceName, String moduleName) {
        currentContext.addImport("java.util.Optional");
        currentContext.addImport("java.util.ServiceLoader");
        currentContext.addImport("org.graalvm.polyglot.Value");
        currentContext.addImport("org.graalvm.polyglot.Context");
        currentContext.addImport("org.graalvm.python.embedding.KeywordArguments");
        currentContext.addImport("org.graalvm.python.javainterfacegen.GuestValue");
        currentContext.addImport("org.graalvm.python.javainterfacegen.Utils");

        String template
                = "{{indent}}interface {{pname}} {\n"
                + "{{indent+1}}{{iname}} createImplementation(Value value);\n"
                + "{{indent}}}\n"
                + "\n"
                + "{{indent}}public static {{iname}} fromContext(Context context) {\n"
                + "{{indent+1}}Value pythonBindings = context.getBindings(Utils.PYTHON);\n"
                + "{{indent+1}}if (!pythonBindings.hasMember(\"{{liname}}_main\")) {\n"
                + "{{indent+2}}pythonBindings.putMember(\"{{liname}}_main\", context.eval(Utils.PYTHON, \"import {{modulename}}\"));\n"
                + "{{indent+1}}}\n"
                + "{{indent+1}}Value {{liname}} = pythonBindings.getMember(\"{{liname}}_main\").getMember(\"{{modulename}}\");\n"
                + "{{indent+1}}Optional<{{pname}}> findFirst = ServiceLoader.load({{pname}}.class).findFirst();\n"
                + "{{indent+1}}if (findFirst.isEmpty()) {\n"
                + "{{indent+2}}return new {{iname}}Impl({{liname}});\n"
                + "{{indent+1}}}\n"
                + "{{indent+1}}return findFirst.get().createImplementation({{liname}});\n"
                + "{{indent}}}\n\n";

        template = template.replace("{{indent}}", GeneratorUtils.indent(currentContext));
        currentContext.increaseIndentLevel();
        template = template.replace("{{indent+1}}", GeneratorUtils.indent(currentContext));
        currentContext.increaseIndentLevel();
        template = template.replace("{{indent+2}}", GeneratorUtils.indent(currentContext));
        currentContext.decreaseIndentLevel();
        currentContext.decreaseIndentLevel();

        template = template.replace("{{pname}}", interfaceName + "ImplProvider");
        template = template.replace("{{iname}}", interfaceName);
        template = template.replace("{{liname}}", lowercaseFirstLetter(interfaceName));
        template = template.replace("{{modulename}}", moduleName);

        return template;
    }

    private String printSymbolTableNames(SymbolTable table, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("========== ").append(title).append(" ==========\n");
        int endLen = sb.length();

        for (Map.Entry<String, SymbolTableNode> entry : table.getTable().entrySet()) {
            sb.append(entry.getKey());
            SymbolTableNode tableNode = entry.getValue();
            sb.append(" -> ").append(tableNode.getNode()).append("\n");
        }
        for (int i = 0; i < endLen; i++) {
            sb.append("=");
        }
        return sb.toString();
    }

//    private String generatedInfo(String location) {
//        LocalDateTime currentDateTime = LocalDateTime.now();
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
//
//        StringBuilder sb = new StringBuilder();
//        sb.append("// Genereted at ");
//        sb.append(currentDateTime.format(formatter)).append("\n");
//        sb.append("// Generated from ").append(location);
//        return sb.toString();
//    }
    private String createFactoryMethod(GeneratorContext context, SymbolNode node, String fromTemplate) {
        DefaultNodeVisitor<String> factoryMethodVisitor = new DefaultNodeVisitor<String>() {
            TypeInfo lastInfo = null;

            @Override
            protected String defaultVisit(Node node) {
                return "{{indent}}// factory method for " + node.getClass().getName();
            }

            @Override
            public String visit(FuncDef funcDef) {
                return "";
            }

            @Override
            public String visit(ClassDef classDef) {
                GeneratorContext classContext = new GeneratorContext(context, configuration, classDef, false);
                String template = fromTemplate;
                String javaName = classContext.useType(classDef.getFullname());
                StringBuilder result = new StringBuilder();
                boolean argsResolved = false;
                if (lastInfo != null) {
                    SymbolTableNode initMethod = null;
                    List<TypeInfo> mro = lastInfo.getMro();
                    for (int i = 0; i < mro.size(); i++) {
                        if (initMethod != null) {
                            break;
                        }
                        TypeInfo mroType = mro.get(i);
                        Map<String, SymbolTableNode> symbolTable = mroType.getNames().getTable();
                        for (Map.Entry<String, SymbolTableNode> entry : symbolTable.entrySet()) {
                            if ("__init__".equals(entry.getKey())) {
                                initMethod = entry.getValue();
                                break;
                            }
                        }
                    }
                    if (initMethod != null && initMethod.getNode() instanceof FuncDef fnInit) {
                        if (!KwArgBuilderGenerator.isAlreadyGenerated(fnInit)) {
                            String builder = KwArgBuilderGenerator.createKwArgsBuilder(classContext, fnInit);
                            if (!builder.isEmpty()) {
                                result.append(builder);
                                result.append('\n');
                            }
                        }
                        String kwArgsBuilderFQN = KwArgBuilderGenerator.getJavaBuilderFQN(fnInit);
                        List<List<OverloadArgsGenerator.Argument>> argVariations
                                = OverloadArgsGenerator.generateVariations(context, fnInit);

                        for (List<OverloadArgsGenerator.Argument> args : argVariations) {
                            
                            if (!args.isEmpty() && args.getLast().getKind() == ArgKind.ARG_STAR2 && kwArgsBuilderFQN != null) {
                                result.append("{{indent}}// The ");
                                result.append(args.getLast().getName());
                                result.append(" argument can be an instance of ");
                                result.append(kwArgsBuilderFQN);
                                result.append("\n");
                            }
                            String javadoc = GeneratorUtils.getJavadoc(fnInit, classContext);
                            if (javadoc.isEmpty() && context.getConfig().addLogComments(classContext)) {
                                javadoc = (GeneratorUtils.getMissingJavadocComment(fnInit.getFullname(), classContext));
                            }
                            
                            if (javadoc != null && !javadoc.isEmpty()) {
                                result.append(javadoc);
                                result.append("\n");
                            }
                            result.append(template
                                    .replace("{{args}}", OverloadArgsGenerator.createArgsText(currentContext, args))
                                    .replace("{{arg_names}}", OverloadArgsGenerator.nameArgs(currentContext, args)));
                            result.append("\n");
                            argsResolved = true;
                        }
                    }
                }
                if (!argsResolved) {
                    result.append(template
                                    .replace("{{args}}", "")
                                    .replace("{{arg_names}}", ""));
                }

                result.append(template
                                .replace("{{args}}", "KeywordArguments arguments")
                                .replace("{{arg_names}}", "arguments"));

                return result.toString().replace("{{name}}", javaName);
            }

//            @Override
//            public String visit(Decorator decorator) {
//                return decorator.getFunc().accept(this);
//            }

            @Override
            public String visit(TypeInfo typeInfo) {
                lastInfo = typeInfo;
                return typeInfo.getDefn().accept(this);
            }

            @Override
            public String visit(Var v) {
                return "{{indent}}// TODO generate factory method for Var node with name '" + v.getName() + "'";
            }

            @Override
            public String visit(OverloadedFuncDef oFnDef) {
                return "{{indent}}// TODO generate factory method for OveloadedFuncDef node with name '" + oFnDef.name() + "'";
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
                            if (!(i == 0 && !fn.isClass()) || i > 0) {
                                isMandatory = true;
                                break;
                            }
                        }
                    }
                    return !isMandatory;
                }
                return false;
            }
        };
        return node.accept(factoryMethodVisitor);
    }

    private String createFactoryTypeMethod(GeneratorContext context, Node node) {
        DefaultNodeVisitor<String> factoryMethodTypeVisitor = new DefaultNodeVisitor<String>() {
            TypeInfo lastInfo = null;

            @Override
            protected String defaultVisit(Node node) {
                return null;
            }

            @Override
            public String visit(TypeInfo typeInfo) {
                currentContext.useType(typeInfo.getFullname() + "Type");
                return TEMPLATE_FACTORY_TYPE_METHOD.replace("{{name}}", typeInfo.getName());
            }
        };

        return node.accept(factoryMethodTypeVisitor);
    }

    private String getBaseInterface(GeneratorContext context) {
        String baseInterfaceName = (String)context.getConfiguration().get(Configuration.P_BASE_INTERFACE_NAME);
        String baseInterfacePackage = (String)context.getConfiguration().get(Configuration.P_BASE_INTERFACE_PACKAGE);
        context.useJavaType(baseInterfacePackage + '.' + baseInterfaceName);
        if (!generatedBaseInterface) {
            String template = TEMPLATE_VALUEBASE_INTERFACE;
            template = template.replace("{{license}}", "");
            template = template.replace("{{package}}", baseInterfacePackage);
            template = template.replace("{{name}}", baseInterfaceName);
            template = GeneratorUtils.indentTemplate(new GeneratorContext(null, configuration, null), template);
            try {
                GeneratorUtils.saveFile(currentContext, baseInterfacePackage, baseInterfaceName, template);
                generatedBaseInterface = true;
            } catch (IOException ex) {
                Logger.getLogger(TransformerVisitor.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return baseInterfaceName;
    }
}

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.generator.GeneratorUtils;
import org.graalvm.python.javainterfacegen.generator.TransformerVisitor;
import org.graalvm.python.javainterfacegen.generator.TypeGenerator;
import org.graalvm.python.javainterfacegen.generator.TypeManager;
import org.graalvm.python.javainterfacegen.generator.TypeNameGenerator;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeInfo;
import org.graalvm.python.javainterfacegen.mypy.types.AnyType;
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.ExtraAttrs;
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
import org.graalvm.python.javainterfacegen.python.Utils;

public class TypeGeneratorImpl implements TypeGenerator {

    private static final String TEMPLATE = """
{{license}}

{{generatedinfo}}

package {{package}};
                                                     
{{imports}}

{{javadoc}}
public class {{name}} {
                                                                                
{{content}}
}
""";
    private static final String TEMPLATE_FIELD = "{{indent}}private final Optional<{{type}}> {{name}};";
    private static final String TEMPLATE_IS_METHOD = """
{{indent}}public boolean is{{Name}}() {
{{indent+1}}return {{name}}.isPresent();
{{indent}}}
""";
    private static final String TEMPLATE_IS_NONE = """
{{indent}}public boolean isNone() {
{{indent+1}}return {{exp}};
{{indent}}}
""";
    private static final String TEMPLATE_GETTER = """
{{indent}}public Optional<{{type}}> get{{Name}}() {
{{indent+1}}return {{name}};
{{indent}}}
""";
    private static final String TEMPLATE_CONSTRUCTOR = """
{{indent}}public {{typename}}({{type}} {{name}}) {
{{indent+1}}this.{{name}} = Optional.of({{name}});
{{others}}
{{indent}}}
""";
    private static final String TEMPLATE_CONSTRUCTOR_NONE = """
{{indent}}public {{typename}}() {
{{others}}
{{indent}}}
""";
    private static final String TEMPLATE_TOSTRING = """
{{indent}}@Override                                          
{{indent}}public String toString() {
{{body}}
{{indent+1}}return "None";
{{indent}}}
""";
    private static final String TEMPLATE_TOSTRING_PART = """
{{indent+1}}if(!{{name}}.isEmpty()) {
{{indent+2}}return {{name}}.toString();
{{indent+1}}}
""";
    private static final String TEMPLATE_CONSTRUCTOR_OTHER_LINE = "{{indent+1}}this.{{name}} = Optional.empty();";

    @Override
    public String createType(Type type, GeneratorContext context, String javaPackage, String name) {
        String template = TEMPLATE;
        template = template.replace("{{license}}", GeneratorUtils.getLicense(context));

        String generatedInfo = "";
        if (context.addTimestamp()) {
            generatedInfo = GeneratorUtils.generateTimeStamp() + '\n';
        }
        template = template.replace("{{generatedinfo}}", generatedInfo);

        template = template.replace("{{package}}", javaPackage);

        Configuration config = context.getConfig();

        GeneratorContext fileContext = new GeneratorContext(null, config, null, true);
        fileContext.setJavaFQN(javaPackage + "." + name);
//        template = template.replace("{{extends}}", "GuestValue");

//        fileContext.addImport(config.getBaseAPIPackage(context) + ".GuestValue");
        template = template.replace("{{javadoc}}", GeneratorUtils.createJavaDoc("Generated from Python type: " + type.toString()));
        template = template.replace("{{name}}", name);

        fileContext.increaseIndentLevel();
        template = template.replace("{{content}}", getBody(fileContext, type, name));
        fileContext.decreaseIndentLevel();

        template = template.replace("{{imports}}", GeneratorUtils.generateImports(fileContext.getImports()));

        try {
            GeneratorUtils.saveFile(context, javaPackage, name, template);
        } catch (IOException ex) {
            Logger.getLogger(TransformerVisitor.class.getName()).log(Level.SEVERE, null, ex);
        }
        return template;
    }

    private static class BodyBuilder implements TypeVisitor<String> {

        private final String typeName;
        private final GeneratorContext context;
        private final Map<String, String> fields;

        private boolean firstLevel;
        protected boolean hasNone;

        public BodyBuilder(GeneratorContext context, String typeName) {
            this.context = context;
            this.typeName = typeName;
            this.fields = new TreeMap();
            this.firstLevel = true;
        }

        @Override
        public String visit(AnyType anyType) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String visit(CallableType callableType) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String visit(Instance instance) {
            TypeInfo info = instance.getType();
            String name = GeneratorUtils.convertToJavaIdentifierName(info.getName());
            String fqn = info.getFullname();
            fields.put(name, fqn);
            return "";
        }

        @Override
        public String visit(NoneType noneType) {
            hasNone = true;
            return "";
        }

        @Override
        public String visit(UnionType unionType) {
            if (!firstLevel) {
                return "Uninon";
            }
            Set<String> parts = new TreeSet();
            List<Type> items = unionType.getItems();
            firstLevel = false;

//            boolean wasFirstLevel = firstLevel;
//            firstLevel = false;
            for (int i = 0; i < items.size(); i++) {
                Type typeItem = items.get(i);
                typeItem.accept(this);
            }
//            StringBuilder sb = new StringBuilder();
////            if (!wasFirstLevel) {
////                sb.append("UnionOf");
////            }
//            boolean first = true;
//            for (String part : parts) {
//                if (first) {
//                    first = false;
//                } else {
//                    sb.append("Or");
//                }
//                sb.append(part);
//            }
//            if (hasNone) {
//                sb.append("OrNone");
//            }
//            if (!wasFirstLevel) {
//                sb.append("_");
//            } else {
//                // remove '_' at the end
//                int index = sb.length();
//                while (sb.charAt(--index) == '_') {
//                    sb.deleteCharAt(index);
//                }
//            }

//            return sb.toString();
            return "";

        }

        @Override
        public String visit(UninhabitedType uType) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String visit(TupleType tupleType) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String visit(TypeAliasType typeAliasType) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String visit(TypeVarType typeVarType) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String visit(LiteralType literalType) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String visit(TypeType typeType) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String visit(Overloaded overloaded) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String visit(ParamSpecType paramSpec) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String visit(Parameters parameters) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        @Override
        public String visit(TypedDictType typedDict) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    protected String getBody(GeneratorContext context, Type type, String typeName) {
//        BodyBuilder builder = new BodyBuilder(context, typeName);
//        type.accept(builder);
//        Map<String, Type> fields = builder.fields;
        Map<String, String> fields = new TreeMap();
        boolean hasNone = false;
        if (type instanceof UnionType union) {
            for (int i = 0; i < union.getItems().size(); i++) {
                Type item = union.getItems().get(i);
                if (item instanceof NoneType) {
                    hasNone = true;
                } else {
                    String itemTypeName = TypeManager.get().resolveJavaType(context, item, false, context.getDefaultJavaType());
                    fields.put(
                            GeneratorUtils.convertToJavaIdentifierName(TypeNameGenerator.createName(item)),
                            TypeManager.get().resolveJavaType(context, item, false, context.getDefaultJavaType())
                    );
                }
            }
        }
        if (type instanceof Instance instance) {

            String itemTypeName = TypeManager.get().resolveJavaType(context, instance, false, context.getDefaultJavaType());
            fields.put(
                    GeneratorUtils.convertToJavaIdentifierName(TypeNameGenerator.createName(instance)),
                    TypeManager.get().resolveJavaType(context, instance, false, context.getDefaultJavaType())
            );

        }

        StringBuilder sb = new StringBuilder();

        context.addImport("java.util.Optional");

        // fields
        for (Map.Entry<String, String> field : fields.entrySet()) {
            String name = field.getKey();
            String fqn = field.getValue();

            String fieldDecl = TEMPLATE_FIELD;
            fieldDecl = fieldDecl.replace("{{name}}", name).replace("{{type}}",
                    TypeManager.javaPrimitiveToWrapper(fqn));
            sb.append(fieldDecl).append("\n");
        }
        sb.append("\n");

        // constructors
        if (hasNone) {
            String constructor = TEMPLATE_CONSTRUCTOR_NONE;
            StringBuilder otherFields = new StringBuilder();
            for (String otherName : fields.keySet()) {
                String otherFieldLine = TEMPLATE_CONSTRUCTOR_OTHER_LINE;
                otherFieldLine = otherFieldLine.replace("{{name}}", otherName);
                otherFields.append(otherFieldLine).append("\n");
            }
            otherFields.deleteCharAt(otherFields.length() - 1);
            sb.append(TEMPLATE_CONSTRUCTOR_NONE.replace("{{typename}}", typeName)
                    .replace("{{others}}", otherFields.toString())
            ).append("\n");
        }

        for (Map.Entry<String, String> field : fields.entrySet()) {
            String name = field.getKey();
            String fqn = field.getValue();

            String constructor = TEMPLATE_CONSTRUCTOR;
            constructor = constructor.replace("{{typename}}", typeName);
            constructor = constructor.replace("{{type}}", fqn);
            constructor = constructor.replace("{{name}}", name);

            StringBuilder otherFields = new StringBuilder();
            for (String otherName : fields.keySet()) {
                if (!name.endsWith(otherName)) {
                    String otherFieldLine = TEMPLATE_CONSTRUCTOR_OTHER_LINE;
                    otherFieldLine = otherFieldLine.replace("{{name}}", otherName);
                    otherFields.append(otherFieldLine).append("\n");
                }
            }
            if (!otherFields.isEmpty()) {
                otherFields.deleteCharAt(otherFields.length() - 1);
            }
            constructor = constructor.replace("{{others}}", otherFields.toString());
            sb.append(constructor).append("\n");
        }

        // is methods
        if (hasNone) {
            StringBuilder expr = new StringBuilder();
            boolean first = true;
            for (String name : fields.keySet()) {
                if (first) {
                    first = false;
                } else {
                    expr.append(" && ");
                }
                expr.append(name).append(".isEmpty()");
            }
            sb.append(TEMPLATE_IS_NONE.replace("{{exp}}", expr.toString()));
            sb.append("\n");
        }

        for (String name : fields.keySet()) {
            sb.append(
                    TEMPLATE_IS_METHOD.replace("{{name}}", name).
                            replace("{{Name}}", GeneratorUtils.uppercaseFirstLetter(name)))
                    .append("\n");
        }

        // getters
        for (Map.Entry<String, String> field : fields.entrySet()) {
            String name = field.getKey();
            String fqn = field.getValue();
            sb.append(
                    TEMPLATE_GETTER.replace("{{type}}",
                            TypeManager.javaPrimitiveToWrapper(fqn))
                            .replace("{{name}}", name)
                            .replace("{{Name}}", GeneratorUtils.uppercaseFirstLetter(name))
            ).append("\n");
        }

        // toString
        StringBuilder toStringBody = new StringBuilder();
        for (String name : fields.keySet()) {
            toStringBody.append(
                    TEMPLATE_TOSTRING_PART.replace("{{name}}", name));
        }
        toStringBody.deleteCharAt(toStringBody.length() - 1);
        sb.append(TEMPLATE_TOSTRING.replace("{{body}}", toStringBody.toString()));
        sb.append("\n");
        return GeneratorUtils.indentTemplate(context, sb.toString());
    }
}

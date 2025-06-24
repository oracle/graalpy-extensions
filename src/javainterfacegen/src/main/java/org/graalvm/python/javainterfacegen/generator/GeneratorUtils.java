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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.mypy.nodes.ClassDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.Decorator;
import org.graalvm.python.javainterfacegen.mypy.nodes.DefaultNodeVisitor;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.Node;
import org.graalvm.python.javainterfacegen.mypy.nodes.NodeVisitor;
import org.graalvm.python.javainterfacegen.mypy.nodes.SymbolTableNode;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeInfo;
import org.graalvm.python.javainterfacegen.mypy.nodes.Var;
import org.graalvm.python.javainterfacegen.mypy.types.TupleType;

public class GeneratorUtils {
    private static String fileSeparator = System.getProperty("file.separator");

    public static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "null", "package", "private", "protected", "public", "return", "short",
            "static", "strictfp", "super", "switch", "synchronized", "this", "throw",
            "throws", "transient", "try", "void", "volatile", "while", "true", "false"));

    public static final Set<String> JAVA_PRIMITIVE_TYPES = new HashSet<>(Arrays.asList(
            "boolean", "byte", "char", "short", "int", "long", "float", "double"));

    public static String indent(GeneratorContext context) {
        String indentText = "";
        int indentation = (Integer) context.getConfiguration().get(Configuration.P_INDENTATION);
        for (int i = 0; i < indentation * context.getIndentLevel(); i++) {
            indentText = indentText + " ";
        }
        return indentText;
    }

    public static String indentTemplate(GeneratorContext context, final String template) {
        String result = template;
        result = result.replace("{{indent}}", indent(context));
        int indentLevel = 1;
        String indentText = "{{indent+" + indentLevel + "}}";
        while (result.indexOf(indentText) > -1) {
            context.increaseIndentLevel();
            result = result.replace(indentText, indent(context));
            indentText = "{{indent+" + ++indentLevel + "}}";
        }

        for (int i = 0; i < indentLevel - 1; i++) {
            context.decreaseIndentLevel();
        }
        return result;
    }

    public static void saveFile(GeneratorContext context, String packageName, String className, String content) throws IOException {
        Path path = getPathForType(context, packageName, className);
        File javaFile = new File(path.toUri());

        File directory = javaFile.getParentFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(javaFile))) {
            writer.write(content);
        }

    }

    public static Path getPathForType(GeneratorContext context, String fqn) {
        Map<String, Object> config = context.getConfiguration();
        String targetFolder = (String) config.get(Configuration.P_TARGET_FOLDER);
        String packagePath = fqn.replace('.', File.separatorChar);
        return Paths.get(targetFolder, packagePath + ".java");
    }

    public static Path getPathForType(GeneratorContext context, String packageName, String className) {
        return getPathForType(context, packageName + "." + className);
    }

    public static String uppercaseFirstLetter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        char firstChar = Character.toUpperCase(text.charAt(0));
        return firstChar + text.substring(1);
    }

    public static String lowercaseFirstLetter(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        char firstChar = Character.toLowerCase(text.charAt(0));
        return firstChar + text.substring(1);
    }

    private static String createLicenseText(String text) {
        String[] lines = text.split("\n");
        StringBuffer sb = new StringBuffer();
        sb.append("/*\n");
        for (String line : lines) {
            sb.append(" * ").append(line).append("\n");
        }
        sb.append(" */");
        return sb.toString();
    }

    public static String generateTimeStamp() {
        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return "// Genereted at " + currentDateTime.format(formatter);
    }

    public static String generateLocation(String location) {
        return "// Generated from " + location;
    }

    public static String generateImports(String[] imports) {
        StringBuilder sbImports = new StringBuilder();
        if (imports.length > 0) {
            for (String fqpn : imports) {
                sbImports.append("import ").append(fqpn).append(";\n");
            }
        }
        if (!sbImports.isEmpty()) {
            sbImports.deleteCharAt(sbImports.length() - 1);
        }
        return sbImports.toString();
    }

    public static String createJavaDoc(String text) {
        String[] lines = text.split("\n");
        StringBuffer sb = new StringBuffer();
        sb.append("/**\n");
        for (String line : lines) {
            sb.append(" * ").append(line).append("\n");
        }
        sb.append(" */");
        return sb.toString();
    }

    public static String importBlock(String[] imports) {
        StringBuilder sbImports = new StringBuilder();
        if (imports.length > 0) {
            for (String fqpn : imports) {
                sbImports.append("import ").append(fqpn).append(";\n");
            }
            sbImports.append("\n");
        }
        return sbImports.toString();
    }

    public static String getIndent(int indentLevel) {
        String indent = switch (indentLevel) {
            case 0 ->
                "";
            case 1 ->
                "{{indent}}";
            default ->
                "{{indent+" + (indentLevel - 1) + "}}";
        };
        return indent;
    }

    public static String wraptToJavadoc(String text, int indentLevel, boolean addStartEndMarks) {
        String indent = getIndent(indentLevel);

        StringBuilder javadocText = new StringBuilder();
        String[] lines = text.split("\n");
        if (addStartEndMarks) {
            javadocText.append(indent).append("/**\n");
        }
        for (String line : lines) {
            javadocText.append(indent).append(" * ").append(line).append("\n");
        }
        if (addStartEndMarks) {
            javadocText.append(indent).append(" */");
        }
        return javadocText.toString();
    }

    public static String getJavadoc(Node node, GeneratorContext context) {
        String[] javadocGenerators = context.getConfig().javadocGenerators(context);
        StringBuilder javadocText = new StringBuilder();
        String indent = getIndent(context.getIndentLevel());
        javadocText.append(indent).append("/**\n");
        boolean isAnyJavadoc = false;
        for (int i = 0; i < javadocGenerators.length; i++) {
            String javadocGenerator = javadocGenerators[i];
            String text = GeneratorFactory.createJavadocGenerator(javadocGenerator).create(node, context);
            if (text != null && !text.isBlank()) {
                isAnyJavadoc = true;
                javadocText.append(wraptToJavadoc(text, context.getIndentLevel(), false));
            }
        }
        javadocText.append(indent).append(" */");
        return isAnyJavadoc ? javadocText.toString() : "";
    }

    private static Map<String, String> licenseCache = new HashMap();
    public static String getLicense(GeneratorContext context) {
        Object licenseFilePath = context.getConfig().getProperties(context).get(Configuration.P_LICENSE_FILE);
        StringBuilder license = new StringBuilder();
        if (licenseFilePath  == null) {
            if (context.getConfig().addLogComments(context)) {
                license.append("// The license file is not provided, you can specify it via license_file property in configuration file");
            }
        } else {
            if (!licenseCache.containsKey(licenseFilePath)) {

                Path licensePath = context.getConfig().getReferenceFolder().resolve((String)licenseFilePath);
                File file = licensePath.toFile();
                FileReader r;
                try {
                    r = new FileReader(file);
                    int fileLen = (int) file.length();
                    CharBuffer cb = CharBuffer.allocate(fileLen);
                    r.read(cb);
                    cb.rewind();
                    licenseCache.put((String)licenseFilePath, GeneratorUtils.createLicenseText(cb.toString()));
                } catch (FileNotFoundException ex) {
                    Logger.getLogger(GeneratorUtils.class.getName()).log(Level.SEVERE, null, ex);
                } catch (IOException ex) {
                    Logger.getLogger(GeneratorUtils.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            license.append(licenseCache.get(licenseFilePath));
        }
        return license.toString();
    }

    public static String getMissingJavadocComment(String javadocKey, GeneratorContext context) {
        String indent = switch (context.getIndentLevel()) {
            case 0 ->
                "";
            case 1 ->
                "{{indent}}";
            default ->
                "{{indent+" + (context.getIndentLevel() - 1) + "}}";
        };
        JavadocStorageManager storageManager = GeneratorFactory.getJavadocStorageManager(
                context.getConfig().getJavadocStorageManager(context));
        Path javadocPath = storageManager.getStoragePath(context);
        String resultPath = javadocPath.toString();
        String javadocFolderPath = (String)context.getConfig().getProperties(context).get(Configuration.P_JAVADOC_FOLDER);
        if (resultPath.startsWith(javadocFolderPath)) {
            resultPath = resultPath.substring(javadocFolderPath.length());
            if (resultPath.startsWith(fileSeparator)) {
                resultPath = resultPath.substring(fileSeparator.length());
            }
        }
        return String.format("%s// TODO provide javadoc for\n%s//     key: %s\n%s//     file: %s",
                indent, indent, javadocKey, indent, resultPath);
    }

    public static String convertToJavaClassName(String pythonName) {
        String[] parts;
        if (pythonName.contains("_")) {
            parts = pythonName.split("_");
        } else {
            parts = splitByUpperCase(pythonName);
        }

        StringBuilder javaName = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                javaName.append(part.substring(0, 1).toUpperCase());
                javaName.append(part.substring(1));
            }
        }

        if (GeneratorUtils.JAVA_KEYWORDS.contains(javaName.toString().toLowerCase())) {
            javaName.append("_");
        }

        return javaName.toString();
    }

    public static boolean isInvalidJavaIdentifier(String input) {
        return JAVA_KEYWORDS.contains(input) || JAVA_PRIMITIVE_TYPES.contains(input);
    }

    public static String toValidJavaIdentifier(String input) {
        if (isInvalidJavaIdentifier(input)) {
            return input + "Value";
        }
        return input;
    }

    public static String convertToJavaIdentifierName(String pythonName) {
        String[] parts = pythonName.split("_");

        StringBuilder javaName = new StringBuilder();
        boolean firstPart = true;
        for (String part : parts) {
            if (!part.isEmpty()) {
                if (firstPart) {
                    javaName.append(part.substring(0, 1).toLowerCase());
                    javaName.append(part.substring(1));
                    firstPart = false;
                } else {
                    javaName.append(part.substring(0, 1).toUpperCase());
                    javaName.append(part.substring(1));
                }
            }
        }

        return toValidJavaIdentifier(javaName.toString());
    }

    private static String[] splitByUpperCase(String s) {
        return s.split("(?=[A-Z])");
    }

    private static class IsStaticVisitor extends DefaultNodeVisitor<Boolean> {

        @Override
        protected Boolean defaultVisit(Node node) {
            return false;
        }

        @Override
        public Boolean visit(Var v) {
            return v.isInitializedInClass();
        }

    }

    public static boolean isStatic(Node node) {
        return node.accept(new IsStaticVisitor());
    }

    public static boolean isClassAttribute(Node node) {
        return node.accept(new DefaultNodeVisitor<Boolean>() {
            @Override
            protected Boolean defaultVisit(Node node) {
                return false;
            }

            @Override
            public Boolean visit(Var v) {
                return v.isInitializedInClass();
            }
        });
    }

    public static boolean isStaticOrClassFunction(Node node) {
        return node.accept(new DefaultNodeVisitor<Boolean>() {
            @Override
            protected Boolean defaultVisit(Node node) {
                return false;
            }

            @Override
            public Boolean visit(FuncDef funcDef) {
                return funcDef.isStatic() || funcDef.isClass();
            }

            @Override
            public Boolean visit(Decorator decorator) {
                return decorator.getFunc().accept(this);
            }

        });
    }

    public static boolean hasStaticMember(Node node) {
        IsStaticVisitor isStaticVisitor = new IsStaticVisitor();

        NodeVisitor<Boolean> hasStaticMemberVisitor = new DefaultNodeVisitor<Boolean>() {

            TypeInfo lastTypeInfo = null;

            @Override
            protected Boolean defaultVisit(Node node) {
                return false;
            }

            @Override
            public Boolean visit(ClassDef classDef) {
                if (lastTypeInfo != null) {
                    Map<String, SymbolTableNode> symbolTable = lastTypeInfo.getNames().getTable();
                    StringBuilder content = new StringBuilder();
                    for (Map.Entry<String, SymbolTableNode> entry : symbolTable.entrySet()) {
                        String key = entry.getKey();
                        // TODO this is a hack, needs to be improved
                        if (lastTypeInfo.getTupleType() instanceof TupleType && key.endsWith("-redefinition")) {
                            continue;
                        }
                        // TODO has to be configurable
                        if (!key.startsWith("_")) {
                            SymbolTableNode tableNode = entry.getValue();
                            if (tableNode.getNode().accept(isStaticVisitor)) {
                                return true;
                            }
                        }
                    }
                }
                return false;
            }

            @Override
            public Boolean visit(TypeInfo typeInfo) {
                lastTypeInfo = typeInfo;
                return typeInfo.getDefn().accept(this);
            }

        };

        return node.accept(hasStaticMemberVisitor);
    }
}

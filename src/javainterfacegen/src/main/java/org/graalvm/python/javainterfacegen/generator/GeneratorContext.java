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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.mypy.nodes.ClassDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.DefaultNodeVisitor;
import org.graalvm.python.javainterfacegen.mypy.nodes.MypyFile;
import org.graalvm.python.javainterfacegen.mypy.nodes.Node;
import org.graalvm.python.javainterfacegen.python.Utils;

public class GeneratorContext {

    private final GeneratorContext parent;
    private final Node currentNode;
    private final Configuration configuration;

    private Set<String> imports;
    private static Set<String> doNotImport = Set.of("double", "int", "boolean", "float", "long",
            "char", "byte", "short",
            "String", "Integer", "Long", "Float", "Double", "Boolean", "Character", "Byte",
            "Short", "Object", "Class",
            "void");

    private Integer indentLevel;
    /**
     * Fully qualified name of the current generated class.
     */
    private String javaFQN;

    private class ShouldProcessVisitor extends DefaultNodeVisitor<Boolean> {

        private final GeneratorContext context;

        public ShouldProcessVisitor(GeneratorContext context) {
            this.context = context;
        }

        @Override
        protected Boolean defaultVisit(Node node) {
            return true;
        }

        @Override
        public Boolean visit(MypyFile mypyFile) {
            String[] paths = context.configuration.getFiles();
            for (String pathStr : paths) {
                Path path = Paths.get(pathStr);
                Path mypyFilePath = Paths.get(mypyFile.getPath());
                if (path.equals(mypyFilePath)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Boolean visit(ClassDef classDef) {
            System.out.print("Checking: " + classDef.getFullname());
            Map<String, Object> properties = context.getConfiguration();

            if (properties.containsKey(Configuration.P_WHITELIST)) {
                for (Object item : (List) properties.get(Configuration.P_WHITELIST)) {
                    System.out.println("item.class" + item.getClass().getName());
                    if (item instanceof Map) {
                        System.out.println("item: " + item);
                        Object clazz = ((Map) item).get(Configuration.P_CLASS);
                        if (clazz != null && classDef.getFullname().equals(clazz)) {
                            System.out.println(" -> true (is in the whitelist)");
                            return true;
                        }
                    }
                }
                System.out.println(" -> false (not in whitelist");
                return false;
            }

            System.out.println(" -> true (no whitelist)");
            return true;
        }
    }

    public GeneratorContext(GeneratorContext parent, Configuration configuration, Node currentNode) {
        this.parent = parent;
        this.configuration = configuration;
        this.currentNode = currentNode;
        this.imports = null;
        this.indentLevel = null;
        this.javaFQN = null;
    }

    public GeneratorContext(GeneratorContext parent, Configuration configuration, Node currentNode, boolean hasImports) {
        this(parent, configuration, currentNode);
        this.imports = hasImports ? new TreeSet() : null;
    }

    public GeneratorContext getParent() {
        return parent;
    }

    public Node getCurrentNode() {
        return currentNode;
    }

    public Map<String, Object> getConfiguration() {
        return configuration.getProperties(this);
    }

    public int getDeep() {
        return parent == null ? 0 : 1 + parent.getDeep();
    }

    public Configuration getConfig() {
        if (configuration != null) {
            return configuration;
        }
        return parent.getConfig();
    }

    /**
     * Fully qualified name of the current generated java class.
     */
    public String getJavaFQN() {
        if (javaFQN == null) {
            return parent != null ? parent.getJavaFQN() : null;
        }
        return javaFQN;
    }

    public void setJavaFQN(String javaFQN) {
        this.javaFQN = javaFQN;
    }

    public int getIndentLevel() {
        if (indentLevel == null) {
            if (parent == null) {
                return 0;
            }
            return parent.getIndentLevel();
        }
        return indentLevel;
    }

    public void setIndentLevel(int level) {
        this.indentLevel = level;
    }

    public void increaseIndentLevel() {
        indentLevel = getIndentLevel() + 1;
    }

    public void decreaseIndentLevel() {
        indentLevel = getIndentLevel() - 1;
    }

    public String getDefaultJavaType() {
        return (String) configuration.getProperties(this).get(Configuration.P_ANY_JAVA_TYPE);
    }

    public String useType(Value value) {
        System.out.println("fqn python type: " + Utils.getFullyQualifedName(value));
        return useType(Utils.getFullyQualifedName(value));
    }

    /**
     * Use a pythonFQN, return name of the java class
     * @param pythonFQN
     * @return
     */
    public String useType(String pythonFQN) {
        return useType(pythonFQN, getDefaultJavaType());
    }

    public String useImplementation(String pythonFQN, String defaultJavaFQN) {
        String javaFqn = TypeManager.get().getJavaType(pythonFQN, true);
        if (javaFqn == null) {
            javaFqn = TypeManager.get().addUnresolvedImplementation(this, pythonFQN, defaultJavaFQN);
            addImport(javaFqn);
            return javaFqn;
        }
        return useJavaType(javaFqn);
    }

    public GeneratorContext getOuterClassContext() {
        if (currentNode instanceof ClassDef) {
            return this;
        }
        if (parent != null ) {
            return parent.getOuterClassContext();
        }
        return null;
    }

    public String useType(String pythonFQN, String defaultJavaFQN) {
        String javaFqn = TypeManager.get().getJavaType(pythonFQN);
        if (javaFqn == null) {
            javaFqn = TypeManager.get().addUnresolvedType(this, pythonFQN, defaultJavaFQN);
            addImport(javaFqn);
            return javaFqn;
        }
        return useJavaType(javaFqn);
    }

    public String useJavaType(String fqn) {
        addImport(fqn);
        int index = fqn.lastIndexOf('.');
        if (index > -1) {
            fqn = fqn.substring(index + 1);
        }
        return fqn;
    }

    public boolean isClass() {
        return currentNode instanceof ClassDef;
    }

    public boolean isFile() {
        return currentNode instanceof MypyFile;
    }

    public String getFileFrom() {
        if (currentNode instanceof MypyFile mypyFile) {
            return mypyFile.getPath();
        }
        return parent.getFileFrom();
    }

    public String getRelativePath() {
        Path filePath = Path.of(getFileFrom());
        Path referenceFolder = configuration.getReferenceFolder();
        if (referenceFolder != null) {
            Path relativePath = referenceFolder.toAbsolutePath().relativize(filePath.toAbsolutePath());
            return relativePath.toString();
        }
        return filePath.toString();
    }

    /**
     *
     * @param fqpn
     * @return true if it was imported, false, when it is not possible to import
     */
    public boolean addImport(String fqpn) {

        if (doNotImport.contains(fqpn)) {
            return false;
        }
        if (fqpn.startsWith("java.lang.")) {
            return false;
        }
        String currentFQN = getJavaFQN();
        int lastDotIndex = currentFQN.lastIndexOf('.');
        String currentPackage = currentFQN.substring(0, lastDotIndex + 1);
        if (fqpn.startsWith(currentPackage) && fqpn.substring(currentPackage.length()).indexOf('.') == -1) {
            // the import is from the same package
            return false;
        }
        String currentName = currentFQN.substring(lastDotIndex + 1);
        int hashIndex = fqpn.lastIndexOf("#");
        String javaFQPN = hashIndex > 0 ? fqpn.substring(0, fqpn.lastIndexOf("#")) : fqpn;
        String importedName = javaFQPN.substring(javaFQPN.lastIndexOf('.') + 1);
        if (currentName.equals(importedName)) {
            // we can not import class with the same name, it has to be reffer with fqn
            return false;
        }
        if (imports != null) {
            imports.add(fqpn);
        } else {
            if (parent == null) {
                imports = new TreeSet<>();
                imports.add(fqpn);
            } else {
                parent.addImport(fqpn);
            }
        }
        return true;
    }

    public boolean isIgnored(String id) {
        Map<String, Object> properties = configuration.getProperties(this);
        Object ignorelist = properties.get(Configuration.P_IGNORE);
        if (ignorelist != null) {
            if (ignorelist instanceof String ignoreStr) {
                return id.equals(ignoreStr);
            } else if (ignorelist instanceof List ignoreList) {
                return ignoreList.contains(id);
            }
        }
        Object whitelist = properties.get(Configuration.P_WHITELIST);
        if (whitelist != null && whitelist instanceof List whiteList) {
            for (Object item : whiteList) {
                if (item instanceof Map whitelistItem) {
                    if (id.equals(whitelistItem.get(Configuration.P_CLASS))) {
                        return false;
                    }
                    if (id.equals(whitelistItem.get(Configuration.P_FUNCTION))) {
                        return false;
                    }
                } else if (item instanceof String name) {
                    if (id.equals(name)) {
                        return false;
                    }
                }
            }
            return true;
        }

        return false;
    }

    public String[] getImports() {
        if (imports != null) {
            return imports.toArray(new String[imports.size()]);
        }
        if (parent != null) {
            return parent.getImports();
        }
        return new String[0];
    }

    public boolean shouldContinue() {
        ShouldProcessVisitor visitor = new ShouldProcessVisitor(this);
        return currentNode.accept(visitor);
    }

    public boolean addTimestamp() {
        Map<String, Object> properties = getConfig().getProperties(this);
        Object shouldGenerate = properties.get(Configuration.P_ADD_GENERATED_TIMESTEMPS);
        if (shouldGenerate instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (shouldGenerate instanceof String stringValue) {
            return "TRUE".equals(stringValue.toUpperCase());
        }
        return true;
    }

    public boolean addLocation() {
        Map<String, Object> properties = getConfig().getProperties(this);
        Object shouldGenerate = properties.get(Configuration.P_ADD_LOCATION);
        if (shouldGenerate instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (shouldGenerate instanceof String stringValue) {
            return "TRUE".equals(stringValue.toUpperCase());
        }
        return true;
    }

    public String overridedReturnType(String memberName) {
        Map<String, Object> overrides = overrides();
        String returnType = null;
        if (overrides != null && overrides.containsKey(memberName)) {
            Object override = overrides.get(memberName);
            if (override != null && override instanceof Map map) {
                Object rt = (String)map.get(Configuration.P_RETURN_TYPE);
                if (rt instanceof String) {
                    returnType = (String)rt;
                }
            }
        }
        return returnType;
    }

    private Map<String, Object> overrides() {
        Map<String, Object> properties = configuration.getProperties(this);
        Object overrides = properties.get(Configuration.P_OVERRIDES);
        if (overrides == null) {
            return null;
        }
        if (overrides instanceof Map map) {
            GeneratorContext context = this;
            while (context != null && !(context.getCurrentNode() instanceof ClassDef)) {
                context = context.getParent();
            }

            if (context != null) {
                ClassDef classDef = (ClassDef)context.getCurrentNode();
                Map<String, Object> result = (Map<String, Object>)map.get(classDef.getFullname());
                if (result != null) {
                    return result;
                }
                result = (Map<String, Object>)map.get(classDef.getName());
                if (result != null) {
                    return result;
                }
            }
            return map;
        }

        return null;
    }

}

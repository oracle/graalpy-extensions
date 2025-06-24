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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.mypy.nodes.TypeInfo;
import org.graalvm.python.javainterfacegen.mypy.types.AnyType;
import org.graalvm.python.javainterfacegen.mypy.types.CallableType;
import org.graalvm.python.javainterfacegen.mypy.types.Instance;
import org.graalvm.python.javainterfacegen.mypy.types.LiteralType;
import org.graalvm.python.javainterfacegen.mypy.types.NoneType;
import org.graalvm.python.javainterfacegen.mypy.types.Overloaded;
import org.graalvm.python.javainterfacegen.mypy.types.TupleType;
import org.graalvm.python.javainterfacegen.mypy.types.Type;
import org.graalvm.python.javainterfacegen.mypy.types.TypeAliasType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeType;
import org.graalvm.python.javainterfacegen.mypy.types.TypeVarType;
import org.graalvm.python.javainterfacegen.mypy.types.TypedDictType;
import org.graalvm.python.javainterfacegen.mypy.types.UninhabitedType;
import org.graalvm.python.javainterfacegen.mypy.types.UnionType;
import org.graalvm.python.javainterfacegen.python.Utils;

public class TypeManager {

    private final static String UNKNOWN_TYPE_TEXT = "\n// TODO These Python types are not resolved:";
    private final static String UNKNOWN_PYTHON_TYPE_COMMENT = "\n//     %s";

    public final static String PYTHON_BOOLEAN = "builtins.bool";
    public final static String PYTHON_INT = "builtins.int";
    public final static String PYTHON_FLOAT = "builtins.float";
    public final static String PYTHON_STR = "builtins.str";

    public final static String PYTHON_LIST = "builtins.list";
    public final static String PYTHON_TUPLE = "builtins.tuple";
    public final static String PYTHON_DICT = "builtins.dict";
    public final static String PYTHON_SET = "builtins.set";

    private final static Set<String> PYTHON_COLLECTIONS = Set.of(
            PYTHON_LIST, PYTHON_TUPLE, PYTHON_DICT, PYTHON_SET
    );

    private final static char DELIMITER = '#';

    private static final TypeManager instance = new TypeManager();

    /**
     * Mapping from Python FQN type to Java FQN type
     */
    private final Map<String, String> registeredType;

    private final Set<String> registeredModules;

    private final Map<String, String> implementationType;

    private final static Map<String, String> primitiveToWrapper = initPrimitiveToWrapper();

    private static Map<String, String> initPrimitiveToWrapper() {
        Map<String, String> map = new HashMap<>();
        map.put("int", "Integer");
        map.put("boolean", "Boolean");
        map.put("char", "Character");
        map.put("byte", "Byte");
        map.put("short", "Short");
        map.put("long", "Long");
        map.put("float", "Float");
        map.put("double", "Double");
        return map;
    }

    private static Set<String> javaTypeWithGenerics = new HashSet(List.of(
            "java.util.Map",
            "java.util.List",
            "java.util.Set"));

    /**
     * Map of Python FQN types to file path, where are used." The key of map is
     * composed as Python FQN#Java FQN, where Java FQN is the default java type,
     * that should replace the Python FQN if, there is not found the
     * corresponding Java type.
     */
    private final Map<String, Set<Path>> unresolvedType;

    private TypeManager() {
        registeredType = new HashMap();
        registeredModules = new HashSet();
        unresolvedType = new HashMap();
        implementationType = new HashMap();
        init();
    }

    public static TypeManager get() {
        return instance;
    }

    /**
     * public mainly for test
     */
    public void init() {
        if (!registeredType.isEmpty()) {
            registeredType.clear();
        }
        if (!registeredModules.isEmpty()) {
            registeredModules.clear();
        }
        if (!unresolvedType.isEmpty()) {
            unresolvedType.clear();
        }
        if (!implementationType.isEmpty()) {
            implementationType.clear();
        }
        registerType(NoneType.FQN, "void");
        registerType(AnyType.FQN, "org.graalvm.polyglot.Value");
        registerType(PYTHON_INT, "long");
        registerType(PYTHON_STR, "java.lang.String");
        registerType(PYTHON_FLOAT, "double");
        registerType(PYTHON_BOOLEAN, "boolean");
        registerType(PYTHON_LIST, "java.util.List");
        registerType(PYTHON_TUPLE, "java.util.List");
        registerType(PYTHON_DICT, "java.util.Map");
        registerType(PYTHON_SET, "java.util.Set");
        registerType(UnionType.FQN, "org.graalvm.polyglot.Value");
    }

    public void clear() {
        registeredType.clear();
        registeredModules.clear();
        unresolvedType.clear();
        init();
    }

    public static boolean isCollection(String fqnPythonType) {
        return PYTHON_COLLECTIONS.contains(fqnPythonType);
    }

    public void registerImplementation(String pythonType, String javaType) {
        registeredType.put(pythonType + "^Impl", javaType);
    }

    public void registerType(String pythonType, String javaType) {
        registeredType.put(pythonType, javaType);
    }

    public void registerModule(String javaFQN) {
        registeredModules.add(javaFQN);
    }

    public void registerTypes(Configuration config) {
        Map<String, Object> globalProperties = config.getGlobalProperties();
        if (globalProperties.containsKey(Configuration.P_TYPE_MAPPINGS)) {
            Object mappings = globalProperties.get(Configuration.P_TYPE_MAPPINGS);
            if (mappings instanceof Map typeMappings) {
                for (Object key : typeMappings.keySet()) {
                    registerType((String) key, (String) typeMappings.get(key));
                }
            }
        }
    }

    public String getJavaType(Value value) {
        String fqn = Utils.getFullyQualifedName(value);
        return registeredType.get(fqn);
    }

    public String getJavaType(String pythonFQN, boolean isImplementation) {
        String type = isImplementation ? pythonFQN + "^Impl" : pythonFQN;
        return registeredType.get(type);
    }

    public String getJavaType(String pythonFQN) {
        return registeredType.get(pythonFQN);
    }

    private String getJustNameIfPosible(GeneratorContext context, String fqn) {
        int hashIndex = fqn.indexOf('#');
        if (hashIndex == -1) {
            String typeName = fqn.substring(fqn.lastIndexOf('.') + 1);
            if (fqn.startsWith("java.")) {
                return typeName;
            }
            String generatedFQN = context.getJavaFQN();
            if (!generatedFQN.endsWith("." + typeName)) {
                return typeName;
            }
            String currentPackage = generatedFQN.substring(0, generatedFQN.lastIndexOf('.') + 1);
            if (fqn.startsWith(currentPackage) && fqn.substring(currentPackage.length()).indexOf('.') == -1) {
                return typeName;
            }
        }
        return fqn;
    }

    public String resolveJavaType(GeneratorContext context, Type pythonType, boolean isArg,  String defaultJavaFQN) {
        if (pythonType instanceof Instance instance) {
            List<Type> args = instance.getArgs();

            TypeInfo info = instance.getType();
            String pythonFQN = info.getFullname();
            if ("typing.Coroutine".equals(pythonFQN)) {
                // ignore these types and replace just with Value
                context.addImport(defaultJavaFQN);
                return defaultJavaFQN.substring(defaultJavaFQN.lastIndexOf('.') + 1);
            }
            // do we know the type already
            String javaFQN = getJavaType(pythonFQN);
            boolean isUnresolved = false;
            if (javaFQN == null || javaFQN.isEmpty()) {
                isUnresolved = true;
                javaFQN = addUnresolvedType(context, pythonFQN, defaultJavaFQN);
            }
            context.addImport(javaFQN);
            if (args.isEmpty()) {
                return getJustNameIfPosible(context, javaFQN);
            } else {
                if (isUnresolved) {
                    // TODO we are not able to resolve the types correctly
                    // Now we can return just Value or jus the type itself, not the generics
                    // So for now let's try to return just types.
                    return javaFQN;
                } else if (!javaTypeWithGenerics.contains(javaFQN)) {
                    // TODO we don't support generics now
                    return getJustNameIfPosible(context, javaFQN);
                }
                StringBuilder sb = new StringBuilder();
                sb.append(getJustNameIfPosible(context, javaFQN));
                sb.append("<");
                for (int i = 0; i < args.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }

                    Type arg = args.get(i);
                    String resultType = resolveJavaType(context, args.get(i), isArg, defaultJavaFQN);
                    sb.append(javaPrimitiveToWrapper(resultType));
                }
                sb.append('>');
                if ("builtins.list".equals(pythonFQN)) {
                    String typeName = TypeNameGenerator.createName(instance);
                    String typePackage = context.getConfig().getTypePackage(context);
                    String fqnTypeName = typePackage + "." + typeName;
//                    System.out.println("typeName: " + typeName);
                    if (!isKnownJavaType(typeName)) {
                        registerType(typeName, fqnTypeName);
                        String typeGeneratorClassName = (String) context.getConfig().getProperties(context).get(Configuration.P_TYPE_GENERATOR);
                        TypeGenerator typeGen = GeneratorFactory.createTypeGenerator(typeGeneratorClassName);
                        typeGen.createType(instance, context, typePackage, typeName);
                    }
                }
                return sb.toString();
            }
        } else if (pythonType instanceof UnionType union) {
            List<Type> items = union.getItems();
            if (isSameLiteralType(union)) {
                return resolveJavaType(context, ((LiteralType) items.get(0)).getFallback(), isArg, defaultJavaFQN);
            }
            if (items.size() == 2) {
                if (items.get(0) instanceof NoneType) {
                    return javaPrimitiveToWrapper(resolveJavaType(context, items.get(1), isArg, defaultJavaFQN));
                }
                if (items.get(1) instanceof NoneType) {
                    return javaPrimitiveToWrapper(resolveJavaType(context, items.get(0), isArg, defaultJavaFQN));
                }
            }

            for (int i = 0; i < items.size(); i++) {
                Type item = items.get(i);
                String pythonFQN = PythonFQNResolver.findPythonFQN(item);
//                System.out.println("^^^^^^^^^^^^^^^ " + item.toString());
//                System.out.println(PythonFQNResolver.findPythonFQN(item));
                if ("builtins.list".equals(pythonFQN)) {
                    String typeName = TypeNameGenerator.createName(item);
                    String typePackage = context.getConfig().getTypePackage(context);
                    String fqnTypeName = typePackage + "." + typeName;
//                    System.out.println("typeName: " + typeName);
                    if (!isKnownJavaType(typeName)) {
                        registerType(typeName, fqnTypeName);
                        String typeGeneratorClassName = (String) context.getConfig().getProperties(context).get(Configuration.P_TYPE_GENERATOR);
                        TypeGenerator typeGen = GeneratorFactory.createTypeGenerator(typeGeneratorClassName);
                        typeGen.createType(item, context, typePackage, typeName);
                    }
                }

            }
//            // I create new type name for the type to cover cases like
//            // Union[None,builtins.str] and Union[builtins.str,None],
//            // which are the same
//            String typeName = union.accept(new TypeNameResolver());
//            String typePackage = context.getConfig().getTypePackage(context);
//            String fqnTypeName = typePackage + "." + typeName;
//            if (!isKnownJavaType(typeName)) {
//                String typeGeneratorClassName = (String) context.getConfig().getProperties(context).get(Configuration.P_TYPE_GENERATOR);
//                TypeGenerator typeGen = GeneratorFactory.createTypeGenerator(typeGeneratorClassName);
//                typeGen.createType(pythonType, context, typePackage, typeName);
//                registerType(typeName, fqnTypeName);
//            }
//            context.addImport(fqnTypeName);
//            return typeName;
            // we decieded to not cover union types from more types.
            context.addImport(defaultJavaFQN);
            return defaultJavaFQN.substring(defaultJavaFQN.lastIndexOf('.') + 1);
        } else if (pythonType instanceof AnyType anyType) {
            context.addImport(defaultJavaFQN);
            return defaultJavaFQN.substring(defaultJavaFQN.lastIndexOf('.') + 1);
        } else if (pythonType instanceof TypeAliasType aliasType) {
            return resolveJavaType(context, aliasType.getAlias().getTarget(), isArg, defaultJavaFQN);
        } else if (pythonType instanceof TupleType tupleType) {
            Instance fallback = tupleType.getPartialFallback();
//            System.out.println("========= tupletype: " + tupleType.toString() + " fallback: " + tupleType.getPartialFallback().toString());
            String pythonFallbackFQN = null;
            if (fallback != null) {
                pythonFallbackFQN = fallback.getType().getFullname();
            }
            if (pythonFallbackFQN == null || "builtins.tuple".equals(pythonFallbackFQN)) {
                Set<String> items = new HashSet();
                List<Type> types = tupleType.getItems();
                for (int i = 0; i < types.size(); i++) {
                    items.add(types.get(i).toString());
                }
                if (items.isEmpty() || items.size() > 1) {
                    context.addImport(defaultJavaFQN);
                    return defaultJavaFQN.substring(defaultJavaFQN.lastIndexOf('.') + 1) + "[]";
                } else {
                    String resultType = resolveJavaType(context, types.get(0), isArg, defaultJavaFQN);
                    return resultType.substring(resultType.lastIndexOf('.') + 1) + "[]";
                }
            } else {
                return resolveJavaType(context, fallback, isArg, defaultJavaFQN);
            }
        } else if (pythonType instanceof UninhabitedType uninhabitedType) {
            // TODO
            return addUnresolvedType(context, "non.resolved.UninhabitedType", defaultJavaFQN);
        } else if (pythonType instanceof NoneType noneType) {
            return isArg ? "Object" : "void";
        } else if (pythonType instanceof TypeVarType typeVarType) {
            if("Self".equals(typeVarType.getName())) {
                String javaFQN = context.getJavaFQN();
                return javaFQN.substring(javaFQN.lastIndexOf('.') + 1);
            }
            // TODO we are not supporting generics now
            //return "<" + typeVarType.getName() + "> " + typeVarType.getName();
            context.addImport(defaultJavaFQN);
            return defaultJavaFQN.substring(defaultJavaFQN.lastIndexOf('.') + 1);
        } else if (pythonType instanceof CallableType callable) {
            // TODO
            return addUnresolvedType(context, "non.resolved.CallableType", defaultJavaFQN);
        } else if (pythonType instanceof LiteralType literal) {
            // TODO
            System.out.println("####### resolving literaltype: " + literal.getValue().toString());
            return resolveJavaType(context, literal.getFallback(), isArg, defaultJavaFQN);
        } else if (pythonType instanceof TypeType typeType) {
            // TODO
            return resolveJavaType(context, typeType.getItem(), isArg, defaultJavaFQN);
        } else if (pythonType instanceof Overloaded overloaded) {
            // TODO
            return addUnresolvedType(context, "non.resolved.Overloaded", defaultJavaFQN);
        } else if (pythonType instanceof TypedDictType typedDict) {
            // TODO
            return addUnresolvedType(context, "non.resolved.TypedDictType", defaultJavaFQN);
        }
        throw new UnsupportedOperationException("Not supported yet " + pythonType.toString() + " "
                + Utils.getFullyQualifedName(pythonType.getValue()));
    }

    public static String javaPrimitiveToWrapper(String primitive) {
        return primitiveToWrapper.containsKey(primitive)
                ? primitiveToWrapper.get(primitive)
                : primitive;
    }

    public static String javaWrapperToPrimitive(String wrapperType) {
        for (Map.Entry<String, String> entry : primitiveToWrapper.entrySet()) {
            if (entry.getValue().equals(wrapperType)) {
                return entry.getKey();
            }
        }
        return wrapperType;
    }

    private boolean isSameLiteralType(UnionType union) {
        List<Type> items = union.getItems();
        if (items.isEmpty()) {
            return false;
        }
        Type item = items.get(0);

        if (item instanceof LiteralType literalType) {
            String fqn = literalType.getFallback().getType().getFullname();
            for (int i = 1; i < items.size(); i++) {
                item = items.get(i);
                if (item instanceof LiteralType literalType2) {
                    if (!fqn.equals(literalType2.getFallback().getType().getFullname())) {
                        return false;
                    }
                }

            }
            return true;
        }
        return false;
    }

    public boolean isKnownJavaType(Value value) {
        return getJavaType(value) != null;
    }

    public boolean isKnownJavaType(String pythonFQN) {
        return getJavaType(pythonFQN) != null;
    }

    /**
     *
     * @param context
     * @param pythonFQN python type, that is not resolved at that moment
     * @param defaultJavaFQN java type, that is used, if the python type is not
     * resolved at all.
     * @return
     */
    public String addUnresolvedType(GeneratorContext context, String pythonFQN, String defaultJavaFQN) {
        // what is generated now
        String javaFQNinUse = context.getJavaFQN();
        Path path = GeneratorUtils.getPathForType(context, javaFQNinUse);
        String key = pythonFQN + DELIMITER + defaultJavaFQN;
        if (unresolvedType.containsKey(key)) {
            unresolvedType.get(key).add(path);
        } else {
            Set<Path> whereUsed = new HashSet();
            whereUsed.add(path);
            unresolvedType.put(key, whereUsed);
        }
        return key;//pythonFQN + DELIMITER;
    }

    public String addUnresolvedImplementation(GeneratorContext context, String pythonFQN, String defaultJavaFQN) {
        // what is generated now
        String javaFQNinUse = context.getJavaFQN();
        Path path = GeneratorUtils.getPathForType(context, javaFQNinUse);
        String key = pythonFQN + "^Impl" + DELIMITER + defaultJavaFQN;
        if (unresolvedType.containsKey(key)) {
            unresolvedType.get(key).add(path);
        } else {
            Set<Path> whereUsed = new HashSet();
            whereUsed.add(path);
            unresolvedType.put(key, whereUsed);
        }
        return pythonFQN + "^Impl" + DELIMITER;
    }

    public void handleUnresolved() {
        if (!unresolvedType.isEmpty()) {

            Set<String> unresolved = new HashSet();
            for (String key : unresolvedType.keySet()) {
                int delimiterIndex = key.indexOf(DELIMITER);
                String pythonType = key.substring(0, delimiterIndex);
                String finalJavaType = getJavaType(pythonType);
                for (Path path : unresolvedType.get(key)) {
                    try {
                        String content = new String(Files.readAllBytes(path));
                        content = replace(content, key, finalJavaType, finalJavaType == null);
                        if (finalJavaType == null) {
                            content = addUnresolvedTypeCommnet(content, pythonType, finalJavaType);
                        }
                        Files.write(path, content.getBytes());
                    } catch (IOException ex) {
                        Logger.getLogger(TypeManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }

            if (!unresolved.isEmpty()) {
                System.out.println("These types are not resolved:");
                for (String type : unresolved) {
                    System.out.println("   " + type);
                }
            }
        } else {
            System.out.println("All types resolved.");
        }

    }

    public Set<String> getUnresolvedTypes() {
        return unresolvedType.keySet();
    }

    private boolean isDefaultForExtends(String javaFQN) {
        return javaFQN.endsWith(".GuestValue");
    }

    protected boolean inExtendsExpr(String source, int index) {
        int indexE = source.lastIndexOf("extends", index);
        if (indexE == -1) {
            return false;
        }
        indexE += 7;
        int indexC = source.indexOf("{", indexE);
        return index > indexE && index < indexC;
    }

    /**
     * Create a comment note about unresolved Python type
     *
     * @param source
     * @param pythonFQN the original Python type
     * @param javaFQN the Java type, that replace the Python type
     * @return
     */
    protected String addUnresolvedTypeCommnet(String source, String pythonFQN, String javaFQN) {
        StringBuilder updatedContent = new StringBuilder(source);
        int endIndexMainSection = updatedContent.indexOf(UNKNOWN_TYPE_TEXT);
        if (endIndexMainSection == -1) {
            // the commnent section was not found -> find the last import
            endIndexMainSection = updatedContent.lastIndexOf("\nimport ");
            endIndexMainSection = updatedContent.indexOf(";\n", endIndexMainSection);
            endIndexMainSection = endIndexMainSection + 2;
            updatedContent.insert(endIndexMainSection, UNKNOWN_TYPE_TEXT + "\n");
        }
        endIndexMainSection = endIndexMainSection + UNKNOWN_TYPE_TEXT.length();
        // find the appropriate subsection for Java type
        updatedContent.insert(endIndexMainSection, String.format(UNKNOWN_PYTHON_TYPE_COMMENT, pythonFQN));
        return updatedContent.toString();
    }

    /**
     *
     * @param source source code
     * @param target the original python type with delimiter and default value
     * @param replacement a default java type, that will be used instead,
     * unresolved python type
     * @return
     */
    protected String replace(String source, String target, String replacement, boolean isDefaultType) {
        String updatedContent = source;
        boolean isImported = true;

        if (replacement == null) {
            int delimiterIndex = target.indexOf(DELIMITER);
            replacement = target.substring(delimiterIndex + 1);
        }
        if (source.contains("import " + replacement + ";")) {
            // the final type is already imported -> remove the python import
            updatedContent = updatedContent.replace("import " + target + ";\n", "");
        } else {
            // the final type is not imported yet, replace type in the import.
            if (updatedContent.contains("import " + target + ";")) {
                updatedContent = updatedContent.replace("import " + target + ";", "import " + replacement + ";");
            } else {
                if (!replacement.startsWith("java.lang")) {
                    int packageIndex = source.indexOf("\npackage ");
                    int endPackageIndex = source.indexOf(";", packageIndex);
                    String packageExpression = source.substring(packageIndex, endPackageIndex).trim();
                    boolean isTheSamePackage = packageExpression.endsWith(" " + replacement.substring(0, replacement.lastIndexOf(".")));
                    if (!isTheSamePackage) {
                        if (source.contains("public interface " + replacement.substring(replacement.lastIndexOf(".") + 1))) {
                            isImported = false;
                        } else {
                            int index = updatedContent.lastIndexOf("\nimport ");
                            if (index == -1) {
                                index = updatedContent.indexOf("package ");
                            }
                            index = updatedContent.indexOf(';', index);
                            index = updatedContent.indexOf('\n', index);
                            index += 1;
                            updatedContent = updatedContent.substring(0, index)
                                    + "import " + replacement + ";\n" + updatedContent.substring(index);
                        }
                    }
                }
            }
        }

        if (isDefaultType) {
            // find the first occurence of the python type
            int index = updatedContent.indexOf(target);
            if (inExtendsExpr(updatedContent, index)) {
                // the default type need special actions in extends expr
                int extendsIndex = updatedContent.lastIndexOf("extends", index);
//                if (extendsIndex == -1) {
//                    // TODO remove this log blog.
//                    System.out.println("Source: ");
//                    System.out.println(source);
//                    System.out.println("-------------------------------------");
//                    System.out.println("target: " + target);
//                    System.out.println("replacement: " + replacement);
//                }
                int curlyIndex = updatedContent.indexOf("{", index);
                String extendsExp = updatedContent.substring(extendsIndex, curlyIndex);
                if (extendsExp.indexOf(',') > -1) {
                    // if there is only one type -> replace it as usually
                    // if there are more types, we can just delete this one
                    // I'm not sure, but probably yes.
                    index = extendsExp.indexOf(target);
                    int endIndex = index + target.length();
                    String newExtendExp = extendsExp.substring(0, index).stripTrailing();
                    if (newExtendExp.charAt(newExtendExp.length() - 1) == ',') {
                        newExtendExp = newExtendExp.substring(0, newExtendExp.length() - 1);
                    } else {
                        // this was the first one
                        endIndex = extendsExp.indexOf(',', endIndex) + 1;
                    }

                    newExtendExp = newExtendExp + extendsExp.substring(endIndex);
                    updatedContent = updatedContent.replace(extendsExp, newExtendExp);
                } else {
                    // only this and is replaced by default value -> remove all the extends expression
                    updatedContent = updatedContent.replace(extendsExp, "");
                }
            }
        }
        // replace all occurences int the source code afer imports end extends expr
        String shortName = replacement.substring(replacement.lastIndexOf('.') + 1);
        updatedContent = updatedContent.replace(target, isImported ? shortName : replacement);
        return updatedContent;
    }

    public void exportTypes(Path toFile, Set<String> exportedPackages) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String exportedPackage: exportedPackages) {
            String lookingFor = exportedPackage + ".";
            for (Map.Entry<String, String> entry : registeredType.entrySet()) {
                String javaType = entry.getValue();
                if (javaType.startsWith(lookingFor)) {
                    content.append(entry.getKey()).append(':')
                            .append(javaType).append('\n');
                }
            }
        }
        File file = new File(toFile.toUri());

        File directory = file.getParentFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content.toString());
        }
    }

    public void createNativeImageProxyConfig(Path toFile, Set<String> exportedPackages) throws IOException {
        String row = "    [\"type\"]";
        StringBuilder content = new StringBuilder();
        content.append("[");

        boolean first = true;
        for (String module : registeredModules) {
            if (!first) {
                content.append(',');
            } else {
                first = false;
            }
            content.append('\n')
                    .append(row.replace("type", module));
        }
        for (String exportedPackage : exportedPackages) {
            String lookingFor = exportedPackage + ".";
            for (Map.Entry<String, String> entry : registeredType.entrySet()) {
                String javaType = entry.getValue();
                if (javaType.startsWith(lookingFor)) {
                    if (!first) {
                        content.append(',');
                    } else {
                        first = false;
                    }
                    content.append('\n')
                            .append(row.replace("type", javaType));
                }
            }
        }
        content.append("\n]\n");
        File file = new File(toFile.toUri());

        File directory = file.getParentFile();
        if (!directory.exists()) {
            directory.mkdirs();
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(content.toString());
        }
    }

}

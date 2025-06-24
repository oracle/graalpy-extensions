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
package org.graalvm.python.javainterfacegen.configuration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.generator.JavadocStorageManager;
import org.graalvm.python.javainterfacegen.mypy.nodes.ClassDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.FuncDef;
import org.graalvm.python.javainterfacegen.mypy.nodes.MypyFile;


public class Configuration {
    /**
     * The generator can generate also a comments that can help to
     * understand, why is generated the code in certain way.
     * Default value: false
     */
    public final static String P_LOG_COMMENTS = "generate_log_comments";
    public final static String P_CLASSES = "classes";
    public final static String P_FILES = "files";
    public final static String P_FUNCTIONS = "functions";
    public final static String P_INDENTATION = "indentation";
    public final static String P_FUNCTION_GENERATORS = "function_generators";
    public final static String P_NAME_GENERATOR = "name_generator";
    public final static String P_TARGET_INTERFACE_PACKAGE = "interface_package";
    public final static String P_TARGET_IMPLEMENTATION_PACKAGE = "implementation_package";
    public final static String P_TARGET_PACKAGE_STRIP = "strip_python_package_prefix";
    public final static String P_TARGET_FOLDER = "target_folder";
    public final static String P_EXCLUDED_IMPORTS = "excluded_imports";
    public final static String P_ANY_JAVA_TYPE = "any_java_type";
    public final static String P_IMPLEMENTATION_NAME_SUFFIX = "implementation_name_suffix";
    public final static String P_GENERATE_BASE_CLASSES = "generate_base_classes";
    public final static String P_BASE_CLASSES_PACKAGE="base_classes_package";
    public final static String P_WHITELIST="whitelist";
    public final static String P_FUNCTION="function";
    public final static String P_CLASS="class";
    public final static String P_IGNORE="ignore";
    public final static String P_PATH = "path";

    public final static String P_TYPE_MAPPINGS = "type_mappings";

    public final static String P_TYPE_PACKAGE = "type_package";
    public final static String P_TYPE_GENERATOR = "type_generator";

    public final static String P_MYPY_CACHE_FOLDER="mypy_cache_folder";
    public final static String P_GENERATOR_CACHE_FOLDER="generator_cache_folder";

    public final static String P_JAVADOC_GENERATORS = "javadoc_generators";
    public final static String P_JAVADOC_STORAGE_MANAGER = "javadoc_storage_manager";

    public final static String P_GENERATE_TYPE_INTERFACE = "generate_type_interface";
    /**
     * Where are the files with Javadoc.
     */
    public final static String P_JAVADOC_FOLDER = "javadoc_folder";

    public final static String P_ADD_GENERATED_TIMESTEMPS = "generate_timestamps";
    public final static String P_ADD_LOCATION = "generate_location";

    public final static String P_OVERRIDES = "overrides";
    public final static String P_RETURN_TYPE = "return_type";

    public final static String P_BASE_INTERFACE_PACKAGE="base_interface_package";
    public final static String P_BASE_INTERFACE_NAME="base_interface_name";

    public final static String P_EXPORT_TYPES="export_types";
    public final static String P_EXPORT_FILE="export_files";
    public final static String P_EXPORT_INCLUDED="export_include";
    public final static String P_EXPORT_EXCLUDED="export_exclude";

    public final static String P_LICENSE_FILE="license_file";

    public final static String P_NATIVE_IMAGE_GENERATE_PROXY_CONFIG="generate_proxy_config";
    public final static String P_NATIVE_IMAGE_PATH_PROXY_CONFIG="path_proxy_config";

    private final ConfigurationLoader loader;

    private Map<String, Object> globalProperties;

    private Path referenceFolder;

    public Configuration(ConfigurationLoader loader) {
        this.loader = loader;
        this.globalProperties = null;
    }

    public void load() throws Exception {
        globalProperties = loader.loadConfiguration();

        Map<String, Object> defaultProp = new DefaultConfigurationLoader().loadConfiguration();
        if (globalProperties.get(P_BASE_INTERFACE_PACKAGE).equals(defaultProp.get(P_BASE_INTERFACE_PACKAGE)) &&
                !globalProperties.get(P_TARGET_INTERFACE_PACKAGE).equals(defaultProp.get(P_TARGET_INTERFACE_PACKAGE))) {
            globalProperties.put(P_BASE_INTERFACE_PACKAGE, globalProperties.get(P_TARGET_INTERFACE_PACKAGE));
        }
        if(globalProperties.get(P_EXPORT_INCLUDED) == null) {
            globalProperties.put(P_EXPORT_INCLUDED, globalProperties.get(P_TARGET_INTERFACE_PACKAGE));
        }

    }

    public Path getReferenceFolder() {
        return referenceFolder;
    }

    private void setReferenceFolderFor(String propertyName) {
        Object folderValue = globalProperties.get(propertyName);
        if (folderValue != null && folderValue instanceof String folder) {
            Path folderPath = Path.of(folder);
            if (!folderPath.isAbsolute()) {
                folderPath = referenceFolder.resolve(folderPath);
                globalProperties.put(propertyName, folderPath.normalize().toString());
            }
        }
    }

    public void setReferenceFolder(Path referenceFolder) {
        this.referenceFolder = referenceFolder;

        setReferenceFolderFor(Configuration.P_MYPY_CACHE_FOLDER);
        setReferenceFolderFor(Configuration.P_GENERATOR_CACHE_FOLDER);
        setReferenceFolderFor(Configuration.P_JAVADOC_FOLDER);
        setReferenceFolderFor(Configuration.P_TARGET_FOLDER);
    }

    public Path getPath(String propertyName) {
        Object folderValue = globalProperties.get(propertyName);
        if (folderValue != null && folderValue instanceof String folder) {
            return Path.of(folder);
        }
        return null;
    }


    public String[] checkConfiguration() {
        try {
            if (globalProperties == null) {
                load();
            }
        } catch (Exception ex) {
            return new String[]{"Was not possible to load configuration: " + ex.getMessage()};
        }
        List<String> messages = new ArrayList();

        Object targetFolder = globalProperties.get(P_TARGET_FOLDER);
        if ( targetFolder == null) {
            messages.add("Target folder has to be specified.");
        } else {
            if (!(targetFolder instanceof String)) {
                messages.add(P_TARGET_FOLDER + " has to be specified as path ");
            }
        }

        return messages.toArray(new String[messages.size()]);
    }

    public String[] getFiles() {
        Object files = globalProperties.get(P_FILES);
        if (files instanceof String path) {
            return new String[]{path};
        }
        if (files instanceof String[] strings) {
            return strings;
        }
        if (files instanceof Map) {
            return ((Map<String, Object>)files).keySet().toArray(String[]::new);
        }
        if (files instanceof List) {
            List<String> paths = new ArrayList();
            for (Object file : (List)files) {
                if (file instanceof Map) {
                    Object path = ((Map)file).get("path");
                    if (path != null && path instanceof String) {
                        paths.add((String)path);
                    }
                }
            }
            return paths.toArray(String[]::new);
        }
        return new String[]{};
    }


    public String getTargetFolder() {
        return (String)globalProperties.get(P_TARGET_FOLDER);
    }

    public Map<String, Object> getGlobalProperties() {
        return globalProperties;
    }

    public Map<String, Object> getProperties(GeneratorContext context) {
        if (context == null) {
            return globalProperties;
        }
        List<GeneratorContext> parents = new ArrayList();
        parents.add(context);
        GeneratorContext currentContext = context;
        while (currentContext.getParent() != null) {
            currentContext = currentContext.getParent();
            parents.add(0, currentContext);
        }


        Map<String,Object> properties = new HashMap(globalProperties);
        for(GeneratorContext curContext : parents) {
            if (curContext.getCurrentNode() instanceof MypyFile mypyFile) {
                Object value = properties.get(P_FILES);
                if (value != null && value instanceof List) {
                    List files = (List)value;
                    for (Object fileConf : files) {
                        if (fileConf instanceof Map ) {
                            Map fileProps = (Map) fileConf;
                            String path = mypyFile.getPath();
                            if (mypyFile.isPackageInitFile()) {
                                path = path.substring(0, path.lastIndexOf('/'));
                            }
                            if (path.equals(fileProps.get(P_PATH))) {
                                properties.putAll(fileProps);
                            }

                        }
                    }
                }
            }

            if (curContext.getCurrentNode() instanceof ClassDef classDef) {
                Object whitelist = properties.get(P_WHITELIST);
                if (whitelist != null && whitelist instanceof List whiteList) {
                    for (Object item : whiteList) {
                        if (item instanceof Map whitelistItem) {
                            Object clazz = whitelistItem.get(Configuration.P_CLASS);
                            if (clazz != null && (clazz.equals(classDef.getFullname()) || clazz.equals(classDef.getName()))) {
                                properties.remove(P_WHITELIST);
                                properties.remove(P_IGNORE);
                                properties.putAll(whitelistItem);
                                break;
                            }
                        } else if (item instanceof String name) {
                            if (name.equals(classDef.getFullname()) || name.equals(classDef.getName())) {
                                properties.remove(P_WHITELIST);
                                properties.remove(P_IGNORE);

                                break;
                            }
                        }
                    }
                }
                Object classes = properties.get(P_CLASSES);
                if (classes != null && classes instanceof Map) {
                    Object classProperties = ((Map)classes).get(classDef.getName());
                    if (classProperties != null && classProperties instanceof Map) {
                        properties.putAll((Map)classProperties);
                        continue;
                    }
                }
            }

            if (curContext.getCurrentNode() instanceof FuncDef funcDef) {
                Object functions = properties.get(P_FUNCTIONS);
                if (functions != null && functions instanceof Map) {
                    Object functionProperties = ((Map)functions).get(funcDef.getName());
                    if (functionProperties != null && functionProperties instanceof Map) {
                        properties.putAll((Map)functionProperties);
                    }
                }
            }

        }
        return properties;
    }

    /**
     *
     * @return true if is set up logging messages in configuration for this context
     */
    public boolean addLogComments(GeneratorContext context) {
        Object result = getProperties(context).get(P_LOG_COMMENTS);
        if (result instanceof Boolean) {
            return (boolean)result;
        }
        return "true".equals(result);
    }

    /**
     * Returns map of properties for the given context. If the context is null,
     * returns global properties
     * @param context
     * @return
     */
    public String getJavadocStorageManager(GeneratorContext context) {
        Map<String, Object> properties = getProperties(context);
        Object gens = properties.get(P_JAVADOC_STORAGE_MANAGER);
        if (gens instanceof List) {
            return ((List<String>)gens).get(0);
        }
        if (gens instanceof String clazz) {
            return clazz;
        }
        return "org.graalvm.python.javainterfacegen.generator.impl.JavadocStorageManagerYaml";
    }

    public String[] functionGenerators(GeneratorContext context) {
        Map<String, Object> properties = getProperties(context);
        Object gens = properties.get(P_FUNCTION_GENERATORS);
        if (gens instanceof List) {
            return ((List<String>)gens).toArray(String[]::new);
        }
        if (gens instanceof String string) {
            return new String[]{string};
        }
        return null;
    }

    public String[] javadocGenerators(GeneratorContext context) {
        Map<String, Object> properties = getProperties(context);
        Object gens = properties.get(P_JAVADOC_GENERATORS);
        if (gens instanceof List) {
            return ((List<String>)gens).toArray(String[]::new);
        }
        if (gens instanceof String string) {
            return new String[]{string};
        }
        return new String[0];
    }

    public String print() {
        return print(globalProperties, "");
    }

    private static String print(Map<String, Object> config, String indent) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            if (entry.getValue() instanceof Map) {
                sb.append(indent).append(entry.getKey()).append(":\n");
                sb.append(print((Map<String, Object>) entry.getValue(), indent + "  "));
            } else {
                sb.append(indent).append(entry.getKey());
                if (entry.getValue() != null) {
                    sb.append(": ");
                }
                if (entry.getValue() instanceof List values) {
                    switch (values.size()) {
                        case 1 -> sb.append(values.get(0));
                        case 0 -> sb.append("[]");
                        default -> {
                            for (Object value : values) {
                                sb.append("\n").append(indent).append("  ").append(value);
                            }
                        }
                    }
                } else if (entry.getValue() != null){
                    sb.append(entry.getValue());
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static Map<String, Object> merge(Map<String, Object> defaultConfig, Map<String, Object> addedConfig) {
        Map<String, Object> mergedConfig = new HashMap<>(defaultConfig);

        for (Map.Entry<String, Object> entry : addedConfig.entrySet()) {
            String key = entry.getKey();
            Object userValue = entry.getValue();
            Object defaultValue = mergedConfig.get(key);

            if (defaultValue instanceof Map && userValue instanceof Map) {
                mergedConfig.put(key, merge((Map<String, Object>) defaultValue, (Map<String, Object>) userValue));
            } else {
                mergedConfig.put(key, userValue);
            }
        }

        return mergedConfig;
    }

    public String getBaseAPIPackage(GeneratorContext context) {
        Map<String, Object> properties = getProperties(context);
        return properties.get(P_BASE_INTERFACE_PACKAGE).toString();
    }

    public String getBaseImplementationPackage(GeneratorContext context) {
        Map<String, Object> properties = getProperties(context);
        return properties.get(P_BASE_CLASSES_PACKAGE).toString();
    }

    public String getTypePackage(GeneratorContext context) {
        Map<String, Object> properties = getProperties(context);
        return properties.get(P_TYPE_PACKAGE).toString();
    }
}

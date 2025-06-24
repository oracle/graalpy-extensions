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
package org.graalvm.python.javainterfacegen;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.configuration.YamlConfigurationLoader;
import org.graalvm.python.javainterfacegen.generator.BaseClassManager;
import org.graalvm.python.javainterfacegen.generator.DefaultImplementationVisitor;
import org.graalvm.python.javainterfacegen.generator.DocstringProvider;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.generator.GeneratorFactory;
import org.graalvm.python.javainterfacegen.generator.JavadocStorageManager;
import org.graalvm.python.javainterfacegen.generator.TransformerVisitor;
import org.graalvm.python.javainterfacegen.generator.TypeManager;
import org.graalvm.python.javainterfacegen.mypy.MypyHook;
import org.graalvm.python.javainterfacegen.mypy.nodes.MypyFile;

public class Main {

    public static record ConfigurationItem (Configuration config, String configurationName, Path configFolder) { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Path to configuration yaml file is needed.");
            System.out.println(Arrays.toString(args));
            System.exit(1);
        }

        String yamlFilePath = args[0];
        Path path = Paths.get(yamlFilePath);

        if (!Files.exists(path) || !Files.isReadable(path)) {
            System.out.println("The specified YAML file does not exist or is not readable: " + yamlFilePath);
            System.exit(1);
        }

        Path configFolder = path.toAbsolutePath().getParent();
        YamlConfigurationLoader yamlLoader = new YamlConfigurationLoader(path);
        Configuration config = new Configuration(yamlLoader);
        config.load();

        String configurationName = path.getFileName().toString();
        configurationName = configurationName.substring(0, configurationName.lastIndexOf('.'));
        process(config, configurationName, configFolder);
    }

    protected static String[] resolvePaths(Configuration config, Path configFolder) {
        String[] whatProcess = config.getFiles();
        List<String> nonResolvedPath = new ArrayList();
        List<String> resolvedPaths = new ArrayList();

        for (String file : whatProcess) {
            Path filePath = Paths.get(file);
            if (!Files.exists(filePath)) {
                Path resolvedPath = configFolder.resolve(file);
                if (Files.exists(resolvedPath)) {
                    resolvedPaths.add(resolvedPath.toAbsolutePath().normalize().toString());
                } else {
                    nonResolvedPath.add(file);
                }
            } else {
                resolvedPaths.add(filePath.toAbsolutePath().normalize().toString());
            }
        }
        if (!nonResolvedPath.isEmpty()) {
            System.out.println("Can not resolve path:");
            for (String filePath : nonResolvedPath) {
                System.out.println("    " + filePath);
            }
            System.exit(1);
        }
        return resolvedPaths.toArray(whatProcess);
    }

    protected static List<String> findTopParents(String[] paths) {
        if (paths == null || paths.length == 0) {
            return Collections.EMPTY_LIST;
        }

        List<Path> pathList = new ArrayList<>();
        for (String pathStr : paths) {
            if (pathStr != null && !pathStr.isEmpty()) {
                pathList.add(Paths.get(pathStr));
            }
        }

        Set<Path> topParents = new HashSet<>();
        for (Path path : pathList) {
            boolean isTopParent = true;
            for (Path otherPath : pathList) {
                if (!path.equals(otherPath) && isSubpath(otherPath, path)) {
                    isTopParent = false;
                    break;
                }
            }
            if (isTopParent) {
                topParents.add(path);
            }
        }

        List<String> result = new ArrayList(topParents.size());
        for (Path path : topParents) {
            result.add(path.toString());
        }

        return result;
    }

    protected static boolean isSubpath(Path potentialParent, Path potentialSubpath) {

        if (potentialParent == null || potentialSubpath == null) {
            return false;
        }

        if (potentialParent.equals(potentialSubpath)) {
            return true;
        }

        Path parentNormalized = potentialParent.normalize();
        Path subpathNormalized = potentialSubpath.normalize();


        try {
            Path relative = parentNormalized.relativize(subpathNormalized);
            if (relative.startsWith("..")) {
                return false;
            }
            return subpathNormalized.startsWith(parentNormalized);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    protected  static Map<String, MypyFile> parseWithMypy(Context context, String[] whatProcess,
            String configurationName, Path pathCacheFile, boolean showProgress) throws Exception {
        MypyHook mypyHook = MypyHook.fromContext(context);
        long start = System.currentTimeMillis();
        Thread timerThread  = null;
        if (showProgress) {
            timerThread = new Thread(new ClockOutRunnable("Mypy running time:"));
            timerThread.start();
        }
        List<String> topPaths = findTopParents(whatProcess);
        Map<String, Value> moduleDict = mypyHook.serialize_result(topPaths, pathCacheFile.toAbsolutePath().toString());
        if (showProgress && timerThread != null) {
            timerThread.interrupt();
        }
        long end = System.currentTimeMillis();
        if (showProgress && timerThread != null) {
            try {
                timerThread.join();
            } catch (InterruptedException e) {
            }
        }
        System.out.println(String.format("Mypy parsing of %s took %d seconds", configurationName, (end - start) / 1000));
        return convert(moduleDict);
    }

    protected static Map<String, MypyFile> deserializeMypy(Context context,
            String configurationName, Path pathCacheFile, boolean showProgress) throws Exception {
        MypyHook mypyHook = MypyHook.fromContext(context);
        Thread timerThread = null;
        long start = System.currentTimeMillis();
        if (showProgress) {
            timerThread = new Thread(new ClockOutRunnable("Deserializing:"));
            timerThread.start();
        }
        Map<String, Value> moduleDict = mypyHook.load_result(pathCacheFile.toAbsolutePath().toString());
        if(showProgress) {
            timerThread.interrupt();
        }
        long end = System.currentTimeMillis();
        System.out.println(String.format("Deserializing from cache of %s took %d seconds", configurationName, (end - start) / 1000));
        return convert(moduleDict);
    }

    protected static void generateJavadoc(Configuration config, Map<String, MypyFile> moduleDict, MypyHook mypyHook, String[] whatProcess) throws IOException {
        List<Path> whatProcessPaths = new ArrayList(whatProcess.length);
        for (int i = 0; i < whatProcess.length; i++) {
            whatProcessPaths.add(Paths.get(whatProcess[i]));
        }
        Path commonRoot = CommonRootCalculator.findCommonRoot(whatProcessPaths);
        String commonRootPath = commonRoot.toString();
        for (MypyFile mypyFile : moduleDict.values()) {
            if (!mypyFile.getPath().startsWith(commonRootPath)) {
                continue;
            }
            System.out.println("javadoc for " + mypyFile.getPath());
            GeneratorContext generatorContext = new GeneratorContext(null, config, mypyFile);
            JavadocStorageManager storageManager = GeneratorFactory.getJavadocStorageManager(
                    config.getJavadocStorageManager(generatorContext));
            storageManager.setReferenceFolder(commonRoot);
            Path javadocPath = storageManager.getStoragePath(generatorContext);
            if (!Files.exists(javadocPath)) {
                DocstringProvider docstringProvider = new DocstringProvider();
                Map<String, String> docstrings = mypyFile.accept(docstringProvider);
                if (docstrings.isEmpty() && mypyFile.getPath().endsWith(".pyi")) {
                    Path pyiPath = Paths.get(mypyFile.getPath());
                    Path pyPath = Paths.get(pyiPath.getParent().toString(), mypyFile.getName() + ".py");
                    if (Files.exists(pyPath)) {

                        docstrings = mypyHook.extract_docstrings(pyPath.toString(), mypyFile.getFullname());
                    } else {

                    }

                }

                if (!docstrings.isEmpty()) {
                    Files.createDirectories(javadocPath.getParent());
                    try (FileWriter writer = new FileWriter(javadocPath.toFile())) {
                        storageManager.save(docstrings, writer);
                    }
                }
            }
        }
    }

    protected static void generateInterfaces(Configuration config, Map<String, MypyFile> moduleDict, MypyHook mypyHook, String[] whatProcess) {
        TransformerVisitor generator = new TransformerVisitor(config);
        List<MypyFile> cache = findWhatProcess(whatProcess, moduleDict);
        // generate interfaces and implementaion
            int round = 1;
            Set<String> processedFilesFirst = new HashSet();
            Set<String> processedFiles = new HashSet();
            while (!cache.isEmpty()) {
                System.out.println("********** round " + round);
                for (MypyFile mypyFile : cache) {
                    generator.visit(mypyFile);
                    String mypyPath = mypyFile.getPath();
                    if(round == 1) {
                        processedFilesFirst.add(mypyPath.substring(0, mypyPath.lastIndexOf("/")));
                    }
                    processedFiles.add(mypyPath);
                }

                cache.clear();
                Set<String> unresolvedTypes = TypeManager.get().getUnresolvedTypes();
                Set<String> addedFiles = new HashSet();
                for (String unresolvedType : unresolvedTypes) {
                    String[] types = unresolvedType.split("#");
                    for (String type : types) {
                        System.out.println("Unresolved type: " + type);
                        String module = type.substring(0, type.lastIndexOf('.'));
                        System.out.println("  module: " + module);
                        System.out.println("  file: " + moduleDict.get(module));
                        MypyFile mFile = moduleDict.get(module);
                        if (mFile != null) {
                            String mypyPath = mFile.getPath();
                            if (!processedFiles.contains(mypyPath) && !addedFiles.contains(mypyPath)) {
                                for(String pathProcessed : processedFilesFirst) {
                                    if (mypyPath.startsWith(pathProcessed)) {
                                        cache.add(mFile);
                                        addedFiles.add(mFile.getPath());
                                        System.out.println("Adding file: " + mFile.getPath());
                                    }
                                }
                            }
                        }
                    }
                }
                round++;
            }
            TypeManager.get().handleUnresolved();
    }

    protected static void exportTypes(Configuration config) throws IOException {
        boolean exportTypes = (boolean) config.getGlobalProperties().get(Configuration.P_EXPORT_TYPES);
        if (exportTypes) {
            String exportPathTxt = (String) config.getGlobalProperties().get(Configuration.P_EXPORT_FILE);
            Path exportPath;
            if (exportPathTxt == null) {
                exportPath = config.getReferenceFolder().resolve("exportedTypes.txt");
            } else {
                exportPath = config.getReferenceFolder().resolve(exportPathTxt);
                if (Files.isDirectory(exportPath)) {
                    exportPath = Paths.get(exportPathTxt, "exportedTypes.txt");
                }
            }
            Object included = config.getGlobalProperties().get(Configuration.P_EXPORT_INCLUDED);
            Set<String> includedPackages = null;
            if (included instanceof List list) {
                includedPackages = Set.copyOf(list);
            } else if (included instanceof String pkg) {
                includedPackages = Set.of(pkg);
            }
            TypeManager.get().exportTypes(exportPath, includedPackages);
        }
    }

    protected static void createNativeProxyConfig(Configuration config) throws IOException {
        boolean nativeProxyConfig = (boolean) config.getGlobalProperties().get(Configuration.P_NATIVE_IMAGE_GENERATE_PROXY_CONFIG);
        if (nativeProxyConfig) {
            String strPathProxyConfig = (String) config.getGlobalProperties().get(Configuration.P_NATIVE_IMAGE_PATH_PROXY_CONFIG);
            Path pathProxyConfig = null;
            if (strPathProxyConfig == null) {
                pathProxyConfig = config.getReferenceFolder().resolve("proxy-config.json");
            } else {
                pathProxyConfig = config.getReferenceFolder().resolve(strPathProxyConfig);
                if (Files.isDirectory(pathProxyConfig)) {
                    pathProxyConfig = Paths.get(strPathProxyConfig, "proxy-config.json");
                }
            }
            pathProxyConfig = pathProxyConfig.normalize();
            TypeManager.get().createNativeImageProxyConfig(pathProxyConfig,
                    Set.of((String) config.getGlobalProperties().get(Configuration.P_TARGET_INTERFACE_PACKAGE)));
        }
    }

    public static void process(Configuration config, String configurationName, Path configFolder) throws Exception {
        System.out.println("============= Used configuration ================");
        System.out.println(config.print());
        System.out.println("=================================================");
        String[] messages = config.checkConfiguration();
        if (messages.length > 0) {
            for (String message : messages) {
                System.out.println(message + "\n");
            }
            return;
        }

        TypeManager.get().registerTypes(config);

        config.setReferenceFolder(configFolder);

        try (Context context = ContextFactory.getContext()) {
            String[] whatProcess = resolvePaths(config, configFolder);

            MypyHook mypyHook = MypyHook.fromContext(context);
            mypyHook.set_mypy_cache_folder(config.getPath(Configuration.P_GENERATOR_CACHE_FOLDER).toAbsolutePath().toString());

            Map<String, MypyFile> moduleDict;
            boolean generateJavadoc = true;
            Path pathCacheFile = config.getPath(Configuration.P_GENERATOR_CACHE_FOLDER)
                    .resolve(configurationName + ".json");
            if (!Files.exists(pathCacheFile)) {
                moduleDict = parseWithMypy(context, whatProcess, configurationName, pathCacheFile, true);
                generateJavadoc = true;
            } else {
                moduleDict = deserializeMypy(context, configurationName, pathCacheFile, true);
                generateJavadoc = true;
            }

            // check if javadoc exists and create if not
            if (generateJavadoc) {
                generateJavadoc(config, moduleDict, mypyHook, whatProcess);
            }

            generateInterfaces(config, moduleDict, mypyHook, whatProcess);

            exportTypes(config);
            createNativeProxyConfig(config);

            (new BaseClassManager(config)).createBaseClasses();
        } catch (PolyglotException e) {
            if (e.isExit()) {
                System.exit(e.getExitStatus());
            } else {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private static List<MypyFile> findWhatProcess(String[] whatProcess, Map<String, MypyFile> moduleDict) {
        List<MypyFile> result = new ArrayList<>();
        Set<String> modules = new HashSet();
        // find out the appropriate MypyFiles from the result and find if needs to be javadoc regenerated
        for (int i = 0; i < whatProcess.length; i++) {
            MypyFile mypyFile = null;
            boolean isStdLibFile = whatProcess[i].contains("typeshed/stdlib/");
            String stdLibFilePath = isStdLibFile ? whatProcess[i].substring(whatProcess[i].indexOf("typeshed/stdlib/")) : null;

            File processingFile = new File(whatProcess[i]);
            boolean isDirectory = processingFile.isDirectory();
            if (isDirectory) {
                String lookingForModule = isDirectory ? whatProcess[i] + "/__init__.py" : whatProcess[i];
                String lookingForModuleI = isDirectory ? whatProcess[i] + "/__init__.pyi" : whatProcess[i];
                for (MypyFile mFile : moduleDict.values()) {
                    if (lookingForModule.equals(mFile.getPath())
                            || lookingForModuleI.equals(mFile.getPath())) {
                        mypyFile = mFile;
                        modules.add(mFile.getPath());
                        break;
                    }
                }
            } else {
                for (MypyFile mFile : moduleDict.values()) {
                    if (!isStdLibFile && whatProcess[i].equals(mFile.getPath())
                            || (isStdLibFile && mFile.getPath().endsWith(stdLibFilePath))) {
                        mypyFile = mFile;
                        break;
                    }
                }
            }
            if (mypyFile != null) {
//                mypyFile.getValue().getMember("imports");
                result.add(mypyFile);
            }
            // TODO report if a file is not found
        }

        // add all submodules
        for (String modulePath: modules) {
            String module = modulePath.substring(0, modulePath.lastIndexOf('/'));
            for (MypyFile mFile : moduleDict.values()) {
                String path = mFile.getPath();
                if (!path.equals(modulePath)
                        && path.startsWith(module)) {
                    String name = path.substring(module.length()+1);
                    if (!name.startsWith("_") && !name.contains("/")) {
                        result.add(mFile);
                    }
                }
            }
        }

        return result;
    }

    protected static Map<String, MypyFile> convert(Map<String, Value> dict) {
        Map<String, MypyFile> result = new HashMap();
        for (Map.Entry<String, Value> entry : dict.entrySet()) {
            String key = entry.getKey();
            Value value = entry.getValue();
            result.put(key, new MypyFile.MypyFileImpl(value));
        }
        return result;
    }
}

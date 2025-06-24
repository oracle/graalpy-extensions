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
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.python.javainterfacegen.configuration.Configuration;
import org.graalvm.python.javainterfacegen.generator.GeneratorContext;
import org.graalvm.python.javainterfacegen.generator.JavadocStorageManager;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

public class JavadocStorageManagerYaml implements JavadocStorageManager {

    private Path referenceFolder = null;


    private static class TrimConstructor extends Constructor {

        public TrimConstructor(LoaderOptions options) {
            super(options);
        }

        @Override
        protected String constructScalar(ScalarNode node) {
            String value = (String) super.constructScalar(node);
            return value.stripTrailing();
        }
    }

    @Override
    public void setReferenceFolder(Path folder) {
        this.referenceFolder = folder;
    }

    @Override
    public Map<String, String> load(InputStream input) {
        LoaderOptions options = new LoaderOptions();
        Constructor cons = new TrimConstructor(options);
        Yaml yaml = new Yaml(cons);
        Map<String, String> data = yaml.load(input);
        return data;
    }

    @Override
    public void save(Map<String, String> map, Writer writer) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setIndent(4);
        options.setPrettyFlow(true);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);

        Representer representer = new Representer(options) {
            @Override
            protected Node representMapping(Tag tag, Map<?, ?> mapping, DumperOptions.FlowStyle flowStyle) {

                List<NodeTuple> nodeTuples = new ArrayList<>();

                for (Map.Entry<?, ?> entry : mapping.entrySet()) {
                    Node keyNode = representScalar(Tag.STR, entry.getKey().toString(), DumperOptions.ScalarStyle.PLAIN);
                    Node valueNode = representScalar(Tag.STR, entry.getValue().toString().stripTrailing() + "\n\n", DumperOptions.ScalarStyle.LITERAL);
                    nodeTuples.add(new NodeTuple(keyNode, valueNode));
                }

                return new MappingNode(tag, nodeTuples, flowStyle);
            }

        };

        Yaml yaml = new Yaml(representer, options);
        yaml.dump(map, writer);
    }

    @Override
    public Path getStoragePath(GeneratorContext context) {
        String javadocFolder = (String) context.getConfiguration().get(Configuration.P_JAVADOC_FOLDER);
        String filePath = context.getFileFrom();
        int endIndex = filePath.lastIndexOf('.');
        if (endIndex != -1) {
            filePath = filePath.substring(0, endIndex);
        }
        String relativePath = getRelativePath(filePath) + ".yaml";
        Path pathJavadocCacheFileName = Paths.get(javadocFolder, relativePath);
        return pathJavadocCacheFileName.normalize();
    }

    private String getRelativePath(String filePath) {
        if (referenceFolder == null) {
            return filePath;
        }
        String referenceFolderPath = referenceFolder.toAbsolutePath().toString();
        if (filePath.startsWith(referenceFolderPath)) {
            return filePath.substring(referenceFolderPath.length());
        }
        return filePath;
    }

}

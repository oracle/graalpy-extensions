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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultConfigurationLoader implements ConfigurationLoader {

    private  Map<String, Object> global;

    public DefaultConfigurationLoader() {
        this.global = null;
    }

    @Override
    public Map<String, Object> loadConfiguration() throws Exception {
        if (global == null) {
            global = new HashMap();
            global.put(Configuration.P_INDENTATION, 4);
            global.put(Configuration.P_TARGET_INTERFACE_PACKAGE, "org.mycompany.api");
            global.put(Configuration.P_TARGET_IMPLEMENTATION_PACKAGE, "org.mycompany.implementation");
            global.put(Configuration.P_FUNCTION_GENERATORS, List.of("org.graalvm.python.javainterfacegen.generator.impl.JustInterfacesGeneratorImpl"));
            global.put(Configuration.P_NAME_GENERATOR, "org.graalvm.python.javainterfacegen.generator.impl.NameGeneratorImpl");
            global.put(Configuration.P_EXCLUDED_IMPORTS, List.of("java.lang.*"));
            global.put(Configuration.P_ANY_JAVA_TYPE, "org.graalvm.polyglot.Value");
            global.put(Configuration.P_TARGET_FOLDER, "./target/generated-sources/src");
            global.put(Configuration.P_IMPLEMENTATION_NAME_SUFFIX, "Impl");
            global.put(Configuration.P_GENERATE_BASE_CLASSES, "false");
            global.put(Configuration.P_LOG_COMMENTS, "false");

            global.put(Configuration.P_TYPE_PACKAGE, "org.mycompany.api.types");
            global.put(Configuration.P_TYPE_GENERATOR, "org.graalvm.python.javainterfacegen.generator.impl.TypeInterfaceGeneratorImpl");

            global.put(Configuration.P_JAVADOC_STORAGE_MANAGER, "org.graalvm.python.javainterfacegen.generator.impl.JavadocStorageManagerYaml");
            global.put(Configuration.P_JAVADOC_FOLDER, "./javadoc-cache");
            global.put(Configuration.P_JAVADOC_GENERATORS, List.of("org.graalvm.python.javainterfacegen.generator.impl.JavadocGeneratorImpl"));

            global.put(Configuration.P_BASE_INTERFACE_PACKAGE, "org.mycompany.api.graalvm");
            global.put(Configuration.P_ADD_GENERATED_TIMESTEMPS, true);
            global.put(Configuration.P_ADD_LOCATION, true);

            global.put(Configuration.P_GENERATE_TYPE_INTERFACE, false);

            global.put(Configuration.P_BASE_INTERFACE_PACKAGE, "org.mycompany.api");
            global.put(Configuration.P_BASE_INTERFACE_NAME, "GraalValueBase");

            global.put(Configuration.P_EXPORT_TYPES, true);
            global.put(Configuration.P_EXPORT_EXCLUDED, Collections.EMPTY_LIST);

            global.put(Configuration.P_NATIVE_IMAGE_GENERATE_PROXY_CONFIG, true);

        }
        return global;
    }

}

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
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.python.javainterfacegen.configuration.Configuration;

public class BaseClassManager {

    private final GeneratorContext globalContext;

    public BaseClassManager(Configuration configuration) {
        this.globalContext = new GeneratorContext(null, configuration, null);
    }

    public void createBaseClasses() {
        Map<String, Object> globalProperties = globalContext.getConfiguration();
        boolean copy = "true".equals(globalProperties.get(Configuration.P_GENERATE_BASE_CLASSES).toString().toLowerCase());

        if (copy) {
            String apiPackage = globalProperties.get(Configuration.P_BASE_INTERFACE_PACKAGE).toString();
            String implPakage = globalProperties.get(Configuration.P_BASE_CLASSES_PACKAGE).toString();

            Set<String> apiTemplates = Set.of("GuestValue", "GuestArray", "Utils");
            Set<String> implTemplates = Set.of("GuestValueDefaultImpl");

            try {
                for (String apiTemplate : apiTemplates) {
                    processTemplate("baseFileTemplates/" + apiTemplate + ".template", apiPackage, apiTemplate);
                }

                for (String implTemplate : implTemplates) {
                    processTemplate("baseFileTemplates/" + implTemplate + ".template", apiPackage, implTemplate);
                }
            } catch (IOException ex) {
                Logger.getLogger(BaseClassManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void processTemplate(String templatePath, String packageFQN, String className) throws IOException {
        InputStream inputStream = BaseClassManager.class.getClassLoader().getResourceAsStream(templatePath);
        if (inputStream == null) {
            throw new IOException("Template not found: " + templatePath);
        }

        String content = new String(inputStream.readAllBytes());
        content = content.replace("{{package}}", packageFQN);

        LocalDateTime currentDateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        content = content.replace("{{generated}}", "// Generated at " + currentDateTime.format(formatter));

        GeneratorUtils.saveFile(globalContext, packageFQN, className, content);
    }

}

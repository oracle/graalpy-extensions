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

import java.util.ServiceLoader;

public class GeneratorFactory {

    private static final ServiceLoader<FunctionGenerator> loaderFnPartGenerators
            = ServiceLoader.load(FunctionGenerator.class);

    public static FunctionGenerator createFunctionGenerator(String className) {
        for (FunctionGenerator generator : loaderFnPartGenerators) {
            if (generator.getClass().getName().equals(className)) {
                return generator;
            }
        }
        throw new IllegalArgumentException("Unknown service class: " + className);
    }

    private static final ServiceLoader<NameGenerator> nameGenerators
            = ServiceLoader.load(NameGenerator.class);

    public static NameGenerator createNameGenerator(String className) {
        for (NameGenerator generator : nameGenerators) {
            if (generator.getClass().getName().equals(className)) {
                return generator;
            }
        }
        throw new IllegalArgumentException("Unknown service class: " + className);
    }

    private static final ServiceLoader<JavadocGenerator> javadocGenerators
            = ServiceLoader.load(JavadocGenerator.class);

    public static JavadocGenerator createJavadocGenerator(String className) {
        for (JavadocGenerator generator : javadocGenerators) {
            if (generator.getClass().getName().equals(className)) {
                return generator;
            }
        }
        throw new IllegalArgumentException("Unknown service class: " + className);
    }

    private static final ServiceLoader<JavadocStorageManager> javadocStorageManagers
            = ServiceLoader.load(JavadocStorageManager.class);

    public static JavadocStorageManager getJavadocStorageManager(String className) {
        for (JavadocStorageManager manager : javadocStorageManagers) {
            if (manager.getClass().getName().equals(className)) {
                return manager;
            }
        }
        throw new IllegalArgumentException("Unknown service class: " + className);
    }

    private static final ServiceLoader<TypeGenerator> typeGenerators
            = ServiceLoader.load(TypeGenerator.class);

    public static TypeGenerator createTypeGenerator(String className) {
        for (TypeGenerator generator : typeGenerators) {
            if (generator.getClass().getName().equals(className)) {
                return generator;
            }
        }
        throw new IllegalArgumentException("Unknown service class: " + className);
    }
}

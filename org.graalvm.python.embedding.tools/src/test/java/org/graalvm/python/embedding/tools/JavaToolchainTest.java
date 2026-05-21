/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
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
 * The above copyright notice and either this complete permission notice or a
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
package org.graalvm.python.embedding.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaToolchainTest {

	@Test
	public void parsesJavaMajorVersion() {
		assertEquals(8, JavaToolchain.parseJavaMajorVersion("1.8.0_412"));
		assertEquals(21, JavaToolchain.parseJavaMajorVersion("21.0.11"));
		assertEquals(25, JavaToolchain.parseJavaMajorVersion("25-ea"));
		assertEquals(25, JavaToolchain.parseJavaMajorVersion("25.0.0"));
		assertEquals(-1, JavaToolchain.parseJavaMajorVersion("ea"));
		assertEquals(-1, JavaToolchain.parseJavaMajorVersion("1.ea"));
	}

	@Test
	public void usesConfiguredJavaExecutableAndVersion() {
		Path java = Path.of("custom-java-home", "bin", "java");
		JavaToolchain javaToolchain = JavaToolchain.fromJavaExecutable(java, 25);
		assertEquals(java, javaToolchain.javaExecutable());
		assertEquals(25, javaToolchain.javaMajorVersion());
	}

	@Test
	public void parsesConfiguredJavaVersion() {
		Path java = Path.of("custom-java-home", "bin", "java");
		JavaToolchain javaToolchain = JavaToolchain.fromJavaExecutable(java, "21.0.11");
		assertEquals(21, javaToolchain.javaMajorVersion());
	}

	@Test
	public void detectsConfiguredJavaVersionWhenBuildToolVersionIsMissing(@TempDir Path fakeJavaHome)
			throws IOException {
		Files.writeString(fakeJavaHome.resolve("release"), "JAVA_VERSION=\"25.0.1\"\n");
		Path java = fakeJavaHome.resolve("bin").resolve("java");
		JavaToolchain javaToolchain = JavaToolchain.fromJavaExecutable(java, (String) null);
		assertEquals(java, javaToolchain.javaExecutable());
		assertEquals(25, javaToolchain.javaMajorVersion());
	}

	@Test
	public void doesNotUseCurrentJavaVersionForConfiguredJavaExecutable() {
		String originalJavaVersion = System.getProperty("java.version");
		try {
			System.setProperty("java.version", "25.0.1");
			Path java = Path.of("custom-java-home", "bin", "java");
			JavaToolchain javaToolchain = JavaToolchain.fromJavaExecutable(java, (String) null);
			assertEquals(java, javaToolchain.javaExecutable());
			assertEquals(-1, javaToolchain.javaMajorVersion());
		} finally {
			System.setProperty("java.version", originalJavaVersion);
		}
	}

	@Test
	public void fallsBackToCurrentJavaHomeAndVersion() {
		String originalJavaVersion = System.getProperty("java.version");
		try {
			System.setProperty("java.version", "25.0.1");
			JavaToolchain javaToolchain = JavaToolchain.fromJavaExecutable(null, "17");
			assertEquals(Path.of(System.getProperty("java.home"), "bin", "java"), javaToolchain.javaExecutable());
			assertEquals(25, javaToolchain.javaMajorVersion());
		} finally {
			System.setProperty("java.version", originalJavaVersion);
		}
	}
}

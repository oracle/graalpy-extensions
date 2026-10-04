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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.regex.Pattern;

public final class JavaToolchain {

	private static final Pattern JAVA_MAJOR_VERSION_PATTERN = Pattern.compile("(?:1\\.(\\d+)|(?!1\\.)(\\d+)).*");

	private final Path javaExecutable;
	private final int javaMajorVersion;

	private JavaToolchain(Path javaExecutable, int javaMajorVersion) {
		this.javaExecutable = Objects.requireNonNull(javaExecutable);
		this.javaMajorVersion = javaMajorVersion;
	}

	public static JavaToolchain fromSystemJava() {
		return new JavaToolchain(Paths.get(System.getProperty("java.home"), "bin", "java"),
				parseJavaMajorVersion(System.getProperty("java.version")));
	}

	public static JavaToolchain fromJavaExecutableAndVersion(Path javaExecutable, int javaMajorVersion) {
		if (javaExecutable == null) {
			return fromSystemJava();
		}
		return new JavaToolchain(javaExecutable, javaMajorVersion);
	}

	public static JavaToolchain fromJavaExecutable(Path javaExecutable) {
		if (javaExecutable == null) {
			return fromSystemJava();
		}
		return fromJavaExecutableAndVersion(javaExecutable, javaMajorVersion(javaExecutable));
	}

	public Path javaExecutable() {
		return javaExecutable;
	}

	public int javaMajorVersion() {
		return javaMajorVersion;
	}

	public boolean isAtLeast(int majorVersion) {
		return javaMajorVersion >= majorVersion;
	}

	static int parseJavaMajorVersion(String javaVersion) {
		if (javaVersion == null || javaVersion.isBlank()) {
			return -1;
		}
		var matcher = JAVA_MAJOR_VERSION_PATTERN.matcher(javaVersion.strip());
		if (!matcher.matches()) {
			return -1;
		}
		String majorVersion = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		return Integer.parseInt(majorVersion);
	}

	private static int javaMajorVersion(Path javaExecutable) {
		Path bin = javaExecutable.getParent();
		if (bin == null) {
			return -1;
		}
		Path javaHome = bin.getParent();
		if (javaHome == null) {
			return -1;
		}
		Path releaseFile = javaHome.resolve("release");
		if (!Files.isRegularFile(releaseFile)) {
			return -1;
		}
		try {
			for (String line : Files.readAllLines(releaseFile, StandardCharsets.UTF_8)) {
				if (line.startsWith("JAVA_VERSION=")) {
					return parseJavaMajorVersion(line.split("=", 2)[1].strip().replace("\"", ""));
				}
			}
		} catch (IOException ignored) {
		}
		return -1;
	}
}

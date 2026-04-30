/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.python.embedding.test;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GraalPyResourcesMigrationTest {
	private static final String MIGRATION_SNIPPETS_CLASS = "org.graalvm.python.embedding.GraalPyResourcesMigrationSnippets";

	@Test
	@SuppressWarnings("deprecation")
	public void createContextMigrationMatchesDeprecatedContextBuilderBeforeBuild() throws Exception {
		assertBuilderConfigEquals(GraalPyResources.contextBuilder(), documentedDefaultVirtualFilesystemMigration());
	}

	@Test
	@SuppressWarnings("deprecation")
	public void defaultVirtualFilesystemMigrationMatchesDeprecatedContextBuilder() throws Exception {
		assertBuilderConfigEquals(GraalPyResources.contextBuilder(), documentedDefaultVirtualFilesystemMigration());
	}

	@Test
	@SuppressWarnings("deprecation")
	public void virtualFilesystemMigrationMatchesDeprecatedContextBuilder() throws Exception {
		try (VirtualFileSystem vfs = VirtualFileSystem.create()) {
			assertBuilderConfigEquals(GraalPyResources.contextBuilder(vfs), documentedVirtualFilesystemMigration(vfs));
		}
	}

	@Test
	@SuppressWarnings("deprecation")
	public void externalDirectoryMigrationMatchesDeprecatedContextBuilder() throws Exception {
		Path externalResourcesDirectory = Files.createTempDirectory("graalpy-resources-migration");
		assertBuilderConfigEquals(GraalPyResources.contextBuilder(externalResourcesDirectory),
				documentedExternalDirectoryMigration(externalResourcesDirectory));
	}

	private static Context.Builder documentedDefaultVirtualFilesystemMigration() throws Exception {
		return invokeMigrationSnippet("defaultVirtualFilesystemContextBuilder");
	}

	private static Context.Builder documentedVirtualFilesystemMigration(VirtualFileSystem vfs) throws Exception {
		return invokeMigrationSnippet("virtualFilesystemContextBuilder", new Class<?>[]{VirtualFileSystem.class}, vfs);
	}

	private static Context.Builder documentedExternalDirectoryMigration(Path externalResourcesDirectory)
			throws Exception {
		return invokeMigrationSnippet("externalDirectoryContextBuilder", new Class<?>[]{Path.class},
				externalResourcesDirectory);
	}

	private static Context.Builder invokeMigrationSnippet(String methodName) throws Exception {
		return invokeMigrationSnippet(methodName, new Class<?>[0]);
	}

	private static Context.Builder invokeMigrationSnippet(String methodName, Class<?>[] parameterTypes, Object... args)
			throws Exception {
		Class<?> snippetsClass = Class.forName(MIGRATION_SNIPPETS_CLASS);
		Method method = snippetsClass.getDeclaredMethod(methodName, parameterTypes);
		method.setAccessible(true);
		return (Context.Builder) method.invoke(null, args);
	}

	private static void assertBuilderConfigEquals(Context.Builder expected, Context.Builder actual) throws Exception {
		assertEquals(builderConfig(expected), builderConfig(actual));
	}

	private static Map<String, Object> builderConfig(Context.Builder builder) throws Exception {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Field field : Context.Builder.class.getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers()) || field.getName().equals("this$0")) {
				continue;
			}
			field.setAccessible(true);
			result.put(field.getName(), normalizeValue(field.get(builder)));
		}
		return result;
	}

	private static Object normalizeValue(Object value) throws Exception {
		if (value == null) {
			return null;
		}
		if (value instanceof IOAccess ioAccess) {
			return ioAccessConfig(ioAccess);
		}
		Class<?> valueClass = value.getClass();
		if (valueClass.isArray()) {
			int length = Array.getLength(value);
			Object[] values = new Object[length];
			for (int i = 0; i < length; i++) {
				values[i] = normalizeValue(Array.get(value, i));
			}
			return Arrays.asList(values);
		}
		return value;
	}

	private static Map<String, Object> ioAccessConfig(IOAccess ioAccess) throws Exception {
		Map<String, Object> result = new LinkedHashMap<>();
		for (String fieldName : new String[]{"allowHostFileAccess", "allowHostSocketAccess", "fileSystem"}) {
			Field field = IOAccess.class.getDeclaredField(fieldName);
			field.setAccessible(true);
			Object value = field.get(ioAccess);
			if (fieldName.equals("fileSystem")) {
				// Default-VFS migration paths create equivalent but distinct FileSystem
				// instances.
				result.put(fieldName, value == null ? null : value.getClass().getName());
			} else {
				result.put(fieldName, value);
			}
		}
		return result;
	}
}

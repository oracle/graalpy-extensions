/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.python.embedding.test.integration;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class GraalPyResourcesTests {
	private static Engine newPythonEngine() {
		return Engine.newBuilder("python").option("engine.WarnInterpreterOnly", "false").build();
	}

	private static String pythonStringLiteral(String value) {
		return value.replace("\\", "\\\\").replace("'", "\\'");
	}

	private static String pythonPathLiteral(Path path) {
		return pythonStringLiteral(path.toAbsolutePath().toString());
	}

	private static String runtimeConfiguration(Context.Builder builder) {
		try (Engine engine = newPythonEngine(); Context context = builder.engine(engine).build()) {
			return context.eval("python", """
					import json
					import sys
					json.dumps([sys.executable, sys.path, sys.dont_write_bytecode, 'site' in sys.modules])
					""").asString();
		}
	}

	@Test
	public void sharedEngine() {
		// simply check if we are able to create a context with a shared engine
		Engine sharedEngine = Engine.create("python");
		Context.newBuilder().apply(GraalPyResources.of(VirtualFileSystem.create())).engine(sharedEngine).build()
				.close();
		Context.newBuilder().apply(GraalPyResources.of(VirtualFileSystem.create())).engine(sharedEngine).build()
				.close();
		Context.newBuilder().apply(GraalPyResources.of(Path.of("test"))).engine(sharedEngine).build().close();
		Context.newBuilder().apply(GraalPyResources.of(VirtualFileSystem.newBuilder().build())).engine(sharedEngine)
				.build().close();
		sharedEngine.close();
	}

	@Test
	@SuppressWarnings("deprecation")
	public void oldAndNewApiEntryPointsSetSameOptions() throws IOException {
		try (VirtualFileSystem vfs = VirtualFileSystem.newBuilder().build()) {
			assertEquals(runtimeConfiguration(GraalPyResources.contextBuilder()),
					runtimeConfiguration(Context.newBuilder().apply(GraalPyResources.of(VirtualFileSystem.create()))));
			assertEquals(runtimeConfiguration(GraalPyResources.contextBuilder(vfs)),
					runtimeConfiguration(Context.newBuilder().apply(GraalPyResources.of(vfs))));
		}
		Path resourcesDir = Files.createTempDirectory("graalpy-resources-options");
		try (VirtualFileSystem vfs = VirtualFileSystem.newBuilder().build()) {
			GraalPyResources.extractVirtualFileSystemResources(vfs, resourcesDir);
		}
		assertEquals(runtimeConfiguration(GraalPyResources.contextBuilder(resourcesDir)),
				runtimeConfiguration(Context.newBuilder().apply(GraalPyResources.of(resourcesDir))));
	}

	@Test
	public void testExtendJavaEmbeddingConfig() {
		try (Engine engine = newPythonEngine();
				Context context = Context.newBuilder().allowHostAccess(HostAccess.ALL)
						.apply(GraalPyResources.of(VirtualFileSystem.create())).engine(engine).build()) {
			context.getBindings("python").putMember("hostObject", new AtomicInteger(42));
			assertEquals(42, context.eval("python", "hostObject.get()").asInt());
		}
		try (Engine engine = newPythonEngine();
				Context context = Context.newBuilder().apply(GraalPyResources.of(VirtualFileSystem.create()))
						.engine(engine).extendHostAccess(HostAccess.ALL,
								hostAccessBuilder -> hostAccessBuilder.denyAccess(AtomicInteger.class))
						.build()) {
			context.getBindings("python").putMember("hostObject", new AtomicInteger(42));
			assertThrows(PolyglotException.class, () -> context.eval("python", "hostObject.get()"));
		}
		try (Engine engine = newPythonEngine();
				Context context = Context.newBuilder()
						.extendHostAccess(HostAccess.ALL,
								hostAccessBuilder -> hostAccessBuilder.denyAccess(AtomicInteger.class))
						.apply(GraalPyResources.of(VirtualFileSystem.create())).engine(engine).build()) {
			context.getBindings("python").putMember("hostObject", new AtomicInteger(42));
			assertThrows(PolyglotException.class, () -> context.eval("python", "hostObject.get()"));
		}
	}

	// file known to be in the testing VFS
	private static String file1Path(String root) {
		return root + (root.endsWith("\\") ? "\\file1" : "/file1");
	}

	@Test
	public void testExtendIOConfig() throws IOException {
		Path resourcesDir = Files.createTempDirectory("graalpy-resources-test");
		Path file1 = Path.of(file1Path(resourcesDir.toString()));
		String defaultFile1;
		try (VirtualFileSystem vfs = VirtualFileSystem.newBuilder().build()) {
			GraalPyResources.extractVirtualFileSystemResources(vfs, resourcesDir);
			defaultFile1 = file1Path(vfs.getMountPoint());
		}
		try (Engine engine = newPythonEngine();
				Context context = Context.newBuilder().apply(GraalPyResources.of(resourcesDir)).engine(engine)
						.build()) {
			assertEquals("text1\ntext2\n",
					context.eval("python", "open('%s').read()".formatted(pythonPathLiteral(file1))).asString());
		}
		assertThrows(PolyglotException.class, () -> {
			try (Engine engine = newPythonEngine();
					Context context = Context.newBuilder().apply(GraalPyResources.of(resourcesDir)).engine(engine)
							.extendIO(IOAccess.ALL, ioBuilder -> ioBuilder.allowHostFileAccess(false)).build()) {
				context.eval("python", "open('%s').read()".formatted(pythonPathLiteral(file1)));
			}
		});
		try (Engine engine = newPythonEngine();
				Context context = Context.newBuilder()
						.extendIO(IOAccess.ALL, ioBuilder -> ioBuilder.allowHostFileAccess(false))
						.apply(GraalPyResources.of(resourcesDir)).engine(engine).build()) {
			assertEquals("text1\ntext2\n",
					context.eval("python", "open('%s').read()".formatted(pythonPathLiteral(file1))).asString());
		}
		try (Engine engine = newPythonEngine();
				Context context = Context.newBuilder().allowIO(IOAccess.NONE)
						.apply(GraalPyResources.of(VirtualFileSystem.create())).engine(engine).build()) {
			assertEquals("text1\ntext2\n", context
					.eval("python", "open('%s').read()".formatted(pythonStringLiteral(defaultFile1))).asString());
		}
	}
}

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

package org.graalvm.python.embedding;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * This class provides utilities related to Python resources used in GraalPy
 * embedding scenarios which can be of the following kind:
 * <ul>
 * <li>Python application files</li>
 * <li>Third-party Python packages</li>
 * </ul>
 *
 * <p>
 * Resource files can be embedded and distributed in an <b>application file</b>
 * or made available from an <b>external directory</b>.
 * </p>
 *
 * <h2>Virtual Filesystem</h2>
 * <p>
 * If the resource files are part of an <b>application file</b> (jar file or a
 * native image executable), then at runtime they will be accessed as standard
 * Java resources through GraalPy {@link VirtualFileSystem}. This will be
 * transparent to Python code running in GraalPy so that it can use standard
 * Python IO to access those files.
 * </p>
 *
 * <p>
 * In order to make this work, it is necessary for those embedded resources to
 * have a common <b>resource root directory</b>. The default value is
 * <code>/org.graalvm.python.vfs</code>, however the recommended convention is
 * to use {@code GRAALPY-VFS/{groupId}/{artifactId}}. This root directory will
 * then be in python code mapped to the virtual filesystem <b>mount point</b>,
 * by default <code>/graalpy_vfs</code>. Refer to
 * {@link VirtualFileSystem.Builder#resourceDirectory(String)} documentation for
 * more details.
 * </p>
 *
 * <h2>External Directory</h2>
 * <p>
 * As an alternative to Java resources with the Virtual Filesystem, it is also
 * possible to configure the GraalPy context to use an external directory, which
 * is not embedded as a Java resource into the resulting application. Python
 * code will access the files directly from the real filesystem.
 * </p>
 *
 * <h2>Conventions</h2>
 * <p>
 * The factory methods in GraalPyResources rely on the following conventions:
 * <ul>
 * <li>${resources_root}/src: used for Python application files. This directory
 * will be configured as the default search path for Python module files
 * (equivalent to PYTHONPATH environment variable).</li>
 * <li>${resources_root}/venv: used for the Python virtual environment holding
 * installed third-party Python packages. The Context will be configured as if
 * it is executed from this virtual environment. Notably packages installed in
 * this virtual environment will be automatically available for importing.</li>
 * </ul>
 * where ${resources_root} is either the resource root
 * <code>/org.graalvm.python.vfs</code> or an external directory.
 *
 * <p>
 * <b>Example</b> creating a GraalPy context configured for the usage with a
 * {@link VirtualFileSystem}:
 *
 * <pre>
 * VirtualFileSystem.Builder builder = VirtualFileSystem.newBuilder();
 * builder.unixMountPoint("/python-resources");
 * VirtualFileSystem vfs = builder.build();
 * try (Context context = Context.newBuilder()
 * 		.apply(GraalPyResources.forVirtualFileSystem(vfs))
 * 		.build()) {
 * 	context.eval("python", "for line in open('/python-resources/data.txt').readlines(): print(line)");
 * } catch (PolyglotException e) {
 * 	if (e.isExit()) {
 * 		System.exit(e.getExitStatus());
 * 	} else {
 * 		throw e;
 * 	}
 * }
 * </pre>
 *
 * In this example we:
 * <ul>
 * <li>create a {@link VirtualFileSystem} configured to have the root
 * <code>/python-resources</code></li>
 * <li>create a GraalPy context preconfigured with that
 * {@link VirtualFileSystem}</li>
 * <li>use the context to invoke a python snippet reading a resource file</li>
 * </ul>
 * <p>
 * <b>GraalPy context</b> instances created by factory methods in this class are
 * preconfigured with some particular resource paths:
 * <ul>
 * <li><code>${resources_root}/venv</code> - is reserved for a python virtual
 * environment holding third-party packages. The context will be configured as
 * if it were executed from this virtual environment. Notably packages installed
 * in this virtual environment will be automatically available for
 * importing.</li>
 * <li><code>${resources_root}/src</code> - is reserved for python application
 * files - e.g. python sources. GraalPy context will be configured to see those
 * files as if set in PYTHONPATH environment variable.</li>
 * </ul>
 * where <code>${resources_root}</code> is either an external directory or the
 * virtual filesystem resource root <code>/org.graalvm.python.vfs</code>.
 * <p>
 * <b>Example</b> creating a GraalPy context configured for the usage with an
 * external resource directory:
 *
 * <pre>
 * try (Context context = Context.newBuilder()
 * 		.apply(GraalPyResources.forExternalDirectory(Path.of("python-resources")))
 * 		.build()) {
 * 	context.eval("python", "import mymodule; mymodule.print_hello_world()");
 * } catch (PolyglotException e) {
 * 	if (e.isExit()) {
 * 		System.exit(e.getExitStatus());
 * 	} else {
 * 		throw e;
 * 	}
 * }
 * </pre>
 *
 * In this example we:
 * <ul>
 * <li>create a GraalPy context which is preconfigured with GraalPy resources in
 * an external resource directory</li>
 * <li>use the context to import the python module <code>mymodule</code>, which
 * should be either located in <code>python-resources/src</code> or in a python
 * package installed in <code>python-resources/venv</code> (python virtual
 * environment)</li>
 * </ul>
 * <p>
 *
 * For <b>more examples</b> on how to use this class refer to <a href=
 * "https://github.com/graalvm/graal-languages-demos/tree/main/graalpy">GraalPy
 * Demos and Guides</a>.
 *
 * @see VirtualFileSystem
 * @see VirtualFileSystem.Builder
 *
 * @since 24.2.0
 */
public final class GraalPyResources implements Consumer<Context.Builder> {
	private Consumer<Context.Builder> builderTemplate;

	private GraalPyResources(Consumer<Context.Builder> builderTemplate) {
		this.builderTemplate = Objects.requireNonNull(builderTemplate);
	}

	/**
	 * Creates a GraalPy resource configuration for the given
	 * {@link VirtualFileSystem}. The configuration is applied to an existing
	 * {@link Context.Builder} and sets the VFS filesystem and Python resource
	 * options for the <a href="https://docs.python.org/3/library/venv.html">Python
	 * virtual environment</a> contained in the virtual filesystem.
	 * <p>
	 * Following resource paths are preconfigured:
	 * <ul>
	 * <li><code>/org.graalvm.python.vfs/venv</code> - is set as the python virtual
	 * environment location</li>
	 * <li><code>/org.graalvm.python.vfs/src</code> - is set as the python sources
	 * location</li>
	 * </ul>
	 * <p>
	 * <b>Example</b> creating a GraalPy context and additionally setting the
	 * verbose option and allowing host interop.
	 *
	 * <pre>
	 * VirtualFileSystem vfs = VirtualFileSystem.create();
	 * Context.Builder builder = Context.newBuilder()
	 * 		.apply(GraalPyResources.forVirtualFileSystem(vfs))
	 * 		.option("python.VerboseFlag", "true")
	 * 		.allowHostAccess(HostAccess.ALL);
	 * try (Context context = builder.build()) {
	 * 	context.eval("python", "print('hello world')");
	 * } catch (PolyglotException e) {
	 * 	if (e.isExit()) {
	 * 		System.exit(e.getExitStatus());
	 * 	} else {
	 * 		throw e;
	 * 	}
	 * }
	 * </pre>
	 * <p>
	 * When the virtual filesystem is located in other than the default resource
	 * directory, {@code org.graalvm.python.vfs}, i.e., using Maven or Gradle option
	 * {@code resourceDirectory}, configure it with
	 * {@link VirtualFileSystem.Builder#resourceDirectory(String)} when building the
	 * {@link VirtualFileSystem} passed to this method.
	 *
	 * <p>
	 * <b>Full details: </b> this method applies the following options to the given
	 * {@link Context.Builder}:
	 *
	 * <pre>
	 *     .extendIO(IOAccess.NONE, ioBuilder -> configureVirtualFileSystem())
	 *     .option("python.ForceImportSite", "true") // allows importing packages from the VFS
	 *     .option("python.PosixModuleBackend", "java")
	 *     .option("python.DontWriteBytecodeFlag", "true")
	 *     .option("python.CheckHashPycsMode", "never")
	 *     .option("python.Executable", appropriatePathWithinVirtualFileSystem())
	 *     .option("python.PythonPath", appropriatePathWithinVirtualFileSystem())
	 *     .option("python.InputFilePath", appropriatePathWithinVirtualFileSystem());
	 * </pre>
	 *
	 * @param vfs
	 *            the {@link VirtualFileSystem} to configure on the target
	 *            {@link Context.Builder}
	 * @see <a href=
	 *      "https://github.com/oracle/graalpython/blob/master/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PythonOptions.java">PythonOptions</a>
	 * @return a {@link GraalPyResources} configuring a
	 *         {@link org.graalvm.polyglot.Context.Builder}
	 * @since 25.1.0
	 */
	public static GraalPyResources forVirtualFileSystem(VirtualFileSystem vfs) {
		return new GraalPyResources(builder -> applyVirtualFilesystemConfig(builder, vfs));
	}

	/**
	 * Creates a GraalPy resource configuration for resources located in an external
	 * directory in the real filesystem. The configuration is applied to an existing
	 * {@link Context.Builder} and sets host filesystem IO plus Python resource path
	 * options.
	 * <p>
	 * Following resource paths are preconfigured:
	 * <ul>
	 * <li><code>${externalResourcesDirectory}/venv</code> - is set as the python
	 * environment location</li>
	 * <li><code>${externalResourcesDirectory}/src</code> - is set as the python
	 * sources location</li>
	 * </ul>
	 * <p>
	 * <b>Example</b> creating a GraalPy context and additionally setting the
	 * verbose option and allowing host interop.
	 *
	 * <pre>
	 * Context.Builder builder = Context.newBuilder()
	 * 		.apply(GraalPyResources.forExternalDirectory(Path.of("python-resources")))
	 * 		.option("python.VerboseFlag", "true")
	 * 		.allowHostAccess(HostAccess.ALL);
	 * try (Context context = builder.build()) {
	 * 	context.eval("python", "import mymodule; mymodule.print_hello_world()");
	 * } catch (PolyglotException e) {
	 * 	if (e.isExit()) {
	 * 		System.exit(e.getExitStatus());
	 * 	} else {
	 * 		throw e;
	 * 	}
	 * }
	 * </pre>
	 *
	 * In this example we:
	 * <ul>
	 * <li>create a GraalPy context which is preconfigured with GraalPy resources in
	 * an external resource directory</li>
	 * <li>use the context to import the python module <code>mymodule</code>, which
	 * should be either located in <code>python-resources/src</code> or in a python
	 * package installed in <code>/python/venv</code> (python virtual
	 * environment)</li>
	 * <li>note that in this scenario, the Python context has access to the
	 * extracted resources as well as the rest of the real filesystem</li>
	 * </ul>
	 *
	 * <p>
	 * External resources directory is often used for better compatibility with
	 * Python native extensions that may bypass the Python abstractions and access
	 * the filesystem directly from native code. Setting the
	 * {@code PosixModuleBackend} option to "native" increases the compatibility
	 * further, but in such case even Python code bypasses the Truffle abstractions
	 * and accesses native POSIX APIs directly. Usage:
	 *
	 * <pre>
	 * Context.newBuilder()
	 * 		.apply(GraalPyResources.forExternalDirectory(Path.of("python-resources")))
	 * 		.option("python.PosixModuleBackend", "native")
	 * </pre>
	 * <p>
	 *
	 * When Maven or Gradle GraalPy plugin is used to build the virtual environment,
	 * it also has to be configured to generate the virtual environment into the
	 * same directory using the {@code <externalDirectory>} tag in Maven or the
	 * {@code externalDirectory} field in Gradle.
	 *
	 * <p>
	 * <b>Full details: </b> this method applies the following options to the given
	 * {@link Context.Builder}:
	 *
	 * <pre>
	 *     .extendIO(IOAccess.NONE, ioBuilder -> ioBuilder.allowHostFileAccess(true))
	 *     .option("python.ForceImportSite", "true") // allows importing packages from the virtual environment
	 *     .option("python.Executable", appropriatePathWithinExternalDirectory())
	 *     .option("python.PythonPath", appropriatePathWithinExternalDirectory())
	 *     .option("python.InputFilePath", appropriatePathWithinExternalDirectory());
	 * </pre>
	 *
	 * @param externalResourcesDirectory
	 *            the root directory with GraalPy specific embedding resources
	 * @return a {@link GraalPyResources} configuring a
	 *         {@link org.graalvm.polyglot.Context.Builder}
	 * @since 25.1.0
	 */
	public static GraalPyResources forExternalDirectory(Path externalResourcesDirectory) {
		return new GraalPyResources(builder -> applyExternalDirectoryConfig(builder, externalResourcesDirectory));
	}

	@Override
	public void accept(Context.Builder builder) {
		builderTemplate.accept(builder);
	}

	// Options shared by both VirtualFileSystem and external resources directory
	// use-case
	private static Context.Builder applySharedContextConfig(Context.Builder builder) {
		return builder.
		// Force to automatically import site.py module, to make Python packages
		// available
				option("python.ForceImportSite", "true");
	}

	private static void applyVirtualFilesystemConfig(Context.Builder builder, VirtualFileSystem vfs) {
		applySharedContextConfig(builder).
		// allow access to the virtual filesystem and preserve any existing IO settings
				extendIO(IOAccess.NONE, ioBuilder -> ioBuilder.fileSystem(vfs.delegatingFileSystem)).
				// choose the backend for the POSIX module
				option("python.PosixModuleBackend", "java").
				// equivalent to the Python -B flag
				option("python.DontWriteBytecodeFlag", "true").
				// The sys.executable path, a virtual path that is used by the interpreter
				// to discover packages
				option("python.Executable", vfs.impl.vfsVenvPath()
						+ (VirtualFileSystemImpl.isWindows() ? "\\Scripts\\python.exe" : "/bin/python"))
				.
				// Set python path to point to sources stored in
				// src/main/resources/org.graalvm.python.vfs/src
				option("python.PythonPath", vfs.impl.vfsSrcPath()).
				// pass the path to be executed
				option("python.InputFilePath", vfs.impl.vfsSrcPath()).
				// causes the interpreter to always assume hash-based pycs are valid
				option("python.CheckHashPycsMode", "never");
	}

	private static void applyExternalDirectoryConfig(Context.Builder builder, Path externalResourcesDirectory) {
		String execPath;
		if (VirtualFileSystemImpl.isWindows()) {
			execPath = externalResourcesDirectory.resolve(VirtualFileSystemImpl.VFS_VENV).resolve("Scripts")
					.resolve("python.exe").toAbsolutePath().toString();
		} else {
			execPath = externalResourcesDirectory.resolve(VirtualFileSystemImpl.VFS_VENV).resolve("bin")
					.resolve("python").toAbsolutePath().toString();
		}

		String srcPath = externalResourcesDirectory.resolve(VirtualFileSystemImpl.VFS_SRC).toAbsolutePath().toString();
		applySharedContextConfig(builder).
		// allow host file access needed by external resources; callers can restrict
		// this by extending IO after applying the resource configuration
				extendIO(IOAccess.NONE, ioBuilder -> ioBuilder.allowHostFileAccess(true)).
				// The sys.executable path, a virtual path that is used by the interpreter
				// to discover packages
				option("python.Executable", execPath).
				// Set python path to point to sources stored in
				// src/main/resources/org.graalvm.python.vfs/src
				option("python.PythonPath", srcPath).
				// pass the path to be executed
				option("python.InputFilePath", srcPath);
	}

	/**
	 * Creates a GraalPy context preconfigured with a {@link VirtualFileSystem} and
	 * other GraalPy and polyglot Context configuration options optimized for the
	 * usage of the <a href="https://docs.python.org/3/library/venv.html">Python
	 * virtual environment</a> contained in the virtual filesystem.
	 * <p>
	 * Following resource paths are preconfigured:
	 * <ul>
	 * <li><code>/org.graalvm.python.vfs/venv</code> - is set as the python virtual
	 * environment location</li>
	 * <li><code>/org.graalvm.python.vfs/src</code> - is set as the python sources
	 * location</li>
	 * </ul>
	 * <p>
	 * When the virtual filesystem is located in other than the default resource
	 * directory, {@code org.graalvm.python.vfs}, i.e., using Maven or Gradle option
	 * {@code resourceDirectory}, use {@link #contextBuilder(VirtualFileSystem)} and
	 * {@link VirtualFileSystem.Builder#resourceDirectory(String)} when building the
	 * {@link VirtualFileSystem}.
	 *
	 * @return a new {@link Context} instance
	 * @since 24.2.0
	 * @deprecated use
	 *             <code>Context.newBuilder().apply(GraalPyResources.forVirtualFileSystem(VirtualFileSystem.create())).build()</code>.
	 *             Unlike this method,
	 *             {@link #forVirtualFileSystem(VirtualFileSystem)} is a
	 *             {@link Consumer} for {@link Context.Builder#apply(Consumer)}. It
	 *             configures <em>only</em> Context options relevant for GraalPy
	 *             resource integration for the default {@link VirtualFileSystem} on
	 *             an existing builder.
	 *             <p>
	 *             Starting from a fresh builder, the following code reproduces the
	 *             complete behavior of deprecated {@link #createContext()} while
	 *             using the new API:
	 *             </p>
	 *
	 *             {@snippet class =
	 *             "org.graalvm.python.embedding.GraalPyResourcesMigrationSnippets"
	 *             region = "default-virtual-filesystem-context-builder"}
	 *             Call {@code builder.build()} to create the {@link Context}.
	 */
	@Deprecated(since = "25.1.0")
	public static Context createContext() {
		return contextBuilder().build();
	}

	/**
	 * Creates a GraalPy context builder preconfigured with a
	 * {@link VirtualFileSystem} and other GraalPy and polyglot Context
	 * configuration options optimized for the usage of the
	 * <a href="https://docs.python.org/3/library/venv.html">Python virtual
	 * environment</a> contained in the virtual filesystem.
	 * <p>
	 * Following resource paths are preconfigured:
	 * <ul>
	 * <li><code>/org.graalvm.python.vfs/venv</code> - is set as the python virtual
	 * environment location</li>
	 * <li><code>/org.graalvm.python.vfs/src</code> - is set as the python sources
	 * location</li>
	 * </ul>
	 * <p>
	 * <b>Example</b> creating a GraalPy context and overriding the verbose option.
	 *
	 * <pre>
	 * Context.Builder builder = GraalPyResources.contextBuilder().option("python.VerboseFlag", "true");
	 * try (Context context = builder.build()) {
	 * 	context.eval("python", "print('hello world')");
	 * } catch (PolyglotException e) {
	 * 	if (e.isExit()) {
	 * 		System.exit(e.getExitStatus());
	 * 	} else {
	 * 		throw e;
	 * 	}
	 * }
	 * </pre>
	 * <p>
	 * When the virtual filesystem is located in other than the default resource
	 * directory, {@code org.graalvm.python.vfs}, i.e., using Maven or Gradle option
	 * {@code resourceDirectory}, use {@link #contextBuilder(VirtualFileSystem)} and
	 * {@link VirtualFileSystem.Builder#resourceDirectory(String)} when building the
	 * {@link VirtualFileSystem}.
	 *
	 * @see <a href=
	 *      "https://github.com/oracle/graalpython/blob/master/graalpython/com.oracle.graal.python/src/com/oracle/graal/python/runtime/PythonOptions.java">PythonOptions</a>
	 * @return a new {@link org.graalvm.polyglot.Context.Builder} instance
	 * @since 24.2.0
	 * @deprecated use
	 *             <code>Context.newBuilder().apply(GraalPyResources.forVirtualFileSystem(VirtualFileSystem.create()))</code>.
	 *             Unlike this method,
	 *             {@link #forVirtualFileSystem(VirtualFileSystem)} is a
	 *             {@link Consumer} for {@link Context.Builder#apply(Consumer)}. It
	 *             configures <em>only</em> Context options relevant for GraalPy
	 *             resource integration for the default {@link VirtualFileSystem} on
	 *             an existing builder.
	 *             <p>
	 *             Starting from a fresh builder, the following code reproduces the
	 *             complete behavior of deprecated {@link #contextBuilder()} while
	 *             using the new API:
	 *             </p>
	 *
	 *             {@snippet class =
	 *             "org.graalvm.python.embedding.GraalPyResourcesMigrationSnippets"
	 *             region = "default-virtual-filesystem-context-builder"}
	 */
	@Deprecated(since = "25.1.0")
	public static Context.Builder contextBuilder() {
		VirtualFileSystem vfs = VirtualFileSystem.create();
		return contextBuilder(vfs);
	}

	/**
	 * Creates a GraalPy context builder preconfigured with the given
	 * {@link VirtualFileSystem} and other GraalPy and polygot Context configuration
	 * options optimized for the usage of the
	 * <a href="https://docs.python.org/3/library/venv.html">Python virtual
	 * environment</a> contained in the virtual filesystem.
	 * <p>
	 * Following resource paths are preconfigured:
	 * <ul>
	 * <li><code>/org.graalvm.python.vfs/venv</code> - is set as the python virtual
	 * environment location</li>
	 * <li><code>/org.graalvm.python.vfs/src</code> - is set as the python sources
	 * location</li>
	 * </ul>
	 * <p>
	 * <b>Example</b> creating a GraalPy context configured for the usage with a
	 * virtual {@link FileSystem}:
	 *
	 * <pre>
	 * VirtualFileSystem.Builder vfsBuilder = VirtualFileSystem.newBuilder();
	 * vfsBuilder.unixMountPoint("/python-resources");
	 * VirtualFileSystem vfs = vfsBuilder.build();
	 * Context.Builder ctxBuilder = GraalPyResources.contextBuilder(vfs);
	 * try (Context context = ctxBuilder.build()) {
	 * 	context.eval("python", "for line in open('/python-resources/data.txt').readlines(): print(line)");
	 * } catch (PolyglotException e) {
	 * 	if (e.isExit()) {
	 * 		System.exit(e.getExitStatus());
	 * 	} else {
	 * 		throw e;
	 * 	}
	 * }
	 * </pre>
	 *
	 * In this example we:
	 * <ul>
	 * <li>create a {@link VirtualFileSystem} configured to have the root
	 * <code>/python-resources</code></li>
	 * <li>create a GraalPy context preconfigured with that
	 * {@link VirtualFileSystem}</li>
	 * <li>use the context to invoke a python snippet reading a resource file</li>
	 * </ul>
	 *
	 * @param vfs
	 *            the {@link VirtualFileSystem} to be used with the created
	 *            {@link Context}
	 * @return a new {@link org.graalvm.polyglot.Context.Builder} instance
	 * @see VirtualFileSystem
	 * @see VirtualFileSystem.Builder
	 *
	 * @since 24.2.0
	 * @deprecated use
	 *             <code>Context.newBuilder().apply(GraalPyResources.forVirtualFileSystem(vfs))</code>.
	 *             Unlike this method,
	 *             {@link #forVirtualFileSystem(VirtualFileSystem)} is a
	 *             {@link Consumer} for {@link Context.Builder#apply(Consumer)}. It
	 *             configures <em>only</em> Context options relevant for GraalPy
	 *             resource integration for the given {@link VirtualFileSystem} on
	 *             an existing builder.
	 *             <p>
	 *             Starting from a fresh builder, the following code reproduces the
	 *             complete behavior of deprecated
	 *             {@link #contextBuilder(VirtualFileSystem)} while using the new
	 *             API:
	 *             </p>
	 *
	 *             {@snippet class =
	 *             "org.graalvm.python.embedding.GraalPyResourcesMigrationSnippets"
	 *             region = "virtual-filesystem-context-builder"}
	 */
	@Deprecated(since = "25.1.0")
	public static Context.Builder contextBuilder(VirtualFileSystem vfs) {
		return createContextBuilder().
		// allow access to the virtual and the host filesystem, as well as sockets
				allowIO(IOAccess.newBuilder().allowHostSocketAccess(true).fileSystem(vfs.delegatingFileSystem).build()).
				// The sys.executable path, a virtual path that is used by the interpreter
				// to discover packages
				option("python.Executable", vfs.impl.vfsVenvPath()
						+ (VirtualFileSystemImpl.isWindows() ? "\\Scripts\\python.exe" : "/bin/python"))
				.
				// Set python path to point to sources stored in
				// src/main/resources/org.graalvm.python.vfs/src
				option("python.PythonPath", vfs.impl.vfsSrcPath()).
				// pass the path to be executed
				option("python.InputFilePath", vfs.impl.vfsSrcPath()).
				// causes the interpreter to always assume hash-based pycs are valid
				option("python.CheckHashPycsMode", "never");
	}

	/**
	 * Creates a GraalPy context preconfigured with GraalPy and polyglot Context
	 * configuration options for use with resources located in an external directory
	 * in real filesystem.
	 * <p>
	 * Following resource paths are preconfigured:
	 * <ul>
	 * <li><code>${externalResourcesDirectory}/venv</code> - is set as the python
	 * virtual environment location</li>
	 * <li><code>${externalResourcesDirectory}/src</code> - is set as the python
	 * sources location</li>
	 * </ul>
	 * <p>
	 * <b>Example</b>
	 *
	 * <pre>
	 * Context.Builder builder = GraalPyResources.contextBuilder(Path.of("python-resources"));
	 * try (Context context = builder.build()) {
	 * 	context.eval("python", "import mymodule; mymodule.print_hello_world()");
	 * } catch (PolyglotException e) {
	 * 	if (e.isExit()) {
	 * 		System.exit(e.getExitStatus());
	 * 	} else {
	 * 		throw e;
	 * 	}
	 * }
	 * </pre>
	 *
	 * In this example we:
	 * <ul>
	 * <li>create a GraalPy context which is preconfigured with GraalPy resources in
	 * an external resource directory</li>
	 * <li>use the context to import the python module <code>mymodule</code>, which
	 * should be either located in <code>python-resources/src</code> or in a python
	 * package installed in <code>/python/venv</code> (python virtual
	 * environment)</li>
	 * <li>note that in this scenario, the Python context has access to the
	 * extracted resources as well as the rest of the real filesystem</li>
	 * </ul>
	 *
	 * <p>
	 * External resources directory is often used for better compatibility with
	 * Python native extensions that may bypass the Python abstractions and access
	 * the filesystem directly from native code. Setting the
	 * {@code PosixModuleBackend} option to "native" increases the compatibility
	 * further, but in such case even Python code bypasses the Truffle abstractions
	 * and accesses native POSIX APIs directly. Usage:
	 *
	 * <pre>
	 * GraalPyResources.contextBuilder(Path.of("python-resources")).option("python.PosixModuleBackend", "native")
	 * </pre>
	 * <p>
	 *
	 * When Maven or Gradle GraalPy plugin is used to build the virtual environment,
	 * it also has to be configured to generate the virtual environment into the
	 * same directory using the {@code <externalDirectory>} tag in Maven or the
	 * {@code externalDirectory} field in Gradle.
	 *
	 * @param externalResourcesDirectory
	 *            the root directory with GraalPy specific embedding resources
	 * @return a new {@link org.graalvm.polyglot.Context.Builder} instance
	 * @since 24.2.0
	 * @deprecated use
	 *             <code>Context.newBuilder().apply(GraalPyResources.forExternalDirectory(externalResourcesDirectory))</code>.
	 *             Unlike this method, {@link #forExternalDirectory(Path)} is a
	 *             {@link Consumer} for {@link Context.Builder#apply(Consumer)}. It
	 *             configures <em>only</em> Context options relevant for GraalPy
	 *             resource integration for an external resource directory on an
	 *             existing Context builder.
	 *             <p>
	 *             Starting from a fresh builder, the following code reproduces the
	 *             complete behavior of deprecated {@link #contextBuilder(Path)}
	 *             while using the new API:
	 *             </p>
	 *
	 *             {@snippet class =
	 *             "org.graalvm.python.embedding.GraalPyResourcesMigrationSnippets"
	 *             region = "external-directory-context-builder"}
	 */
	@Deprecated(since = "25.1.0")
	public static Context.Builder contextBuilder(Path externalResourcesDirectory) {
		String execPath;
		if (VirtualFileSystemImpl.isWindows()) {
			execPath = externalResourcesDirectory.resolve(VirtualFileSystemImpl.VFS_VENV).resolve("Scripts")
					.resolve("python.exe").toAbsolutePath().toString();
		} else {
			execPath = externalResourcesDirectory.resolve(VirtualFileSystemImpl.VFS_VENV).resolve("bin")
					.resolve("python").toAbsolutePath().toString();
		}

		String srcPath = externalResourcesDirectory.resolve(VirtualFileSystemImpl.VFS_SRC).toAbsolutePath().toString();
		return createContextBuilder().
		// allow all IO access
				allowIO(IOAccess.ALL).
				// The sys.executable path, a virtual path that is used by the interpreter
				// to discover packages
				option("python.Executable", execPath).
				// Set python path to point to sources stored in
				// src/main/resources/org.graalvm.python.vfs/src
				option("python.PythonPath", srcPath).
				// pass the path to be executed
				option("python.InputFilePath", srcPath);
	}

	private static Context.Builder createContextBuilder() {
		return Context.newBuilder().
		// set true to allow experimental options
				allowExperimentalOptions(false).
				// setting false will deny all privileges unless configured below
				allowAllAccess(false).
				// allows python to access the java language
				allowHostAccess(HostAccess.ALL).
				// allow creating python threads
				allowCreateThread(true).
				// allow running Python native extensions
				allowNativeAccess(true).
				// allow exporting Python values to polyglot bindings and accessing Java
				// from Python
				allowPolyglotAccess(PolyglotAccess.ALL).
				// choose the backend for the POSIX module
				option("python.PosixModuleBackend", "java").
				// equivalent to the Python -B flag
				option("python.DontWriteBytecodeFlag", "true").
				// Force to automatically import site.py module, to make Python packages
				// available
				option("python.ForceImportSite", "true");
	}

	/**
	 * Determines a native executable path if running in
	 * {@link ImageInfo#inImageRuntimeCode()}.
	 * <p>
	 * <b>Example </b> creating a GraalPy context preconfigured with an external
	 * resource directory located next to a native image executable.
	 *
	 * <pre>
	 * Path resourcesDir = GraalPyResources.getNativeExecutablePath()
	 * 		.getParent()
	 * 		.resolve("python-resources");
	 * try (Context context = Context.newBuilder()
	 * 		.apply(GraalPyResources.forExternalDirectory(resourcesDir))
	 * 		.build()) {
	 * 	context.eval("python", "print('hello world')");
	 * }
	 * </pre>
	 *
	 * @return the native executable path if it could be retrieved, otherwise
	 *         <code>null</code>.
	 * @see #forExternalDirectory(Path)
	 *
	 * @since 24.2.0
	 */
	public static Path getNativeExecutablePath() {
		if (ImageInfo.inImageRuntimeCode()) {
			String pn = null;
			if (ProcessProperties.getArgumentVectorBlockSize() > 0) {
				pn = ProcessProperties.getArgumentVectorProgramName();
			} else {
				pn = ProcessProperties.getExecutableName();
			}
			if (pn != null) {
				return Paths.get(pn).toAbsolutePath();
			}
		}
		return null;
	}

	/**
	 * Extract Python resources which are distributed as part of a <b>jar file</b>
	 * or a <b>native image</b> executable into a directory. This can be useful to
	 * manage and ship resources with the Maven workflow, but use them (cached) from
	 * the real filesystem for better compatibility.
	 * <p>
	 * The structure of the created resource directory will stay the same like the
	 * embedded Python resources structure:
	 * <ul>
	 * <li><code>${externalResourcesDirectory}/venv</code> - the python virtual
	 * environment location</li>
	 * <li><code>${externalResourcesDirectory}/src</code> - the python sources
	 * location</li>
	 * </ul>
	 * <p>
	 * <b>Example</b>
	 *
	 * <pre>
	 * Path resourcesDir = Path.of(System.getProperty("user.home"), ".cache", "my.java.python.app.resources");
	 * VirtualFileSystem vfs = VirtualFileSystem.create();
	 * GraalPyResources.extractVirtualFileSystemResources(vfs, resourcesDir);
	 * try (Context context = Context.newBuilder()
	 * 		.apply(GraalPyResources.forExternalDirectory(resourcesDir))
	 * 		.build()) {
	 * 	context.eval("python", "print('hello world')");
	 * }
	 * </pre>
	 *
	 * @param vfs
	 *            the {@link VirtualFileSystem} from which resources are to be
	 *            extracted
	 * @param externalResourcesDirectory
	 *            the target directory to extract the resources to
	 * @throws IOException
	 *             if resources isn't a directory
	 * @see #forExternalDirectory(Path)
	 * @see VirtualFileSystem.Builder#resourceLoadingClass(Class)
	 *
	 * @since 24.2.0
	 */
	public static void extractVirtualFileSystemResources(VirtualFileSystem vfs, Path externalResourcesDirectory)
			throws IOException {
		if (Files.exists(externalResourcesDirectory) && !Files.isDirectory(externalResourcesDirectory)) {
			throw new IOException(String.format("%s has to be a directory", externalResourcesDirectory.toString()));
		}
		vfs.impl.extractResources(externalResourcesDirectory);
	}
}

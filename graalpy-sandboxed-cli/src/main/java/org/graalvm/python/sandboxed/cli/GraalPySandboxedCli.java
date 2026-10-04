/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.python.sandboxed.cli;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;

import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

public final class GraalPySandboxedCli {

	private static final String PROPERTIES_FILE = "graalpy-sandboxed.properties";
	private static final String HELP_TEXT = """
			Usage: graalpy-sandboxed (-c code | -m module | script.py [args...] | < stdin)

			Options:
			  --help  Show this help message and exit.
			  -c      Execute the given Python code string.
			  -m      Run a library module as a script.

			Configuration is read from ./%s when present.
			""".formatted(PROPERTIES_FILE);
	private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT)
			.contains("windows");

	private GraalPySandboxedCli() {
	}

	public static void main(String[] args) {
		int exitCode = run(args);
		System.exit(exitCode);
	}

	static int run(String[] args) {
		try {
			Invocation invocation = Invocation.parse(args);
			if (invocation.mode == Mode.HELP) {
				System.out.print(HELP_TEXT);
				return 0;
			}
			SandboxConfig config = SandboxConfig.loadFromCurrentDirectory();
			if (config.virtualenv != null && !config.allowReadFs) {
				System.err.println("virtualenv/venv property requires allow-read-fs=true");
				return 2;
			}
			if (invocation.mode == Mode.SCRIPT && !config.allowReadFs) {
				System.err.println("Script execution requires allow-read-fs=true in " + PROPERTIES_FILE);
				return 2;
			}
			return runWithContext(config, invocation);
		} catch (UsageException e) {
			System.err.println(e.getMessage());
			return 2;
		} catch (IOException e) {
			System.err.println("I/O error: " + e.getMessage());
			return 1;
		}
	}

	private static int runWithContext(SandboxConfig config, Invocation invocation) {
		Builder builder = Context.newBuilder("python") //
				.allowExperimentalOptions(true) //
				.allowHostAccess(HostAccess.NONE) //
				.in(System.in) //
				.out(System.out) //
				.err(System.err) //
				.arguments("python", invocation.argv) //
				.option("sandbox.MaxCPUTime", config.maxCpuTime) //
				.option("engine.Compilation", "false") //
				.option("engine.SpawnIsolate", "true") //
				.option("engine.MaxIsolateMemory", config.maxMemory) //
				.option("engine.WarnInterpreterOnly", "false") //
				.option("python.NoUserSiteFlag", "true") //
				.option("python.IgnoreEnvironmentFlag", "true") //
				.option("python.IsolateFlag", "true") //
				.option("python.SafePathFlag", "true") //
				.option("python.DontWriteBytecodeFlag", "true");
		if (config.allowReadFs) {
			FileSystem fileSystem = FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem());
			builder.allowIO(IOAccess.newBuilder().fileSystem(fileSystem).build());
		} else {
			builder.allowIO(IOAccess.NONE);
		}
		if (config.allowReadEnv) {
			builder.allowEnvironmentAccess(EnvironmentAccess.INHERIT);
		}
		if (config.virtualenv != null) {
			Path path = Paths.get(config.virtualenv);
			Path execPath;
			if (IS_WINDOWS) {
				execPath = path.resolve("Scripts").resolve("python.exe");
			} else {
				execPath = path.resolve("bin").resolve("python");
			}
			builder.option("python.ForceImportSite", "true");
			builder.option("python.Executable", execPath.toAbsolutePath().toString());
		}

		if (invocation.mode == Mode.SCRIPT) {
			builder.option("python.InputFilePath", invocation.payload);
		}

		try (Context context = builder.build()) {
			try {
				String program = invocation.toPythonProgram();
				context.eval("python", program);
				return 0;
			} catch (PolyglotException e) {
				return handlePolyglotException(context, e);
			}
		}
	}

	private static int handlePolyglotException(Context context, PolyglotException e) {
		if (e.isExit()) {
			return e.getExitStatus();
		}
		if (e.isResourceExhausted()) {
			System.err.println("Resource exhausted: " + e.getMessage());
			return 1;
		}
		if (!e.isGuestException()) {
			e.printStackTrace(System.err);
			return 1;
		}
		try {
			var formatter = context.eval("python", """
					import traceback
					def format_exception(e):
					    return ''.join(traceback.format_exception(e))
					format_exception
					""");
			var result = formatter.execute(e.getGuestObject());
			System.err.print(result.asString());
			return 1;
		} catch (Exception e2) {
			System.err.println("Failed to format Python exception");
			e2.printStackTrace(System.err);
			return 1;
		}
	}

	private enum Mode {
		CODE, MODULE, SCRIPT, STDIN, HELP
	}

	private record Invocation(Mode mode, String payload, String[] argv) {

		private static Invocation parse(String[] args) throws IOException, UsageException {
			if (args.length == 1 && "--help".equals(args[0])) {
				return new Invocation(Mode.HELP, "", new String[]{"--help"});
			}

			if (args.length == 0) {
				if (System.console() != null) {
					throw new UsageException(
							"No program specified. REPL is not supported; use -c, -m, a script file, or pipe program via stdin.");
				}
				return new Invocation(Mode.STDIN, "", new String[0]);
			}

			if ("-c".equals(args[0])) {
				if (args.length < 2) {
					throw new UsageException("-c requires an argument");
				}
				String[] argv = new String[args.length - 1];
				argv[0] = "-c";
				System.arraycopy(args, 2, argv, 1, args.length - 2);
				return new Invocation(Mode.CODE, args[1], argv);
			}

			if ("-m".equals(args[0])) {
				if (args.length < 2) {
					throw new UsageException("-m requires a module name");
				}
				String[] argv = new String[args.length - 1];
				System.arraycopy(args, 1, argv, 0, args.length - 1);
				return new Invocation(Mode.MODULE, args[1], argv);
			}

			if (args[0].startsWith("-")) {
				throw new UsageException("Unsupported option: " + args[0] + " (only --help, -c and -m are supported)");
			}

			return new Invocation(Mode.SCRIPT, args[0], args.clone());
		}

		private String toPythonProgram() {
			if (mode == Mode.HELP) {
				throw new IllegalStateException("Help mode does not execute Python code");
			}
			return switch (mode) {
				case CODE -> payload;
				case MODULE -> "import runpy; runpy._run_module_as_main('" + payload + "')";
				case SCRIPT, STDIN -> "__graalpython__.run_path()";
				case HELP -> throw new IllegalStateException("Help mode does not execute Python code");
			};
		}
	}

	private record SandboxConfig(boolean allowReadFs, boolean allowReadEnv, String virtualenv, String maxCpuTime,
			String maxMemory) {
		private static final String DEFAULT_MAX_CPU_TIME = "20s";
		private static final String DEFAULT_MAX_MEMORY = "512MB";

		private static SandboxConfig loadFromCurrentDirectory() throws IOException {
			Path path = Paths.get(PROPERTIES_FILE);
			Properties props = new Properties();
			if (Files.exists(path)) {
				try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					props.load(reader);
				}
			}
			boolean allowReadFs = getBoolean(props, "allow-read-fs", false);
			boolean allowReadEnv = getBoolean(props, "allow-read-env", false);
			String virtualenv = getString(props, "virtualenv", getString(props, "venv", null));
			String maxCpuTime = getString(props, "max-cpu-time", DEFAULT_MAX_CPU_TIME);
			String maxMemory = getString(props, "max-memory", DEFAULT_MAX_MEMORY);
			return new SandboxConfig(allowReadFs, allowReadEnv, virtualenv, maxCpuTime, maxMemory);
		}

		private static boolean getBoolean(Properties props, String key, boolean defaultValue) {
			String value = getString(props, key, null);
			if (value == null) {
				return defaultValue;
			}
			return Boolean.parseBoolean(value);
		}

		private static String getString(Properties props, String key, String defaultValue) {
			String value = props.getProperty(key);
			if (value == null) {
				String dottedKey = key.replace('-', '.');
				value = props.getProperty(dottedKey);
			}
			if (value == null) {
				return defaultValue;
			}
			return value.trim();
		}
	}

	private static final class UsageException extends Exception {
		@Serial
		private static final long serialVersionUID = 1L;

		private UsageException(String message) {
			super(message);
		}
	}
}

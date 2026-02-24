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
package org.graalvm.python.mcp;

import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import io.micronaut.runtime.Micronaut;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

@Command(name = "graalpy-sandboxed-mcp", mixinStandardHelpOptions = true, description = "MCP server backed by GraalPy with configurable sandboxing options")
@Singleton
public class McpServer implements Runnable {

	@Option(names = "--allow-read-fs", description = "Enable read-only filesystem for the Python context (blocks writes).")
	boolean allowReadFs;

	@Option(names = "--allow-read-env", description = "Allow reading environment variables")
	boolean allowReadEnv;

	@Option(names = "--cwd", description = "Change to this directory")
	String cwd;

	@Option(names = {"--venv",
			"--virtualenv"}, description = "Path to a virtualenv created by a GraalPy standalone that should be activated. Requires --allow-read-fs")
	String virtualenv;

	@Option(names = "--max-cpu-time", description = "Maximum CPU time allowed for a single eval command. Use a number with a unit, like s. Default 20s.", defaultValue = "20s")
	String maxCpuTime;

	@Option(names = "--max-memory", description = "Maximum memory used by the sandboxed isolate. Use a number with a unit like MB, GB. Default 512MB. Minimum 32MB.", defaultValue = "512MB")
	String maxMemory;

	private static final Logger LOG = LoggerFactory.getLogger(McpServer.class);
	private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT)
			.contains("windows");

	@Tool(name = "eval_python", description = "Evaluate Python code and return the result of the last statement as text (using str)")
	public CallToolResult evalPython(@ToolArg(name = "code") String code) {
		Builder builder = Context.newBuilder("python") //
				.allowExperimentalOptions(true) //
				.allowHostAccess(HostAccess.NONE) //
				.out(System.err) //
				.option("sandbox.MaxCPUTime", maxCpuTime) //
				.option("engine.Compilation", "false") //
				.option("engine.SpawnIsolate", "true") //
				.option("engine.MaxIsolateMemory", maxMemory) //
				.option("engine.WarnInterpreterOnly", "false") //
				.option("python.NoUserSiteFlag", "true") //
				.option("python.IgnoreEnvironmentFlag", "true") //
				.option("python.IsolateFlag", "true") //
				.option("python.SafePathFlag", "true") //
				.option("python.DontWriteBytecodeFlag", "true");
		if (allowReadFs) {
			FileSystem fileSystem = FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem());
			if (cwd != null) {
				fileSystem.setCurrentWorkingDirectory(Paths.get(cwd).toAbsolutePath());
			}
			builder.allowIO(IOAccess.newBuilder().fileSystem(fileSystem).build());
		} else {
			builder.allowIO(IOAccess.NONE);
		}
		if (allowReadEnv) {
			builder.allowEnvironmentAccess(EnvironmentAccess.INHERIT);
		}
		if (virtualenv != null) {
			assert allowReadFs;
			Path path = Paths.get(virtualenv);
			Path execPath;
			if (IS_WINDOWS) {
				execPath = path.resolve("Scripts").resolve("python.exe");
			} else {
				execPath = path.resolve("bin").resolve("python");
			}
			builder.option("python.ForceImportSite", "true").option("python.Executable",
					execPath.toAbsolutePath().toString());
		}
		try (Context context = builder.build()) {
			try {
				Value str = context.eval("python", "str");
				Object result = context.eval("python", code);
				String resultStr = str.execute(result).asString();
				return CallToolResult.builder().addTextContent(resultStr).build();
			} catch (PolyglotException e) {
				if (e.isGuestException()) {
					Value formatException = context.eval("python", """
							import traceback
							def format_exception(e):
							    return ''.join(traceback.format_exception(e));
							format_exception
							""");
					return errorResult(formatException.execute(e.getGuestObject()).asString());
				}
				throw e;
			}
		} catch (PolyglotException e) {
			if (e.isResourceExhausted()) {
				return errorResult("Resource exhausted: " + e.getMessage());
			}
			LOG.error("Tool call failed", e);
			return errorResult("Internal error");
		} catch (Exception e) {
			LOG.error("Tool call failed", e);
			return errorResult("Internal error");
		}
	}

	private static CallToolResult errorResult(String errorMessage) {
		return CallToolResult.builder().isError(true).addTextContent(errorMessage).build();
	}

	// The picocli CLI entry point
	@Override
	public void run() {
		if (virtualenv != null && !allowReadFs) {
			System.err.println("--virtualenv option requires --allow-read-fs");
			System.exit(2);
		}
		Micronaut.build(new String[0]).banner(false).singletons(this).start();
	}

	public static void main(String... args) {
		int exitCode = new CommandLine(new McpServer()).execute(args);
		System.exit(exitCode);
	}
}

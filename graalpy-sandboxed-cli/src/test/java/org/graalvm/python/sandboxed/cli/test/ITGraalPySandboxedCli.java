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
package org.graalvm.python.sandboxed.cli.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ITGraalPySandboxedCli {

	private static final String PROPERTIES_FILE = "graalpy-sandboxed.properties";
	private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");

	private record CliResult(int exitCode, String stdout, String stderr) {
	}

	@Test
	public void testHelpOption(@TempDir Path workDir) throws Exception {
		CliResult result = runCli(workDir, List.of("--help"), null);
		assertEquals(0, result.exitCode);
		assertTrue(result.stdout.contains("Usage: graalpy-sandboxed"), result.stdout);
		assertTrue(result.stdout.contains("--help"), result.stdout);
		assertEquals("", result.stderr);
	}

	@Test
	public void testCodeMode(@TempDir Path workDir) throws Exception {
		CliResult result = runCli(workDir, List.of("-c", "print(1 + 2)"), null);
		assertEquals(0, result.exitCode);
		assertEquals("3\n", result.stdout);
	}

	@Test
	public void testModuleMode(@TempDir Path workDir) throws Exception {
		CliResult result = runCli(workDir, List.of("-m", "this"), null);
		assertEquals(0, result.exitCode);
		assertTrue(result.stdout.contains("The Zen of Python"), result.stdout);
	}

	@Test
	public void testCodeModePassesArguments(@TempDir Path workDir) throws Exception {
		CliResult result = runCli(workDir, List.of("-c", "import sys; print(sys.argv)", "1", "2", "3"), null);
		assertEquals(0, result.exitCode);
		assertEquals("['-c', '1', '2', '3']\n", result.stdout);
	}

	@Test
	public void testStdinMode(@TempDir Path workDir) throws Exception {
		CliResult result = runCli(workDir, List.of(), "print('from-stdin')\n");
		assertEquals(0, result.exitCode);
		assertEquals("from-stdin\n", result.stdout);
	}

	@Test
	public void testScriptModeRequiresAllowReadFs(@TempDir Path workDir) throws Exception {
		Path script = workDir.resolve("hello.py");
		Files.writeString(script, "print('hello-script')\n", StandardCharsets.UTF_8);

		CliResult result = runCli(workDir, List.of(script.toAbsolutePath().toString()), null);
		assertEquals(2, result.exitCode);
		assertTrue(result.stderr.contains("allow-read-fs=true"), result.stderr);
	}

	@Test
	public void testScriptModeWithAllowReadFs(@TempDir Path workDir) throws Exception {
		writeProperties(workDir, "allow-read-fs=true");
		Path script = workDir.resolve("hello.py");
		Files.writeString(script, "print('hello-script')\n", StandardCharsets.UTF_8);

		CliResult result = runCli(workDir, List.of(script.toAbsolutePath().toString()), null);
		assertEquals(0, result.exitCode);
		assertEquals("hello-script\n", result.stdout);
	}

	@Test
	public void testSandboxReadEnvDisabledByDefault(@TempDir Path workDir) throws Exception {
		CliResult result = runCli(workDir, List.of("-c", "import os; print(os.environ['PATH'])"), null);
		assertNotEquals(0, result.exitCode);
	}

	@Test
	public void testSandboxReadEnvEnabled(@TempDir Path workDir) throws Exception {
		writeProperties(workDir, "allow-read-env=true");
		CliResult result = runCli(workDir, List.of("-c", "import os; print(os.environ['PATH'])"), null);
		assertEquals(0, result.exitCode);
		assertFalse(result.stdout.isBlank(), result.stdout);
	}

	@Test
	public void testSandboxReadFile(@TempDir Path workDir) throws Exception {
		Path file = workDir.resolve("in.txt");
		Files.writeString(file, "hello-from-file", StandardCharsets.UTF_8);

		String readCode = "import pathlib; print(pathlib.Path(r'" + file.toAbsolutePath() + "').read_text())";

		CliResult blocked = runCli(workDir, List.of("-c", readCode), null);
		assertNotEquals(0, blocked.exitCode);

		writeProperties(workDir, "allow-read-fs=true");
		CliResult allowed = runCli(workDir, List.of("-c", readCode), null);
		assertEquals(0, allowed.exitCode);
		assertTrue(allowed.stdout.contains("hello-from-file"), allowed.stdout);
	}

	@Test
	public void testSandboxReadOnlyFsBlocksWrites(@TempDir Path workDir) throws Exception {
		writeProperties(workDir, "allow-read-fs=true");
		Path out = workDir.resolve("out.txt");
		String code = "import pathlib; pathlib.Path(r'" + out.toAbsolutePath() + "').write_text('x')";

		CliResult result = runCli(workDir, List.of("-c", code), null);
		assertNotEquals(0, result.exitCode);
		assertFalse(Files.exists(out));
	}

	@Test
	public void testSandboxBlocksSubprocessAndNetworking(@TempDir Path workDir) throws Exception {
		CliResult subprocess = runCli(workDir, List.of("-c", "import subprocess; subprocess.run(['echo', 'hi'])"),
				null);
		assertNotEquals(0, subprocess.exitCode);

		CliResult network = runCli(workDir,
				List.of("-c", "import urllib.request; urllib.request.urlopen('http://example.com').read()"), null);
		assertNotEquals(0, network.exitCode);
	}

	@Test
	public void testSandboxLimits(@TempDir Path workDir) throws Exception {
		writeProperties(workDir, "max-memory=32MB", "max-cpu-time=1s");

		CliResult memory = runCli(workDir, List.of("-c", "x = b'x' * (64 * 1024 * 1024)"), null);
		assertNotEquals(0, memory.exitCode);

		CliResult cpu = runCli(workDir, List.of("-c", "while True: pass"), null);
		assertNotEquals(0, cpu.exitCode);
	}

	private static CliResult runCli(Path workingDir, List<String> args, String stdin) throws Exception {
		String packaging = System.getProperty("test.packaging", "jar");
		List<String> command = new ArrayList<>();
		if ("native-image".equals(packaging)) {
			String executableName = "graalpy-sandboxed";
			if (IS_WINDOWS) {
				executableName += ".exe";
			}
			Path binary = Path.of(System.getProperty("test.buildDir"), executableName);
			if (!Files.exists(binary)) {
				throw new IllegalStateException("Native image " + binary + " not found");
			}
			command.add(binary.toString());
		} else {
			command.add(System.getProperty("test.javaCmd"));
			command.add("-cp");
			command.add(System.getProperty("java.class.path"));
			command.add("org.graalvm.python.sandboxed.cli.GraalPySandboxedCli");
		}
		command.addAll(args);

		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(workingDir.toFile());
		Process process = pb.start();

		if (stdin != null) {
			try (OutputStream outputStream = process.getOutputStream()) {
				outputStream.write(stdin.getBytes(StandardCharsets.UTF_8));
			}
		} else {
			process.getOutputStream().close();
		}

		int exitCode = process.waitFor();
		String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
		return new CliResult(exitCode, stdout, stderr);
	}

	private static void writeProperties(Path dir, String... lines) throws IOException {
		Properties props = new Properties();
		for (String line : lines) {
			int idx = line.indexOf('=');
			if (idx <= 0) {
				throw new IllegalArgumentException("Invalid property line: " + line);
			}
			props.setProperty(line.substring(0, idx), line.substring(idx + 1));
		}
		try (var writer = Files.newBufferedWriter(dir.resolve(PROPERTIES_FILE), StandardCharsets.UTF_8)) {
			props.store(writer, null);
		}
	}
}

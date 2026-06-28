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
package org.graalvm.python.embedding.tools.exec;

import org.graalvm.python.embedding.tools.JavaToolchain;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class GraalPyRunner {

	private static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");
	private static final String BIN_DIR = IS_WINDOWS ? "Scripts" : "bin";
	private static final String EXE_SUFFIX = IS_WINDOWS ? ".exe" : "";

	private GraalPyRunner() {
	}

	public static String[] getExtraJavaOptions(JavaToolchain javaToolchain) {
		if (javaToolchain.isAtLeast(24)) {
			return new String[]{"--sun-misc-unsafe-memory-access=allow"};
		}
		return new String[0];
	}

	public static void run(String classpath, BuildToolLog log, JavaToolchain javaToolchain, String... args)
			throws IOException, InterruptedException {
		String workdir = System.getProperty("exec.workingdir");
		List<String> cmd = new ArrayList<>();
		cmd.add(javaToolchain.javaExecutable().toString());
		cmd.add("--enable-native-access=ALL-UNNAMED");
		cmd.addAll(Arrays.asList(getExtraJavaOptions(javaToolchain)));
		cmd.add("-classpath");
		cmd.add(classpath);
		cmd.add("com.oracle.graal.python.shell.GraalPythonMain");
		cmd.addAll(List.of(args));
		var pb = new ProcessBuilder(cmd);
		if (workdir != null) {
			pb.directory(new File(workdir));
		}
		infoCmd(log, "Running GraalPy:", cmd);
		runProcess(pb, log);
	}

	public static void runLauncher(String launcherPath, BuildToolLog log, String... args)
			throws IOException, InterruptedException {
		var cmd = new ArrayList<String>();
		cmd.add(launcherPath);
		cmd.addAll(List.of(args));
		infoCmd(log, "Running:", cmd);
		var pb = new ProcessBuilder(cmd);
		runProcess(pb, log);
	}

	public static void runPip(Path venvDirectory, String command, BuildToolLog log, String... args)
			throws IOException, InterruptedException {
		var newArgs = new ArrayList<String>();
		newArgs.add("-m");
		newArgs.add("pip");
		addProxy(newArgs);
		newArgs.add(command);
		newArgs.addAll(List.of(args));

		runVenvBin(venvDirectory, "graalpy", log, newArgs);
	}

	public static void runVenvBin(Path venvDirectory, String command, BuildToolLog log, String... args)
			throws IOException, InterruptedException {
		runVenvBin(venvDirectory, command, log, List.of(args));
	}

	private static void runVenvBin(Path venvDirectory, String command, BuildToolLog log, List<String> args)
			throws IOException, InterruptedException {
		var cmd = new ArrayList<String>();
		cmd.add(venvDirectory.resolve(BIN_DIR).resolve(command + EXE_SUFFIX).toString());
		cmd.addAll(args);
		infoCmd(log, "Executing:", cmd);
		var pb = new ProcessBuilder(cmd);
		runProcess(pb, log);
	}

	private static void addProxy(ArrayList<String> args) {
		addProxy(args, System.getenv(), ProxySelector.getDefault());
	}

	static void addProxy(ArrayList<String> args, Map<String, String> env, ProxySelector proxySelector) {
		if (env.keySet().stream().noneMatch(k -> k.equalsIgnoreCase("http_proxy") || k.equalsIgnoreCase("https_proxy"))
				&& proxySelector != null) {
			List<Proxy> proxies = proxySelector.select(URI.create("https://pypi.org"));
			for (Proxy proxy : proxies) {
				if (proxy.type() == Proxy.Type.HTTP && proxy.address() instanceof InetSocketAddress addr) {
					args.add("--proxy");
					args.add(formatProxyAddress(addr));
					return;
				}
			}
		}
	}

	static String formatProxyAddress(InetSocketAddress addr) {
		String host = addr.getHostString();
		// IPv6 literals in URI authorities must be enclosed in square brackets.
		if (host.indexOf(':') >= 0 && !host.startsWith("[")) {
			host = "[" + host + "]";
		}
		return "http://" + host + ":" + addr.getPort();
	}

	private static void runProcess(ProcessBuilder pb, BuildToolLog log) throws IOException, InterruptedException {
		Process process = pb.start();
		Thread outputReader = new Thread(() -> {
			try (InputStream is = process.getInputStream();
					BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					subProcessOut(log, line);
				}
			} catch (IOException e) {
				// Do nothing for now. Probably is not good idea to stop the
				// execution at this moment
				warn(log, "exception while reading subprocess out", e);
			}
		});
		outputReader.start();

		Thread errorReader = new Thread(() -> {
			try (BufferedReader errorBufferedReader = new BufferedReader(
					new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = errorBufferedReader.readLine()) != null) {
					subProcessErr(log, line);
				}
			} catch (IOException e) {
				// Do nothing for now. Probably is not good idea to stop the
				// execution at this moment
				warn(log, "exception while reading subprocess err", e);
			}
		});
		errorReader.start();

		process.waitFor();
		outputReader.join();
		errorReader.join();

		if (process.exitValue() != 0) {
			throw new RuntimeException(
					String.format("Running command: '%s' ended with code %d.See the error output above.",
							String.join(" ", pb.command()), process.exitValue()));
		}
	}

	@SuppressFBWarnings("UC_USELESS_VOID_METHOD")
	private static void warn(BuildToolLog log, String txt, Throwable t) {
		if (log.isWarningEnabled()) {
			log.warning(txt, t);
		}
	}

	@SuppressFBWarnings("UC_USELESS_VOID_METHOD")
	private static void infoCmd(BuildToolLog log, String msg, List<String> cmd) {
		if (log.isInfoEnabled()) {
			log.info(String.format("%s %s", msg, String.join(" ", cmd)));
		}
	}

	private static void subProcessOut(BuildToolLog log, String txt) {
		if (log.isSubprocessOutEnabled()) {
			log.subProcessOut(txt);
		}
	}

	@SuppressFBWarnings("UC_USELESS_VOID_METHOD")
	private static void subProcessErr(BuildToolLog log, String txt) {
		if (log.isErrorEnabled()) {
			log.subProcessErr(txt);
		}
	}
}

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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GraalPyRunnerTest {

	@Test
	public void detectsExtraOptionsFromConfiguredJavaVersion() {
		JavaToolchain javaToolchain = JavaToolchain.fromJavaExecutableAndVersion(
				Path.of("custom-java-home", "bin", "java"), 25);
		assertArrayEquals(new String[]{"--sun-misc-unsafe-memory-access=allow"},
				GraalPyRunner.getExtraJavaOptions(javaToolchain));
	}

	@Test
	public void skipsExtraOptionsForOlderConfiguredJavaVersion() {
		JavaToolchain javaToolchain = JavaToolchain.fromJavaExecutableAndVersion(
				Path.of("custom-java-home", "bin", "java"), 21);
		assertArrayEquals(new String[0], GraalPyRunner.getExtraJavaOptions(javaToolchain));
	}

	@Test
	public void addsSystemProxyWithoutUnresolvedAddressMarker() {
		var args = new ArrayList<String>();
		var address = InetSocketAddress.createUnresolved("127.0.0.1", 7897);
		GraalPyRunner.addProxy(args, Map.of(), proxySelector(new Proxy(Proxy.Type.HTTP, address)));
		assertEquals(List.of("--proxy", "http://127.0.0.1:7897"), args);
	}

	@Test
	public void formatsIpv6ProxyHost() {
		var address = InetSocketAddress.createUnresolved("::1", 7897);
		assertEquals("http://[::1]:7897", GraalPyRunner.formatProxyAddress(address));
	}

	@Test
	public void skipsSystemProxyWhenProxyEnvironmentIsConfigured() {
		var args = new ArrayList<String>();
		var address = InetSocketAddress.createUnresolved("system-proxy.example.com", 7897);
		GraalPyRunner.addProxy(args, Map.of("HTTPS_PROXY", "http://env-proxy.example.com:7897"),
				proxySelector(new Proxy(Proxy.Type.HTTP, address)));
		assertEquals(List.of(), args);
	}

	private static ProxySelector proxySelector(Proxy... proxies) {
		return new ProxySelector() {
			@Override
			public List<Proxy> select(URI uri) {
				assertEquals(URI.create("https://pypi.org"), uri);
				return List.of(proxies);
			}

			@Override
			public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
			}
		};
	}
}

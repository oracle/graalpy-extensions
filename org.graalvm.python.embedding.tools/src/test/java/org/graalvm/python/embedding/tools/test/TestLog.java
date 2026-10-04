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
package org.graalvm.python.embedding.tools.test;

import org.graalvm.python.embedding.tools.exec.BuildToolLog;

public final class TestLog implements BuildToolLog {
	private final StringBuilder output = new StringBuilder();

	private void addLine(String s) {
		this.output.append('\n').append(s);
	}

	void clearOutput() {
		output.delete(0, output.length());
	}

	public void subProcessOut(String s) {
		println("[subout] ", s);
		addLine(s);
	}

	public void subProcessErr(String s) {
		println("[suberr] ", s);
		addLine(s);
	}

	public void info(String s) {
		println("[info] ", s);
		addLine(s);
	}

	public void warning(String s) {
		println("[warn] ", s);
		addLine(s);
	}

	public void warning(String s, Throwable t) {
		println("[warn] ", s);
		t.printStackTrace();
		addLine(s);
	}

	public void error(String s) {
		println("[err] ", s);
		addLine(s);
	}

	@Override
	public void debug(String s) {
		println("[debug] ", s);
		addLine(s);
	}

	@Override
	public boolean isWarningEnabled() {
		return true;
	}

	@Override
	public boolean isInfoEnabled() {
		return true;
	}

	@Override
	public boolean isErrorEnabled() {
		return true;
	}

	@Override
	public boolean isSubprocessOutEnabled() {
		return true;
	}

	@Override
	public boolean isDebugEnabled() {
		return isVerbose();
	}

	public String getOutput() {
		return output.toString();
	}

	static void println(String... args) {
		if (isVerbose()) {
			System.out.println(String.join(" ", args));
		}
	}

	private static boolean isVerbose() {
		return Boolean.getBoolean("com.oracle.graal.python.test.verbose");
	}
}

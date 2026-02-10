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
package org.graalvm.python.tasks;

import org.graalvm.python.embedding.tools.vfs.VFSUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

import static org.graalvm.python.embedding.tools.vfs.VFSUtils.VFS_ROOT;

/**
 * This task compiles the user-supplied sources to bytecode. The sources in the
 * venv are compiled byt the InstallPackagesTask.
 */
@CacheableTask
public abstract class CompileBytecodeTask extends AbstractPackagesTask {
	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getVfsDirectories();

	@Input
	@Optional
	public abstract Property<String> getResourceDirectory();

	@TaskAction
	public void exec() {
		String vfsRoot = getResourceDirectory().getOrElse(VFS_ROOT);
		Path outPath = getOutput().get().getAsFile().toPath();
		Path tmpPath = outPath.getParent().resolve(outPath.getFileName() + ".tmp");
		getVfsDirectories().getElements().get().forEach(location -> {
			Path vfsParentDir = location.getAsFile().toPath();
			if (Files.isDirectory(vfsParentDir)) {
				Path vfsDir = vfsParentDir.resolve(vfsRoot);
				if (Files.isDirectory(vfsDir)) {
					try {
						VFSUtils.compileBytecode(createLauncher(), getLog(), vfsDir, tmpPath);
						// Python will mirror the absolute path tree, we need to extract the directory
						// we're interested in
						Path prefix = vfsDir.toAbsolutePath().getRoot().relativize(vfsDir.toAbsolutePath());
						Path compiledSubtree = tmpPath.resolve(prefix);
						if (Files.isDirectory(compiledSubtree)) {
							moveSubtree(compiledSubtree, outPath.resolve(vfsRoot));
						}
					} catch (IOException e) {
						getLog().warning(String.format("Failed to compile bytecode files in '%s'", vfsDir), e);
					} finally {
						deleteDirectory(tmpPath);
					}
				}
			}
		});
	}

	private void moveSubtree(Path fromDir, Path toDir) throws IOException {
		Files.createDirectories(toDir);
		try (var walk = Files.walk(fromDir)) {
			walk.forEach(from -> {
				try {
					Path rel = fromDir.relativize(from);
					Path to = toDir.resolve(rel);
					if (Files.isDirectory(from)) {
						Files.createDirectories(to);
					} else {
						to = to.getParent().resolve("__pycache__").resolve(to.getFileName());
						Files.createDirectories(to.getParent());
						Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException e) {
					getLog().warning(String.format("Failed to move bytecode file '%s' into final location", from), e);
				}
			});
		}
	}

	private void deleteDirectory(Path dir) {
		if (!Files.exists(dir)) {
			return;
		}
		try (var walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(p -> {
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					getLog().warning(String.format("Failed to delete temporary path '%s'", p), e);
				}
			});
		} catch (IOException e) {
			getLog().warning(String.format("Failed to delete temporary directory '%s'", dir), e);
		}
	}
}

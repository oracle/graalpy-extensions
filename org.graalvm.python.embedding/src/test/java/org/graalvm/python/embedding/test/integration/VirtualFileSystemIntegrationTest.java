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

package org.graalvm.python.embedding.test.integration;

import org.graalvm.polyglot.*;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.graalvm.python.embedding.test.TestUtils.IS_WINDOWS;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

public class VirtualFileSystemIntegrationTest {

    static final String VFS_UNIX_MOUNT_POINT = "/test_mount_point";
    static final String VFS_WIN_MOUNT_POINT = "X:\\test_win_mount_point";
    static final String VFS_MOUNT_POINT = IS_WINDOWS ? VFS_WIN_MOUNT_POINT : VFS_UNIX_MOUNT_POINT;
    static final String VFS_DIRECTORIES_SOURCE = "vfsDirectories";

    static final String PYTHON = "python";
    static final String USE_DEFAULT_VFS_DIR = "--default--";

    private static Engine engine;

    @BeforeAll
    public static void makeEngine() {
        engine = Engine.create("python");
    }

    @AfterAll
    public static void closeEngine() {
        engine.close();
    }

    /**
     * We run most of the tests with two identical filesystems in different locations.
     */
    @SuppressWarnings("unused") // used as JUnit parameter source
    public static Collection<String> vfsDirectories() {
        // null stands for the "default"
        return List.of(USE_DEFAULT_VFS_DIR, "GRAALPY-VFS/com.oracle.graal.python.test/integration");
    }

    @SuppressWarnings("StringEquality")
    private boolean useDefaultResourcesDir(String resourceDirectory) {
        return resourceDirectory == USE_DEFAULT_VFS_DIR;
    }

    private VirtualFileSystem createVirtualFileSystem(String resourceDirectory) {
        if (useDefaultResourcesDir(resourceDirectory)) {
            return VirtualFileSystem.create();
        }
        return VirtualFileSystem.newBuilder().resourceDirectory(resourceDirectory).build();
    }

    private Context.Builder newContextBuilder(String resourceDirectory) {
        if (useDefaultResourcesDir(resourceDirectory)) {
            return GraalPyResources.contextBuilder().engine(engine);
        }
        return GraalPyResources.contextBuilder(createVirtualFileSystem(resourceDirectory)).engine(engine);
    }

    private VirtualFileSystem.Builder newVirtualFileSystemBuilder(String resourceDirectory) {
        VirtualFileSystem.Builder builder = VirtualFileSystem.newBuilder();
        if (!useDefaultResourcesDir(resourceDirectory)) {
            builder.resourceDirectory(resourceDirectory);
        }
        return builder;
    }

    public VirtualFileSystemIntegrationTest() {
        Logger logger = Logger.getLogger(VirtualFileSystem.class.getName());
        for (Handler handler : logger.getHandlers()) {
            handler.setLevel(Level.FINE);
        }
        logger.setLevel(Level.FINE);
    }

    @ParameterizedTest
    @MethodSource(VFS_DIRECTORIES_SOURCE)
    public void defaultValues(String vfsDir) throws IOException {
        try (VirtualFileSystem fs = createVirtualFileSystem(vfsDir); VirtualFileSystem fs2 = createVirtualFileSystem(vfsDir)) {
            assertEquals(fs.getMountPoint(), fs2.getMountPoint());
            assertEquals(IS_WINDOWS ? "X:\\graalpy_vfs" : "/graalpy_vfs", fs.getMountPoint());
        }
    }

    @ParameterizedTest
    @MethodSource(VFS_DIRECTORIES_SOURCE)
    public void mountPoints(String vfsDir) throws IOException {
        try (VirtualFileSystem vfs = newVirtualFileSystemBuilder(vfsDir).//
                        unixMountPoint(VFS_UNIX_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).build()) {
            assertEquals(VFS_MOUNT_POINT, vfs.getMountPoint());
        }

        String multiPathUnixMountPoint = "/test/mount/point";
        String multiPathWinMountPoint = "X:\\test\\win\\mount\\point";
        VirtualFileSystem vfs = newVirtualFileSystemBuilder(vfsDir).//
                        unixMountPoint(multiPathUnixMountPoint).//
                        windowsMountPoint(multiPathWinMountPoint).//
                        resourceLoadingClass(VirtualFileSystemIntegrationTest.class).build();
        try (Context ctx = addTestOptions(GraalPyResources.contextBuilder(vfs)).build()) {
            ctx.eval(PYTHON, "from os import listdir; listdir('" + (IS_WINDOWS ? multiPathWinMountPoint.replace("\\", "\\\\") : multiPathUnixMountPoint) + "')");
        }
    }

    private static void checkExtractedFile(Path extractedFile, String[] expectedContens) throws IOException {
        assertTrue(Files.exists(extractedFile));
        List<String> lines = Files.readAllLines(extractedFile);
        if (expectedContens != null) {
            assertEquals(expectedContens.length, lines.size(), "expected " + expectedContens.length + " lines in extracted file '" + extractedFile + "'");
            for (String line : expectedContens) {
                assertTrue(lines.contains(line), "expected line '" + line + "' in file '" + extractedFile + "' with contents:\n" + Arrays.toString(expectedContens));
            }
        } else {
            assertEquals(0, lines.size(), "extracted file '" + extractedFile + "' expected to be empty, but had " + lines.size() + " lines");
        }
    }

    @ParameterizedTest
    @MethodSource(VFS_DIRECTORIES_SOURCE)
    public void fsOperations(String vfsDir) {
        try (Context ctx = createContext(vfsDir)) {
            fsOperations(ctx, "/test_mount_point/");
        }
        try (Context ctx = createContext(vfsDir, null, b -> b.currentWorkingDirectory(Path.of(VFS_MOUNT_POINT)))) {
            fsOperations(ctx, "");
        }
    }

    @ParameterizedTest
    @MethodSource(VFS_DIRECTORIES_SOURCE)
    @Disabled // GR-61545
    public void parallelFsOperations(String vfsDir) throws ExecutionException, InterruptedException {
        int threadsCount = Runtime.getRuntime().availableProcessors();
        CountDownLatch latch = new CountDownLatch(threadsCount);
        ExecutorService executorService = Executors.newFixedThreadPool(threadsCount, new ThreadFactory() {
            private int index;

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "vfs-tests-thread-pool-thread-" + index++);
            }
        });

        try (Context ctx = createContext(vfsDir, null, null)) {
            Future<?>[] tasks = new Future<?>[threadsCount];
            for (int i = 0; i < threadsCount; i++) {
                tasks[i] = executorService.submit(() -> {
                    latch.countDown();
                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    fsOperations(ctx, "/test_mount_point/");
                });
            }

            for (int i = 0; i < threadsCount; i++) {
                tasks[i].get();
            }
        }
    }

    public void fsOperations(Context ctx, String pathPrefix) {

        // os.path.exists
        eval(ctx, "import os; assert os.path.exists('/test_mount_point')", pathPrefix);
        eval(ctx, "import os; assert os.path.exists('{pathPrefix}.')", pathPrefix);
        eval(ctx, "import os; assert os.path.exists('{pathPrefix}file1')", pathPrefix);
        eval(ctx, "import os; assert os.path.exists('{pathPrefix}dir1')", pathPrefix);
        eval(ctx, "import os; assert os.path.exists('{pathPrefix}dir1/')", pathPrefix);
        eval(ctx, "import os; assert os.path.exists('{pathPrefix}emptydir')", pathPrefix);
        eval(ctx, "import os; assert os.path.exists('{pathPrefix}emptydir/')", pathPrefix);
        eval(ctx, "import os; assert os.path.exists('{pathPrefix}dir1/file2')", pathPrefix);
        eval(ctx, "import os; assert not os.path.exists('{pathPrefix}doesnotexist')", pathPrefix);
        eval(ctx, "import os; assert not os.path.exists('{pathPrefix}doesnotexist/')", pathPrefix);

        // pathlib.exists
        eval(ctx, "from pathlib import Path; assert Path('{pathPrefix}').exists()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert Path('{pathPrefix}file1').exists()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert Path('{pathPrefix}dir1').exists()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert Path('{pathPrefix}dir1/').exists()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert Path('{pathPrefix}emptydir').exists()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert Path('{pathPrefix}emptydir/').exists()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert not Path('{pathPrefix}doesnotexist').exists()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert not Path('{pathPrefix}doesnotexist/').exists()", pathPrefix);

        // path.isfile|isdir

        eval(ctx, "import os; assert os.path.isfile('{pathPrefix}file1')", pathPrefix);
        eval(ctx, "import os; assert not os.path.isfile('{pathPrefix}dir1')", pathPrefix);
        eval(ctx, "import os; assert not os.path.isfile('{pathPrefix}dir1/')", pathPrefix);

        eval(ctx, "import os; assert not os.path.isfile('/test_mount_point')", pathPrefix);
        eval(ctx, "import os; assert os.path.isdir('/test_mount_point')", pathPrefix);
        eval(ctx, "import os; assert not os.path.isdir('{pathPrefix}file1')", pathPrefix);
        eval(ctx, "import os; assert os.path.isdir('{pathPrefix}dir1')", pathPrefix);
        eval(ctx, "import os; assert os.path.isdir('{pathPrefix}dir1/')", pathPrefix);

        // pathlib.is_file|is_dir

        eval(ctx, "from pathlib import Path; assert not Path('/test_mount_point').is_file()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert Path('{pathPrefix}file1').is_file()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert not Path('{pathPrefix}dir1').is_file()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert not Path('{pathPrefix}dir1/').is_file()", pathPrefix);

        eval(ctx, "from pathlib import Path; assert Path('/test_mount_point').is_dir()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert not Path('{pathPrefix}file1').is_dir()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert Path('{pathPrefix}dir1').is_dir()", pathPrefix);
        eval(ctx, "from pathlib import Path; assert Path('{pathPrefix}dir1/').is_dir()", pathPrefix);

        // delete os.remove|rmdir

        eval(ctx, """
                        import os
                        try:
                            os.remove('{pathPrefix}doesnotexist')
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);
        eval(ctx, """
                        import os
                        try:
                            os.remove('{pathPrefix}file1')
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);
        eval(ctx, """
                        import os
                        try:
                            os.rmdir('{pathPrefix}file1')
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);
        eval(ctx, """
                        import os
                        try:
                            os.remove('{pathPrefix}dir1')
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);
        eval(ctx, """
                        import os
                        try:
                            os.rmdir('{pathPrefix}dir1')
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);
        eval(ctx, """
                        import os
                        try:
                            os.rmdir('{pathPrefix}emptydir')
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);

        // delete pathlib.unlink|rmdir

        eval(ctx, """
                        from pathlib import Path
                        try:
                            Path('{pathPrefix}doesnotexist').unlink()
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);
        eval(ctx, """
                        from pathlib import Path
                        try:
                            Path('{pathPrefix}file').unlink()
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);
        eval(ctx, """
                        from pathlib import Path
                        try:
                            Path('{pathPrefix}file1').rmdir()
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);
        eval(ctx, """
                        from pathlib import Path
                        try:
                            Path('{pathPrefix}dir1').unlink()
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);
        eval(ctx, """
                        from pathlib import Path
                        try:
                            Path('{pathPrefix}dir1').rmdir()
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);
        eval(ctx, """
                        from pathlib import Path
                        try:
                            Path('{pathPrefix}emptydir').rmdir()
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);

        // delete shutil.rmtree

        eval(ctx, """
                        import shutil
                        try:
                            shutil.rmtree('{pathPrefix}dir1')
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);
        eval(ctx, """
                        import shutil
                        try:
                            shutil.rmtree('{pathPrefix}emptydir')
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);

        // os.listdir

        eval(ctx, """
                        from os import listdir
                        try:
                            f = listdir('{pathPrefix}doesnotexist')
                        except FileNotFoundError:
                            pass
                        except Error:
                            assert False, 'expected FileNotFoundError'
                        f = listdir('{pathPrefix}emptydir')
                        assert len(f) == 0, 'expected no files'

                        f = listdir('/test_mount_point/')
                        assert len(f) == 6, 'expected 6 files, got ' + repr(list(f))

                        assert 'dir1' in f, 'does not contain "dir1"'
                        assert 'emptydir' in f, 'does not contain "emptydir"'
                        assert 'file1' in f, 'does not contain "file1"'

                        f = listdir('{pathPrefix}dir1')
                        if len(f) != 2:
                            print('files in {pathPrefix}dir1:')
                            for ff in f:
                                print(ff)
                            assert False, 'expected 3 got ' + str(len(f))
                        assert 'file2' in f, 'does not contain "file2"'
                        assert 'extractme' in f, 'does not contain "extractme"'
                        """, pathPrefix);

        // os.walk

        eval(ctx, """
                        from os import walk
                        i = 0
                        for r, d, f in walk('{pathPrefix}doesnotexist'):
                            i = i + 1
                        assert i == 0

                        for r, d, f in walk('{pathPrefix}emptydir'):
                            i = i + 1
                            assert r == '{pathPrefix}emptydir', 'expected {pathPrefix}emptydir, got' + r
                            assert len(d) == 0, 'expected no dirs in emptydir'
                            assert len(f) == 0, 'expected no files in emptydir'
                        assert i == 1

                        roots = set()
                        dirs = set()
                        files = set()
                        for r, d, f in walk('/test_mount_point'):
                            roots.add(r)
                            for ff in f:
                                files.add(r + "/" + ff)
                            for dd in d:
                                dirs.add(r + "/" + dd)

                        assert len(roots) == 9, 'expected 10 roots, got ' + str(len(roots))
                        assert len(files) == 12, 'expected 12 files, got ' + str(len(files))
                        assert len(dirs) == 8, 'expected 8 dirs, got ' + str(len(dirs))
                        """, pathPrefix);

        // read file

        eval(ctx, """
                        with open("{pathPrefix}file1", "r") as f:
                            l = f.readlines()
                            assert len(l) == 2, 'expect 2 lines, got ' + len(l)
                            assert l[0].startswith("text1"), f'expected "text1", got "{l[0]}"'
                            assert l[1].startswith("text2"), f'expected "text2", got "{l[1]}"'

                        with open("{pathPrefix}dir1/file2", "r") as f:
                            l = f.readlines()
                            assert len(l) == 0, 'expect 0 lines from empty file, got ' + str(len(l))
                        """, pathPrefix);

        // write file
        eval(ctx, """
                        try:
                            f = open("{pathPrefix}file1", "w")
                        except OSError:
                            pass
                        else:
                            assert False
                        """, pathPrefix);
    }

    @ParameterizedTest
    @MethodSource(VFS_DIRECTORIES_SOURCE)
    public void osChdir(String vfsDir) {
        try (Context ctx = createContext(vfsDir)) {
            // os.path.exists
            eval(ctx, """
                            import os
                            assert not os.path.exists('file1')
                            os.chdir('/test_mount_point')
                            assert os.path.exists('file1')
                            """);
        }
    }

    @ParameterizedTest
    @MethodSource(VFS_DIRECTORIES_SOURCE)
    public void fsOperationsCaseInsensitive(String vfsDir) {
        try (Context ctx = createContext(vfsDir, b -> b.caseInsensitive(true), null)) {
            eval(ctx, """
                            import os
                            assert os.path.exists('/test_mount_point/SomeFile')
                            assert os.path.exists('/test_mount_point/someFile')
                            assert os.path.exists('/test_mount_point/somefile')
                            assert not os.path.exists('/test_mount_point/somefile1')
                            """);
        }
    }

    private static void eval(Context ctx, String s, String pathPrefix) {
        eval(ctx, s.replace("{pathPrefix}", pathPrefix));
    }

    private static void eval(Context ctx, String s) {
        String src = patchMountPoint(s);
        ctx.eval(PYTHON, src);
    }

    private static String patchMountPoint(String src) {
        if (IS_WINDOWS) {
            return src.replace(VFS_UNIX_MOUNT_POINT, "X:\\\\test_win_mount_point");
        }
        return src;
    }

    public Context createContext(String vfsDir) {
        return createContext(vfsDir, null, null);
    }

    public Context createContext(String vfsDir, Function<VirtualFileSystem.Builder, VirtualFileSystem.Builder> vfsBuilderFunction, Function<Context.Builder, Context.Builder> ctxBuilderFunction) {
        VirtualFileSystem.Builder builder = newVirtualFileSystemBuilder(vfsDir).//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        extractFilter(p -> p.getFileName().toString().endsWith(".tso")).//
                        resourceLoadingClass(VirtualFileSystemIntegrationTest.class);
        if (vfsBuilderFunction != null) {
            builder = vfsBuilderFunction.apply(builder);
        }
        VirtualFileSystem fs = builder.build();
        Context.Builder ctxBuilder = addTestOptions(GraalPyResources.contextBuilder(fs));
        if (ctxBuilderFunction != null) {
            ctxBuilder = ctxBuilderFunction.apply(ctxBuilder);
        }
        return ctxBuilder.build();
    }

    @ParameterizedTest
    @MethodSource(VFS_DIRECTORIES_SOURCE)
    public void vfsBuilderTest(String vfsDir) {
        try (Context context = addTestOptions(newContextBuilder(vfsDir)).allowAllAccess(true).allowHostAccess(HostAccess.ALL).build()) {
            context.eval(PYTHON, "import java; java.type('java.lang.String')");
        }

        try (Context context = addTestOptions(newContextBuilder(vfsDir)).allowAllAccess(false).allowHostAccess(HostAccess.NONE).build()) {
            context.eval(PYTHON, """
                            import java
                            try:
                                java.type('java.lang.String');
                            except NotImplementedError:
                                pass
                            else:
                                assert False, 'expected NotImplementedError'
                            """);
        }
        VirtualFileSystem fs = newVirtualFileSystemBuilder(vfsDir).//
                        unixMountPoint(VFS_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).//
                        resourceLoadingClass(VirtualFileSystemIntegrationTest.class).build();
        try (Context context = addTestOptions(GraalPyResources.contextBuilder(fs)).build()) {
            context.eval(PYTHON, patchMountPoint("from os import listdir; listdir('/test_mount_point')"));
        }

        try (Context context = newContextBuilder(vfsDir).build()) {
            context.eval(PYTHON, "from os import listdir; listdir('.')");
        }
        try (Context context = addTestOptions(newContextBuilder(vfsDir)).allowIO(IOAccess.NONE).build()) {
            boolean gotPE = false;
            try {
                context.eval(PYTHON, "from os import listdir; listdir('.')");
            } catch (PolyglotException pe) {
                gotPE = true;
            }
            assert gotPE : "expected PolyglotException";
        }
    }

    @ParameterizedTest
    @MethodSource(VFS_DIRECTORIES_SOURCE)
    public void externalResourcesBuilderTest(String vfsDir) throws IOException {
        Path resourcesDir = Files.createTempDirectory("vfs-test-resources");
        try (VirtualFileSystem fs = newVirtualFileSystemBuilder(vfsDir).//
                        resourceLoadingClass(VirtualFileSystemIntegrationTest.class).build()) {
            // extract VFS
            GraalPyResources.extractVirtualFileSystemResources(fs, resourcesDir);
        }
        // check extracted contents
        InputStream stream = VirtualFileSystemIntegrationTest.class.getResourceAsStream("/org.graalvm.python.vfs/fileslist.txt");
        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.substring("/org.graalvm.python.vfs/".length());
            if (line.length() == 0) {
                continue;
            }
            Path extractedFile = resourcesDir.resolve(line);
            assert Files.exists(extractedFile);
            if (line.endsWith("/")) {
                assert Files.isDirectory(extractedFile);
            }
        }
        checkExtractedFile(resourcesDir.resolve(Path.of("file1")), new String[]{"text1", "text2"});

        // create context with extracted resource dir and check if we can see the extracted file
        try (Context context = addTestOptions(GraalPyResources.contextBuilder(resourcesDir)).build()) {
            context.eval("python", "import os; assert os.path.exists('" + resourcesDir.resolve("file1").toString().replace("\\", "\\\\") + "')");
        }
    }

    @Test
    public void vfsMountPointTest() {
        if (IS_WINDOWS) {
            assertThrows(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("test"));
            assertThrows(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("test\\"));
            assertThrows(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("\\test\\"));
            assertThrows(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("\\test"));
            assertThrows(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("X:\\test\\"));
            assertThrows(InvalidPathException.class, () -> VirtualFileSystem.newBuilder().windowsMountPoint("X:\\test|test"));
        } else {
            assertThrows(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("test"));
            assertThrows(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("test/"));
            assertThrows(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("/test/"));
            assertThrows(IllegalArgumentException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("X:\\test"));
            assertThrows(InvalidPathException.class, () -> VirtualFileSystem.newBuilder().unixMountPoint("/test/\0"));
        }
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @MethodSource(VFS_DIRECTORIES_SOURCE)
    public void pythonPathsTest(String vfsDir) throws IOException {
        String defaultMountPoint;
        try (VirtualFileSystem vfs = createVirtualFileSystem(vfsDir)) {
            defaultMountPoint = vfs.getMountPoint();
        }

        String getPathsSource = "import sys; [sys.path, sys.executable]";
        try (Context ctx = newContextBuilder(vfsDir).build()) {
            Value paths = ctx.eval("python", getPathsSource);

            assertEquals(IS_WINDOWS ? "X:\\graalpy_vfs" : "/graalpy_vfs", defaultMountPoint);
            checkPaths(paths.as(List.class), defaultMountPoint);
        }

        try (Context ctx = newContextBuilder(vfsDir).build()) {
            Value paths = ctx.eval("python", getPathsSource);
            checkPaths(paths.as(List.class), defaultMountPoint);
        }
        VirtualFileSystem vfs = newVirtualFileSystemBuilder(vfsDir).//
                        unixMountPoint(VFS_UNIX_MOUNT_POINT).//
                        windowsMountPoint(VFS_WIN_MOUNT_POINT).build();
        assertEquals(VFS_MOUNT_POINT, vfs.getMountPoint());
        try (Context ctx = addTestOptions(GraalPyResources.contextBuilder(vfs)).build()) {
            Value paths = ctx.eval("python", getPathsSource);
            checkPaths(paths.as(List.class), vfs.getMountPoint());
        }
        Path resourcesDir = Files.createTempDirectory("python-resources");

        try (Context ctx = addTestOptions(GraalPyResources.contextBuilder(resourcesDir)).build()) {
            Value paths = ctx.eval("python", getPathsSource);
            checkPaths(paths.as(List.class), resourcesDir.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkPaths(List<Object> l, String pathPrefix) {
        // option python.PythonPath
        assertThat(((List<Object>) l.get(0)), hasItem(pathPrefix + File.separator + "src"));
        // option python.Executable
        assertEquals(l.get(1), pathPrefix + (IS_WINDOWS ? "\\venv\\Scripts\\python.exe" : "/venv/bin/python"));
    }

    private static Builder addTestOptions(Builder builder) {
        return builder.engine(Engine.newBuilder("python").option("engine.WarnInterpreterOnly", "false").build());
    }

    @Test
    public void testAnotherVfs() throws IOException {
        try (var vfs = VirtualFileSystem.newBuilder().resourceDirectory("GRAALPY-VFS/foo").build()) {
            try (Context ctx = addTestOptions(GraalPyResources.contextBuilder(vfs)).build()) {
                eval(ctx, """
                                def test(mount_point):
                                    import os
                                    assert not os.path.exists('hello.txt')
                                    print(os.path.join(mount_point, 'otherdir'))
                                    os.chdir(os.path.join(mount_point, 'otherdir'))
                                    assert os.path.exists('hello.txt')
                                    with open('hello.txt', 'r') as f:
                                        return f.read()
                                """);
                Value test = ctx.getBindings("python").getMember("test");
                Value result = test.execute(vfs.getMountPoint());
                assertEquals("world", result.asString());
            }
        }
    }
}

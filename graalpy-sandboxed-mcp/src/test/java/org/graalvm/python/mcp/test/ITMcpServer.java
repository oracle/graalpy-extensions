package org.graalvm.python.mcp.test;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ITMcpServer {

    private static final String[] NO_ARGS = new String[0];
    private static final String[] ALLOW_READ_FS = new String[]{"--allow-read-fs"};
    private static final String[] ALLOW_READ_ENV = new String[]{"--allow-read-env"};

    // It's slow to start a client for every test, so cache them per args
    private static final Map<String, McpSyncClient> clients = new HashMap<>();

    public static McpSyncClient getClient(String[] args) {
        String id = Arrays.toString(args);
        McpSyncClient client = clients.get(id);
        if (client == null) {
            client = createClient(args);
            clients.put(id, client);
        }
        return client;
    }

    public static McpSyncClient createClient(String... args) {
        String packaging = System.getProperty("test.packaging");
        Path buildDir = Paths.get(System.getProperty("test.buildDir"));
        ServerParameters parameters;
        if (packaging.equals("jar")) {
            Path jarPath = buildDir.resolve(System.getProperty("test.finalName") + ".jar");
            if (!Files.exists(jarPath)) {
                throw new IllegalStateException("Jar file " + jarPath + " not found");
            }
            // Tool execution needs Graal/Truffle. If the packaged jar loses the Multi-Release attribute,
            // Truffle initialization fails. Disable this guard for the integration test-run process.
            parameters = ServerParameters.builder(System.getProperty("test.javaCmd"))
                    .args(Stream.concat(Stream.of("-Dpolyglotimpl.DisableMultiReleaseCheck=true", "-jar", jarPath.toString()), Stream.of(args)).toArray(String[]::new))
                    .build();
        } else if (packaging.equals("native-image")) {
            Path binaryPath = buildDir.resolve(System.getProperty("test.artifactId"));
            if (!Files.exists(binaryPath)) {
                throw new IllegalStateException("Native image " + binaryPath + " not found");
            }
            parameters = ServerParameters.builder(binaryPath.toString())
                    .args(args)
                    .build();
        } else {
            throw new IllegalStateException("Unexpected packaging " + packaging);
        }
        StdioClientTransport transport = new StdioClientTransport(parameters, McpJsonMapper.createDefault());
        Duration timeout = Duration.of(10, ChronoUnit.SECONDS);
        McpSyncClient client = McpClient.sync(transport).initializationTimeout(timeout).requestTimeout(timeout).build();
        client.initialize();
        return client;
    }

    @AfterAll
    public static void tearDown() {
        for (McpSyncClient client : clients.values()) {
            client.close();
        }
    }

    @Test
    public void testListTools() {
        McpSchema.ListToolsResult tools = getClient(NO_ARGS).listTools();
        assertEquals(1, tools.tools().size(), "Expected a single tool");
        Tool tool = tools.tools().getFirst();
        assertEquals("eval_python", tool.name(), "Expected eval_python tool");
    }

    private static CallToolResult callEvalPython(String[] args, String code) {
        return getClient(args).callTool(new CallToolRequest(
                "eval_python",
                Map.of("code", code)
        ));
    }

    private static String getTextContent(CallToolResult result) {
        assertNotNull(result.content());
        assertFalse(result.content().isEmpty());
        assertInstanceOf(TextContent.class, result.content().getFirst());
        return ((TextContent) result.content().getFirst()).text();
    }

    private static String callEvalPythonExpectSuccess(String[] args, String code) {
        CallToolResult result = callEvalPython(args, code);
        String output = getTextContent(result);
        assertFalse(result.isError(), "Tool call should not be marked as error. Code: " + code + "\nOutput: " + output);
        return output;
    }

    private static String callEvalPythonExpectSuccess(String code) {
        return callEvalPythonExpectSuccess(NO_ARGS, code);
    }

    private static String callEvalPythonExpectError(String[] args, String code) {
        CallToolResult result = callEvalPython(args, code);
        String output = getTextContent(result);
        assertTrue(result.isError(), "Tool call should be marked as error. Code: " + code + "\nOutput: " + output);
        return output;
    }

    private static String callEvalPythonExpectError(String code) {
        return callEvalPythonExpectError(NO_ARGS, code);
    }

    @Test
    public void testEvalPythonSimple() {
        String text = callEvalPythonExpectSuccess("1 + 2");
        assertEquals("3", text);
    }

    @Test
    public void testEvalPythonComplex() {
        String text = callEvalPythonExpectSuccess("""
                class MyObject:
                  def __str__(self):
                    return "str(obj)"
                  def __repr__(self):
                    return "repr(obj)"
                MyObject()
                """);
        assertEquals("str(obj)", text);
    }

    @Test
    public void testEvalPythonStdlib() {
        String text = callEvalPythonExpectSuccess("""
                import json
                json.dumps({'a': None})
                """);
        assertEquals("{\"a\": null}", text);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testEvalPythonReadEnv(boolean allowEnvironmentAccess) {
        String expectedPath = System.getenv("PATH");
        Assumptions.assumeTrue(expectedPath != null, "PATH must be set in the test environment");
        String code = """
                import os
                os.environ['PATH']
                """;
        if (allowEnvironmentAccess) {
            String actualPath = callEvalPythonExpectSuccess(ALLOW_READ_ENV, code);
            assertEquals(expectedPath, actualPath);
        } else {
            callEvalPythonExpectError(code);
        }

    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testEvalPythonReadFile(boolean allowed) throws Exception {
        Path tempFile = Files.createTempFile("eval_python_read_", ".txt");
        Files.writeString(tempFile, "hello-from-temp-file", StandardCharsets.UTF_8);

        String code = String.format("""
                import pathlib
                pathlib.Path(r'%s').read_text(encoding='utf-8')
                """, tempFile.toAbsolutePath());
        if (allowed) {
            String text = callEvalPythonExpectSuccess(ALLOW_READ_FS, code);
            assertEquals("hello-from-temp-file", text);
        } else {
            callEvalPythonExpectError(code);
        }
    }

    @Test
    public void testEvalPythonCwd() throws Exception {
        Path tempDir = Files.createTempDirectory("eval_python_cwd_");
        Path fileInTempDir = tempDir.resolve("hello.txt");
        Files.writeString(fileInTempDir, "hello-from-cwd", StandardCharsets.UTF_8);

        String code = """
                import pathlib
                pathlib.Path('hello.txt').read_text(encoding='utf-8')
                """;
        // Without --cwd the relative path should not resolve (and should error)
        String noCwdError = callEvalPythonExpectError(ALLOW_READ_FS, code);
        assertTrue(noCwdError.contains("FileNotFoundError"), noCwdError);

        // With --cwd the relative path should resolve within that directory
        String[] args = new String[]{"--allow-read-fs", "--cwd", tempDir.toString()};
        String text = callEvalPythonExpectSuccess(args, code);
        assertEquals("hello-from-cwd", text);
    }

    @Test
    public void testEvalPythonException() {
        String text = callEvalPythonExpectError("raise ValueError('boom')");

        assertEquals("""
                Traceback (most recent call last):
                  File "Unnamed", line 1, in <module>
                ValueError: boom
                """, text);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testEvalPythonSandboxing(boolean readingAllowed) {
        String[] args = readingAllowed ? ALLOW_READ_FS : NO_ARGS;
        // 1) Filesystem writes should be blocked by read-only filesystem
        callEvalPythonExpectError(args, "open('/tmp/foo', 'w')");
        callEvalPythonExpectError(args, """
                import pathlib
                pathlib.Path('/tmp/mcp_should_not_write').write_text('x')
                """);
        callEvalPythonExpectError(args, """
                import tempfile
                tempfile.NamedTemporaryFile(delete=False)
                """);

        // 2) Delete/rename/mkdir should be blocked by read-only filesystem
        callEvalPythonExpectError(args, """
                import os
                os.mkdir('/tmp/mcp_mkdir')
                """);

        // 3) Process execution should be blocked
        callEvalPythonExpectError(args, "import subprocess; subprocess.check_call(['echo'])");
        callEvalPythonExpectError(args, "import os; os.system('echo hi')");
        callEvalPythonExpectError(args, "import subprocess; subprocess.Popen(['echo', 'hi'])");
        callEvalPythonExpectError(args, "import subprocess; subprocess.run(['echo', 'hi'])");

        // Native module

        // 4) Networking should be blocked
        callEvalPythonExpectError(args, "import socket; socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)");
        callEvalPythonExpectError(args, """
                import urllib.request
                urllib.request.urlopen('http://example.com').read()
                """);

        // 6) ctypes and native modules should be blocked
        callEvalPythonExpectError(args, """
                import ctypes
                ctypes.CDLL(None)
                """);
        callEvalPythonExpectError(args, "import termios");

        // 7) polyglot and host interop should be blocked
        callEvalPythonExpectError(args, "import polyglot; polyglot.eval('js', '1+1')");
        callEvalPythonExpectError(args, """
                from java.lang import System
                System.getProperty('user.home')
                """);

        // 8) exit should not exit the VM
        callEvalPythonExpectError(args, "import sys; sys.exit(1)");
        callEvalPythonExpectError(args, "import os; os._exit(1)");

        // XXX graalpy-25.0.2 lets you use the signal module. It can only be used to kill the server itself, but still not nice
        // callEvalPythonExpectError(args, "import signal; signal.raise_signal(signal.SIGTERM)")
    }
}

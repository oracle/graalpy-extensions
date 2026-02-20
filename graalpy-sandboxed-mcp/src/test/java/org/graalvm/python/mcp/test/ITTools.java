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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ITTools {

    private static McpSyncClient client;

    @BeforeAll
    public static void setup() {
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
                    .args("-Dpolyglotimpl.DisableMultiReleaseCheck=true", "-jar", jarPath.toString())
                    .build();
        } else if (packaging.equals("native-image")) {
            Path binaryPath = buildDir.resolve(System.getProperty("test.artifactId"));
            if (!Files.exists(binaryPath)) {
                throw new IllegalStateException("Native image " + binaryPath + " not found");
            }
            parameters = ServerParameters.builder(binaryPath.toString()).build();
        } else {
            throw new IllegalStateException("Unexpected packaging " + packaging);
        }
        StdioClientTransport transport = new StdioClientTransport(parameters, McpJsonMapper.createDefault());
        Duration timeout = Duration.of(60, ChronoUnit.SECONDS);
        client = McpClient.sync(transport).initializationTimeout(timeout).requestTimeout(timeout).build();
        client.initialize();
    }

    @AfterAll
    public static void teardown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    public void testListTools() {
        McpSchema.ListToolsResult tools = client.listTools();
        assertEquals(1, tools.tools().size(), "Expected a single tool");
        Tool tool = tools.tools().getFirst();
        assertEquals("eval_python", tool.name(), "Expected eval_python tool");
    }

    private static CallToolResult callEvalPython(String code) {
        return client.callTool(new CallToolRequest(
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

    private static String callEvalPythonExpectSuccess(String code) {
        CallToolResult result = callEvalPython(code);
        assertFalse(result.isError(), "Tool call should not be marked as error. Code: " + code);
        return getTextContent(result);
    }

    private static String callEvalPythonExpectError(String code) {
        CallToolResult result = callEvalPython(code);
        assertTrue(result.isError(), "Tool call should be marked as error. Code: " + code);
        return getTextContent(result);
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
    public void testEvalPythonReadFileAbsolutePath() throws Exception {
        Path tempFile = Files.createTempFile("eval_python_read_", ".txt");
        Files.writeString(tempFile, "hello-from-temp-file", StandardCharsets.UTF_8);

        String text = callEvalPythonExpectSuccess(String.format("""
                import pathlib
                pathlib.Path(r'%s').read_text(encoding='utf-8')
                """, tempFile.toAbsolutePath()));
        assertEquals("hello-from-temp-file", text);
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

    @Test
    public void testEvalPythonSandboxing() {
        callEvalPythonExpectError("open('/tmp/foo', 'w')");
        callEvalPythonExpectError("import subprocess; subprocess.check_call(['echo'])");
        // Native module
        callEvalPythonExpectError("import termios");
        callEvalPythonExpectError("import socket; socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)");
        // XXX graalpy-25.0.2 let's you use the signal module. It can only be used to kill the server itself, but still not nice
        // "import signal; signal.raise_signal(signal.SIGTERM)",
    }
}

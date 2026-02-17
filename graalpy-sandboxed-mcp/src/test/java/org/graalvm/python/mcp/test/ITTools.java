package org.graalvm.python.mcp.test;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

    private static final McpJsonMapper JSON_MAPPER = McpJsonMapper.createDefault();

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

    @Test
    public void testEvalPythonSimple() {
        CallToolRequest okRequest = new CallToolRequest(
                "eval_python",
                Map.of("code", "1 + 2")
        );
        McpSchema.CallToolResult okResult = client.callTool(okRequest);
        assertFalse(okResult.isError(), "Tool call should not be marked as error");

        assertNotNull(okResult.content());
        assertFalse(okResult.content().isEmpty());
        assertInstanceOf(TextContent.class, okResult.content().getFirst());
        assertEquals("3", ((TextContent) okResult.content().getFirst()).text());
    }


    @Test
    public void testEvalPythonComplex() {
        CallToolRequest okRequest = new CallToolRequest(
                "eval_python",
                Map.of("code", """
                        class MyObject:
                          def __repr__(self):
                            return "obj"
                        MyObject()
                        """)
        );
        McpSchema.CallToolResult okResult = client.callTool(okRequest);
        assertFalse(okResult.isError(), "Tool call should not be marked as error");

        assertNotNull(okResult.content());
        assertFalse(okResult.content().isEmpty());
        assertInstanceOf(TextContent.class, okResult.content().getFirst());
        assertEquals("obj", ((TextContent) okResult.content().getFirst()).text());
    }

    @Test
    public void testEvalPythonException() {
        CallToolRequest errRequest = new CallToolRequest(
                "eval_python",
                Map.of("code", "raise ValueError('boom')")
        );
        McpSchema.CallToolResult errResult = client.callTool(errRequest);
        assertTrue(errResult.isError(), "Tool call should be marked as error");

        assertNotNull(errResult.content());
        assertFalse(errResult.content().isEmpty());
        assertInstanceOf(TextContent.class, errResult.content().getFirst());

        assertEquals("""
                Traceback (most recent call last):
                  File "Unnamed", line 1, in <module>
                ValueError: boom
                """, ((TextContent) errResult.content().getFirst()).text());
    }
}

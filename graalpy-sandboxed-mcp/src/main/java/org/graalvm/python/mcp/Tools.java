package org.graalvm.python.mcp;

import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;

@Singleton
public class Tools {

    @Tool(name = "eval_python", description = "Evaluate Python code and return the result of the last statement as text (using str)")
    public CallToolResult evalPython(@ToolArg(name = "code") String code) {
        try (Context context = Context.newBuilder("python")
                .allowExperimentalOptions(true)
                .allowIO(IOAccess.newBuilder().fileSystem(FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem())).build())
                .out(System.err)
                .option("python.ForceImportSite", "true")
                .build()) {
            try {
                Object result = context.eval("python", code);
                return CallToolResult.builder().addTextContent(result.toString()).build();
            } catch (PolyglotException e) {
                Value formatException = context.eval("python", """
                        import traceback
                        def format_exception(e):
                            return ''.join(traceback.format_exception(e));
                        format_exception
                        """);
                return CallToolResult.builder().isError(true).addTextContent(formatException.execute(e.getGuestObject()).asString()).build();
            }
        } catch (Exception e) {
            return CallToolResult.builder().isError(true).addTextContent("Internal error").build();
        }
    }
}

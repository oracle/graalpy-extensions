package org.graalvm.python.mcp;

import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import io.micronaut.runtime.Micronaut;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;


@Command(
        name = "graalpy-sandboxed-mcp",
        mixinStandardHelpOptions = true,
        description = "MCP server backed by GraalPy with configurable sandboxing options"
)
@Singleton
public class McpServer implements Runnable {

    @Option(
            names = "--allow-read-fs",
            description = "Enable read-only filesystem for the Python context (blocks writes)."
    )
    boolean allowReadFs;

    @Option(
            names = "--virtualenv",
            description = "Path to a virtualenv created by a GraalPy standalone that should be activated. Requires --allow-read-fs"
    )
    String virtualenv;

    private static final Logger LOG = LoggerFactory.getLogger(McpServer.class);
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");

    @Tool(name = "eval_python", description = "Evaluate Python code and return the result of the last statement as text (using str)")
    public CallToolResult evalPython(@ToolArg(name = "code") String code) {
        Builder builder = Context.newBuilder("python")
                .allowExperimentalOptions(true)
                .allowHostAccess(HostAccess.NONE)
                .out(System.err)
                .option("engine.WarnInterpreterOnly", "false")
                .option("python.DontWriteBytecodeFlag", "true")
                .option("python.UseReprForPrintString", "false");
        if (allowReadFs) {
            builder.allowIO(IOAccess.newBuilder().fileSystem(FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem())).build());
        } else {
            builder.allowIO(IOAccess.NONE);
        }
        if (virtualenv != null) {
            assert allowReadFs;
            Path path = Paths.get(virtualenv);
            Path execPath;
            if (IS_WINDOWS) {
                execPath = path.resolve("Scripts").resolve("python.exe");
            } else {
                execPath = path.resolve("bin").resolve("python");
            }
            builder.option("python.ForceImportSite", "true")
                    .option("python.Executable", execPath.toAbsolutePath().toString());
        }
        try (Context context = builder.build()) {
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
            LOG.error("Tool call failed", e);
            return CallToolResult.builder().isError(true).addTextContent("Internal error").build();
        }
    }

    // The picocli CLI entry point
    @Override
    public void run() {
        if (virtualenv != null && !allowReadFs) {
            System.err.println("--virtualenv option requires --allow-read-fs");
            System.exit(2);
        }
        Micronaut.build(new String[0]).banner(false).singletons(this).start();
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new McpServer()).execute(args);
        System.exit(exitCode);
    }
}

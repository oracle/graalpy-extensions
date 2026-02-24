package org.graalvm.python.mcp;

import io.micronaut.mcp.annotations.Tool;
import io.micronaut.mcp.annotations.ToolArg;
import io.micronaut.runtime.Micronaut;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.EnvironmentAccess;
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
            names = "--allow-read-env",
            description = "Allow reading environment variables"
    )
    boolean allowReadEnv;

    @Option(
            names = "--cwd",
            description = "Change to this directory"
    )
    String cwd;

    @Option(
            names = {"--venv", "--virtualenv"},
            description = "Path to a virtualenv created by a GraalPy standalone that should be activated. Requires --allow-read-fs"
    )
    String virtualenv;

    @Option(
            names = "--max-cpu-time",
            description = "Maximum CPU time allowed for a single eval command. Use a number with a unit, like s. Default 20s.",
            defaultValue = "20s"
    )
    String maxCpuTime;

    @Option(
            names = "--max-memory",
            description = "Maximum memory used by the sandboxed isolate. Use a number with a unit like MB, GB. Default 1024MB. Minimum 32MB.",
            defaultValue = "1024MB"
    )
    String maxMemory;

    private static final Logger LOG = LoggerFactory.getLogger(McpServer.class);
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");

    @Tool(name = "eval_python", description = "Evaluate Python code and return the result of the last statement as text (using str)")
    public CallToolResult evalPython(@ToolArg(name = "code") String code) {
        Builder builder = Context.newBuilder("python")
                .allowExperimentalOptions(true)
                .allowHostAccess(HostAccess.NONE)
                .out(System.err)
                .option("sandbox.MaxCPUTime", maxCpuTime)
                .option("engine.Compilation", "false")
                .option("engine.SpawnIsolate", "true")
                .option("engine.MaxIsolateMemory", maxMemory)
                .option("engine.WarnInterpreterOnly", "false")
                .option("python.DontWriteBytecodeFlag", "true");
        if (allowReadFs) {
            FileSystem fileSystem = FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem());
            if (cwd != null) {
                fileSystem.setCurrentWorkingDirectory(Paths.get(cwd).toAbsolutePath());
            }
            builder.allowIO(IOAccess.newBuilder().fileSystem(fileSystem).build());
        } else {
            builder.allowIO(IOAccess.NONE);
        }
        if (allowReadEnv) {
            builder.allowEnvironmentAccess(EnvironmentAccess.INHERIT);
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
                Value str = context.eval("python", "str");
                Object result = context.eval("python", code);
                String resultStr = str.execute(result).asString();
                return CallToolResult.builder().addTextContent(resultStr).build();
            } catch (PolyglotException e) {
                if (e.isGuestException()) {
                    Value formatException = context.eval("python", """
                            import traceback
                            def format_exception(e):
                                return ''.join(traceback.format_exception(e));
                            format_exception
                            """);
                    return errorResult(formatException.execute(e.getGuestObject()).asString());
                }
                throw e;
            }
        } catch (PolyglotException e) {
            if (e.isResourceExhausted()) {
                return errorResult("Resource exhausted: " + e.getMessage());
            }
            LOG.error("Tool call failed", e);
            return errorResult("Internal error");
        } catch (Exception e) {
            LOG.error("Tool call failed", e);
            return errorResult("Internal error");
        }
    }

    private static CallToolResult errorResult(String errorMessage) {
        return CallToolResult.builder().isError(true).addTextContent(errorMessage).build();
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

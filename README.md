# GraalPy Extensions

Collection of tools and libraries that extend [GraalPy](https://graalvm.org/python), a high-performance embeddable Python 3 runtime for Java.

## GraalPy Embedding

Java library with utility classes useful when embedding GraalPy.

* `VirtualFileSystem`: implementation of the [FileSystem SPI](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/io/FileSystem.html) backed by Java resources and tailored for use with GraalPy Maven/Gradle plugin (see below).
* `GraalPyResources`: factory methods for creating a [Context](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html) configured with the `VirtualFileSystem`.
* `PositionalArguments` and `KeywordArguments`: provide way to pass positional and keyword arguments via the generic [Context API](https://www.graalvm.org/sdk/javadoc/org/graalvm/polyglot/Context.html).

## GraalPy Maven Archetype

Maven archetype to generate a Java project that embeds GraalPy.

```
mvn archetype:generate \
  -DarchetypeGroupId=org.graalvm.python \
  -DarchetypeArtifactId=graalpy-archetype-polyglot-app \
  -DarchetypeVersion=24.2.0
```

## GraalPy Maven Plugin

Maven plugin to install and manage Python packages for embedded GraalPy.
For more details see the [user manual](https://www.graalvm.org/python/docs/#embedding-build-tools).

## GraalPy Gradle Plugin

Gradle plugin to install and manage Python packages for embedded GraalPy.
For more details see the [user manual](https://www.graalvm.org/python/docs/#embedding-build-tools).

## GraalPy JBang Integration

With this integration, one can add Python package dependencies using syntax similar
to how Maven dependencies are added:
```
//PIP qrcode==7.4.2
```

For an example, see [GraalPy JBang QR Code demo](https://github.com/graalvm/graal-languages-demos/tree/main/graalpy/graalpy-jbang-qrcode)

## GraalPy Sandboxed MCP Server

We provide an MCP server that can execute python code in a configurable
sandbox. See [GraalPy Sandboxed MCP Server documentation](./graalpy-sandboxed-mcp/README.md)

## Contributing

This project welcomes contributions from the community. Before submitting a pull request, please [review our contribution guide](./CONTRIBUTING.md)

## Security

Please consult the [security guide](./SECURITY.md) for our responsible security vulnerability disclosure process

## License

Copyright (c) 2025 Oracle and/or its affiliates.

Released under the Universal Permissive License v1.0 as shown at
<https://oss.oracle.com/licenses/upl/>.

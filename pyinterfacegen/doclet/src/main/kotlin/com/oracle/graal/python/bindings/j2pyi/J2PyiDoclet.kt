package org.graalvm.python.pyinterfacegen

import com.sun.source.util.DocTrees
import jdk.javadoc.doclet.Doclet
import jdk.javadoc.doclet.DocletEnvironment
import jdk.javadoc.doclet.Reporter
import java.io.File
import java.util.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.tools.Diagnostic

class J2PyiDoclet : Doclet {
    // Python keywords that cannot be used as identifiers/parameter names.
    // Keep in sync with Python 3.11+; covers all reserved words including match/case.
    private val PYTHON_KEYWORDS: Set<String> = setOf(
        "False", "None", "True",
        "and", "as", "assert", "async", "await",
        "break", "class", "continue", "def", "del", "elif", "else", "except",
        "finally", "for", "from", "global", "if", "import", "in", "is",
        "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
        "try", "while", "with", "yield", "match", "case"
    )

    private data class Config(
        val includePrefixes: MutableList<String> = mutableListOf(),   // package or qualified-name prefixes to include (if empty, include all)
        val excludePrefixes: MutableList<String> = mutableListOf(),   // package or qualified-name prefixes to exclude
        var interfaceAsProtocol: Boolean = true,
        var propertySynthesis: Boolean = true,
        var visibility: String = "public", // "public" | "public+package"
        var emitMetadataHeader: Boolean = false,
        var nullabilityMode: String = "annotations", // "annotations" | "conservative" | "aggressive" (currently informational)
        val nullabilityExtra: MutableList<String> = mutableListOf(),
        var collectionMapping: String = "sequence", // "sequence" | "list" (informational v1)
        var streamMapping: String = "iterable", // "iterable" | "iterator" (informational v1)
        // Map Java package prefixes to Python package prefixes for output structure and import paths.
        // Example: com.knuddels.jtokkit -> jtokkit
        val packagePrefixMap: MutableList<Pair<String, String>> = mutableListOf(),
        var moduleName: String? = null,
        var moduleVersion: String = "0.1.0"
    )

    private val config: Config = Config()

    // Nullability detection (package prefixes per spec; simple-name heuristic allowed for tests)
    private val NULLABILITY_PACKAGE_PREFIXES: List<String> = listOf(
        "javax.annotation",
        "jakarta.annotation",
        "org.jetbrains.annotations",
        "edu.umd.cs.findbugs.annotations",
        "org.checkerframework.checker.nullness.qual",
        "androidx.annotation",
        "org.jspecify.annotations",
        "io.micronaut.core.annotation"
    )

    private enum class Nullability { NULLABLE, NONNULL, UNKNOWN }

    private var reporter: Reporter? = null
    private var outputDir: String? = null
    private var docTrees: DocTrees? = null

    override fun init(locale: Locale, reporter: Reporter) {
        this.reporter = reporter
    }

    override fun getName(): String = "j2pyi"

    override fun run(environment: DocletEnvironment): Boolean {
        this.docTrees = environment.docTrees
        // Build IR for all included types (classes, interfaces, enums) honoring include/exclude and visibility.
        val typeIRs = environment.includedElements
            .filterIsInstance<TypeElement>()
            .filter { it.kind == ElementKind.CLASS || it.kind == ElementKind.INTERFACE || it.kind == ElementKind.ENUM }
            .filter { shouldIncludeType(it) }
            .mapNotNull { maybeBuildTypeIR(it) }
            .sortedBy { it.qualifiedName }

        // Determine output directory. Respect -d if provided; otherwise default to build/pyi.
        val baseOut = File(outputDir ?: "build/pyi")
        baseOut.mkdirs()

        if (typeIRs.isEmpty()) {
            return true
        }

        // Emit one .pyi module per top-level type and collect
        // package contents for __init__ re-exports and runtime symbols.
        // Map: package -> (simpleName -> fullyQualifiedName)
        val pkgToTypes = mutableMapOf<String, MutableMap<String, String>>()
        for (t: TypeIR in typeIRs) {
            val mappedPkg = mapPackage(t.packageName)
            val pkgDir = packageDir(baseOut, mappedPkg)
            pkgDir.mkdirs()
            // Type stubs
            File(pkgDir, "${t.simpleName}.pyi").writeText(emitTypeAsPyi(t))
            // Record for __init__.py and __init__.pyi aggregation
            pkgToTypes.computeIfAbsent(mappedPkg) { linkedMapOf() }[t.simpleName] = t.qualifiedName
        }

        // Write __init__.pyi per package with stable, alphabetical re-exports.
        for ((pkg: String, types: MutableMap<String, String>) in pkgToTypes.toSortedMap()) {
            val pkgDir = packageDir(baseOut, pkg)
            val lines = types.keys.toList().sorted().map { n -> "from .$n import $n as $n" }
            File(pkgDir, "__init__.pyi").writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        }
        assemblePythonModule(baseOut, pkgToTypes)
        return true
    }

    override fun getSupportedOptions(): MutableSet<out Doclet.Option> =
        mutableSetOf(
            object : Doclet.Option {
                override fun getArgumentCount(): Int = 1
                override fun getDescription(): String = "Output directory for .pyi files"
                override fun getKind(): Doclet.Option.Kind = Doclet.Option.Kind.STANDARD
                override fun getNames(): MutableList<String> = mutableListOf("-d")
                override fun getParameters(): String = "<dir>"
                override fun process(option: String?, arguments: MutableList<String>?): Boolean {
                    outputDir = arguments?.firstOrNull()
                    return true
                }
            },
            object : Doclet.Option {
                override fun getArgumentCount(): Int = 1
                override fun getDescription(): String = "Document title (ignored by this stub)"
                override fun getKind(): Doclet.Option.Kind = Doclet.Option.Kind.STANDARD
                override fun getNames(): MutableList<String> = mutableListOf("-doctitle")
                override fun getParameters(): String = "<title>"
                override fun process(option: String?, arguments: MutableList<String>?): Boolean = true
            },
            object : Doclet.Option {
                override fun getArgumentCount(): Int = 1
                override fun getDescription(): String = "Window title (ignored by this stub)"
                override fun getKind(): Doclet.Option.Kind = Doclet.Option.Kind.STANDARD
                override fun getNames(): MutableList<String> = mutableListOf("-windowtitle")
                override fun getParameters(): String = "<title>"
                override fun process(option: String?, arguments: MutableList<String>?): Boolean = true
            },
            // Extended options for configuration
            stringOption("-Xj2pyi-include", "<prefixes>", "Comma-separated package or qualified-name prefixes to include.") {
                config.includePrefixes.clear()
                config.includePrefixes.addAll(splitCsv(it))
                true
            },
            stringOption("-Xj2pyi-exclude", "<prefixes>", "Comma-separated package or qualified-name prefixes to exclude.") {
                config.excludePrefixes.clear()
                config.excludePrefixes.addAll(splitCsv(it))
                true
            },
            // Note: use 'intfAsProtocol' to avoid potential parsing issues with the word 'interface' in some javadoc environments.
            stringOption("-Xj2pyi-intfAsProtocol", "<true|false>", "Emit interfaces as typing.Protocol (default true).") {
                config.interfaceAsProtocol = it.equals("true", ignoreCase = true)
                true
            },
            // Convenience flag (no argument) to disable Protocol emission for interfaces.
            flagOption("-Xj2pyi-noInterfaceProtocol", "Do not emit interfaces as typing.Protocol (treat as plain classes).") {
                config.interfaceAsProtocol = false
                true
            },
            stringOption("-Xj2pyi-propertySynthesis", "<true|false>", "Synthesize @property from getters/setters (default true).") {
                config.propertySynthesis = it.equals("true", ignoreCase = true)
                true
            },
            stringOption("-Xj2pyi-visibility", "<public|public+package>", "Visibility filter for members/types.") {
                config.visibility = it
                true
            },
            stringOption("-Xj2pyi-emitMetadataHeader", "<true|false>", "Emit a one-line metadata header at top of files.") {
                config.emitMetadataHeader = it.equals("true", ignoreCase = true)
                true
            },
            stringOption("-Xj2pyi-packageMap", "<javaPkg=pyPkg[,more...]>", "Map Java package prefixes to Python package prefixes (CSV).") {
                config.packagePrefixMap.clear()
                config.packagePrefixMap.addAll(parsePackageMap(it))
                true
            },
            stringOption("-Xj2pyi-nullabilityMode", "<annotations|conservative|aggressive>", "Nullability mode (currently informational).") {
                config.nullabilityMode = it
                true
            },
            stringOption("-Xj2pyi-nullabilityExtra", "<prefixes>", "Comma-separated additional nullability annotation package prefixes.") {
                config.nullabilityExtra.clear()
                config.nullabilityExtra.addAll(splitCsv(it))
                true
            },
            stringOption("-Xj2pyi-collectionMapping", "<sequence|list>", "Array mapping preference).") {
                config.collectionMapping = it
                true
            },
            stringOption("-Xj2pyi-streamMapping", "<iterable|iterator>", "Stream mapping preference.") {
                config.streamMapping = it
                true
            },
            stringOption("-Xj2pyi-moduleName", "<name>", "Name for the assembled Python module distribution.") {
                config.moduleName = it
                true
            },
            stringOption("-Xj2pyi-moduleVersion", "<version>", "Version string for assembled Python module (default 0.1.0).") {
                config.moduleVersion = it
                true
            }
        )

    // Helpers for extended options
    private fun stringOption(
        name: String,
        param: String,
        desc: String,
        handler: (String) -> Boolean
    ): Doclet.Option {
        return object : Doclet.Option {
            override fun getArgumentCount(): Int = 1
            override fun getDescription(): String = desc
            override fun getKind(): Doclet.Option.Kind = Doclet.Option.Kind.EXTENDED
            override fun getNames(): MutableList<String> = mutableListOf(name)
            override fun getParameters(): String = param
            override fun process(option: String?, arguments: MutableList<String>?): Boolean {
                val v = arguments?.firstOrNull() ?: return false
                return handler(v)
            }
        }
    }

    private fun flagOption(name: String, desc: String, handler: () -> Boolean): Doclet.Option {
        return object : Doclet.Option {
            override fun getArgumentCount(): Int = 0
            override fun getDescription(): String = desc
            override fun getKind(): Doclet.Option.Kind = Doclet.Option.Kind.EXTENDED
            override fun getNames(): MutableList<String> = mutableListOf(name)
            override fun getParameters(): String = ""
            override fun process(option: String?, arguments: MutableList<String>?): Boolean {
                return handler()
            }
        }
    }

    private fun splitCsv(s: String): List<String> =
        s.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    private fun packageDir(base: File, pkg: String): File =
        if (pkg.isBlank()) base else File(base, pkg.replace('.', File.separatorChar))

    // Map a Java package name to a Python package name using the configured prefix map.
    // Chooses the longest matching Java prefix (boundary at '.' or end).
    private fun mapPackage(javaPkg: String): String {
        val mappings: List<Pair<String, String>> = config.packagePrefixMap
        if (mappings.isEmpty()) return javaPkg
        var best: Pair<String, String>? = null
        for ((from, to) in mappings) {
            if (javaPkg == from || javaPkg.startsWith("$from.")) {
                if (best == null || from.length > best.first.length) {
                    best = from to to
                }
            }
        }
        val match = best ?: return javaPkg
        val (from, to) = match
        return if (javaPkg.length == from.length) {
            to
        } else {
            val rest = javaPkg.substring(from.length + 1) // skip the dot
            if (to.isBlank()) rest else "$to.$rest"
        }
    }

    private fun parsePackageMap(spec: String): List<Pair<String, String>> {
        if (spec.isBlank()) return emptyList()
        return spec.split(',', ';')
            .mapNotNull { part ->
                val s = part.trim()
                if (s.isEmpty()) return@mapNotNull null
                val kv = s.split("=", limit = 2)
                if (kv.size != 2) {
                    reporter?.print(Diagnostic.Kind.WARNING, "Ignoring invalid -Xj2pyi-packageMap entry: '$s' (expect javaPkg=pyPkg)")
                    null
                } else {
                    kv[0].trim() to kv[1].trim()
                }
            }
    }

    private fun assemblePythonModule(stubOutDir: File, pkgToTypes: Map<String, Map<String, String>>) {
        val moduleName = (config.moduleName?.takeIf { it.isNotBlank() }) ?: "j2pyi-stubs"
        val outRoot = stubOutDir
        outRoot.mkdirs()

        // Return all ancestor packages of the given package (e.g., "a.b.c" -> ["a", "a.b"])
        fun ancestorPackages(pkg: String, includeSelf: Boolean = false): List<String> {
            if (pkg.isBlank()) return emptyList()
            val parts = pkg.split('.')
            val last = if (includeSelf) parts.size else parts.size - 1
            val out = mutableListOf<String>()
            var cur = ""
            for (i in 0 until last) {
                cur = if (i == 0) parts[i] else "$cur.${parts[i]}"
                out += cur
            }
            return out
        }

        val pkgInitContent = """
            |# Auto-generated by j2pyi.
            |# This file makes this a regular Python package for packaging and type checkers.
            |from __future__ import annotations
            |# Runtime note: This package contains type stubs for GraalPy interop.
            """.trimMargin() + "\n"

        val leafPkgNames: Set<String> = pkgToTypes.keys.filter { it.isNotBlank() }.toSortedSet()
        for ((pkg, types) in pkgToTypes.toSortedMap()) {
            val pkgPath = pkg.replace('.', File.separatorChar)
            val dir = File(outRoot, pkgPath)
            if (!dir.exists()) dir.mkdirs()
            val runtime = buildString {
                append(pkgInitContent)
                appendLine("try:")
                appendLine("    import java  # type: ignore")
                appendLine("except Exception as _e:")
                appendLine("    raise ImportError(\"GraalPy java.type() not available; importing Java types requires running under GraalPy.\") from _e")
                appendLine("")
                for (name in types.keys.sorted()) {
                    val fqcn = types[name]
                    appendLine("%s = java.type(\"%s\")  # type: ignore[attr-defined]".format(name, fqcn))
                }
            }
            File(dir, "__init__.py").writeText(runtime)
            val initPyi = File(dir, "__init__.pyi")
            if (!initPyi.exists()) initPyi.writeText("from __future__ import annotations\n")
        }

        // IMPORTANT: Leave empty ancestor packages as implicit namespace packages (PEP 420).
        // Do not emit any __init__.py or __init__.pyi for empty packages so Python can merge
        // multiple distributions that share the same package tree.
        // Intentionally left blank.
        // Write minimal pyproject.toml
        val pyproject = File(outRoot, "pyproject.toml")

        // Only list non-empty packages in pyproject.toml. A non-empty package contains at least
        // one Python source file other than an __init__ file: either a .py (not __init__.py)
        // or a .pyi (not __init__.pyi). This avoids listing ancestor/namespace packages that
        // are present only to make the directory structure importable.
        fun discoverNonEmptyPackages(root: File): Set<String> {
            if (!root.isDirectory) return emptySet()
            val pkgs = mutableSetOf<String>()
            root.walkTopDown().forEach { f ->
                if (f.isFile) {
                    val name = f.name
                    val isPy = name.endsWith(".py")
                    val isPyi = name.endsWith(".pyi")
                    val isInit = name == "__init__.py" || name == "__init__.pyi"
                    if ((isPy || isPyi) && !isInit) {
                        val rel = f.parentFile.relativeTo(root).invariantSeparatorsPath
                        if (rel.isNotBlank()) pkgs += rel.replace('/', '.')
                    }
                }
            }
            return pkgs
        }

        val packagesForToml = discoverNonEmptyPackages(outRoot).toSortedSet().toList()
        val packagesTomlList = packagesForToml.joinToString(", ") { "\"$it\"" }
        val classifiers = listOf(
            "Development Status :: 3 - Alpha",
            "Typing :: Stubs",
            "Programming Language :: Python :: 3",
            "License :: OSI Approved :: Apache Software License"
        ).joinToString("\n") { "  \"$it\"," }
        pyproject.writeText(
            """
            |[build-system]
            |requires = ["setuptools>=68", "wheel"]
            |build-backend = "setuptools.build_meta"
            |
            |[project]
            |name = "$moduleName"
            |version = "${config.moduleVersion}"
            |description = "PEP 561 stub-only package generated from Javadoc for GraalPy interop"
            |requires-python = ">=3.8"
            |license = {text = "Apache-2.0"}
            |classifiers = [
            |$classifiers
            |]
            |
            |[tool.setuptools]
            |packages = [$packagesTomlList]
            |zip-safe = false
            |include-package-data = true
            |
            |[tool.setuptools.package-data]
            |"*" = ["**/*.pyi", "*.pyi"]
            |""".trimMargin()
        )

        reporter?.print(Diagnostic.Kind.NOTE, "j2pyi: Assembled Python module at ${outRoot} (name=${moduleName}, packages=${leafPkgNames.size})")
    }

    // Visibility and include/exclude filtering
    private fun isIncludedByVisibility(e: Element): Boolean {
        if (e.modifiers.contains(Modifier.PUBLIC)) return true
        if (config.visibility == "public+package") {
            // Package-private: no PROTECTED/PRIVATE
            return !(e.modifiers.contains(Modifier.PROTECTED) || e.modifiers.contains(Modifier.PRIVATE))
        }
        return false
    }

    private fun shouldIncludeType(te: TypeElement): Boolean {
        val pkg = packageOf(te)
        val qn = te.qualifiedName.toString()
        val matchesInclude =
            if (config.includePrefixes.isEmpty())
                true
            else
                config.includePrefixes.any { qn.startsWith(it) || pkg.startsWith(it) }
        val matchesExclude = config.excludePrefixes.any { qn.startsWith(it) || pkg.startsWith(it) }
        val visible = isIncludedByVisibility(te)
        return matchesInclude && !matchesExclude && visible
    }

    // Determine the package name for any element, including nested/member classes.
    private fun packageOf(e: Element): String {
        var cur: Element? = e
        while (cur != null && cur !is PackageElement) cur = cur.enclosingElement
        return cur?.qualifiedName?.toString() ?: ""
    }

    private fun maybeBuildTypeIR(te: TypeElement): TypeIR? {
        if (!isIncludedByVisibility(te)) return null

        val pkg = packageOf(te)
        val kind = when (te.kind) {
            ElementKind.INTERFACE -> Kind.INTERFACE
            ElementKind.ENUM -> Kind.ENUM
            else -> Kind.CLASS
        }
        // Collect type parameters with simple upper bounds (first non-Object bound only).
        val typeParams: List<TypeParamIR> = te.typeParameters.map { tp ->
            val name = tp.simpleName.toString()
            // Prefer first bound that's not java.lang.Object; fall back to first or null
            val chosenBound: TypeMirror? = tp.bounds.firstOrNull { b ->
                val decl = (b as? DeclaredType)?.asElement() as? TypeElement
                val qn = decl?.qualifiedName?.toString()
                qn != null && qn != "java.lang.Object"
            } ?: tp.bounds.firstOrNull()
            val mapped = chosenBound?.let { mapType(it) }
            val normalized = when (mapped) {
                null -> null
                PyType.AnyT -> null
                else -> mapped
            }
            TypeParamIR(name, normalized)
        }
        val typeDoc: String? = docTrees?.javadocFull(te)
        val fields = if (kind == Kind.INTERFACE) {
            emptyList()
        } else {
            ElementFilter.fieldsIn(te.enclosedElements)
                .filter { isIncludedByVisibility(it) && it.kind != ElementKind.ENUM_CONSTANT }
                .sortedBy { it.simpleName.toString() }
                .map { f -> FieldIR(f.simpleName.toString(), mapFieldTypeWithNullability(f)) }
        }

        val constructors =
            if (kind == Kind.CLASS) {
                ElementFilter.constructorsIn(te.enclosedElements)
                    .filter { isIncludedByVisibility(it) }
                    .sortedBy { it.parameters.joinToString(",") { p -> p.asType().toString() } }
                    .map { c ->
                        val isVar = c.isVarArgs
                        val params = paramsToIR(c, isVar)
                        ConstructorIR(params = params, doc = docTrees?.javadocFull(c))
                    }
            } else {
                emptyList()
            }

        // Collect methods
        val allMethods = ElementFilter.methodsIn(te.enclosedElements)
            .filter { isIncludedByVisibility(it) }
            .sortedWith(compareBy({ it.simpleName.toString() }, { it.parameters.size }))
        val mappedMethods = allMethods.map { m ->
            val variadic = m.isVarArgs
            MethodIR(
                name = m.simpleName.toString(),
                params = paramsToIR(m, variadic),
                returnType = mapReturnTypeWithNullability(m),
                isStatic = m.modifiers.contains(Modifier.STATIC),
                doc = docTrees?.javadocFull(m)
            )
        }

        // Synthesize properties per JavaBeans rules and filter out matched getters/setters (classes only).
        val (properties, remainingMethods) =
            if (kind == Kind.CLASS && config.propertySynthesis) synthesizeProperties(fields, allMethods, mappedMethods)
            else Pair(emptyList(), mappedMethods)

        // Enum constants (names only)
        val enumConstants =
            if (kind == Kind.ENUM) {
                te.enclosedElements
                    .filter { it.kind == ElementKind.ENUM_CONSTANT }
                    .map { it.simpleName.toString() }
                    .sorted()
            } else {
                emptyList()
            }

        return TypeIR(
            packageName = pkg,
            simpleName = te.simpleName.toString(),
            qualifiedName = te.qualifiedName.toString(),
            kind = kind,
            isAbstract = te.modifiers.contains(Modifier.ABSTRACT),
            typeParams = typeParams,
            doc = typeDoc,
            fields = fields,
            constructors = constructors,
            methods = remainingMethods,
            properties = properties,
            enumConstants = enumConstants
        )
    }

    private fun synthesizeProperties(
        fields: List<FieldIR>,
        rawMethods: List<ExecutableElement>,
        mappedMethods: List<MethodIR>
    ): Pair<List<PropertyIR>, List<MethodIR>> {
        // Index raw methods by name for bean detection; exclude static methods from consideration
        data class Getter(val el: ExecutableElement, val name: String, val kind: String) // kind: "get" or "is"
        data class Setter(val el: ExecutableElement, val name: String)

        fun decapitalize(s: String): String {
            if (s.isEmpty()) return s
            return if (s.length >= 2 && s[0].isUpperCase() && s[1].isUpperCase()) s else s.replaceFirstChar { it.lowercaseChar() }
        }

        val gettersByProp = mutableMapOf<String, MutableList<Getter>>()
        val settersByProp = mutableMapOf<String, MutableList<Setter>>()

        for (m in rawMethods) {
            if (m.modifiers.contains(Modifier.STATIC)) continue
            val name = m.simpleName.toString()
            val params = m.parameters
            when {
                name.startsWith("get") && name.length > 3 && params.isEmpty() -> {
                    val suffix = name.substring(3)
                    val prop = decapitalize(suffix)
                    gettersByProp.computeIfAbsent(prop) { mutableListOf() }.add(Getter(m, name, "get"))
                }

                name.startsWith("is") && name.length > 2 && params.isEmpty() && m.returnType.kind == TypeKind.BOOLEAN -> {
                    val suffix = name.substring(2)
                    val prop = decapitalize(suffix)
                    gettersByProp.computeIfAbsent(prop) { mutableListOf() }.add(Getter(m, name, "is"))
                }

                // TODO: Should we allow through setters with non-void return values, as sometimes seen in builders?
                name.startsWith("set") && name.length > 3 && params.size == 1 && m.returnType.kind == TypeKind.VOID -> {
                    val suffix = name.substring(3)
                    val prop = decapitalize(suffix)
                    settersByProp.computeIfAbsent(prop) { mutableListOf() }.add(Setter(m, name))
                }
            }
        }

        val fieldsSet: Set<String> = fields.map { it.name }.toSet()
        val mappedByName: Map<String, List<MethodIR>> = mappedMethods.groupBy { it.name }

        val toDropRaw = mutableSetOf<ExecutableElement>()
        val props = mutableListOf<PropertyIR>()

        for ((propName, getters: List<Getter>) in gettersByProp) {
            // Exactly one compatible getter must exist
            if (getters.size != 1) continue
            val g: Getter = getters.single()
            val gType: PyType = mapReturnTypeWithNullability(g.el)
            val gDoc: String? = docTrees?.javadocSummary(g.el)

            // Resolve setters for the same property; must be zero or one and type-compatible
            val setters: List<Setter> = settersByProp[propName] ?: emptyList()
            val compatibleSetters = setters.filter { s ->
                // Setter param type must map to same PyType as getter
                val p = s.el.parameters.first()
                mapParamTypeWithNullability(p) == gType
            }
            if (compatibleSetters.size > 1) continue // more than one compatible setter -> skip
            // Conflict checks: no field or method named propName (excluding the matched getter/setter methods themselves)
            if (fieldsSet.contains(propName)) continue
            val otherMethodsNamed = mappedByName[propName].orEmpty().filter { _ ->
                // Any method with the property name is a conflict
                true
            }
            if (otherMethodsNamed.isNotEmpty()) continue

            // Good to synthesize — sanitize property name for Python
            val readOnly = compatibleSetters.isEmpty()
            val pyName = safeIdentifier(propName)
            props += PropertyIR(pyName, gType, readOnly, gDoc)
            // Drop matched getter
            toDropRaw += g.el
            // Drop the single compatible setter if present
            compatibleSetters.singleOrNull()?.let { toDropRaw += it.el }
        }

        if (toDropRaw.isNotEmpty()) {
            // Recompute remaining by linking raw -> mapped using name and arity and static flag and return type mapping
            val dropKeys = toDropRaw.map { raw ->
                Triple(raw.simpleName.toString(), raw.parameters.size, raw.modifiers.contains(Modifier.STATIC))
            }.toSet()
            val filtered = mappedMethods.filterNot { mm ->
                dropKeys.contains(Triple(mm.name, mm.params.size, mm.isStatic))
            }
            return Pair(props.sortedBy { it.name }, filtered)
        }

        return Pair(props.sortedBy { it.name }, mappedMethods)
    }

    private fun paramsToIR(m: ExecutableElement, isVar: Boolean): List<ParamIR> {
        return m.parameters.mapIndexed { idx, p ->
            val isLast = idx == m.parameters.lastIndex
            val isVarargs = isVar && isLast
            val t: PyType =
                if (isVarargs) {
                    val pt: TypeMirror = p.asType()
                    val comp: TypeMirror = if (pt.kind == TypeKind.ARRAY) {
                        (pt as ArrayType).componentType
                    } else pt
                    mapParamTypeWithNullability(p, overrideType = comp)
                } else {
                    mapParamTypeWithNullability(p)
                }
            ParamIR(safeParamName(p), t, isVarargs = isVarargs)
        }
    }

    private fun safeParamName(p: VariableElement): String {
        val raw = p.simpleName.toString()
        // Default fallback name
        var name = if (raw.isBlank() || raw == "self") "p" else raw
        // Replace characters not valid in a Python identifier with underscores.
        name = name.replace(Regex("[^0-9A-Za-z_]"), "_")
        // Python identifiers cannot start with a digit.
        if (name.firstOrNull()?.isDigit() == true) {
            name = "p_$name"
        }
        // Disallow empty result after normalization.
        if (name.isBlank()) name = "p"
        // Avoid reserved keywords (case-sensitive match as in Python).
        if (PYTHON_KEYWORDS.contains(name)) {
            name = "${name}_"
        }
        // Don't let parameters be called exactly 'self' (reserved for instance methods).
        if (name == "self") name = "p"
        return name
    }

    // Sanitize a general Python identifier (e.g., synthesized property names).
    private fun safeIdentifier(raw0: String, allowSelf: Boolean = false): String {
        var name = raw0
        if (name.isBlank()) return "name"
        name = name.replace(Regex("[^0-9A-Za-z_]"), "_")
        if (name.isBlank()) name = "name"
        if (name.first().isDigit()) name = "n_$name"
        if (!allowSelf && name == "self") name = "name"
        if (PYTHON_KEYWORDS.contains(name)) name = "${name}_"
        return name
    }

    // Basic Java -> Python type mapping (includes collections/arrays and streams)
    private fun mapType(t: TypeMirror): PyType = when (t.kind) {
        TypeKind.BOOLEAN -> PyType.Bool
        TypeKind.BYTE, TypeKind.SHORT, TypeKind.INT, TypeKind.LONG -> PyType.IntT
        TypeKind.FLOAT, TypeKind.DOUBLE -> PyType.FloatT
        TypeKind.CHAR -> PyType.Str
        TypeKind.VOID -> PyType.NoneT
        TypeKind.TYPEVAR -> {
            // Reference to a declared type parameter (e.g., T)
            val tv = t.toString()
            PyType.TypeVarRef(tv)
        }

        TypeKind.ARRAY -> {
            val at = t as ArrayType
            // Map arrays to Sequence[T]
            val comp = at.componentType
            PyType.Abc("Sequence", listOf(mapType(comp)))
        }

        TypeKind.DECLARED -> mapDeclaredType(t as DeclaredType)
        // If a referenced type cannot be resolved on the classpath, Javadoc yields an ERROR type.
        // Parse its qualified name from toString() so we can still emit a stable import.
        TypeKind.ERROR -> mapUnresolvedDeclaredType(t)
        // Wildcard handling: treat wildcards (? extends X / ? super X) as Any at use sites.
        TypeKind.WILDCARD -> PyType.AnyT
        else -> PyType.AnyT
    }

    private fun mapUnresolvedDeclaredType(t: TypeMirror): PyType {
        val text = t.toString()
        // Strip generic arguments if present
        val raw = text.substringBefore('<')
        val lastDot = raw.lastIndexOf('.')
        val pkg = if (lastDot >= 0) raw.substring(0, lastDot) else ""
        val simple = if (lastDot >= 0) raw.substring(lastDot + 1) else raw
        // Treat unresolved Java platform types as object to avoid unresolved names in stubs.
        if (isFullyQualifiedNameAJDKType(pkg)) {
            return PyType.ObjectT
        }
        return if (simple.isBlank()) PyType.AnyT else PyType.Ref(pkg, simple)
    }

    private fun mapDeclaredType(dt: DeclaredType): PyType {
        val el = dt.asElement() as? TypeElement ?: return PyType.AnyT
        val qn = el.qualifiedName.toString()

        // Helper to map the i-th generic type argument, or default to Any if missing
        fun argOrAny(i: Int): PyType {
            val args: List<TypeMirror> = dt.typeArguments
            return if (i >= 0 && i < args.size) mapType(args[i]) else PyType.AnyT
        }
        return when (qn) {
            // Core
            "java.lang.String" -> PyType.Str
            "java.lang.Object" -> PyType.AnyT
            "java.lang.Number" -> PyType.NumberT
            // Boxed primitives
            "java.lang.Boolean" -> PyType.Bool
            "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long" -> PyType.IntT
            "java.lang.Float", "java.lang.Double" -> PyType.FloatT
            "java.lang.Character" -> PyType.Str

            // Collections and streams handling
            "java.util.List" -> PyType.Generic("list", listOf(argOrAny(0)))
            "java.util.Set" -> PyType.Generic("set", listOf(argOrAny(0)))
            "java.util.Map" -> PyType.Generic("dict", listOf(argOrAny(0), argOrAny(1)))
            "java.util.Collection" -> PyType.Abc("Collection", listOf(argOrAny(0)))
            "java.lang.Iterable" -> PyType.Abc("Iterable", listOf(argOrAny(0)))
            "java.util.Iterator" -> PyType.Abc("Iterator", listOf(argOrAny(0)))
            "java.util.Optional" -> optionalOf(argOrAny(0))
            "java.util.stream.Stream" -> PyType.Abc("Iterable", listOf(argOrAny(0)))

            else -> {
                // Fallback: reference to another declared type that we (likely) also emit.
                // Render as a simple name and add an import later.
                // If this is a Java platform type we don't recognize specially, map to object
                // to avoid unresolved names/imports in generated stubs.
                if (isFullyQualifiedNameAJDKType(qn)) {
                    PyType.ObjectT
                } else {
                    val pkg = packageOf(el)
                    val name = el.simpleName.toString()
                    PyType.Ref(pkg, name)
                }
            }
        }
    }

    private fun isFullyQualifiedNameAJDKType(qn: String): Boolean = qn.startsWith("java.") || qn.startsWith("javax.") || qn.startsWith("jdk.")

    private fun optionalOf(inner: PyType): PyType {
        // Normalize: if inner already includes None, don't add another
        return when (inner) {
            // Special-case: nullable Any should be rendered as 'object | None', not 'Any | None'
            PyType.AnyT -> PyType.Union(listOf(PyType.ObjectT, PyType.NoneT))
            PyType.NoneT -> PyType.NoneT
            is PyType.Union -> {
                if (inner.items.any { it === PyType.NoneT }) inner
                else PyType.Union(inner.items + PyType.NoneT)
            }

            else -> PyType.Union(listOf(inner, PyType.NoneT))
        }
    }

    // ===== Nullability helpers =====
    private fun mapReturnTypeWithNullability(m: ExecutableElement): PyType {
        val base = mapType(m.returnType)
        return when (computeNullability(m.annotationMirrors + m.returnType.annotationMirrors)) {
            Nullability.NULLABLE -> optionalOf(base)
            else -> base
        }
    }

    private fun mapParamTypeWithNullability(p: VariableElement, overrideType: TypeMirror? = null): PyType {
        val t = overrideType ?: p.asType()
        val base = mapType(t)
        return when (computeNullability(p.annotationMirrors + t.annotationMirrors)) {
            Nullability.NULLABLE -> optionalOf(base)
            else -> base
        }
    }

    private fun mapFieldTypeWithNullability(f: VariableElement): PyType {
        val t = f.asType()
        val base = mapType(t)
        return when (computeNullability(f.annotationMirrors + t.annotationMirrors)) {
            Nullability.NULLABLE -> optionalOf(base)
            else -> base
        }
    }

    private fun computeNullability(anns: List<AnnotationMirror>): Nullability {
        var foundNullable = false
        var foundNonNull = false
        for (am in anns) {
            val at = am.annotationType
            val aEl = (at.asElement() as? TypeElement) ?: continue
            val qn = aEl.qualifiedName.toString()
            val simple = aEl.simpleName.toString()
            val pkg = qn.substringBeforeLast('.', missingDelimiterValue = "")
            val extra = config.nullabilityExtra
            val pkgRecognized = pkg.isNotEmpty() && (NULLABILITY_PACKAGE_PREFIXES.any { pkg.startsWith(it) } ||
                    extra.any { pkg.startsWith(it) })
            val simpleIsNullable = simple == "Nullable" || simple == "CheckForNull"
            val simpleIsNonNull = simple == "NotNull" || simple == "NonNull" || simple == "Nonnull"
            if (pkgRecognized && simpleIsNullable) foundNullable = true
            if (pkgRecognized && simpleIsNonNull) foundNonNull = true
        }
        return when {
            foundNullable && !foundNonNull -> Nullability.NULLABLE
            foundNonNull && !foundNullable -> Nullability.NONNULL
            else -> Nullability.UNKNOWN
        }
    }

    // Helper: compute a "generality score" for a type; higher = more general
    private fun typeGeneralityScore(pt: PyType): Int {
        var score = 0
        pt.walk { node ->
            score += when (node) {
                is PyType.AnyT -> 100
                is PyType.ObjectT -> 80
                is PyType.TypeVarRef -> 50
                is PyType.Abc -> 10
                is PyType.Generic -> 10
                is PyType.Union -> 5
                // Prefer ints as most specific relative to float/Number
                is PyType.IntT -> 0
                is PyType.FloatT -> 5
                is PyType.Str, is PyType.Bool -> 0
                is PyType.NoneT -> 0
                is PyType.Ref -> 5 // referenced declared types: treat as moderately specific
                is PyType.NumberT -> 20
            }
        }
        return score
    }

    // Approximate: does type 'a' accept everything type 'b' accepts? (a is broader-or-equal than b)
    private fun typeIsBroaderOrEqual(a: PyType, b: PyType): Boolean {
        if (a === b) return true
        // Any and object are the broadest
        if (a === PyType.AnyT) return true
        if (a === PyType.ObjectT && b !== PyType.AnyT) return true
        // Number is broader than int/float
        if (a === PyType.NumberT) return b === PyType.IntT || b === PyType.FloatT
        // float is broader than int per Python typing
        if (a === PyType.FloatT && b === PyType.IntT) return true
        // TypeVars are treated broadly (unknown constraint) -> assume broader
        if (a is PyType.TypeVarRef) return true
        // Same Abc/Generic family with broader args
        if (a is PyType.Abc && b is PyType.Abc && a.name == b.name && a.args.size == b.args.size) {
            return a.args.zip(b.args).all { (aa, bb) -> typeIsBroaderOrEqual(aa, bb) }
        }
        if (a is PyType.Generic && b is PyType.Generic && a.name == b.name && a.args.size == b.args.size) {
            return a.args.zip(b.args).all { (aa, bb) -> typeIsBroaderOrEqual(aa, bb) }
        }
        // For other cases (Union, Ref, mixed kinds), don't assume broader.
        return false
    }

    private fun methodDominates(a: WithParamsIR, b: WithParamsIR): Boolean {
        // Overload dominance: same arity, every param in 'a' is broader-or-equal than in 'b'
        if (a.params.size != b.params.size) return false
        for ((pa, pb) in a.params.zip(b.params)) {
            // Varargs considered broader than positional
            val va = pa.isVarargs
            val vb = pb.isVarargs
            if (va && !vb) {
                // a is varargs where b is fixed -> broader
            } else if (!typeIsBroaderOrEqual(pa.type, pb.type)) {
                return false
            }
        }
        return true
    }

    private fun methodGeneralitySignature(m: WithParamsIR): List<Int> {
        // Varargs is inherently general; add a small penalty.
        return m.params.map { p -> typeGeneralityScore(p.type) + if (p.isVarargs) 10 else 0 }
    }

    // Emit a single type as .pyi text (class, interface-as-Protocol, or enum)
    private fun emitTypeAsPyi(t: TypeIR): String {
        val hasTypeParams = t.typeParams.isNotEmpty()
        val needsEnumImport = t.kind == Kind.ENUM
        val sb = StringBuilder()
        // Optional metadata header (single line)
        // TODO: Consider removing this.
        if (config.emitMetadataHeader) {
            val opts = mutableListOf<String>()
            opts += "interfaceAsProtocol=${config.interfaceAsProtocol}"
            opts += "propertySynthesis=${config.propertySynthesis}"
            opts += "visibility=${config.visibility}"
            opts += "nullabilityMode=${config.nullabilityMode}"
            if (config.nullabilityExtra.isNotEmpty()) opts += "nullabilityExtra=${config.nullabilityExtra.joinToString("|")}"
            opts += "collectionMapping=${config.collectionMapping}"
            opts += "streamMapping=${config.streamMapping}"
            if (config.includePrefixes.isNotEmpty()) opts += "include=${config.includePrefixes.joinToString("|")}"
            if (config.excludePrefixes.isNotEmpty()) opts += "exclude=${config.excludePrefixes.joinToString("|")}"
            sb.appendLine("# javadoc2pyi: ${opts.joinToString(", ")}")
        }
        // typing imports
        run {
            val items = mutableListOf<String>()
            if (t.needsAnyImport()) items += "Any"
            if (t.needsOverloadImport()) items += "overload"
            if (t.kind == Kind.INTERFACE && config.interfaceAsProtocol) items += "Protocol"
            if (items.isNotEmpty()) {
                sb.appendLine("from typing import ${items.joinToString(", ")}")
            }
        }
        // numbers imports
        if (t.needsNumberImport()) {
            sb.appendLine("from numbers import Number")
        }
        // builtins imports (for builtins.object)
        if (t.needsBuiltinsImport()) {
            sb.appendLine("import builtins")
        }
        // enum import
        if (needsEnumImport) {
            sb.appendLine("from enum import Enum")
        }
        // collections.abc imports
        val abcImports = t.collectionsAbcImports()
        if (abcImports.isNotEmpty()) {
            sb.appendLine("from collections.abc import ${abcImports.sorted().joinToString(", ")}")
        }
        if (sb.isNotEmpty()) sb.appendLine()
        // Local and cross-package imports for referenced declared types.
        run {
            val refs = referencedDeclaredTypes(t)
                // Don't import self
                .filterNot { it.packageName == t.packageName && it.simpleName == t.simpleName }
                // Do not emit imports for Java platform packages; mypy can't resolve them.
                .filterNot { isFullyQualifiedNameAJDKType(it.packageName) }
                .sortedWith(compareBy({ it.packageName }, { it.simpleName }))
            val thisPkgMapped = mapPackage(t.packageName)
            for (r in refs) {
                val rMappedPkg = mapPackage(r.packageName)
                if (rMappedPkg == thisPkgMapped) {
                    // Same mapped package -> relative import
                    sb.appendLine("from .${r.simpleName} import ${r.simpleName}")
                } else if (rMappedPkg.isNotBlank()) {
                    // Absolute import using mapped package path
                    sb.appendLine("from ${rMappedPkg}.${r.simpleName} import ${r.simpleName}")
                } else {
                    // No package (default) – import by module name only
                    sb.appendLine("from ${r.simpleName} import ${r.simpleName}")
                }
            }
            if (refs.isNotEmpty()) sb.appendLine()
        }

        // Emit TypeVar declarations (PEP 484) for type parameters used by this module.
        // Include both class-level type params and any method-level TypeVar references.
        if (t.kind != Kind.ENUM) {
            // Declared on the class
            val declared: Set<String> = t.typeParams.map { it.name }.toMutableSet()

            // Referenced anywhere in signatures
            fun gatherTypeVars(py: PyType, out: MutableSet<String>) {
                py.walk { node ->
                    if (node is PyType.TypeVarRef) out += node.name
                }
            }

            val referenced = mutableSetOf<String>()
            // Additional tracking for variance inference when emitting Protocols:
            // For each TypeVar, note if it's seen in "return" (covariant position) and/or "param" (contravariant).
            val seenInReturn = mutableSetOf<String>()
            val seenInParam = mutableSetOf<String>()

            // Fields (attributes) – treat as "return-like" usage for variance purposes.
            for (f: FieldIR in t.fields) {
                gatherTypeVars(f.type, referenced)
                gatherTypeVars(f.type, seenInReturn)
            }
            // Constructors – parameters only
            for (c: ConstructorIR in t.constructors) {
                for (p: ParamIR in c.params) {
                    gatherTypeVars(p.type, referenced)
                    gatherTypeVars(p.type, seenInParam)
                }
            }
            // Methods – collect both return and param appearances
            for (m: MethodIR in t.methods) {
                gatherTypeVars(m.returnType, referenced)
                gatherTypeVars(m.returnType, seenInReturn)
                for (p in m.params) {
                    gatherTypeVars(p.type, referenced)
                    gatherTypeVars(p.type, seenInParam)
                }
            }
            // Properties – getter return is "return-like"; setter value is "param-like"
            for (p: PropertyIR in t.properties) {
                gatherTypeVars(p.type, referenced)
                gatherTypeVars(p.type, seenInReturn)
                if (!p.readOnly) {
                    // Treat setter as param position of same type
                    gatherTypeVars(p.type, seenInParam)
                }
            }
            val toDeclare = (declared + referenced).toMutableSet()
            if (toDeclare.isNotEmpty()) {
                fun inferVariance(wantVariance: Boolean, name: String, seenInReturn: MutableSet<String>, seenInParam: MutableSet<String>): String? {
                    val onlyReturn = wantVariance && name in seenInReturn && name !in seenInParam
                    val onlyParam = wantVariance && name in seenInParam && name !in seenInReturn
                    val unused = wantVariance && name !in seenInReturn && name !in seenInParam
                    return when {
                        onlyReturn -> "covariant=True"
                        onlyParam -> "contravariant=True"
                        unused -> "covariant=True"
                        else -> null
                    }
                }

                sb.appendLine("from typing import TypeVar")
                // Emit declared class type params first (preserve bounds), then remaining refs unbounded
                for (tp: TypeParamIR in t.typeParams) {
                    val bound = tp.bound?.render()
                    // Infer variance only for interfaces being emitted as Protocols (PEP 544 requires consistency).
                    val wantVariance = (t.kind == Kind.INTERFACE && config.interfaceAsProtocol)
                    val name = tp.name
                    val varianceArg: String? = inferVariance(wantVariance, name, seenInReturn, seenInParam)
                    // Build TypeVar(...) arguments
                    val args = mutableListOf("\"$name\"")
                    if (!(bound.isNullOrBlank() || bound == "Any")) {
                        args += "bound=$bound"
                    }
                    if (varianceArg != null) {
                        args += varianceArg
                    }
                    sb.appendLine("$name = TypeVar(${args.joinToString(", ")})")
                    toDeclare.remove(tp.name)
                }
                // Method-level or otherwise unbound TypeVars
                for (name in toDeclare.sorted()) {
                    val wantVariance = (t.kind == Kind.INTERFACE && config.interfaceAsProtocol)
                    val varianceArg: String? = inferVariance(wantVariance, name, seenInReturn, seenInParam)
                    if (varianceArg != null) {
                        sb.appendLine("$name = TypeVar(\"$name\", $varianceArg)")
                    } else {
                        sb.appendLine("$name = TypeVar(\"$name\")")
                    }
                }
                sb.appendLine()
            }
        }

        // If the class is generic, we will bind TypeVars with Generic[...] in bases.
        val needsGenericBase = t.kind != Kind.ENUM && hasTypeParams
        if (needsGenericBase) {
            sb.appendLine("from typing import Generic")
        }
        // Build class header with Protocol/Enum bases using PEP 484 generics.
        val header = run {
            val bases = mutableListOf<String>()
            when (t.kind) {
                Kind.INTERFACE -> if (config.interfaceAsProtocol) bases += "Protocol"
                Kind.ENUM -> bases += "Enum"
                else -> {}
            }
            if (needsGenericBase) {
                val tvNames = t.typeParams.map { it.name }
                bases += "Generic[${tvNames.joinToString(", ")}]"
            }
            val typeParamHead = "" // no PEP 695 inline generics
            val baseText = if (bases.isEmpty()) "" else "(${bases.joinToString(", ")})"
            "class ${t.simpleName}$typeParamHead$baseText:"
        }
        sb.appendLine(header)
        val indent = "    "

        // type docstring
        if (!t.doc.isNullOrBlank()) {
            appendDocString(t.doc, indent, sb)
        }

        if (t.kind == Kind.ENUM) {
            // Emit enum members
            // mypy expects assignment-style members (NAME = ...), not annotations.
            if (t.enumConstants.isEmpty()) {
                if (t.doc.isNullOrBlank()) {
                    sb.appendLine("${indent}pass")
                }
            } else {
                for (c in t.enumConstants) {
                    sb.appendLine("${indent}${c} = ...")
                }
            }
            return sb.toString()
        }

        // Is the type empty?
        if (t.constructors.isEmpty() && t.methods.isEmpty() && t.fields.isEmpty() && t.properties.isEmpty()) {
            if (t.doc.isNullOrBlank()) {
                sb.appendLine("${indent}pass")
            }
            return sb.toString()
        }

        // Fields (as attributes) for classes only.
        // If a property with the same name will be emitted, skip the raw field to avoid duplicate names.
        if (t.kind == Kind.CLASS) {
            val propNames = t.properties.map { it.name }.toSet()
            val methodNames = t.methods.map { safeIdentifier(it.name, allowSelf = true) }.toSet()
            for (f in t.fields) {
                // Skip raw field if a property or method with the same name exists to avoid duplicate symbol names.
                if (f.name in propNames) continue
                if (f.name in methodNames) continue
                sb.appendLine("${indent}${f.name}: ${f.type.render()}")
            }
        }

        if (t.kind == Kind.CLASS) {
            // Constructors: deduplicate identical signatures that can arise after Java->Python type mapping,
            // then order by specificity and drop dominated overloads.
            run {
                // Normalize to a signature key: rendered param types (including varargs marker) and return type (always None here).
                fun constructorSigKey(c: ConstructorIR): String {
                    val paramKey = c.params.joinToString(",") { p ->
                        val ty = p.type.render()
                        if (p.isVarargs) "*args:$ty" else ty
                    }
                    return "(__init__)($paramKey)->None"
                }

                val seen = LinkedHashSet<String>()
                val uniqueConstructors = mutableListOf<ConstructorIR>()
                for (c in t.constructors) {
                    val key = constructorSigKey(c)
                    if (seen.add(key)) uniqueConstructors += c
                }

                // Sort most specific first
                val ordered = uniqueConstructors.sortedWith(
                    compareBy(
                        { methodGeneralitySignature(it).joinToString(",") },
                        { it.params.size }
                    ))

                val filtered: List<ConstructorIR> = dropDominatedOverloads(ordered)
                for (c in filtered) {
                    val params = renderParams(c.params, includeSelf = true)
                    if (filtered.size > 1) sb.appendLine("${indent}@overload")
                    sb.append("${indent}def __init__($params) -> None:")
                    if (!c.doc.isNullOrBlank()) {
                        sb.appendLine()
                        appendIndentedDocStringAndPass(indent, c.doc, sb)
                    } else {
                        sb.appendLine(" ...")
                    }
                }
            }

            for (p in t.properties) {
                sb.appendLine("${indent}@property")
                sb.append("${indent}def ${p.name}(self) -> ${p.type.render()}:")
                if (!p.doc.isNullOrBlank()) {
                    sb.appendLine()
                    appendIndentedDocStringAndPass(indent, p.doc, sb)
                } else {
                    sb.appendLine(" ...")
                }
                if (!p.readOnly) {
                    sb.appendLine("${indent}@${p.name}.setter")
                    sb.appendLine("${indent}def ${p.name}(self, value: ${p.type.render()}) -> None: ...")
                }
            }
        }

        // Methods: group by (name, isStatic) for overloads
        val groups = t.methods.groupBy { it.name to it.isStatic }.toSortedMap(
            compareBy({ it.first }, { it.second })
        )

        // If both a static and an instance group exist for the same name, prefer the instance group.
        // Python typing cannot express both static and instance overloads sharing the same attribute name cleanly.
        val namesWithBoth = groups.keys.groupBy({ it.first }, { it.second })
            .filterValues { it.toSet().size > 1 }.keys

        for ((key, methods) in groups) {
            val isStatic = key.second
            if (key.first in namesWithBoth && isStatic) {
                // Skip static group when there is both static and instance of same name.
                continue
            }

            // Deduplicate identical overloads by normalized signature (post-mapping), keeping first doc found.
            // Build unique list preserving order of first occurrences.
            val unique: List<MethodIR> = run {
                fun methodSigKey(m: MethodIR): String {
                    val paramKey = m.params.joinToString(",") { p ->
                        val ty = p.type.render()
                        if (p.isVarargs) "*args:$ty" else ty
                    }
                    val ret = m.returnType.render()
                    val prefix = if (isStatic) "static" else "inst"
                    val safeName = safeIdentifier(m.name, allowSelf = true)
                    return "$prefix|$safeName|($paramKey)->$ret"
                }

                val seen = LinkedHashSet<String>()
                val out = ArrayList<MethodIR>(methods.size)
                for (m in methods) {
                    val key = methodSigKey(m)
                    if (seen.add(key)) {
                        out += m
                    }
                }
                // Sort by specificity: most specific first (lowest generality score lexicographically).
                out.sortedWith(
                    compareBy(
                        { methodGeneralitySignature(it).joinToString(",") }, // compare lists lexicographically via string
                        { it.params.size } // tie-breaker: more params first tends to be more specific
                    ))
            }

            // Filter out overloads dominated by an earlier (more specific) one.
            val filtered: List<MethodIR> = dropDominatedOverloads(unique)
            if (filtered.size > 1) {
                for (m in filtered) {
                    sb.appendLine("${indent}@overload")
                    appendMethodBody(isStatic, sb, indent, m)
                }
            } else {
                val m = filtered.firstOrNull() ?: continue
                appendMethodBody(isStatic, sb, indent, m)
            }
        }

        return sb.toString()
    }

    private fun <T : WithParamsIR> dropDominatedOverloads(ordered: List<T>): List<T> {
        val filtered = ArrayList<T>(ordered.size)
        outer@ for (c in ordered) {
            for (k in filtered) {
                if (methodDominates(k, c)) continue@outer
            }
            filtered += c
        }
        return filtered
    }

    private fun appendDocString(doc: String, indent: String, sb: StringBuilder) {
        val (delim, escaped) = chooseTripleQuoteAndEscape(doc)
        val lines = escaped.split('\n')
        val first = lines.firstOrNull() ?: ""
        val rest = if (lines.size > 1) {
            lines.drop(1).joinToString("\n") { ln -> if (ln.isEmpty()) "" else "$indent$ln" }
        } else ""
        val body = if (rest.isEmpty()) first else "$first\n$rest"
        sb.appendLine("${indent}$delim$body$delim")
    }

    private fun appendMethodBody(isStatic: Boolean, sb: StringBuilder, indent: String, m: MethodIR) {
        if (isStatic) sb.appendLine("${indent}@staticmethod")
        val params = renderParams(m.params, includeSelf = !isStatic)

        // Avoid shadowing built-in type names in class scope (e.g., a method named 'object' interfering with the 'object' type).
        fun avoidBuiltinTypeShadow(name: String): String {
            return when (name) {
                // Common built-in type names used in annotations
                "object", "str", "int", "float", "bool", "list", "dict", "set", "tuple" -> "${name}_"
                else -> name
            }
        }

        val defName = avoidBuiltinTypeShadow(safeIdentifier(m.name, allowSelf = true))
        // Adjust return type: replace TypeVars that don't appear in any parameter with Any to satisfy mypy.
        val adjustedRet: PyType = adjustReturnTypeTypeVars(m)
        if (!m.doc.isNullOrBlank()) {
            sb.appendLine("${indent}def ${defName}($params) -> ${adjustedRet.render()}:")
            appendDocString(m.doc, indent.repeat(2), sb)
            sb.appendLine("${indent.repeat(2)}...")
        } else {
            sb.appendLine("${indent}def ${defName}($params) -> ${adjustedRet.render()}: ...")
        }
    }

    private fun appendIndentedDocStringAndPass(indent: String, doc: String, sb: StringBuilder) {
        appendDocString(doc, indent.repeat(2), sb)
        sb.appendLine("${indent.repeat(2)}...")
    }

    // Replace in the return type any TypeVar that doesn't appear in parameters with Any,
    // to avoid mypy complaints about returning a TypeVar not bound by any argument.
    private fun adjustReturnTypeTypeVars(m: MethodIR): PyType {
        // Collect TypeVars present in parameters
        val tvInParams = mutableSetOf<String>()
        fun collect(pt: PyType) {
            pt.walk { node ->
                if (node is PyType.TypeVarRef) tvInParams += node.name
            }
        }
        for (p in m.params) collect(p.type)
        // Transform return type
        fun subst(pt: PyType): PyType {
            return when (pt) {
                is PyType.TypeVarRef -> {
                    if (pt.name in tvInParams) pt else PyType.AnyT
                }

                is PyType.Generic -> pt.copy(args = pt.args.map(::subst))
                is PyType.Abc -> pt.copy(args = pt.args.map(::subst))
                is PyType.Union -> pt.copy(items = pt.items.map(::subst))
                else -> pt
            }
        }
        return subst(m.returnType)
    }

    // Escape only those backslashes that would form invalid escape sequences in Python string literals.
    // This prevents "invalid escape sequence" warnings/errors in docstrings while preserving recognized
    // escapes like \n, \t, \\, \xNN, \uXXXX, \UXXXXXXXX, \N{...} and octal escapes.
    private fun escapeInvalidPythonEscapes(s: String): String {
        fun isHex(c: Char): Boolean = (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')
        val out = StringBuilder(s.length + 8)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            // Backslash at end -> escape it.
            if (i + 1 >= s.length) {
                out.append("\\\\")
                i++
                continue
            }
            val n = s[i + 1]
            when (n) {
                '\\', '\'', '"', 'a', 'b', 'f', 'n', 'r', 't', 'v' -> {
                    out.append('\\').append(n)
                    i += 2
                }
                // Hex escape: \xNN (exactly two hex digits)
                'x' -> {
                    if (i + 3 < s.length && isHex(s[i + 2]) && isHex(s[i + 3])) {
                        out.append("\\x").append(s[i + 2]).append(s[i + 3])
                        i += 4
                    } else {
                        out.append("\\\\").append('x')
                        i += 2
                    }
                }
                // Unicode escapes: \uXXXX and \UXXXXXXXX
                'u' -> {
                    if (i + 5 < s.length &&
                        isHex(s[i + 2]) && isHex(s[i + 3]) && isHex(s[i + 4]) && isHex(s[i + 5])
                    ) {
                        out.append("\\u")
                            .append(s[i + 2]).append(s[i + 3]).append(s[i + 4]).append(s[i + 5])
                        i += 6
                    } else {
                        out.append("\\\\").append('u')
                        i += 2
                    }
                }

                'U' -> {
                    if (i + 9 < s.length &&
                        (2..9).all { k -> isHex(s[i + k]) }
                    ) {
                        out.append("\\U")
                        for (k in 2..9) out.append(s[i + k])
                        i += 10
                    } else {
                        out.append("\\\\").append('U')
                        i += 2
                    }
                }
                // Named Unicode: \N{...}
                'N' -> {
                    if (i + 2 < s.length && s[i + 2] == '{') {
                        var j = i + 3
                        var found = false
                        while (j < s.length) {
                            if (s[j] == '}') {
                                found = true
                                break
                            }
                            j++
                        }
                        if (found) {
                            out.append("\\N")
                            out.append(s, i + 2, j + 1)
                            i = j + 1
                        } else {
                            out.append("\\\\").append('N')
                            i += 2
                        }
                    } else {
                        out.append("\\\\").append('N')
                        i += 2
                    }
                }
                // Octal escapes: up to 3 octal digits after backslash.
                in '0'..'7' -> {
                    var j = i + 1
                    var count = 0
                    while (j < s.length && count < 3 && s[j] in '0'..'7') {
                        j++; count++
                    }
                    out.append(s, i, j)
                    i = j
                }
                // Line continuation (backslash followed by newline) – leave intact.
                '\n', '\r' -> {
                    out.append('\\').append(n)
                    i += 2
                }

                else -> {
                    // Anything else would be an invalid escape; double the backslash.
                    out.append("\\\\").append(n)
                    i += 2
                }
            }
        }
        return out.toString()
    }

    // Choose a safe triple-quote delimiter based on content and escape embedded triples accordingly.
    private fun chooseTripleQuoteAndEscape(s: String): Pair<String, String> {
        val trimmedEnd = s.trimEnd()
        val endsWithDbl = trimmedEnd.endsWith('"')
        val hasTripleDbl = s.contains("\"\"\"")
        // Prefer double-quoted triple strings by default (matches tests/expectations).
        // Fall back to single-quoted triples if content ends with a double quote or contains """ blocks.
        val useSingle = hasTripleDbl || endsWithDbl
        val delim = if (useSingle) "'''" else "\"\"\""
        val escapedTriples = if (useSingle) {
            // Escape embedded triple-single-quotes
            s.replace("'''", "\\'\\'\\'")
        } else {
            // Escape embedded triple-double-quotes
            s.replace("\"\"\"", "\\\"\\\"\\\"")
        }
        // After handling quote delimiters, ensure no invalid escapes remain in content.
        val escaped = escapeInvalidPythonEscapes(escapedTriples)
        return delim to escaped
    }

    private fun renderParams(params: List<ParamIR>, includeSelf: Boolean): String {
        val items = mutableListOf<String>()
        if (includeSelf) items += "self"
        for (p in params) {
            items += if (p.isVarargs) {
                "*args: ${p.type.render()}"
            } else {
                "${p.name}: ${p.type.render()}"
            }
        }
        return items.joinToString(", ")
    }

    private fun anyInType(pt: PyType): Boolean {
        var found = false
        pt.walk { if (it === PyType.AnyT) found = true }
        return found
    }

    private fun numberInType(pt: PyType): Boolean {
        var found = false
        pt.walk { if (it === PyType.NumberT) found = true }
        return found
    }

    private fun objectInType(pt: PyType): Boolean {
        var found = false
        pt.walk { if (it === PyType.ObjectT) found = true }
        return found
    }

    private fun TypeIR.needsAnyImport(): Boolean {
        // Base scan for Any present anywhere in the type signatures.
        val base = fields.any { anyInType(it.type) } ||
                constructors.any { it.params.any { p -> anyInType(p.type) } } ||
                methods.any { anyInType(it.returnType) || it.params.any { p -> anyInType(p.type) } } ||
                properties.any { anyInType(it.type) } ||
                typeParams.any { it.bound?.let { b -> anyInType(b) } == true }
        if (base) return true
        // Extra: our emission replaces return-only TypeVars with Any to satisfy mypy.
        // If a method's return type references a TypeVar that's not present in any parameter, we need Any imported.
        fun tvsIn(t: PyType): Set<String> {
            val out = mutableSetOf<String>()
            t.walk { node -> if (node is PyType.TypeVarRef) out += node.name }
            return out
        }
        for (m in methods) {
            val retTVs = tvsIn(m.returnType)
            if (retTVs.isEmpty()) continue
            val paramsTVs = m.params.flatMap { tvsIn(it.type) }.toSet()
            if ((retTVs - paramsTVs).isNotEmpty()) return true
        }
        return false
    }

    private fun TypeIR.needsNumberImport(): Boolean =
        fields.any { numberInType(it.type) } ||
                constructors.any { it.params.any { p -> numberInType(p.type) } } ||
                methods.any { numberInType(it.returnType) || it.params.any { p -> numberInType(p.type) } } ||
                properties.any { numberInType(it.type) } ||
                typeParams.any { it.bound?.let { b -> numberInType(b) } == true }

    private fun TypeIR.needsBuiltinsImport(): Boolean =
        fields.any { objectInType(it.type) } ||
                constructors.any { it.params.any { p -> objectInType(p.type) } } ||
                methods.any { objectInType(it.returnType) || it.params.any { p -> objectInType(p.type) } } ||
                properties.any { objectInType(it.type) } ||
                typeParams.any { it.bound?.let { b -> objectInType(b) } == true }

    private fun TypeIR.needsOverloadImport(): Boolean {
        // Overload needed if any method group has >1 entries or multiple constructors
        if (kind == Kind.CLASS && constructors.size > 1) return true
        val groups = methods.groupBy { it.name to it.isStatic }
        return groups.values.any { it.size > 1 }
    }

    private fun TypeIR.collectionsAbcImports(): Set<String> {
        val names = linkedSetOf<String>()
        fun collect(pt: PyType) {
            pt.walk {
                if (it is PyType.Abc) names += it.name
            }
        }
        collectAllMembers(this, ::collect)
        return names
    }

    // Collect referenced declared types (PyType.Ref) used by this type, to emit imports.
    private fun referencedDeclaredTypes(t: TypeIR): Set<PyType.Ref> {
        val refs = linkedSetOf<PyType.Ref>()
        fun collect(pt: PyType) {
            pt.walk {
                if (it is PyType.Ref) refs += it
            }
        }
        collectAllMembers(t, ::collect)
        return refs
    }

    private fun collectAllMembers(t: TypeIR, function: (pt: PyType) -> Unit) {
        t.fields.forEach { function(it.type) }
        t.constructors.forEach { it.params.forEach { p -> function(p.type) } }
        t.methods.forEach { m ->
            function(m.returnType)
            m.params.forEach { p -> function(p.type) }
        }
        t.properties.forEach { function(it.type) }
        t.typeParams.forEach { it.bound?.let { b -> function(b) } }
    }

    // No per-class runtime shims are emitted; runtime names are exposed in package __init__.py
}

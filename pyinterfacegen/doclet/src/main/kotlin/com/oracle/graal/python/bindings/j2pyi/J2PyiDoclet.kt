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
import kotlin.collections.iterator

class J2PyiDoclet : Doclet {
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

        // Ensure runtime __init__.py in each leaf package dir that contains stubs. Keep __init__.pyi minimal if missing.
        fun ensureFile(path: File, content: String) {
            if (!path.parentFile.exists()) path.parentFile.mkdirs()
            if (!path.exists()) path.writeText(content)
        }

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
        val pkgInitContent = (
            """
            |# Auto-generated by j2pyi.
            |# This file makes this a regular Python package for packaging and type checkers.
            |from __future__ import annotations
            |# Runtime note: This package contains type stubs for GraalPy interop.
            """.trimMargin() + "\n"
        )

        val leafPkgNames: Set<String> = pkgToTypes.keys.filter { it.isNotBlank() }.toSortedSet()
        // Ancestor packages that should exist as Python packages (even if no direct types there)
        val allPkgsNeeded: Set<String> = buildSet {
            addAll(leafPkgNames)
            for (p in leafPkgNames) addAll(ancestorPackages(p, includeSelf = false))
        }.filter { it.isNotBlank() }.toSortedSet()
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

            // Good to synthesize
            val readOnly = compatibleSetters.isEmpty()
            props += PropertyIR(propName, gType, readOnly, gDoc)
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
        return if (raw.isBlank() || raw == "self") "p" else raw
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
        return if (simple.isBlank()) PyType.AnyT else PyType.Ref(pkg, simple)
    }

    private fun mapDeclaredType(dt: DeclaredType): PyType {
        val el = dt.asElement() as? TypeElement ?: return PyType.AnyT
        val qn = el.qualifiedName.toString()
        val targs = dt.typeArguments
        fun argOrAny(i: Int): PyType = targs.getOrNull(i)?.let { mapType(it) } ?: PyType.AnyT
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
                val pkg = packageOf(el)
                val name = el.simpleName.toString()
                PyType.Ref(pkg, name)
            }
        }
    }

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
        // Python 3.12+: No module-level TypeVar declarations; generics are declared inline on the class/function.
        // Build class header with Protocol/Enum bases and PEP 695 inline generics.
        val header = run {
            val bases = mutableListOf<String>()
            when (t.kind) {
                Kind.INTERFACE -> if (config.interfaceAsProtocol) bases += "Protocol"
                Kind.ENUM -> bases += "Enum"
                else -> {}
            }
            // Inline type parameter list (skip for enums)
            val typeParamHead = if (hasTypeParams && t.kind != Kind.ENUM) {
                val parts = t.typeParams.map { tp ->
                    val b = tp.bound?.render()
                    if (b.isNullOrBlank()) tp.name else "${tp.name}: $b"
                }
                "[${parts.joinToString(", ")}]"
            } else ""
            val baseText = if (bases.isEmpty()) "" else "(${bases.joinToString(", ")})"
            "class ${t.simpleName}$typeParamHead$baseText:"
        }
        sb.appendLine(header)
        val indent = "    "

        // type docstring
        if (!t.doc.isNullOrBlank()) {
            val body = escapeTripleQuotes(t.doc).replace("\n", "\n$indent")
            sb.appendLine("${indent}\"\"\"$body\"\"\"")
        }

        if (t.kind == Kind.ENUM) {
            // Emit enum members
            if (t.enumConstants.isEmpty()) {
                if (t.doc.isNullOrBlank()) {
                    sb.appendLine("${indent}pass")
                }
            } else {
                for (c in t.enumConstants) {
                    // Stub-style: member name as attribute of the enum type
                    sb.appendLine("${indent}${c}: ${t.simpleName}")
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

        // Fields (as attributes) for classes only
        if (t.kind == Kind.CLASS) {
            for (f in t.fields) {
                sb.appendLine("${indent}${f.name}: ${f.type.render()}")
            }
        }

        if (t.kind == Kind.CLASS) {
            for (c in t.constructors) {
                val params = renderParams(c.params, includeSelf = true)
                if (t.constructors.size > 1) sb.appendLine("${indent}@overload")
                sb.append("${indent}def __init__($params) -> None:")
                if (!c.doc.isNullOrBlank()) {
                    sb.appendLine()
                    appendIndentedDocStringAndPass(indent, c.doc, sb)
                } else {
                    sb.appendLine(" ...")
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
        for ((key, methods) in groups) {
            val isStatic = key.second
            // Stable order: by param count then by rendered signature text
            val sorted = methods.sortedWith(
                compareBy({ it.params.size }, { renderParams(it.params, includeSelf = !isStatic) })
            )
            for (m in sorted) {
                if (sorted.size > 1) sb.appendLine("${indent}@overload")
                appendMethodBody(isStatic, sb, indent, m)
            }
        }

        return sb.toString()
    }

    private fun appendMethodBody(isStatic: Boolean, sb: StringBuilder, indent: String, m: MethodIR) {
        if (isStatic) sb.appendLine("${indent}@staticmethod")
        val params = renderParams(m.params, includeSelf = !isStatic)
        if (!m.doc.isNullOrBlank()) {
            sb.appendLine("${indent}def ${m.name}($params) -> ${m.returnType.render()}:")
            run {
                val innerIndent = indent.repeat(2)
                val body = escapeTripleQuotes(m.doc).replace("\n", "\n$innerIndent")
                sb.appendLine("${innerIndent}\"\"\"$body\"\"\"")
            }
            sb.appendLine("${indent.repeat(2)}...")
        } else {
            sb.appendLine("${indent}def ${m.name}($params) -> ${m.returnType.render()}: ...")
        }
    }

    private fun appendIndentedDocStringAndPass(indent: String, doc: String, sb: StringBuilder) {
        val innerIndent = indent.repeat(2)
        val body = escapeTripleQuotes(doc).replace("\n", "\n$innerIndent")
        sb.appendLine("${innerIndent}\"\"\"$body\"\"\"")
        sb.appendLine("${indent.repeat(2)}...")
    }

    private fun escapeTripleQuotes(s: String): String =
        s.replace("\"\"\"", "\\\"\\\"\\\"")

    private fun renderParams(params: List<ParamIR>, includeSelf: Boolean): String {
        val items = mutableListOf<String>()
        if (includeSelf) items += "self"
        for (p in params) {
            if (p.isVarargs) {
                items += "*args: ${p.type.render()}"
            } else {
                items += "${p.name}: ${p.type.render()}"
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

    private fun TypeIR.needsAnyImport(): Boolean =
        fields.any { anyInType(it.type) } ||
                constructors.any { it.params.any { p -> anyInType(p.type) } } ||
                methods.any { anyInType(it.returnType) || it.params.any { p -> anyInType(p.type) } } ||
                properties.any { anyInType(it.type) } ||
                typeParams.any { it.bound?.let { b -> anyInType(b) } == true }

    private fun TypeIR.needsNumberImport(): Boolean =
        fields.any { numberInType(it.type) } ||
                constructors.any { it.params.any { p -> numberInType(p.type) } } ||
                methods.any { numberInType(it.returnType) || it.params.any { p -> numberInType(p.type) } } ||
                properties.any { numberInType(it.type) } ||
                typeParams.any { it.bound?.let { b -> numberInType(b) } == true }

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

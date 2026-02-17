package org.graalvm.python.pyinterfacegen

// Prefer imports to fully qualified calls for helpers from the same package.
import com.sun.source.util.DocTrees
import jdk.javadoc.doclet.Doclet
import jdk.javadoc.doclet.DocletEnvironment
import jdk.javadoc.doclet.Reporter
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.*
import java.util.regex.Pattern
import javax.lang.model.SourceVersion
import javax.lang.model.element.*
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter

class J2PyiDoclet : Doclet {
    // Packages for which we are actually generating stubs in this run.
    // Used as the default for which references can safely be emitted as imports.
    private var allowedPkgs: Set<String> = emptySet()

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
        // Additional package prefixes to treat as "platform" (i.e., not mapped as Ref/imported).
        // Users can extend the default set (java., javax., jdk., org.w3c., org.xml., org.omg., org.ietf.)
        // via -Xj2pyi-extraPlatformPackages.

        val extraPlatformPackages: MutableList<String> = mutableListOf(),
        // Additional Java package names assumed to have .pyi stubs elsewhere.
        // When empty, only packages emitted by the current doclet run are assumed typed.
        // Values can be specified via globs and/or regexes.
        val assumedTypedPackageGlobs: MutableList<String> = mutableListOf(),
        val assumedTypedPackageRegexes: MutableList<String> = mutableListOf(),

        // Pre-compiled versions of the above, rebuilt each run after option parsing.
        var assumedTypedPkgGlobMatchers: List<PathMatcher> = emptyList(),
        var assumedTypedPkgRegexes: List<Pattern> = emptyList(),
        var moduleName: String? = null,
        var moduleVersion: String = "0.1.0"
    )

    private val config: Config = Config()

    private var reporter: Reporter? = null
    private var outputDir: String? = null
    private var docTrees: DocTrees? = null

    override fun init(locale: Locale, reporter: Reporter) {
        this.reporter = reporter
    }

    override fun getName(): String = "j2pyi"

    override fun run(environment: DocletEnvironment): Boolean {
        this.docTrees = environment.docTrees

        compileAssumedTypedPkgMatchers()

        // Build an intermediate representation for all included types (classes, interfaces, enums) honoring include/exclude and visibility.
        val typeIRs = environment.includedElements
            .asSequence()
            .filterIsInstance<TypeElement>()
            .filter { it.kind == ElementKind.CLASS || it.kind == ElementKind.INTERFACE || it.kind == ElementKind.ENUM }
            .filter { shouldIncludeType(it) }
            .mapNotNull { maybeBuildTypeIR(it) }
            .sortedBy { it.qualifiedName }
            .toList()

        // Determine output directory. Respect -d if provided; otherwise default to build/pyi.
        val baseOut = File(outputDir ?: "build/pyi")
        baseOut.mkdirs()

        if (typeIRs.isEmpty()) {
            return true
        }

        // Record the set of packages for which we will emit stubs; used to avoid importing external refs.
        allowedPkgs = typeIRs.map { it.packageName }.toSet()

        // Emit one .pyi module per top-level type and collect
        // package contents for __init__ re-exports and runtime symbols.
        // Map: package -> (simpleName -> fullyQualifiedName)
        val pkgToTypes = mutableMapOf<String, MutableMap<String, String>>()
        for (t: TypeIR in typeIRs) {
            val mappedPkg = mapPackage(t.packageName)
            val pkgDir = packageDir(baseOut, mappedPkg)
            pkgDir.mkdirs()
            // Type stubs (scrub references to external packages to builtins.object)
            val text = emitTypeAsPyi(scrubExternalRefs(t))
            // Avoid unused typing import if no overloads are present
            val cleaned = if (!text.contains("@overload")) {
                text.lineSequence()
                    .dropWhile { it.isBlank() }
                    .let { seq ->
                        val lines = seq.toList()
                        if (lines.firstOrNull()?.trim() == "from typing import overload") {
                            (lines.drop(1)).joinToString("\n")
                        } else {
                            text
                        }
                    }
            } else {
                text
            }
            File(pkgDir, "${t.simpleName}.pyi").writeText(cleaned)
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

    override fun getSupportedOptions(): MutableSet<out Doclet.Option> = mutableSetOf(
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
        stringOption(
            "-Xj2pyi-include",
            "<prefixes>",
            "Comma-separated package or qualified-name prefixes to include."
        ) {
            config.includePrefixes.clear()
            config.includePrefixes.addAll(splitCsv(it))
            true
        },
        stringOption(
            "-Xj2pyi-exclude",
            "<prefixes>",
            "Comma-separated package or qualified-name prefixes to exclude."
        ) {
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
        flagOption(
            "-Xj2pyi-noInterfaceProtocol",
            "Do not emit interfaces as typing.Protocol (treat as plain classes)."
        ) {
            config.interfaceAsProtocol = false
            true
        },
        stringOption(
            "-Xj2pyi-propertySynthesis",
            "<true|false>",
            "Synthesize @property from getters/setters (default true)."
        ) {
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
        stringOption(
            "-Xj2pyi-packageMap",
            "<javaPkg=pyPkg[,more...]>",
            "Map Java package prefixes to Python package prefixes (CSV)."
        ) {
            config.packagePrefixMap.clear()
            config.packagePrefixMap.addAll(parsePackageMap(it, reporter))
            true
        },
        stringOption(
            "-Xj2pyi-nullabilityMode",
            "<annotations|conservative|aggressive>",
            "Nullability mode (currently informational)."
        ) {
            config.nullabilityMode = it
            true
        },
        stringOption(
            "-Xj2pyi-nullabilityExtra",
            "<prefixes>",
            "Comma-separated additional nullability annotation package prefixes."
        ) {
            config.nullabilityExtra.clear()
            config.nullabilityExtra.addAll(splitCsv(it))
            true
        },
        // Let users extend the set of platform packages that are mapped to builtins.object instead of emitting Refs/imports.
        // This is useful when dependencies reference external packages the user doesn't want to map/emit imports for.
        stringOption(
            "-Xj2pyi-extraPlatformPackages",
            "<prefixes>",
            "Comma-separated package or qualified-name prefixes to treat as platform (not mapped/imported)."
        ) {
            config.extraPlatformPackages.clear()
            config.extraPlatformPackages.addAll(splitCsv(it))
            true
        },
        stringOption(
            "-Xj2pyi-assumedTypedPackageGlobs",
            "<globs>",
            "Comma-separated glob patterns for Java package names assumed to have .pyi stubs elsewhere."
        ) {
            config.assumedTypedPackageGlobs.clear()
            config.assumedTypedPackageGlobs.addAll(splitCsv(it))
            true
        },
        stringOption(
            "-Xj2pyi-assumedTypedPackageRegexes",
            "<regexes>",
            "Comma-separated regexes for Java package names assumed to have .pyi stubs elsewhere."
        ) {
            config.assumedTypedPackageRegexes.clear()
            config.assumedTypedPackageRegexes.addAll(splitCsv(it))
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
        stringOption(
            "-Xj2pyi-moduleVersion",
            "<version>",
            "Version string for assembled Python module (default 0.1.0)."
        ) {
            config.moduleVersion = it
            true
        })

    // Helpers for extended options
    private fun stringOption(
        name: String, param: String, desc: String, handler: (String) -> Boolean
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

    private fun splitCsv(s: String): List<String> = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun compileAssumedTypedPkgMatchers() {
        config.assumedTypedPkgGlobMatchers = config.assumedTypedPackageGlobs.map { glob ->
            val normalized = glob.trim().replace('.', '/')
            FileSystems.getDefault().getPathMatcher("glob:$normalized")
        }

        config.assumedTypedPkgRegexes = config.assumedTypedPackageRegexes.map { rx ->
            try {
                Pattern.compile(rx)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid -Xj2pyi-assumedTypedPackageRegexes entry: '$rx'", e)
            }
        }
    }

    private fun isAssumedTypedPackage(javaPkg: String): Boolean {
        // Default: only the packages generated in the current run.
        if (allowedPkgs.contains(javaPkg)) return true
        if (config.assumedTypedPkgGlobMatchers.isEmpty() && config.assumedTypedPkgRegexes.isEmpty()) return false

        // Globs are matched against the full Java package name, e.g. "org.example.foo".
        // Use '/' normalization to reuse file glob matching.
        val pkgPath = javaPkg.replace('.', '/')
        if (config.assumedTypedPkgGlobMatchers.any { it.matches(Path.of(pkgPath)) }) {
            return true
        }
        return config.assumedTypedPkgRegexes.any { it.matcher(javaPkg).matches() }
    }

    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()

    // Thin wrappers to bind config to top-level helpers.
    private fun mapPackage(javaPkg: String): String = mapPackage(javaPkg, config.packagePrefixMap)
    private fun isFullyQualifiedNameAJDKType(qn: String): Boolean =
        isFullyQualifiedNameAJDKType(qn, config.extraPlatformPackages)

    private fun mapType(t: TypeMirror): PyType = mapType(t, config.extraPlatformPackages)
    private fun mapReturnTypeWithNullability(m: ExecutableElement): PyType =
        mapReturnTypeWithNullability(m, config.nullabilityExtra, config.extraPlatformPackages)

    private fun mapParamTypeWithNullability(p: VariableElement, overrideType: TypeMirror? = null): PyType =
        mapParamTypeWithNullability(p, config.nullabilityExtra, config.extraPlatformPackages, overrideType)

    private fun mapFieldTypeWithNullability(f: VariableElement): PyType =
        mapFieldTypeWithNullability(f, config.nullabilityExtra, config.extraPlatformPackages)

    private fun assemblePythonModule(stubOutDir: File, pkgToTypes: Map<String, Map<String, String>>) =
        assemblePythonModule(stubOutDir, pkgToTypes, config.moduleName, config.moduleVersion, reporter)

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
        val matchesInclude = if (config.includePrefixes.isEmpty()) true
        else config.includePrefixes.any { qn.startsWith(it) || pkg.startsWith(it) }
        val matchesExclude = config.excludePrefixes.any { qn.startsWith(it) || pkg.startsWith(it) }
        val visible = isIncludedByVisibility(te)
        return matchesInclude && !matchesExclude && visible
    }

    private fun maybeBuildTypeIR(te: TypeElement): TypeIR? {
        if (!isIncludedByVisibility(te)) return null

        val pkg = packageOf(te)
        val kind = when (te.kind) {
            ElementKind.INTERFACE -> Kind.INTERFACE
            ElementKind.ENUM -> Kind.ENUM
            else -> Kind.CLASS
        }
        fun isThrowableBound(tm: TypeMirror?): Boolean {
            if (tm == null) return false
            val decl = (tm as? DeclaredType)?.asElement() as? TypeElement
            val qn = decl?.qualifiedName?.toString() ?: return false
            return qn == "java.lang.Throwable" || qn == "java.lang.Exception" || qn == "java.lang.RuntimeException"
        }
        // Collect type parameters with simple upper bounds (first non-Object bound only).
        // Sanitize names to avoid stray whitespace that can lead to malformed TypeVar declarations.
        val typeParams: List<TypeParamIR> = te.typeParameters.map { tp ->
            val name = tp.simpleName.toString().trim()
            // Python has no notion of generic exceptions. Many Java libraries use a type parameter solely to model
            // the thrown exception type (e.g. <E extends Throwable>). Including such a parameter in a Protocol
            // causes mypy variance errors, and it doesn't add useful information for Python users.
            // So, drop type parameters that are bounded directly by Throwable/Exception.
            if (tp.bounds.any { isThrowableBound(it) }) {
                return@map null
            }
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
        }.filterNotNull()
        val typeDoc: String? = docTrees?.javadocFull(te)
        val fields = if (kind == Kind.INTERFACE) {
            emptyList()
        } else {
            ElementFilter.fieldsIn(te.enclosedElements)
                .filter { isIncludedByVisibility(it) && it.kind != ElementKind.ENUM_CONSTANT }
                .sortedBy { it.simpleName.toString() }
                .map { f -> FieldIR(f.simpleName.toString(), mapFieldTypeWithNullability(f)) }
        }

        val constructors = if (kind == Kind.CLASS) {
            ElementFilter.constructorsIn(te.enclosedElements).filter { isIncludedByVisibility(it) }
                .sortedBy { it.parameters.joinToString(",") { p -> p.asType().toString() } }.map { c ->
                    val isVar = c.isVarArgs
                    val params = paramsToIR(c, isVar)
                    ConstructorIR(params = params, doc = docTrees?.javadocFull(c))
                }
        } else {
            emptyList()
        }

        // Collect methods
        val allMethods = ElementFilter.methodsIn(te.enclosedElements).filter { isIncludedByVisibility(it) }
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
        val (properties, remainingMethods) = if (kind == Kind.CLASS && config.propertySynthesis) synthesizeProperties(
            fields,
            allMethods,
            mappedMethods
        )
        else Pair(emptyList(), mappedMethods)

        // Enum constants (names only)
        val enumConstants = if (kind == Kind.ENUM) {
            te.enclosedElements.filter { it.kind == ElementKind.ENUM_CONSTANT }.map { it.simpleName.toString() }
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
        fields: List<FieldIR>, rawMethods: List<ExecutableElement>, mappedMethods: List<MethodIR>
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
            val t: PyType = if (isVarargs) {
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

    // Emit a single type as .pyi text (class, interface-as-Protocol, or enum)
    private fun emitTypeAsPyi(t: TypeIR): String {
        // After platform-type scrubbing (e.g. mapping java.lang.reflect.Type -> builtins.object), some Java generic
        // parameters may no longer appear anywhere in the exposed Python types. Keeping such "phantom" type
        // parameters causes mypy variance errors for Protocols and adds noise for users, so drop them.
        if (t.typeParams.isNotEmpty()) {
            val used = mutableSetOf<String>()
            fun walk(py: PyType) {
                py.walk { node ->
                    if (node is PyType.TypeVarRef) used += node.name
                }
            }
            for (f in t.fields) walk(f.type)
            for (c in t.constructors) for (p in c.params) walk(p.type)
            for (m in t.methods) {
                walk(m.returnType)
                for (p in m.params) walk(p.type)
            }
            for (p in t.properties) walk(p.type)

            val filtered = t.typeParams.filter { it.name in used }
            if (filtered.size != t.typeParams.size) {
                return emitTypeAsPyi(t.copy(typeParams = filtered))
            }
        }

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

            val referenced = mutableSetOf<String>()
            // Variance inference with invariant container awareness:
            // - track appearances in return vs parameter positions
            // - if a TypeVar occurs within an invariant generic (list/set/dict), it must be treated as invariant
            val seenInReturn = mutableSetOf<String>()
            val seenInParam = mutableSetOf<String>()
            val seenInvariant = mutableSetOf<String>()

            fun collectVariance(py: PyType, retLike: Boolean, invariantCtx: Boolean) {
                when (py) {
                    is PyType.TypeVarRef -> {
                        val base = py.name
                        // FIXME: Check if/when this extra splitting and sanitization actually triggers.
                        val nm = base.split(Regex("\\s+")).lastOrNull()?.replace(Regex("[^A-Za-z0-9_]"), "") ?: base
                        if (nm.isNotEmpty()) {
                            referenced += nm
                            if (retLike) seenInReturn += nm else seenInParam += nm
                            if (invariantCtx) seenInvariant += nm
                        }
                    }

                    is PyType.Generic -> {
                        // list/set/dict are invariant in typing
                        val inv = invariantCtx || (py.name == "list" || py.name == "set" || py.name == "dict")
                        for (a in py.args) {
                            collectVariance(a, retLike, inv)
                        }
                    }

                    is PyType.Abc -> {
                        // abc types like Sequence/Iterable/Iterator/Collection are covariant; keep context
                        for (a in py.args) {
                            collectVariance(a, retLike, invariantCtx)
                        }
                    }

                    is PyType.Union -> {
                        for (a in py.items) {
                            collectVariance(a, retLike, invariantCtx)
                        }
                    }

                    else -> {} // Any/None/Ref: nothing to do
                }
            }

            // Fields (attributes) – treat as "return-like" usage for variance purposes.
            for (f: FieldIR in t.fields) collectVariance(f.type, retLike = true, invariantCtx = false)
            // Constructors – parameters only
            for (c: ConstructorIR in t.constructors) {
                for (p: ParamIR in c.params) collectVariance(p.type, retLike = false, invariantCtx = false)
            }
            // Methods – collect both return and param appearances
            for (m: MethodIR in t.methods) {
                collectVariance(m.returnType, retLike = true, invariantCtx = false)
                for (p in m.params) collectVariance(p.type, retLike = false, invariantCtx = false)
            }
            // Properties – getter return is "return-like"; setter value is "param-like"
            for (p: PropertyIR in t.properties) {
                collectVariance(p.type, retLike = true, invariantCtx = false)
                if (!p.readOnly) collectVariance(p.type, retLike = false, invariantCtx = false)
            }
            val toDeclare = (declared + referenced).toMutableSet()
            if (toDeclare.isNotEmpty()) {
                fun inferVariance(
                    name: String,
                    seenInReturn: Set<String>,
                    seenInParam: Set<String>,
                    seenInvariant: Set<String>
                ): String? {
                    // If a TypeVar appears within an invariant container (e.g. list/set/dict), it must be invariant
                    // regardless of positional usage.
                    if (name in seenInvariant) return null
                    // For Protocols (PEP 544), use position-only rules:
                    // - only in params  -> contravariant
                    // - only in returns -> covariant
                    // - unused          -> invariant (no variance arg)
                    // - both            -> invariant (no variance arg)
                    val inRet = name in seenInReturn
                    val inPar = name in seenInParam
                    var result: String? = when {
                        inPar && !inRet -> "contravariant=True"
                        inRet && !inPar -> "covariant=True"
                        else -> null
                    }
                    // Fallback: if Protocol variance is requested but inference yielded null (i.e., invariant or both),
                    // apply a simple rule only when not seen in invariant context: param-only -> contravariant,
                    // return-only -> covariant.
                    if (result == null && name !in seenInvariant) {
                        val inRet = name in seenInReturn
                        val inPar = name in seenInParam
                        result = when {
                            inPar && !inRet -> "contravariant=True"
                            inRet && !inPar -> "covariant=True"
                            else -> null
                        }
                    }
                    return result
                }

                sb.appendLine("from typing import TypeVar")
                // Emit declared class type params first (preserve bounds), then remaining refs unbounded
                for (tp: TypeParamIR in t.typeParams) {
                    val bound = tp.bound?.render()
                    val name = tp.name
                    // Infer variance only for interfaces being emitted as Protocols (PEP 544 requires consistency).
                    val varianceArg: String? = if (t.kind == Kind.INTERFACE && config.interfaceAsProtocol)
                        inferVariance(name, seenInReturn, seenInParam, seenInvariant)
                    else
                        null
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
                for (name in toDeclare.filter { it.isNotEmpty() }.toSortedSet()) {
                    val varianceArg: String? = if (t.kind == Kind.INTERFACE && config.interfaceAsProtocol)
                        inferVariance(name, seenInReturn, seenInParam, seenInvariant)
                    else
                        null
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
                val ordered =
                    uniqueConstructors.sortedWith(
                        compareBy(
                            { methodGeneralitySignature(it).joinToString(",") },
                            { it.params.size })
                    )

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

        // Methods: group by (name, isStatic) for overloads. If both static and instance
        // methods exist with the same Java name, prefer emitting ONLY the static group.
        // Python can't have both an instance method and a staticmethod with the same name
        // at class scope (the later overwrites the former), and mypy treats adjacent
        // overloads of the same name as one set that must consistently use @staticmethod.
        // Emitting both would therefore either shadow one another or cause mypy errors like:
        //   - "Name 'foo' already defined"
        //   - "Overload does not consistently use the '@staticmethod' decorator"
        // Resolve this by dropping the instance group when a static group exists.
        val groups = t.methods.groupBy { it.name to it.isStatic }.toSortedMap(
            compareBy({ it.first }, { it.second })
        )
        val staticNames = t.methods.asSequence().filter { it.isStatic }.map { it.name }.toSet()

        // Keep return-only TypeVars for Protocols so variance inference sees producer positions.
        val keepProtocolReturnTypeVars = (t.kind == Kind.INTERFACE && config.interfaceAsProtocol)
        for ((key, methods) in groups) {
            val isStatic = key.second
            // If a static group exists for this name, skip the instance group to avoid conflicts.
            if (!isStatic && key.first in staticNames) {
                continue
            }

            // Deduplicate identical overloads by normalized signature (post-mapping), keeping first doc found.
            // Then sort: most specific first, broad varargs last.
            val orderedUnique: List<MethodIR> = run {
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
                // Sort by specificity: most specific first, non-varargs before varargs, and specific varargs before Any/object varargs.
                fun isBroadVarargs(m: MethodIR): Boolean {
                    val last = m.params.lastOrNull() ?: return false
                    if (!last.isVarargs) return false
                    val ty = last.type.render()
                    return ty == "builtins.object" || ty == "Any" || ty == "builtins.object | None" || ty == "Any | None"
                }
                out.sortedWith(
                    compareBy<MethodIR>(
                        { methodGeneralitySignature(it).joinToString(",") }
                    )
                        .thenByDescending { it.params.size }
                        .thenBy { if (it.params.lastOrNull()?.isVarargs == true) 1 else 0 }
                        .thenBy { if (isBroadVarargs(it)) 1 else 0 }
                )
            }

            // Filter out overloads dominated by an earlier (more specific) one.
            var filtered: List<MethodIR> = dropDominatedOverloads(orderedUnique)

            // Keep emission order consistent with our sort (specific first; non-varargs before varargs; broad varargs last)
            fun isBroadVarargsForSort(m: MethodIR): Int {
                val last = m.params.lastOrNull() ?: return 0
                val ty = last.type.render()
                return if (last.isVarargs && (ty == "builtins.object" || ty == "Any" || ty == "builtins.object | None" || ty == "Any | None")) 1 else 0
            }
            filtered = filtered.sortedWith(
                compareBy<MethodIR> { methodGeneralitySignature(it).joinToString(",") }
                    .thenByDescending { it.params.size }
                    .thenBy { if (it.params.lastOrNull()?.isVarargs == true) 1 else 0 }
                    .thenBy { isBroadVarargsForSort(it) }
            )
            if (filtered.size > 1) {
                for (m in filtered) {
                    sb.appendLine("${indent}@overload")
                    appendMethodBody(isStatic, sb, indent, m, keepProtocolReturnTypeVars && !isStatic)
                }
            } else {
                val m = filtered.firstOrNull() ?: continue
                appendMethodBody(isStatic, sb, indent, m, keepProtocolReturnTypeVars && !isStatic)
            }
        }

        return sb.toString()
    }

    private fun appendMethodBody(
        isStatic: Boolean,
        sb: StringBuilder,
        indent: String,
        m: MethodIR,
        keepUnboundReturnTypeVars: Boolean
    ) {
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
        // Adjust return type: normally replace return-only TypeVars with Any; but keep them for Protocols.
        val adjustedRet: PyType = if (keepUnboundReturnTypeVars) m.returnType else adjustReturnTypeTypeVars(m)
        if (!m.doc.isNullOrBlank()) {
            sb.appendLine("${indent}def ${defName}($params) -> ${adjustedRet.render()}:")
            appendDocString(m.doc, indent.repeat(2), sb)
            sb.appendLine("${indent.repeat(2)}...")
        } else {
            sb.appendLine("${indent}def ${defName}($params) -> ${adjustedRet.render()}: ...")
        }
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
        val base =
            fields.any { anyInType(it.type) } || constructors.any { it.params.any { p -> anyInType(p.type) } } || methods.any {
                anyInType(it.returnType) || it.params.any { p -> anyInType(p.type) }
            } || properties.any { anyInType(it.type) } || typeParams.any { it.bound?.let { b -> anyInType(b) } == true }
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
        fields.any { numberInType(it.type) } || constructors.any { it.params.any { p -> numberInType(p.type) } } || methods.any {
            numberInType(it.returnType) || it.params.any { p -> numberInType(p.type) }
        } || properties.any { numberInType(it.type) } || typeParams.any { it.bound?.let { b -> numberInType(b) } == true }

    private fun TypeIR.needsBuiltinsImport(): Boolean =
        fields.any { objectInType(it.type) } || constructors.any { it.params.any { p -> objectInType(p.type) } } || methods.any {
            objectInType(it.returnType) || it.params.any { p -> objectInType(p.type) }
        } || properties.any { objectInType(it.type) } || typeParams.any { it.bound?.let { b -> objectInType(b) } == true }

    private fun TypeIR.needsOverloadImport(): Boolean {
        // Constructors: overload import needed if more than one ctor remains.
        if (kind == Kind.CLASS && constructors.size > 1) return true
        // Methods: account for our rule that when both static and instance methods exist with the same name,
        // we emit only the static group. Compute effective groups after this rule, then check for >1 per group.
        val byKind = methods.groupBy { it.name to it.isStatic }
        val staticNames = methods.asSequence().filter { it.isStatic }.map { it.name }.toSet()
        // Drop instance groups if a static group of the same name exists.
        val effectiveGroups = byKind.filterKeys { (name, isStatic) -> isStatic || name !in staticNames }
        return effectiveGroups.values.any { it.size > 1 }
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
                if (it is PyType.Ref) {
                    // Only import types that are within the set of packages we are emitting.
                    if (!isFullyQualifiedNameAJDKType(it.packageName) && isAssumedTypedPackage(it.packageName)) {
                        refs += it
                    }
                }
            }
        }
        collectAllMembers(t, ::collect)
        return refs
    }

    // Replace PyType.Ref pointing to external packages with builtins.object to avoid missing imports.
    private fun scrubExternalRefs(t: TypeIR): TypeIR {
        fun scrub(pt: PyType): PyType {
            return when (pt) {
                is PyType.Ref -> if (isAssumedTypedPackage(pt.packageName)) pt else PyType.ObjectT
                is PyType.Generic -> pt.copy(args = pt.args.map(::scrub))
                is PyType.Abc -> pt.copy(args = pt.args.map(::scrub))
                is PyType.Union -> pt.copy(items = pt.items.map(::scrub))
                else -> pt
            }
        }

        fun scrubParams(params: List<ParamIR>) = params.map { it.copy(type = scrub(it.type)) }
        // Scrub fields/constructors/methods/properties and type param bounds
        val fields = t.fields.map { it.copy(type = scrub(it.type)) }
        val ctors = t.constructors.map { it.copy(params = scrubParams(it.params)) }
        val methods = t.methods.map { it.copy(params = scrubParams(it.params), returnType = scrub(it.returnType)) }
        val props = t.properties.map { it.copy(type = scrub(it.type)) }
        val tparams = t.typeParams.map { it.copy(bound = it.bound?.let(::scrub)) }
        return t.copy(
            fields = fields,
            constructors = ctors,
            methods = methods,
            properties = props,
            typeParams = tparams
        )
    }

    private fun collectAllMembers(t: TypeIR, function: (pt: PyType) -> Unit) {
        for (field in t.fields) {
            function(field.type)
        }
        for (ctor in t.constructors) {
            for (p in ctor.params) {
                function(p.type)
            }
        }
        for (m in t.methods) {
            function(m.returnType)
            for (p in m.params) {
                function(p.type)
            }
        }
        for (prop in t.properties) {
            function(prop.type)
        }
        for (tp in t.typeParams) {
            tp.bound?.let { b -> function(b) }
        }
    }

    // No per-class runtime shims are emitted; runtime names are exposed in package __init__.py
}

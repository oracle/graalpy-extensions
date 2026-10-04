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
import javax.lang.model.type.ExecutableType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.lang.model.util.Types

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
    private var typeUtils: Types? = null
    private var elementUtils: Elements? = null
    private val generatedMemberNamesCache = mutableMapOf<String, Set<String>>()
    private val protocolVarianceCache = mutableMapOf<String, List<Variance>>()
    private val generatedTypeElements = mutableMapOf<String, TypeElement>()

    override fun init(locale: Locale, reporter: Reporter) {
        this.reporter = reporter
    }

    override fun getName(): String = "j2pyi"

    override fun run(environment: DocletEnvironment): Boolean {
        this.docTrees = environment.docTrees
        this.typeUtils = environment.typeUtils
        this.elementUtils = environment.elementUtils
        generatedMemberNamesCache.clear()
        protocolVarianceCache.clear()
        generatedTypeElements.clear()

        compileAssumedTypedPkgMatchers()

        // Build an intermediate representation for all included types (classes, interfaces, enums) honoring include/exclude and visibility.
        val includedTypes = environment.includedElements
            .asSequence()
            .filterIsInstance<TypeElement>()
            .filter { it.kind == ElementKind.CLASS || it.kind == ElementKind.INTERFACE || it.kind == ElementKind.RECORD || it.kind == ElementKind.ENUM }
            .filter { shouldIncludeType(it) }
            .toList()
        // Make this available while building each IR: it determines whether an inherited declared type will
        // survive external-reference scrubbing and can therefore provide a Python member contract.
        allowedPkgs = includedTypes.map { packageOf(it) }.toSet()
        generatedTypeElements.putAll(
            includedTypes.associateBy { it.qualifiedName.toString() }
        )
        val typeIRs = includedTypes
            .asSequence()
            .mapNotNull { maybeBuildTypeIR(it) }
            .sortedBy { it.qualifiedName }
            .toList()

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
        // Collect type parameters with simple upper bounds (first non-Object bound only).
        // Sanitize names to avoid stray whitespace that can lead to malformed TypeVar declarations.
        val typeParams: List<TypeParamIR> = te.typeParameters.map { tp ->
            val name = tp.simpleName.toString().trim()
            // Python has no notion of generic exceptions. Many Java libraries use a type parameter solely to model
            // the thrown exception type (e.g. <E extends Throwable>). Including such a parameter in a Protocol
            // causes mypy variance errors, and it doesn't add useful information for Python users.
            // So, drop type parameters that are bounded directly by Throwable/Exception.
            if (isThrowableTypeParameter(tp)) {
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
        fun mapSuperType(tm: TypeMirror?): PyType? {
            if (tm == null || tm.kind == TypeKind.NONE) return null
            return when (val mapped = mapType(tm)) {
                // Only generated Java declarations can safely be represented as Python bases. Built-in
                // collection/ABC mappings remain useful in member annotations, but using them as bases
                // would impose Python runtime contracts that Java binding objects do not implement.
                is PyType.Ref -> mapped.takeIf { isAssumedTypedPackage(it.packageName) }
                else -> null
            }
        }
        val omittedSuperTypeNames = mutableListOf<String>()
        val superTypeEntries = mutableListOf<Pair<PyType, List<Variance>>>()
        val selectedSuperContracts = mutableMapOf<String, Set<String>>()
        fun addSuperType(tm: TypeMirror, mapped: PyType, isJavaSuperclass: Boolean) {
            val element = (tm as? DeclaredType)?.asElement() as? TypeElement
            val contracts = generatedPythonMemberContracts(tm)
            // Python cannot represent two unrelated bases that publish incompatible methods under
            // the same name. Prefer the concrete Java superclass; otherwise retain the first
            // interface and document the omitted relationship.
            val hasIncompatibleContract = contracts.any { (name, signatures) ->
                selectedSuperContracts[name]?.let { it != signatures } == true
            }
            if (!isJavaSuperclass && hasIncompatibleContract) {
                omittedSuperTypeNames += mapped.render()
                return
            }
            superTypeEntries += mapped to protocolTypeParameterVariances(element)
            for ((name, signatures) in contracts) {
                selectedSuperContracts.putIfAbsent(name, signatures)
            }
        }

        val mappedSuperclass = if (kind == Kind.CLASS) mapSuperType(te.superclass) else null
        if (mappedSuperclass != null)
            addSuperType(te.superclass, mappedSuperclass, isJavaSuperclass = true)
        for (interfaceType in te.interfaces) {
            // A redundantly declared interface is already present through an emitted superclass.
            // Keep every other direct interface unless its Python member contract conflicts with
            // a base already selected above.
            val inheritedThroughSuperclass = mappedSuperclass != null &&
                typeUtils?.isSubtype(te.superclass, interfaceType) == true
            if (!inheritedThroughSuperclass) {
                val mapped = mapSuperType(interfaceType)
                if (mapped != null)
                    addSuperType(interfaceType, mapped, isJavaSuperclass = false)
            }
        }
        val superTypes = superTypeEntries.map { it.first }
        val superTypeVariances = superTypeEntries.map { it.second }
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
        val suppressedOverrideMethods = allMethods.filter { method -> conflictsWithEmittedPythonBase(method, te) }.toSet()
        val emittedMethods = allMethods
            .filterNot { it in suppressedOverrideMethods }
        val mappedMethods = emittedMethods
            .map { m ->
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
            emittedMethods,
            mappedMethods,
            blockedPropertyNames = emittedPythonBaseMembers(te).keys
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
            superTypes = superTypes,
            superTypeVariances = superTypeVariances,
            omittedSuperTypeNames = omittedSuperTypeNames.distinct().sorted(),
            suppressedMemberNames = suppressedOverrideMethods.map { it.simpleName.toString() }.distinct().sorted(),
            doc = typeDoc,
            fields = fields,
            constructors = constructors,
            methods = remainingMethods,
            properties = properties,
            enumConstants = enumConstants
        )
    }

    private fun generatedTypeElement(ref: PyType.Ref): TypeElement? {
        val qualifiedName = "${ref.packageName}.${ref.simpleName}"
        return generatedTypeElements[qualifiedName] ?: elementUtils?.getTypeElement(qualifiedName)
    }

    /**
     * Determine how a generated Protocol uses each type parameter. This is needed when a child
     * Protocol forwards one of its variables through a generic base: the child's variance must
     * agree with the base's variance or mypy rejects the class header.
     */
    private fun protocolTypeParameterVariances(
        type: TypeElement?,
        active: MutableSet<String> = mutableSetOf()
    ): List<Variance> {
        if (type == null || type.kind != ElementKind.INTERFACE || !config.interfaceAsProtocol) return emptyList()
        val qualifiedName = type.qualifiedName.toString()
        protocolVarianceCache[qualifiedName]?.let { return it }
        if (!active.add(qualifiedName)) return emptyList()
        // Keep these slots aligned with mapDeclaredType(), which removes Java exception-only
        // parameters from every reference to the generated Protocol.
        val parameters = type.typeParameters.filterNot(::isThrowableTypeParameter)
        if (parameters.isEmpty()) {
            active.remove(qualifiedName)
            protocolVarianceCache[qualifiedName] = emptyList()
            return emptyList()
        }
        val returned = mutableSetOf<String>()
        val parameter = mutableSetOf<String>()
        val invariant = mutableSetOf<String>()
        val parameterNames = parameters.map { it.simpleName.toString() }.toSet()

        fun collect(py: PyType, retLike: Boolean, invariantContext: Boolean) {
            when (py) {
                is PyType.TypeVarRef -> {
                    val name = py.name
                    if (name !in parameterNames) return
                    if (retLike) returned += name else parameter += name
                    if (invariantContext) invariant += name
                }
                is PyType.Generic -> {
                    val nestedInvariant = invariantContext || py.name == "list" || py.name == "set" || py.name == "dict"
                    for (arg in py.args) collect(arg, retLike, nestedInvariant)
                }
                is PyType.Abc -> {
                    // The emitted ABCs used by the mapper (Collection, Sequence, Iterator,
                    // Iterable and Mapping) are covariant in their element parameters.
                    for (arg in py.args) collect(arg, retLike, invariantContext)
                }
                is PyType.Ref -> {
                    val referenced = generatedTypeElement(py)
                    val variances = protocolTypeParameterVariances(referenced, active)
                    for ((index, arg) in py.args.withIndex()) {
                        when (variances.getOrNull(index) ?: Variance.INVARIANT) {
                            Variance.COVARIANT -> collect(arg, retLike, invariantContext)
                            Variance.CONTRAVARIANT -> collect(arg, !retLike, invariantContext)
                            Variance.INVARIANT -> collect(arg, retLike, true)
                        }
                    }
                }
                is PyType.Union -> py.items.forEach { collect(it, retLike, invariantContext) }
                else -> Unit
            }
        }

        // Scan all return types and method parameters recursively to establish variance.
        ElementFilter.methodsIn(type.enclosedElements)
            .filter { isIncludedByVisibility(it) }
            .filterNot { conflictsWithEmittedPythonBase(it, type) }
            .forEach { method ->
                collect(mapReturnTypeWithNullability(method), retLike = true, invariantContext = false)
                paramsToIR(method, method.isVarArgs).forEach { collect(it.type, retLike = false, invariantContext = false) }
            }

        // A Protocol also inherits variance constraints from its own generic bases. This is
        // especially important for map-like interfaces combining read (covariant) and write
        // (contravariant) views, which are therefore invariant overall.
        typeUtils?.directSupertypes(type.asType()).orEmpty().forEach { superMirror ->
            val mapped = mapType(superMirror)
            if (mapped is PyType.Ref) {
                val referenced = generatedTypeElement(mapped)
                val variances = protocolTypeParameterVariances(referenced, active)
                mapped.args.forEachIndexed { index, arg ->
                    when (variances.getOrNull(index) ?: Variance.INVARIANT) {
                        Variance.COVARIANT -> collect(arg, retLike = true, invariantContext = false)
                        Variance.CONTRAVARIANT -> collect(arg, retLike = false, invariantContext = false)
                        Variance.INVARIANT -> collect(arg, retLike = false, invariantContext = true)
                    }
                }
            }
        }

        val result = parameters.map { parameterElement ->
            when (val name = parameterElement.simpleName.toString()) {
                in invariant -> Variance.INVARIANT
                in returned if name !in parameter -> Variance.COVARIANT
                in parameter if name !in returned -> Variance.CONTRAVARIANT
                in returned if name in parameter -> Variance.INVARIANT
                else -> Variance.COVARIANT
            }
        }
        active.remove(qualifiedName)
        protocolVarianceCache[qualifiedName] = result
        return result
    }

    /**
     * A Java member must not replace an inherited Python member contract with an incompatible
     * Java-shaped signature. Java can overload methods by parameter types and arity, whereas a
     * Python subclass definition replaces the inherited attribute with the same name.
     */
    private fun conflictsWithEmittedPythonBase(method: ExecutableElement, owner: TypeElement): Boolean {
        val pythonName = safeIdentifier(method.simpleName.toString(), allowSelf = true)
        val bases = emittedPythonBaseMembers(owner)[pythonName].orEmpty()
        return bases.isNotEmpty()
    }

    private fun emittedPythonBaseMembers(owner: TypeElement): Map<String, Set<String>> {
        val types = typeUtils ?: return emptyMap()
        val visited = mutableSetOf<String>()
        val members = mutableMapOf<String, MutableSet<String>>()

        fun isEmittedBase(type: TypeMirror): Boolean {
            val mapped = mapType(type)
            return mapped is PyType.Ref && isAssumedTypedPackage(mapped.packageName)
        }

        fun visit(type: TypeMirror) {
            for (base in types.directSupertypes(type)) {
                if (!visited.add(base.toString())) continue
                // If this base is scrubbed from the Python class header, none of its ancestors can
                // supply an inherited Python contract either.
                if (!isEmittedBase(base)) continue
                val baseElement = (base as? DeclaredType)?.asElement() as? TypeElement
                if (baseElement != null) {
                    for (name in generatedPythonMemberNames(baseElement)) {
                        members.getOrPut(name) { mutableSetOf() } += baseElement.qualifiedName.toString()
                    }
                }
                visit(base)
            }
        }

        visit(owner.asType())
        return members
    }

    /**
     * Python-visible contracts for comparing multiple bases. Generic members are viewed through
     * the actual declared supertype so A<T>.get(T) and B<U>.get(U) compare equal in C<X>.
     *
     * For a concrete class, follow only its superclass chain: interfaces that could not safely be
     * represented on that class must not leak back into a descendant's effective contract.
     */
    private fun generatedPythonMemberContracts(root: TypeMirror): Map<String, Set<String>> {
        val types = typeUtils ?: return emptyMap()
        val contracts = mutableMapOf<String, MutableSet<String>>()
        val visited = mutableSetOf<String>()
        val emittedNamesAcrossHierarchy = mutableSetOf<String>()
        val methodNamesAcrossHierarchy = mutableSetOf<String>()

        fun visit(type: TypeMirror) {
            val declared = type as? DeclaredType ?: return
            if (!visited.add(declared.toString())) return
            val element = declared.asElement() as? TypeElement ?: return
            val emittedNames = generatedPythonMemberNames(element)
            emittedNamesAcrossHierarchy += emittedNames

            ElementFilter.methodsIn(element.enclosedElements)
                .filter { isIncludedByVisibility(it) }
                .filterNot { conflictsWithEmittedPythonBase(it, element) }
                .forEach { method ->
                    val name = safeIdentifier(method.simpleName.toString(), allowSelf = true)
                    if (name !in emittedNames) return@forEach
                    val executable = runCatching { types.asMemberOf(declared, method) as ExecutableType }
                        .getOrElse { method.asType() as ExecutableType }
                    val params = executable.parameterTypes.mapIndexed { index, parameterType ->
                        val prefix = if (method.isVarArgs && index == executable.parameterTypes.lastIndex) "*" else ""
                        "$prefix${mapType(parameterType).render()}"
                    }
                    val staticPrefix = if (method.modifiers.contains(Modifier.STATIC)) "static" else "instance"
                    val signature = "$staticPrefix(${params.joinToString(",")})->${mapType(executable.returnType).render()}"
                    contracts.getOrPut(name) { mutableSetOf() } += signature
                    methodNamesAcrossHierarchy += name
                }

            val directBases = types.directSupertypes(declared)
            if (element.kind == ElementKind.INTERFACE) {
                directBases.forEach(::visit)
            } else {
                directBases.firstOrNull {
                    ((it as? DeclaredType)?.asElement() as? TypeElement)?.kind != ElementKind.INTERFACE
                }?.let(::visit)
            }
        }

        visit(root)
        // Fields and synthesized properties also occupy a Python class attribute. Add the opaque
        // contract only if no method anywhere in the effective hierarchy provides that name.
        for (name in emittedNamesAcrossHierarchy - methodNamesAcrossHierarchy) {
            contracts.getOrPut(name) { mutableSetOf() } += "attribute"
        }
        return contracts.mapValues { it.value.toSet() }
    }

    /** Names that are actually emitted in a type's Python class body after visibility and property synthesis. */
    private fun generatedPythonMemberNames(type: TypeElement): Set<String> =
        generatedMemberNamesCache.getOrPut(type.qualifiedName.toString()) {
            val kind = when (type.kind) {
                ElementKind.INTERFACE -> Kind.INTERFACE
                ElementKind.ENUM -> Kind.ENUM
                else -> Kind.CLASS
            }
            val fields = if (kind == Kind.INTERFACE) {
                emptyList()
            } else {
                ElementFilter.fieldsIn(type.enclosedElements)
                    .filter { isIncludedByVisibility(it) && it.kind != ElementKind.ENUM_CONSTANT }
                    .map { FieldIR(it.simpleName.toString(), mapFieldTypeWithNullability(it)) }
            }
            val rawMethods = ElementFilter.methodsIn(type.enclosedElements)
                .filter { isIncludedByVisibility(it) }
                .sortedWith(compareBy({ it.simpleName.toString() }, { it.parameters.size }))
            val emittedRawMethods = rawMethods.filterNot { conflictsWithEmittedPythonBase(it, type) }
            val mappedMethods = emittedRawMethods.map { method ->
                MethodIR(
                    name = method.simpleName.toString(),
                    params = paramsToIR(method, method.isVarArgs),
                    returnType = mapReturnTypeWithNullability(method),
                    isStatic = method.modifiers.contains(Modifier.STATIC),
                    doc = null
                )
            }
            val (properties, remainingMethods) =
                if (kind == Kind.CLASS && config.propertySynthesis) synthesizeProperties(
                    fields,
                    emittedRawMethods,
                    mappedMethods,
                    blockedPropertyNames = emittedPythonBaseMembers(type).keys
                )
                else Pair(emptyList(), mappedMethods)

            val ownNames = buildSet {
                val propertyNames = properties.mapTo(mutableSetOf()) { it.name }
                val methodNames = remainingMethods.mapTo(mutableSetOf()) {
                    safeIdentifier(it.name, allowSelf = true)
                }
                addAll(propertyNames)
                addAll(methodNames)
                for (field in fields) {
                    if (field.name !in propertyNames && field.name !in methodNames) add(field.name)
                }
            }
            ownNames + emittedPythonBaseMembers(type).keys
        }

    private fun synthesizeProperties(
        fields: List<FieldIR>,
        rawMethods: List<ExecutableElement>,
        mappedMethods: List<MethodIR>,
        blockedPropertyNames: Set<String> = emptySet()
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
            // An inherited method/property with this Python name is authoritative. Keep the Java
            // getter/setter as ordinary methods instead of creating a conflicting property.
            if (safeIdentifier(propName) in blockedPropertyNames || propName in blockedPropertyNames) continue
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
        val abcImports = if (t.kind == Kind.ENUM) emptySet() else t.collectionsAbcImports()
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

                    is PyType.Ref -> {
                        val referenced = generatedTypeElement(py)
                        val variances = protocolTypeParameterVariances(referenced)
                        for ((index, a) in py.args.withIndex()) {
                            when (variances.getOrNull(index) ?: Variance.INVARIANT) {
                                Variance.COVARIANT -> collectVariance(a, retLike, invariantCtx)
                                Variance.CONTRAVARIANT -> collectVariance(a, !retLike, invariantCtx)
                                Variance.INVARIANT -> collectVariance(a, retLike, invariantCtx = true)
                            }
                        }
                    }

                    else -> {} // Any/None: nothing to do
                }
            }

            // Fields (attributes) – treat as "return-like" usage for variance purposes.
            // Forwarded type arguments inherit the variance of the generic Protocol base. Treating
            // every base argument as invariant makes otherwise valid iterator/predicate protocols
            // fail mypy's variance checks.
            for ((index, superType) in t.superTypes.withIndex()) {
                val variances = t.superTypeVariances.getOrNull(index).orEmpty()
                if (superType is PyType.Ref) {
                    superType.args.forEachIndexed { argIndex, arg ->
                        when (variances.getOrNull(argIndex) ?: Variance.INVARIANT) {
                            Variance.COVARIANT -> collectVariance(arg, retLike = true, invariantCtx = false)
                            Variance.CONTRAVARIANT -> collectVariance(arg, retLike = false, invariantCtx = false)
                            Variance.INVARIANT -> collectVariance(arg, retLike = false, invariantCtx = true)
                        }
                    }
                } else {
                    collectVariance(superType, retLike = false, invariantCtx = true)
                }
            }
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
                    // - unused          -> covariant (the least restrictive Protocol variance)
                    // - both            -> invariant (no variance arg)
                    val inRet = name in seenInReturn
                    val inPar = name in seenInParam
                    var result: String? = when {
                        inPar && !inRet -> "contravariant=True"
                        inRet && !inPar -> "covariant=True"
                        !inRet /* && !inPar  [always true] */ -> "covariant=True"
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
                            !inRet /* && !inPar  [always true] */ -> "covariant=True"
                            else -> null
                        }
                    }
                    return result
                }

                sb.appendLine("from typing import TypeVar")
                // Emit declared class type params first (preserve bounds), then remaining refs unbounded
                for (tp: TypeParamIR in t.typeParams) {
                    // Python TypeVar bounds are module-level expressions. A Java bound such as
                    // Comparable<T> or the F-bounded B extends AbstractSupplier<T, B> therefore
                    // cannot refer back to the class's type variables; erase those references to
                    // Any while retaining the useful outer bound.
                    val bound: String? = tp.bound?.let { sanitizeTypeParameterBound(it).render() }
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
            if (t.kind != Kind.ENUM) {
                bases += t.superTypes.map { it.render() }
            }
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

        fun appendSuppressedMemberComments() {
            for (base in t.omittedSuperTypeNames) {
                sb.appendLine("${indent}# Java base '$base' omitted because its Python members conflict with another base.")
            }
            for (name in t.suppressedMemberNames) {
                sb.appendLine("${indent}# Java member '$name' omitted to preserve the inherited Python signature.")
            }
        }

        // Is the type empty?
        if (t.constructors.isEmpty() && t.methods.isEmpty() && t.fields.isEmpty() && t.properties.isEmpty()) {
            appendSuppressedMemberComments()
            if (t.doc.isNullOrBlank()) {
                sb.appendLine("${indent}pass")
            }
            return sb.toString()
        }

        appendSuppressedMemberComments()

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

        // Java methods are exposed as attributes by GraalPy, and Python permits an
        // attribute to have the same name as a builtin (for example, `box.set()`).
        // Keep that name in the stub so its API matches the runtime binding.
        val defName = safeIdentifier(m.name, allowSelf = true)
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

    private fun sanitizeTypeParameterBound(type: PyType): PyType = when (type) {
        is PyType.TypeVarRef -> PyType.AnyT
        is PyType.Generic -> type.copy(args = type.args.map(::sanitizeTypeParameterBound))
        is PyType.Abc -> type.copy(args = type.args.map(::sanitizeTypeParameterBound))
        is PyType.Ref -> type.copy(args = type.args.map(::sanitizeTypeParameterBound))
        is PyType.Union -> type.copy(items = type.items.map(::sanitizeTypeParameterBound))
        else -> type
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
            superTypes.any { anyInType(it) } || fields.any { anyInType(it.type) } || constructors.any { it.params.any { p -> anyInType(p.type) } } || methods.any {
                anyInType(it.returnType) || it.params.any { p -> anyInType(p.type) }
            } || properties.any { anyInType(it.type) } ||
            typeParams.any { it.bound?.let { b -> anyInType(sanitizeTypeParameterBound(b)) } == true }
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
        superTypes.any { numberInType(it) } || fields.any { numberInType(it.type) } || constructors.any { it.params.any { p -> numberInType(p.type) } } || methods.any {
            numberInType(it.returnType) || it.params.any { p -> numberInType(p.type) }
        } || properties.any { numberInType(it.type) } || typeParams.any { it.bound?.let { b -> numberInType(b) } == true }

    private fun TypeIR.needsBuiltinsImport(): Boolean =
        superTypes.any { objectInType(it) } || fields.any { objectInType(it.type) } || constructors.any { it.params.any { p -> objectInType(p.type) } } || methods.any {
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
                        refs += it.copy(args = emptyList())
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
                is PyType.Ref -> if (isAssumedTypedPackage(pt.packageName)) pt.copy(args = pt.args.map(::scrub)) else PyType.ObjectT
                is PyType.Generic -> pt.copy(args = pt.args.map(::scrub))
                is PyType.Abc -> pt.copy(args = pt.args.map(::scrub))
                is PyType.Union -> pt.copy(items = pt.items.map(::scrub))
                else -> pt
            }
        }

        fun scrubParams(params: List<ParamIR>) = params.map { it.copy(type = scrub(it.type)) }
        // Scrub fields/constructors/methods/properties and type param bounds
        val superTypes = t.superTypes.map(::scrub)
        val fields = t.fields.map { it.copy(type = scrub(it.type)) }
        val ctors = t.constructors.map { it.copy(params = scrubParams(it.params)) }
        val methods = t.methods.map { it.copy(params = scrubParams(it.params), returnType = scrub(it.returnType)) }
        val props = t.properties.map { it.copy(type = scrub(it.type)) }
        val tparams = t.typeParams.map { it.copy(bound = it.bound?.let(::scrub)) }
        return t.copy(
            superTypes = superTypes,
            fields = fields,
            constructors = ctors,
            methods = methods,
            properties = props,
            typeParams = tparams
        )
    }

    private fun collectAllMembers(t: TypeIR, function: (pt: PyType) -> Unit) {
        for (superType in t.superTypes) {
            function(superType)
        }
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

package org.graalvm.python.pyinterfacegen

import jdk.javadoc.doclet.Reporter
import java.io.File
import javax.lang.model.element.*
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.type.TypeVariable
import javax.tools.Diagnostic

// ===== Python identifier utils =====
// Python keywords that cannot be used as identifiers/parameter names.
// Keep in sync with Python 3.11+; covers all reserved words including match/case.
val PYTHON_KEYWORDS: Set<String> = setOf(
    "False",
    "None",
    "True",
    "and",
    "as",
    "assert",
    "async",
    "await",
    "break",
    "class",
    "continue",
    "def",
    "del",
    "elif",
    "else",
    "except",
    "finally",
    "for",
    "from",
    "global",
    "if",
    "import",
    "in",
    "is",
    "lambda",
    "nonlocal",
    "not",
    "or",
    "pass",
    "raise",
    "return",
    "try",
    "while",
    "with",
    "yield",
    "match",
    "case"
)

// Precompiled regexes (expensive to compile repeatedly).
private val NON_IDENTIFIER_CHARS: Regex = Regex("[^0-9A-Za-z_]")

fun safeParamName(p: VariableElement): String {
    val raw = p.simpleName.toString()
    // Default fallback name
    var name = if (raw.isBlank() || raw == "self") "p" else raw
    // Replace characters not valid in a Python identifier with underscores.
    name = name.replace(NON_IDENTIFIER_CHARS, "_")
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
fun safeIdentifier(raw0: String, allowSelf: Boolean = false): String {
    var name = raw0
    if (name.isBlank()) return "name"
    name = name.replace(NON_IDENTIFIER_CHARS, "_")
    if (name.isBlank()) name = "name"
    if (name.first().isDigit()) name = "n_$name"
    if (!allowSelf && name == "self") name = "name"
    if (PYTHON_KEYWORDS.contains(name)) name = "${name}_"
    return name
}

// ===== Package helpers =====
fun packageOf(e: Element): String {
    var cur: Element? = e
    while (cur != null && cur !is PackageElement) cur = cur.enclosingElement
    return cur?.qualifiedName?.toString() ?: ""
}

fun packageDir(base: File, pkg: String): File =
    if (pkg.isBlank()) base else File(base, pkg.replace('.', File.separatorChar))

// Map a Java package name to a Python package name using the provided prefix map.
// Chooses the longest matching Java prefix (boundary at '.' or end).
fun mapPackage(javaPkg: String, mappings: List<Pair<String, String>>): String {
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

fun parsePackageMap(spec: String, reporter: Reporter?): List<Pair<String, String>> {
    if (spec.isBlank()) return emptyList()
    return spec.split(',', ';').mapNotNull { part ->
        val s = part.trim()
        if (s.isEmpty()) return@mapNotNull null
        val kv = s.split("=", limit = 2)
        if (kv.size != 2) {
            reporter?.print(
                Diagnostic.Kind.WARNING,
                "Ignoring invalid -Xj2pyi-packageMap entry: '$s' (expect javaPkg=pyPkg)"
            )
            null
        } else {
            kv[0].trim() to kv[1].trim()
        }
    }
}

// ===== Type/nullability mapping =====
enum class Nullability { NULLABLE, NONNULL, UNKNOWN }

private val REMOVE_ANNOTATIONS_REGEX = Regex("^(@[A-Za-z0-9_$.]+\\s+)+")

// Remove any leading Java type-use annotations from a textual representation of a type,
// e.g. "@org.jspecify.annotations.NonNull B" -> "B"
private fun stripLeadingTypeUseAnnotations(text: String): String =
    text.trimStart().replace(REMOVE_ANNOTATIONS_REGEX, "")

// Nullability detection (package prefixes per spec; simple-name heuristic allowed for tests)
val NULLABILITY_PACKAGE_PREFIXES: List<String> = listOf(
    "javax.annotation",
    "jakarta.annotation",
    "org.jetbrains.annotations",
    "edu.umd.cs.findbugs.annotations",
    "org.checkerframework.checker.nullness.qual",
    "androidx.annotation",
    "org.jspecify.annotations",
    "io.micronaut.core.annotation"
)

fun computeNullability(
    anns: List<AnnotationMirror>,
    extraPrefixes: List<String>
): Nullability {
    var foundNullable = false
    var foundNonNull = false
    for (am in anns) {
        val at = am.annotationType
        val aEl = (at.asElement() as? TypeElement) ?: continue
        val qn = aEl.qualifiedName.toString()
        val simple = aEl.simpleName.toString()
        val pkg = qn.substringBeforeLast('.', missingDelimiterValue = "")
        val pkgRecognized =
            pkg.isNotEmpty() && (NULLABILITY_PACKAGE_PREFIXES.any { pkg.startsWith(it) } || extraPrefixes.any {
                pkg.startsWith(it)
            })
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

fun isFullyQualifiedNameAJDKType(qn: String, extraPlatformPackages: List<String>): Boolean {
    // Built-in defaults
    if (qn.startsWith("java.") || qn.startsWith("javax.") || qn.startsWith("jdk.") ||
        // Treat common JDK-adjacent namespaces as platform types too; we don't emit Python imports for them.
        qn.startsWith("org.w3c.") || qn.startsWith("org.xml.") || qn.startsWith("org.omg.") || qn.startsWith("org.ietf.")
    ) {
        return true
    }
    // User-extended prefixes
    if (extraPlatformPackages.isNotEmpty()) {
        for (p in extraPlatformPackages) {
            if (p.isNotBlank() && (qn == p || qn.startsWith("$p."))) return true
        }
    }
    return false
}

fun optionalOf(inner: PyType): PyType {
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

fun mapType(t: TypeMirror, extraPlatformPackages: List<String>): PyType = when (t.kind) {
    TypeKind.BOOLEAN -> PyType.Bool
    TypeKind.BYTE, TypeKind.SHORT, TypeKind.INT, TypeKind.LONG -> PyType.IntT
    TypeKind.FLOAT, TypeKind.DOUBLE -> PyType.FloatT
    TypeKind.CHAR -> PyType.Str
    TypeKind.VOID -> PyType.NoneT
    TypeKind.TYPEVAR -> {
        // Prefer clean element name over TypeMirror.toString() to avoid type-use annotations creeping in.
        val nameFromElem = (t as? TypeVariable)?.asElement()?.simpleName?.toString()
        val tv = if (!nameFromElem.isNullOrBlank()) nameFromElem else stripLeadingTypeUseAnnotations(t.toString())
        PyType.TypeVarRef(tv.trim())
    }

    TypeKind.ARRAY -> {
        val at = t as ArrayType
        val comp = at.componentType
        PyType.Abc("Sequence", listOf(mapType(comp, extraPlatformPackages)))
    }

    TypeKind.DECLARED -> mapDeclaredType(t as DeclaredType, extraPlatformPackages)
    TypeKind.ERROR -> mapUnresolvedDeclaredType(t, extraPlatformPackages)
    TypeKind.WILDCARD -> PyType.AnyT
    else -> PyType.AnyT
}

fun mapUnresolvedDeclaredType(t: TypeMirror, extraPlatformPackages: List<String>): PyType {
    val text = stripLeadingTypeUseAnnotations(t.toString())
    val raw = text.substringBefore('<')
    val lastDot = raw.lastIndexOf('.')
    val pkg = if (lastDot >= 0) raw.substring(0, lastDot) else ""
    val simple = if (lastDot >= 0) raw.substring(lastDot + 1) else raw
    // Treat unresolved Java platform types as object to avoid unresolved names in stubs.
    if (isFullyQualifiedNameAJDKType(pkg, extraPlatformPackages)) {
        return PyType.ObjectT
    }
    return if (simple.isBlank()) PyType.AnyT else PyType.Ref(pkg, simple)
}

fun mapDeclaredType(dt: DeclaredType, extraPlatformPackages: List<String>): PyType {
    val el = dt.asElement() as? TypeElement ?: return PyType.AnyT
    val qn = el.qualifiedName.toString()

    fun argOrAny(i: Int): PyType {
        val args: List<TypeMirror> = dt.typeArguments
        return if (i >= 0 && i < args.size) mapType(args[i], extraPlatformPackages) else PyType.AnyT
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
        // CharSequence maps naturally to Python 'str' for these APIs
        "java.lang.CharSequence" -> PyType.Str

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
            if (isFullyQualifiedNameAJDKType(qn, extraPlatformPackages)) {
                PyType.ObjectT
            } else {
                val isTopLevel = el.enclosingElement is PackageElement
                val isPublic = el.modifiers.contains(Modifier.PUBLIC)
                if (isTopLevel && isPublic) {
                    val pkg = packageOf(el)
                    val name = el.simpleName.toString()
                    PyType.Ref(pkg, name)
                } else {
                    PyType.ObjectT
                }
            }
        }
    }
}

fun mapReturnTypeWithNullability(
    m: ExecutableElement,
    nullabilityExtra: List<String>,
    extraPlatformPackages: List<String>
): PyType {
    val base = mapType(m.returnType, extraPlatformPackages)
    return when (computeNullability(m.annotationMirrors + m.returnType.annotationMirrors, nullabilityExtra)) {
        Nullability.NULLABLE -> optionalOf(base)
        else -> base
    }
}

fun mapParamTypeWithNullability(
    p: VariableElement,
    nullabilityExtra: List<String>,
    extraPlatformPackages: List<String>,
    overrideType: TypeMirror? = null
): PyType {
    val t = overrideType ?: p.asType()
    val base = mapType(t, extraPlatformPackages)
    return when (computeNullability(p.annotationMirrors + t.annotationMirrors, nullabilityExtra)) {
        Nullability.NULLABLE -> optionalOf(base)
        else -> base
    }
}

fun mapFieldTypeWithNullability(
    f: VariableElement,
    nullabilityExtra: List<String>,
    extraPlatformPackages: List<String>
): PyType {
    val t = f.asType()
    val base = mapType(t, extraPlatformPackages)
    return when (computeNullability(f.annotationMirrors + t.annotationMirrors, nullabilityExtra)) {
        Nullability.NULLABLE -> optionalOf(base)
        else -> base
    }
}

// ===== Overload/dominance helpers =====
// Helper: compute a "generality score" for a type; higher = more general
fun typeGeneralityScore(pt: PyType): Int {
    var score = 0
    pt.walk { node ->
        score += when (node) {
            is PyType.AnyT -> 100
            is PyType.ObjectT -> 90
            is PyType.Union -> 80
            is PyType.Abc -> when (node.name) {
                "Iterable" -> 70
                "Collection" -> 60
                "Sequence", "Iterator" -> 50
                else -> 40
            }

            is PyType.Generic -> when (node.name) {
                "list", "set", "dict" -> 30
                else -> 20
            }

            is PyType.TypeVarRef -> 10
            is PyType.IntT -> 0
            is PyType.FloatT -> 5
            is PyType.Str, is PyType.Bool -> 0
            is PyType.NoneT -> 0
            is PyType.Ref -> 5
            is PyType.NumberT -> 20
        }
    }
    return score
}

// Approximate: does type 'a' accept everything type 'b' accepts? (a is broader-or-equal than b)
fun typeIsBroaderOrEqual(a: PyType, b: PyType): Boolean {
    if (a === b) return true
    if (a === PyType.AnyT) return true
    if (a === PyType.ObjectT && b !== PyType.AnyT) return true
    if (a === PyType.NumberT) return b === PyType.IntT || b === PyType.FloatT
    if (a === PyType.FloatT && b === PyType.IntT) return true
    if (a is PyType.TypeVarRef) return true
    if (a is PyType.Abc && b is PyType.Abc && a.name == b.name && a.args.size == b.args.size) {
        return a.args.zip(b.args).all { (aa, bb) -> typeIsBroaderOrEqual(aa, bb) }
    }
    if (a is PyType.Generic && b is PyType.Generic && a.name == b.name && a.args.size == b.args.size) {
        return a.args.zip(b.args).all { (aa, bb) -> typeIsBroaderOrEqual(aa, bb) }
    }
    if (a is PyType.Abc && b is PyType.Generic && a.args.size == b.args.size) {
        val mapping = mapOf("Sequence" to "list", "Collection" to "list", "Iterable" to "list")
        if (mapping[a.name] == b.name) {
            return a.args.zip(b.args).all { (aa, bb) -> typeIsBroaderOrEqual(aa, bb) }
        }
    }
    return false
}

fun methodDominates(a: WithParamsIR, b: WithParamsIR): Boolean {
    val aParams = a.params
    val bParams = b.params
    val aVarargs = aParams.lastOrNull()?.isVarargs == true
    if (!aVarargs) {
        if (aParams.size != bParams.size) return false
        for ((pa, pb) in aParams.zip(bParams)) {
            val va = pa.isVarargs
            val vb = pb.isVarargs
            if (va && !vb) continue
            if (!typeIsBroaderOrEqual(pa.type, pb.type)) return false
        }
        return true
    } else {
        val k = aParams.size - 1
        if (bParams.size < k) return false
        for (i in 0 until k) {
            val pa = aParams[i]
            val pb = bParams[i]
            if (!typeIsBroaderOrEqual(pa.type, pb.type)) return false
        }
        val varargType = aParams.last().type
        for (i in k until bParams.size) {
            val pb = bParams[i]
            if (!typeIsBroaderOrEqual(varargType, pb.type)) return false
        }
        return true
    }
}

fun methodGeneralitySignature(m: WithParamsIR): List<Int> {
    return m.params.map { p -> typeGeneralityScore(p.type) + if (p.isVarargs) 10 else 0 }
}

fun <T : WithParamsIR> dropDominatedOverloads(ordered: List<T>): List<T> {
    val filtered = ArrayList<T>(ordered.size)
    outer@ for (c in ordered) {
        for (k in filtered) {
            if (methodDominates(k, c)) continue@outer
        }
        filtered += c
    }
    return filtered
}

// ===== Docstring helpers =====
// Escape only those backslashes that would form invalid escape sequences in Python string literals.
fun escapeInvalidPythonEscapes(s: String): String {
    fun isHex(c: Char): Boolean = (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')
    val out = StringBuilder(s.length + 8)
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c != '\\') {
            out.append(c); i++; continue
        }
        if (i + 1 >= s.length) {
            out.append("\\\\"); i++; continue
        }
        val n = s[i + 1]
        when (n) {
            '\\', '\'', '"', 'a', 'b', 'f', 'n', 'r', 't', 'v' -> {
                out.append('\\').append(n); i += 2
            }

            'x' -> {
                if (i + 3 < s.length && isHex(s[i + 2]) && isHex(s[i + 3])) {
                    out.append("\\x").append(s[i + 2]).append(s[i + 3]); i += 4
                } else {
                    out.append("\\\\").append('x'); i += 2
                }
            }

            'u' -> {
                if (i + 5 < s.length && isHex(s[i + 2]) && isHex(s[i + 3]) && isHex(s[i + 4]) && isHex(s[i + 5])) {
                    out.append("\\u").append(s[i + 2]).append(s[i + 3]).append(s[i + 4]).append(s[i + 5]); i += 6
                } else {
                    out.append("\\\\").append('u'); i += 2
                }
            }

            'U' -> {
                if (i + 9 < s.length && (2..9).all { k -> isHex(s[i + k]) }) {
                    out.append("\\U"); for (k in 2..9) out.append(s[i + k]); i += 10
                } else {
                    out.append("\\\\").append('U'); i += 2
                }
            }

            'N' -> {
                if (i + 2 < s.length && s[i + 2] == '{') {
                    var j = i + 3;
                    var found = false
                    while (j < s.length) {
                        if (s[j] == '}') {
                            found = true; break
                        }; j++
                    }
                    if (found) {
                        out.append("\\N"); out.append(s, i + 2, j + 1); i = j + 1
                    } else {
                        out.append("\\\\").append('N'); i += 2
                    }
                } else {
                    out.append("\\\\").append('N'); i += 2
                }
            }

            in '0'..'7' -> {
                var j = i + 1;
                var count = 0
                while (j < s.length && count < 3 && s[j] in '0'..'7') {
                    j++; count++
                }
                out.append(s, i, j); i = j
            }

            '\n', '\r' -> {
                out.append('\\').append(n); i += 2
            }

            else -> {
                out.append("\\\\").append(n); i += 2
            }
        }
    }
    return out.toString()
}

// Choose a safe triple-quote delimiter and escape embedded triples accordingly.
fun chooseTripleQuoteAndEscape(s: String): Pair<String, String> {
    val trimmedEnd = s.trimEnd()
    val endsWithDbl = trimmedEnd.endsWith('"')
    val hasTripleDbl = s.contains("\"\"\"")
    val useSingle = hasTripleDbl || endsWithDbl
    val delim = if (useSingle) "'''" else "\"\"\""
    val escapedTriples = if (useSingle) {
        s.replace("'''", "\\'\\'\\'")
    } else {
        s.replace("\"\"\"", "\\\"\\\"\\\"")
    }
    val escaped = escapeInvalidPythonEscapes(escapedTriples)
    return delim to escaped
}

fun appendDocString(doc: String, indent: String, sb: StringBuilder) {
    val (delim, escaped) = chooseTripleQuoteAndEscape(doc)
    val lines = escaped.split('\n')
    val first = lines.firstOrNull() ?: ""
    val rest = if (lines.size > 1) {
        lines.drop(1).joinToString("\n") { ln -> if (ln.isEmpty()) "" else "$indent$ln" }
    } else ""
    val body = if (rest.isEmpty()) first else "$first\n$rest"
    sb.appendLine("${indent}$delim$body$delim")
}

fun appendIndentedDocStringAndPass(indent: String, doc: String, sb: StringBuilder) {
    appendDocString(doc, indent.repeat(2), sb)
    sb.appendLine("${indent.repeat(2)}...")
}

fun renderParams(params: List<ParamIR>, includeSelf: Boolean): String {
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

// ===== Module assembly =====
fun assemblePythonModule(
    stubOutDir: File,
    pkgToTypes: Map<String, Map<String, String>>,
    moduleName: String?,
    moduleVersion: String,
    reporter: Reporter?
) {
    val name = (moduleName?.takeIf { it.isNotBlank() }) ?: "j2pyi-stubs"
    val outRoot = stubOutDir
    outRoot.mkdirs()

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

    // Only list non-empty packages in pyproject.toml.
    fun discoverNonEmptyPackages(root: File): Set<String> {
        if (!root.isDirectory) return emptySet()
        val pkgs = mutableSetOf<String>()
        for (f in root.walkTopDown()) {
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
    File(outRoot, "pyproject.toml").writeText(
        """
            |[build-system]
            |requires = ["setuptools>=68", "wheel"]
            |build-backend = "setuptools.build_meta"
            |
            |[project]
            |name = "$name"
            |version = "$moduleVersion"
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

    reporter?.print(
        Diagnostic.Kind.NOTE,
        "j2pyi: Assembled Python module at ${outRoot} (name=${name}, packages=${leafPkgNames.size})"
    )
}

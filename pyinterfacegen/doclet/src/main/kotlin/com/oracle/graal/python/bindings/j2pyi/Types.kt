package org.graalvm.python.pyinterfacegen

// Type universe
sealed interface PyType {
    fun render(): String
    fun walk(visit: (PyType) -> Unit) {
        visit(this)
    }

    // Reference to another declared (top-level) type.
    data class Ref(val packageName: String, val simpleName: String) : PyType {
        override fun render(): String = simpleName
    }

    // Reference to a type variable declared on the containing type.
    data class TypeVarRef(val name: String) : PyType {
        override fun render(): String = name
    }

    object Bool : PyType {
        override fun render() = "bool"
    }

    object IntT : PyType {
        override fun render() = "int"
    }

    object FloatT : PyType {
        override fun render() = "float"
    }

    object Str : PyType {
        override fun render() = "str"
    }

    object NoneT : PyType {
        override fun render() = "None"
    }

    object AnyT : PyType {
        override fun render() = "Any"
    }

    // Python's root object type
    object ObjectT : PyType {
        override fun render() = "object"
    }

    object NumberT : PyType {
        override fun render() = "Number"
    }

    // Built-in generics that need no import in Python 3.11+ (list, set, dict)
    data class Generic(val name: String, val args: List<PyType>) : PyType {
        override fun render(): String =
            if (args.isEmpty()) name else "$name[${args.joinToString(", ") { it.render() }}]"

        override fun walk(visit: (PyType) -> Unit) {
            visit(this)
            args.forEach { it.walk(visit) }
        }
    }

    // abc = abstract base class
    // collections.abc types like Sequence, Iterable, Iterator, Collection
    data class Abc(val name: String, val args: List<PyType>) : PyType {
        override fun render(): String =
            if (args.isEmpty()) name else "$name[${args.joinToString(", ") { it.render() }}]"

        override fun walk(visit: (PyType) -> Unit) {
            visit(this)
            args.forEach { it.walk(visit) }
        }
    }

    // Simple union type to support Optionals (T | None). Keep deterministic ordering with None last.
    data class Union(val items: List<PyType>) : PyType {
        override fun render(): String {
            // Deterministic order: sort by a weight (None last) then by rendered text
            val sorted = items.sortedWith(compareBy({ if (it === NoneT) 1 else 0 }, { it.render() }))
            return sorted.joinToString(" | ") { it.render() }
        }

        override fun walk(visit: (PyType) -> Unit) {
            visit(this)
            items.forEach { it.walk(visit) }
        }
    }
}

data class TypeIR(
    val packageName: String,
    val simpleName: String,
    val qualifiedName: String,
    val kind: Kind,
    val isAbstract: Boolean,
    val typeParams: List<TypeParamIR>,
    val doc: String?,   // First-sentence Javadoc summary for the type
    val fields: List<FieldIR>,
    val constructors: List<ConstructorIR>,
    val methods: List<MethodIR>,
    val properties: List<PropertyIR>,
    val enumConstants: List<String> = emptyList()
)

enum class Kind { CLASS, INTERFACE, ENUM }

data class TypeParamIR(
    val name: String,
    val bound: PyType? // null or Any -> unbounded
)

data class FieldIR(
    val name: String,
    val type: PyType
)

interface WithParamsIR {
    val params: List<ParamIR>
    val doc: String?
}

data class ConstructorIR(
    override val params: List<ParamIR>,
    override val doc: String?
) : WithParamsIR

data class MethodIR(
    val name: String,
    override val params: List<ParamIR>,
    val returnType: PyType,
    val isStatic: Boolean,
    override val doc: String?
) : WithParamsIR

data class PropertyIR(
    val name: String,
    val type: PyType,
    val readOnly: Boolean,
    val doc: String?  // summary from getter
)

data class ParamIR(
    val name: String,
    val type: PyType,
    val isVarargs: Boolean = false
)

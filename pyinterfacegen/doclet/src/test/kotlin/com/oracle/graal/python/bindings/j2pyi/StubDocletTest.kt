package org.graalvm.python.pyinterfacegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class StubDocletTest {
    @Test
    fun simpleClass_emitsMethodsFieldsAndCtor() {
        val java = """
            public class Hello {
                public int count;
                public Hello() {}
                public String greet(String name) { return "x"; }
            }
        """.trimIndent()
        val expected = """
            class Hello:
                count: int
                def __init__(self) -> None: ...
                def greet(self, name: str) -> str: ...
        """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }

    @Test
    fun javadoc_pre_block_preserved_multiline() {
        val java = """
            /**
             * Container for code doc test.
             */
            public class CodeDoc {
                /**
                 * <p>
                 * Does a thing.
                 * <p>
                 * See below.
                 * <pre>
                 * {@code
                 * Encoding encoding = EncodingRegistry.getEncoding(EncodingType.CL100K_BASE);
                 * encoding.encode("hello world");
                 * // returns [15339, 1917]
                 *
                 * encoding.encode("hello endoftext world");
                 * // raises an UnsupportedOperationException
                 * }
                 * </pre>
                 *
                 * After.
                 */
                public java.util.List<String> sample() { return null; }
            }
        """.trimIndent()
        val tripleQuote = "\"\"\""
        val expected = """
            class CodeDoc:
                ${tripleQuote}Container for code doc test.${tripleQuote}
                def __init__(self) -> None: ...
                def sample(self) -> list[str]:
                    ${tripleQuote}Does a thing.

                    See below.

                    >>> Encoding encoding = EncodingRegistry.getEncoding(EncodingType.CL100K_BASE);
                        encoding.encode("hello world");
                        // returns [15339, 1917]
                        encoding.encode("hello endoftext world");
                        // raises an UnsupportedOperationException

                    After.${tripleQuote}
                    ...
        """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }

    @Test
    fun inner_interface_emitted_in_same_package_not_root() {
        // Public inner interface declared inside a public class should be generated
        // into the same Java package directory (not the root of the output tree).
        val pkg = "com.example.nesting"
        val java = """
            public class Outer {
                public Outer() {}
                public interface Inner {
                    String ping(String s);
                }
            }
        """.trimIndent()
        val dest = DocletTestUtil.runDocletMulti(pkg to java)
        val pkgDir = File(dest, pkg.replace('.', '/'))
        val rootInner = File(dest, "Inner.pyi")
        val inPkgInner = File(pkgDir, "Inner.pyi")
        // We expect Inner.pyi to be in the package dir, and not at the root.
        assertTrue(inPkgInner.isFile, "Expected inner type generated under its package: ${inPkgInner.absolutePath}")
        assertTrue(!rootInner.exists(), "Inner type should not be generated at the root: ${rootInner.absolutePath}")
    }

    @Test
    fun inner_class_emitted_in_same_package_not_root() {
        val pkg = "com.example.nesting2"
        val java = """
            public class A {
                public A() {}
                public static class B {
                    public B() {}
                    public int value() { return 1; }
                }
            }
        """.trimIndent()
        val dest = DocletTestUtil.runDocletMulti(pkg to java)
        val pkgDir = File(dest, pkg.replace('.', '/'))
        val rootB = File(dest, "B.pyi")
        val inPkgB = File(pkgDir, "B.pyi")
        assertTrue(inPkgB.isFile, "Expected inner class generated under its package: ${inPkgB.absolutePath}")
        assertTrue(!rootB.exists(), "Inner class should not be generated at the root: ${rootB.absolutePath}")
    }

    @Test
    fun numberMapping_importsNumbersNumber() {
        val java = """
            public class NumberTest {
                public Number getNumber() { return 0; }
            }
        """.trimIndent()
        val expected = """
            from numbers import Number

            class NumberTest:
                def __init__(self) -> None: ...
                @property
                def number(self) -> Number: ...
        """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }

    @Test
    fun staticMethod_hasDecoratorAndNoSelf() {
        val java = """
            public class StaticUtil {
                public static int util() { return 1; }
            }
        """.trimIndent()
        val expected = """
            class StaticUtil:
                def __init__(self) -> None: ...
                @staticmethod
                def util() -> int: ...
        """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }

    @Test
    fun arrayParam_mapsToSequence() {
        val java = """
            public class ArrayThing {
                public void setBytes(byte[] data) {}
            }
        """.trimIndent()
        val expected = """
            from collections.abc import Sequence

            class ArrayThing:
                def __init__(self) -> None: ...
                def setBytes(self, data: Sequence[int]) -> None: ...
        """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }

    // Collections and arrays
    @Test
    fun list_set_map_and_iterables() {
        val java = """
            import java.util.*;
            import java.util.stream.*;
            public class C {
                public C() {}
                public List<String> names() { return null; }
                public Set<Integer> ids() { return null; }
                public Map<String, Double> weights() { return null; }
                public Iterable<String> it() { return null; }
                public Iterator<String> it2() { return null; }
                public Collection<Integer> cs() { return null; }
                public Stream<Long> stream() { return null; }
            }
        """.trimIndent()
        val expected = """
            from collections.abc import Collection, Iterable, Iterator

            class C:
                def __init__(self) -> None: ...
                def cs(self) -> Collection[int]: ...
                def ids(self) -> set[int]: ...
                def it(self) -> Iterable[str]: ...
                def it2(self) -> Iterator[str]: ...
                def names(self) -> list[str]: ...
                def stream(self) -> Iterable[int]: ...
                def weights(self) -> dict[str, float]: ...
        """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }

    @Test
    fun optional_normalizes_to_union_with_none() {
        val java = """
            import java.util.Optional;
            public class O {
                public O() {}
                public Optional<String> maybe() { return Optional.empty(); }
            }
        """.trimIndent()
        val expected = """
            class O:
                def __init__(self) -> None: ...
                def maybe(self) -> str | None: ...
        """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }

    @Test
    fun emitsInitReexports_forMultipleTypesInPackage() {
        val dest = DocletTestUtil.runDocletMulti(
            "com.example" to """
                public class Zebra {
                    public Zebra() {}
                }
            """.trimIndent(),
            "com.example" to """
                public class Alpha {
                    public Alpha() {}
                }
            """.trimIndent()
        )
        val pkgDir = File(dest, "com/example")
        assertTrue(pkgDir.isDirectory, "Expected package directory at ${pkgDir.absolutePath}")
        // Individual modules
        assertTrue(File(pkgDir, "Alpha.pyi").isFile, "Expected Alpha.pyi")
        assertTrue(File(pkgDir, "Zebra.pyi").isFile, "Expected Zebra.pyi")
        // __init__.pyi with alphabetical re-exports
        val initText = File(pkgDir, "__init__.pyi").readText().trimEnd()
        val expected = """
            from .Alpha import Alpha as Alpha
            from .Zebra import Zebra as Zebra
        """.trimIndent().trimEnd()
        assertEquals(expected, initText)
    }

    @Test
    fun emitsInitReexports_forSubpackage() {
        val dest = DocletTestUtil.runDocletMulti(
            "com.example" to """
                public class TopLevel {
                    public TopLevel() {}
                }
            """.trimIndent(),
            "com.example.sub" to """
                public class Inner {
                    public Inner() {}
                }
            """.trimIndent()
        )
        val topPkg = File(dest, "com/example")
        val subPkg = File(dest, "com/example/sub")
        assertTrue(topPkg.isDirectory, "Expected top-level package directory")
        assertTrue(subPkg.isDirectory, "Expected subpackage directory")
        val topInit = File(topPkg, "__init__.pyi").readText().trimEnd()
        val subInit = File(subPkg, "__init__.pyi").readText().trimEnd()
        assertEquals("from .TopLevel import TopLevel as TopLevel", topInit)
        assertEquals("from .Inner import Inner as Inner", subInit)
    }

    @Test
    fun overloadedMethods_groupedByKind_andUseOverload() {
        val java = """
            public class Over {
                public Over() {}
                public String f(int a) { return ""; }
                public String f(String b) { return ""; }
                public static String f(int x, int y) { return ""; }
            }
        """.trimIndent()
        val expected = """
            class Over:
                def __init__(self) -> None: ...
                @staticmethod
                def f(x: int, y: int) -> str: ...
        """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }

    @Test
    fun varargs_forMethodsAndCtors_emitStarArgs() {
        val java = """
            public class V {
                public V() {}
                public V(int... xs) {}
                public void add(String... names) {}
            }
        """.trimIndent()
        val expected = """
            from typing import overload

            class V:
                @overload
                def __init__(self) -> None: ...
                @overload
                def __init__(self, *args: int) -> None: ...
                def add(self, *args: str) -> None: ...
        """.trimIndent().trimEnd()
             val actual = DocletTestUtil.runDoclet(java)
             assertEquals(expected, actual)
         }

         // Properties
         @Test
         fun property_readonly_fromGetter() {
             val java = """
                 public class Bean {
                     public Bean() {}
                     public String getName() { return "x"; }
                 }
             """.trimIndent()
             val expected = """
                 class Bean:
                     def __init__(self) -> None: ...
                     @property
                     def name(self) -> str: ...
             """.trimIndent().trimEnd()
             val actual = DocletTestUtil.runDoclet(java)
             assertEquals(expected, actual)
         }

         @Test
         fun property_readwrite_fromGetterSetter() {
             val java = """
                 public class Bean2 {
                     public Bean2() {}
                     public int getCount() { return 0; }
                     public void setCount(int v) {}
                 }
             """.trimIndent()
             val expected = """
                 class Bean2:
                     def __init__(self) -> None: ...
                     @property
                     def count(self) -> int: ...
                     @count.setter
                     def count(self, value: int) -> None: ...
             """.trimIndent().trimEnd()
             val actual = DocletTestUtil.runDoclet(java)
             assertEquals(expected, actual)
         }

         @Test
         fun property_boolean_isPrefix() {
             val java = """
                 public class Flaggy {
                     public Flaggy() {}
                     public boolean isReady() { return true; }
                 }
             """.trimIndent()
             val expected = """
                 class Flaggy:
                     def __init__(self) -> None: ...
                     @property
                     def ready(self) -> bool: ...
             """.trimIndent().trimEnd()
             val actual = DocletTestUtil.runDoclet(java)
             assertEquals(expected, actual)
         }

         @Test
         fun property_conflict_withField_skipsSynthesis() {
             val java = """
                 public class Clash {
                     public int name; // conflicts with getName()
                     public Clash() {}
                     public String getName() { return "x"; }
                 }
             """.trimIndent()
             val expected = """
                 class Clash:
                     name: int
                     def __init__(self) -> None: ...
                     def getName(self) -> str: ...
             """.trimIndent().trimEnd()
             val actual = DocletTestUtil.runDoclet(java)
             assertEquals(expected, actual)
         }

         @Test
         fun property_acronymDecap_URLRemainsUpper() {
             val java = """
                 public class Web {
                     public Web() {}
                     public String getURL() { return "http://example.com"; }
                 }
             """.trimIndent()
             val expected = """
                 class Web:
                     def __init__(self) -> None: ...
                     @property
                     def URL(self) -> str: ...
             """.trimIndent().trimEnd()
             val actual = DocletTestUtil.runDoclet(java)
             assertEquals(expected, actual)
         }
     }

     // Interfaces and Enums
     @Test
     fun interface_emitsProtocol_withMethodsOnly() {
         val java = """
             public interface Greeter {
                 String greet(String name);
                 static int version() { return 1; }
             }
         """.trimIndent()
         val expected = """
             from typing import Protocol

             class Greeter(Protocol):
                 def greet(self, name: str) -> str: ...
                 @staticmethod
                 def version() -> int: ...
         """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }

    @Test
    fun enum_emitsEnum_withMembers() {
        val java = """
            public enum Color {
                RED, GREEN, BLUE;
            }
        """.trimIndent()
        val expected = """
            from enum import Enum

            class Color(Enum):
                BLUE = ...
                GREEN = ...
                RED = ...
        """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }

    // Nullability
    @Test
    fun nullable_on_return_and_param() {
        val java = """
            @interface Nullable {}
            @interface NotNull {}
            public class N {
                public N() {}
                public @Nullable String maybe() { return null; }
                public String echo(@Nullable String s) { return s; }
                public String echo2(@NotNull String s) { return s; }
            }
        """.trimIndent()
        val expected = """
            class N:
                def __init__(self) -> None: ...
                def echo(self, s: str | None) -> str: ...
                def echo2(self, s: str) -> str: ...
                def maybe(self) -> str | None: ...
        """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }

    @Test
    fun nullable_field_and_property_from_getter() {
        val java = """
            @interface Nullable {}
            public class P {
                public @Nullable String name;
                public P() {}
                public @Nullable String getTitle() { return null; }
                public void setTitle(@Nullable String v) {}
            }
        """.trimIndent()
        val expected = """
            class P:
                name: str | None
                def __init__(self) -> None: ...
                @property
                def title(self) -> str | None: ...
                @title.setter
                def title(self, value: str | None) -> None: ...
        """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }
    // Javadocs to docstrings
    @Test
    fun class_and_methods_emit_multiline_docstrings_from_javadoc() {
        val java = """
            /**
             * Greeter class summary.
             * More details that should not appear in summary.
             */
            public class GreeterDoc {
                /**
                 * Default constructor summary.
                 * Extra line not included.
                 */
                public GreeterDoc() {}
                /**
                 * Say hello to a name.
                 * @param name the name to greet
                 * @return the greeting
                 */
                public String greet(String name) { return "hi"; }
                /**
                 * {@code util} link and code in summary.
                 */
                public static int util() { return 1; }
            }
        """.trimIndent()
        val expected = """
            class GreeterDoc:
                \"\"\"Greeter class summary.

                More details that should not appear in summary.\"\"\"
                def __init__(self) -> None:
                    \"\"\"Default constructor summary.

                    Extra line not included.\"\"\"
                    ...
                def greet(self, name: str) -> str:
                    \"\"\"Say hello to a name.

                    Args:
                      name: the name to greet

                    Returns:
                      the greeting\"\"\"
                    ...
                @staticmethod
                def util() -> int:
                    \"\"\"util link and code in summary.\"\"\"
                    ...
        """.trimIndent().trimEnd()
        val actual = DocletTestUtil.runDoclet(java)
        assertEquals(expected, actual)
    }

    @Test
    fun overloadedInstanceMethods_includeDocsForEachOverload() {
        val java = """
            public class OverDoc {
                /**
                 * Over 1 doc.
                 */
                public String f(int a) { return ""; }
                /**
                 * Over 2 doc.
                 */
                public String f(String b) { return ""; }
            }
        """.trimIndent()
        val text = DocletTestUtil.runDoclet(java)
        // Each overload should include a docstring block with the summary.
        assertTrue(
            text.contains(
                """
                    @overload
                    def f(self, a: int) -> str:
                        """ + "\"\"\"" + """Over 1 doc.""" + "\"\"\"" + """
                        ...
                """.trimIndent()
            ),
            "Expected docstring on first overload:\n$text"
        )
        assertTrue(
            text.contains(
                """
                    @overload
                    def f(self, b: str) -> str:
                        """ + "\"\"\"" + """Over 2 doc.""" + "\"\"\"" + """
                        ...
                """.trimIndent()
            ),
            "Expected docstring on second overload:\n$text"
        )
    }

    @Test
    fun overloadedStaticMethods_includeDocsForEachOverload() {
        val java = """
            public class OverStaticDoc {
                /**
                 * S1 doc.
                 */
                public static String f(int a, int b) { return ""; }
                /**
                 * S2 doc.
                 */
                public static String f(String s) { return ""; }
            }
        """.trimIndent()
        val text = DocletTestUtil.runDoclet(java)
        // For static overloads, expect both @overload and @staticmethod and docstring.
        assertTrue(
            text.contains(
                """
                    @overload
                    @staticmethod
                    def f(a: int, b: int) -> str:
                        """ + "\"\"\"" + """S1 doc.""" + "\"\"\"" + """
                        ...
                """.trimIndent()
            ),
            "Expected docstring on first static overload:\n$text"
        )
        assertTrue(
            text.contains(
                """
                    @overload
                    @staticmethod
                    def f(s: str) -> str:
                        """ + "\"\"\"" + """S2 doc.""" + "\"\"\"" + """
                        ...
                """.trimIndent()
            ),
            "Expected docstring on second static overload:\n$text"
        )
    }

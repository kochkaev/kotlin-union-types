package io.github.kochkaev.kotlin.uniontypes

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.kochkaev.kotlin.uniontypes.compiler.UnionTypeCompilerPluginRegistrar
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
abstract class BaseCompilerPluginTest {

    protected fun compile(
        @Language("kotlin") source: String,
        shouldFail: Boolean = false,
        errorMessage: String? = null,
        warningMessage: String? = null,
    ) {
        val sourceFile = SourceFile.kotlin("Test.kt", """
            package io.github.kochkaev.kotlin.uniontypes.test
            
            import io.github.kochkaev.kotlin.uniontypes.meta.*
            import kotlin.reflect.KClass

            $source
        """)

        val compilation = KotlinCompilation().apply {
            sources = listOf(sourceFile)
            compilerPluginRegistrars = listOf(UnionTypeCompilerPluginRegistrar())
            inheritClassPath = true
            verbose = false
        }

        val result = compilation.compile()
        val messages = result.messages

        if (shouldFail) {
            assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode, "Compilation should have failed but didn't.\nMessages:\n$messages")
            if (errorMessage != null) {
                assertTrue(messages.contains(errorMessage), "Expected error message not found: '$errorMessage'\nActual messages:\n$messages")
            }
        } else {
            assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, "Compilation failed with messages:\n$messages")
        }
        
        if (warningMessage != null) {
            assertTrue(messages.contains("w:"), "Expected a warning but none was found.\nMessages:\n$messages")
            assertTrue(messages.contains(warningMessage), "Expected warning message not found: '$warningMessage'\nActual messages:\n$messages")
        }
    }
}

class CompilerPluginTest: BaseCompilerPluginTest() {

    @Nested
    inner class UnionTypes {
        @Test
        fun `should allow assigning a correct type`() {
            compile("val x: @Union(String::class, Int::class) Any = \"hello\"")
        }

        @Test
        fun `should forbid assigning an incorrect type`() {
            compile(
                "val x: @Union(String::class, Int::class) Any = 1.0",
                shouldFail = true,
                errorMessage = "Type mismatch. Found: Double, but expected: String | Int"
            )
        }

        @Test
        fun `should allow correct return type`() {
            compile("fun f(): @Union(String::class) Any = \"a\"")
        }

        @Test
        fun `should forbid incorrect return type`() {
            compile(
                "fun f(): @Union(String::class) Any = 1",
                shouldFail = true,
                errorMessage = "Type mismatch. Found: Int, but expected: String"
            )
        }

        @Test
        fun `should handle parameterized types`() {
            compile("val x: @UnionAdv(Type(List::class, generics = [Type(String::class)])) Any = listOf(\"a\")")
        }

        @Test
        fun `should fail on incorrect parameterized type`() {
            compile(
                "val x: @UnionAdv(Type(List::class, generics = [Type(String::class)])) Any = listOf(1)",
                shouldFail = true,
                errorMessage = "Type mismatch. Found: List<Int>, but expected: List<String>"
            )
        }

        @Test
        fun `should handle typeParameter from a function`() {
            compile("fun <T: Number> process(value: @UnionAdv(Type(typeParameter = \"T\"), Type(String::class)) Any) {}\nfun main() { process<Int>(123); process<Int>(\"hello\") }")
        }

        @Test
        fun `should fail on incorrect typeParameter from a function`() {
            compile(
                "fun <T: Number> process(value: @UnionAdv(Type(typeParameter = \"T\"), Type(String::class)) Any) {}\nfun main() { process<Int>(true) }",
                shouldFail = true,
                errorMessage = "Type mismatch. Found: Boolean, but expected: Int | String"
            )
        }

        @Test
        fun `should handle nested union in @UnionAdv`() {
            compile("""
                typealias NestedUnion = @UnionAdv(
                    Type(String::class),
                    Type(union = [Type(Int::class), Type(Double::class)])
                ) Any

                val x: NestedUnion = "hello"
                val y: NestedUnion = 123
                val z: NestedUnion = 45.6
            """)
        }

        @Test
        fun `should fail for incorrect type in nested union`() {
            compile("""
                typealias NestedUnion = @UnionAdv(
                    Type(String::class),
                    Type(union = [Type(Int::class), Type(Double::class)])
                ) Any

                val x: NestedUnion = true
            """, shouldFail = true, errorMessage = "Type mismatch. Found: Boolean, but expected: String | Int | Double")
        }

        @Test
        fun `should combine stacked annotations`() {
            compile("val x: @Union(String::class) @Union(Int::class) Any = 1")
        }

        @Test
        fun `should fail for incorrect type with stacked annotations`() {
            compile(
                "val x: @Union(String::class) @Union(Int::class) Any = 1.0",
                shouldFail = true,
                errorMessage = "Type mismatch. Found: Double, but expected: String | Int"
            )
        }
    }

    @Nested
    inner class IntersectionTypes {
        @Test
        fun `should allow assigning a correct type to intersection`() {
            compile("""
                interface A
                interface B
                class C: A, B
                val x: @Intersection(A::class, B::class) Any = C()
            """)
        }

        @Test
        fun `should forbid assigning an incorrect type to intersection`() {
            compile("""
                interface A
                interface B
                class C: A
                val x: @Intersection(A::class, B::class) Any = C()
            """, shouldFail = true, errorMessage = "Type mismatch. Found: C, but expected: A & B")
        }

        @Test
        fun `should handle advanced intersection`() {
            compile("""
                interface A<T>
                interface B<T>
                class C<T>: A<T>, B<T>
                val x: @IntersectionAdv(Type(A::class, generics = [Type(String::class)]), Type(B::class, generics = [Type(String::class)])) Any = C<String>()
            """)
        }

        @Test
        fun `should fail for incorrect advanced intersection`() {
            compile("""
                interface A<T>
                interface B<T>
                class C<T>: A<T>, B<T>
                val x: @IntersectionAdv(Type(A::class, generics = [Type(String::class)]), Type(B::class, generics = [Type(Int::class)])) Any = C<String>()
            """, shouldFail = true, errorMessage = "Type mismatch. Found: C<String>, but expected: A<String> & B<Int>")
        }
    }

    @Nested
    inner class TypeAlias {
        @Language("kotlin")
        private val commonSource = "typealias A = @Union(String::class, Int::class, Long::class) Any"

        @Test
        fun `should expand union when typealias is used in annotation`() {
            compile("$commonSource\ntypealias B = @Union(A::class, Float::class) Any\nfun main() { val v: B = 7.8f }")
        }

        @Test
        fun `should constrain union when annotation is on a union typealias`() {
            compile("$commonSource\ntypealias C = @Union(String::class, Int::class) A\nfun main() { val v: C = 123 }")
        }

        @Test
        fun `should fail when assigning a filtered-out type`() {
            compile(
                "$commonSource\ntypealias C = @Union(String::class, Int::class) A\nfun main() { val v: C = 456L }",
                shouldFail = true,
                errorMessage = "Type mismatch. Found: Long, but expected: String | Int"
            )
        }

        @Test
        fun `should fail if constraining type is not in the base union`() {
            compile(
                "$commonSource\ntypealias D = @Union(String::class, Float::class) A",
                shouldFail = true,
                errorMessage = "The union of all members (String | Float) must be a subtype of or equivalent to the base type (String | Int | Long)."
            )
        }
    }

    @Nested
    inner class InheritanceAndOverrides {
        @Language("kotlin")
        private val base = """
            typealias U_S_I = @Union(String::class, Int::class) Any
            typealias U_S_I_D = @Union(String::class, Int::class, Double::class) Any
            open class Base {
                open fun f1(): U_S_I_D = "base"
                open fun f2(): U_S_I = "base"
                open val p1: U_S_I_D = "base"
                open var p2: U_S_I_D = "base"
            }
        """

        @Test
        fun `should allow overriding function with a subset of union`() {
            compile("$base\nclass D:Base(){ override fun f1(): U_S_I = \"d\" }")
        }

        @Test
        fun `should forbid overriding function with a superset of union`() {
            compile(
                "$base\nclass D:Base(){ override fun f2(): U_S_I_D = \"d\" }",
                shouldFail = true,
                errorMessage = "Type mismatch. Found: String | Int | Double, but expected: String | Int"
            )
        }

        @Test
        fun `should allow overriding val with a subtype`() {
            compile("$base\nclass D:Base(){ override val p1: String = \"d\" }")
        }

        @Test
        fun `should forbid overriding var type on override (invariance)`() {
            compile(
                "$base\nclass D:Base(){ override var p2: U_S_I = \"d\" }",
                shouldFail = true,
                errorMessage = "Type mismatch. Found: String | Int, but expected: String | Int | Double"
            )
        }

        @Test
        fun `should require intersection of return types from all parents`() {
            compile("""
                typealias U_S_I = @Union(String::class, Int::class) Any
                typealias U_N_CS = @Union(Number::class, CharSequence::class) Any
                interface I1 { fun f(): U_S_I }
                interface I2 { fun f(): U_N_CS }
                class C : I1, I2 {
                    override fun f(): @Union(String::class, Int::class) Any = "s"
                }
            """)
        }

        @Test
        fun `should fail if override does not satisfy all parents`() {
            compile("""
                typealias U_S_I = @Union(String::class, Int::class) Any
                typealias U_N_CS = @Union(Number::class, CharSequence::class) Any
                interface I1 { fun f(): U_S_I }
                interface I2 { fun f(): U_N_CS }
                class C : I1, I2 {
                    override fun f(): @Union(String::class, Double::class) Any = 1.0
                }
            """, shouldFail = true, errorMessage = "Type mismatch. Found: String | Double, but expected: String | Int")
        }
    }

    @Nested
    inner class ErrorCases {
        @Test
        fun `should forbid union on supertype`() {
            compile(
                "class MyClass : @Union(String::class) Any()",
                shouldFail = true,
                errorMessage = "Union and intersection type annotations is not allowed on supertypes"
            )
        }

        @Test
        fun `should forbid extension on union type property`() {
            compile(
                "val (@Union(String::class) Any).ext: Int\n get() = 0",
                shouldFail = true,
                errorMessage = "Extension functions/properties are not allowed on union/intersection types"
            )
        }

        @Test
        fun `should forbid union on context parameter`() {
            compile(
                "context(context: @Union(String::class) Any) fun f() {}",
                shouldFail = true,
                errorMessage = "Union/intersection types are not allowed on context parameters"
            )
        }

        @Test
        fun `should forbid using type and typeParameter simultaneously`() {
            compile(
                "val x: @UnionAdv(Type(type = String::class, typeParameter = \"T\")) Any = \"a\"",
                shouldFail = true,
                errorMessage = "Only one of 'type', 'typeParameter', 'union', or 'intersection' can be used at the same time."
            )
        }

        @Test
        fun `should fail when a type is annotated with both @Union and @Intersection`() {
            compile(
                """
                val x: @Union(String::class) @Intersection(CharSequence::class) Any = "hello"
                """,
                shouldFail = true,
                errorMessage = "A type cannot be annotated with both @Union/@UnionAdv and @Intersection/@IntersectionAdv at the same time."
            )
        }

        @Test
        fun `should fail on recursive type alias`() {
            compile(
                """
                typealias Recursive = @Union(Int::class, Recursive::class) Any
                val x: Recursive = 1
                """,
                shouldFail = true,
                errorMessage = "Recursive types in union/intersection are not supported."
            )
        }
    }

    @Nested
    inner class CastAndWhen {
        @Test
        fun `should warn on unreachable when branch`() {
            compile(
                """
                fun main() {
                    val x: @Union(String::class) Any = "a"
                    when(x) {
                        is String -> {}
                        is Int -> {} // Unreachable
                    }
                }
                """,
                shouldFail = true,
                errorMessage = "Check for instance is always 'false'."
            )
        }

        @Test
        fun `should error on a cast that will always fail`() {
            compile(
                """
                fun main() {
                    val x: @Union(String::class) Any = "a"
                    val y = x as Int
                }
                """,
                shouldFail = true,
                errorMessage = "This cast can never succeed."
            )
        }

        @Test
        fun `should warn on a useless safe cast`() {
            compile(
                """
                fun main() {
                    val x: @Union(String::class) Any = "a"
                    val y = x as? Int
                }
                """,
                warningMessage = "This cast can never succeed."
            )
        }

        @Test
        fun `should warn on an unsafe cast`() {
            compile(
                """
                fun main() {
                    val x: @Union(String::class, Int::class) Any = "a"
                    val y = x as String
                }
                """,
                warningMessage = "Unsafe cast of String | Int to String"
            )
        }
    }
    
    @Nested
    inner class Variance {
        @Test
        fun `should allow covariant (out) assignment`() {
            compile("""
                typealias ListOfCharSequence = @UnionAdv(Type(List::class, generics = [Type(type = CharSequence::class, variance = Variance.OUT)])) Any
                val x: ListOfCharSequence = listOf<String>("a", "b")
            """)
        }

        @Test
        fun `should fail for incorrect covariant (out) assignment`() {
            compile("""
                typealias ListOfNumber = @UnionAdv(Type(List::class, generics = [Type(type = Number::class, variance = Variance.OUT)])) Any
                val x: ListOfNumber = listOf<Any>(1, "a")
            """, shouldFail = true, errorMessage = "Type mismatch. Found: List<Any>, but expected: List<out Number>")
        }

        @Test
        fun `should allow contravariant (in) assignment`() {
            compile("""
                typealias ConsumerOfNumber = @UnionAdv(Type(MutableList::class, generics = [Type(type = Number::class, variance = Variance.IN)])) Any
                val x: ConsumerOfNumber = mutableListOf<Any>()
            """)
        }

        @Test
        fun `should fail for incorrect contravariant (in) assignment`() {
            compile(
                """
                typealias ConsumerOfInt = @UnionAdv(Type(MutableList::class, generics = [Type(type = Number::class, variance = Variance.IN)])) Any
                val x: ConsumerOfInt = mutableListOf<Int>()
            """, shouldFail = true, errorMessage = "Type mismatch. Found: MutableList<Int>, but expected: MutableList<in Number>")
        }

        @Test
        fun `should fail when variance is used on a non-generic type`() {
            compile("""
                val x: @UnionAdv(Type(type = String::class, variance = Variance.OUT)) Any = "a"
            """, shouldFail = true, errorMessage = "Variance can only be specified for generic types.")
        }
        
        @Test
        fun `should fail when variance is used on a top-level type in a union`() {
            compile("""
                val x: @UnionAdv(Type(type = List::class, generics = [Type(String::class)], variance = Variance.OUT)) Any = listOf("a")
            """, shouldFail = true, errorMessage = "Variance can only be specified for generic types.")
        }
    }

    @Nested
    inner class ComplexInteractions {
        @Test
        fun `should allow @Intersection to be applied to a Union typealias`() {
            compile(
                """
                typealias U = @Union(Collection::class, Iterable::class) Any
                val x: @IntersectionAdv(Type(type = List::class, generics = [Type(String::class)])) U = listOf("hello")
                """
            )
        }

        @Test
        fun `should fail when intersection on union is not a subtype of the union's members intersection`() {
            compile("""
                interface A
                interface B
                typealias U = @Union(A::class, B::class) Any
                typealias I = @Intersection(CharSequence::class) U
            """, shouldFail = true, errorMessage = "The intersection of all members (CharSequence) must be a subtype of or equivalent to the base type (A | B).")
        }

        @Test
        fun `should correctly intersect a type with a union type`() {
            compile("""
                interface A
                open class B
                class C: B(), A
                class D: B(), A
                class E: B()

                typealias U = @Union(C::class, D::class) Any
                // Resulting type is (A & C) | (A & D), which simplifies to C | D
                val x: @Intersection(A::class, U::class) Any = C()
                val y: @Intersection(A::class, U::class) Any = D()
            """)
        }

        @Test
        fun `should fail when assigned type does not match intersection with a union`() {
            compile("""
                interface A
                interface B
                interface C
                class ImplA: A
                class ImplAB: A, B
                class ImplAC: A, C

                typealias U_BC = @Union(ImplAB::class, ImplAC::class) Any
                // Intersection with A gives (A & ImplAB) | (A & ImplAC) which is ImplAB | ImplAC
                val x: @Intersection(A::class, U_BC::class) Any = ImplAB()
                val y: @Intersection(A::class, U_BC::class) Any = ImplAC()
                val z: @Intersection(A::class, U_BC::class) Any = ImplA()
            """, shouldFail = true, errorMessage = "Type mismatch. Found: ImplA, but expected: ImplAB | ImplAC")
        }
    }

    @Nested
    inner class AdvancedErrorCases {
        @Test
        fun `should fail when type parameter is not found`() {
            compile(
                "fun <T> f(p: @UnionAdv(Type(typeParameter = \"X\")) Any) {}",
                shouldFail = true,
                errorMessage = "Type parameter X not found"
            )
        }

        @Test
        fun `should fail when union of members is not a subtype of the base type`() {
            compile(
                "val x: @Union(String::class, Int::class) CharSequence = \"a\"",
                shouldFail = true,
                errorMessage = "The union of all members (String | Int) must be a subtype of or equivalent to the base type (CharSequence)."
            )
        }

        @Test
        fun `should fail when intersection of members is not a subtype of the base type`() {
            compile(
                """
                interface A
                interface B
                class C: A
                val x: @Intersection(A::class, B::class) C? = null
                """,
                shouldFail = true,
                errorMessage = "The intersection of all members (A & B) must be a subtype of or equivalent to the base type (C?)."
            )
        }
    }

    @Nested
    inner class ComplexGenericsAndInheritance {
        @Test
        fun `should handle deeply nested generics with type aliases`() {
            compile("""
                typealias StringList = List<String>
                typealias IntSet = Set<Int>
                typealias ComplexUnion = @UnionAdv(
                    Type(Map::class, generics = [Type(String::class), Type(StringList::class)]),
                    Type(List::class, generics = [Type(IntSet::class)])
                ) Any

                val x: ComplexUnion = mapOf("a" to listOf("b", "c"))
                val y: ComplexUnion = listOf(setOf(1, 2))
            """)
        }

        @Test
        fun `should fail for incorrect deeply nested generics with type aliases`() {
            compile("""
                typealias StringList = List<String>
                typealias IntSet = Set<Int>
                typealias ComplexUnion = @UnionAdv(
                    Type(Map::class, generics = [Type(String::class), Type(StringList::class)]),
                    Type(List::class, generics = [Type(IntSet::class)])
                ) Any

                val x: ComplexUnion = mapOf("a" to listOf(1, 2))
            """, shouldFail = true, errorMessage = "Type mismatch. Found: Map<String, List<Int>>, but expected: Map<String, List<String>> | List<Set<Int>>")
        }

        @Test
        fun `should handle multi-level inheritance with union type overrides`() {
            compile("""
                typealias U_S_I = @Union(String::class, Int::class) Any
                typealias U_S_I_D = @Union(String::class, Int::class, Double::class) Any
                
                open class A {
                    open fun f(): U_S_I_D = "a"
                }
                open class B : A() {
                    override fun f(): U_S_I = 1
                }
                class C : B() {
                    override fun f(): @Union(String::class) Any = "c"
                }
            """)
        }

        @Test
        fun `should fail on incorrect multi-level inheritance override`() {
            compile(
                """
                typealias U_S_I = @Union(String::class, Int::class) Any
                typealias U_S_I_D = @Union(String::class, Int::class, Double::class) Any

                open class A {
                    open fun f(): U_S_I = 1
                }
                class B : A() {
                    override fun f(): U_S_I_D = 1.0
                }
                """, shouldFail = true, errorMessage = "Type mismatch. Found: String | Int | Double, but expected: String | Int"
            )
        }

        @Test
        fun `should handle union types with star projection`() {
            compile("""
                typealias ListOrSet = @UnionAdv(
                    Type(List::class),
                    Type(Set::class)
                ) Any
    
                val x: ListOrSet = listOf("a", 1)
                val y: ListOrSet = setOf(1.0, true)
            """)
        }
    
        @Test
        fun `should handle star projection with empty Type`() {
            compile("""
                typealias ListOfAnything = @UnionAdv(Type(List::class, generics = [Type()])) Any
                val x: ListOfAnything = listOf(1, "a", true)
            """)
        }

        @Test
        fun `should calculate intersection for where clauses with different supertypes`() {
            compile("""
                typealias A = @Union(String::class, Int::class) Comparable<*>
                typealias B = @Union(Number::class, CharSequence::class) Any
                fun <T> f(v: T) where T: A, T: B {}
                fun main() {
                    f(123)
                    f("hello")
                }
            """)
        }
    
        @Test
        fun `should fail for type not in where clause intersection`() {
            compile("""
                typealias A = @Union(String::class, Int::class) Comparable<*>
                typealias B = @Union(Number::class, CharSequence::class) Any
                fun <T> f(v: T) where T: B, T: A {}
                fun main() {
                    f(123L)
                }
            """, shouldFail = true, errorMessage = "Type mismatch. Found: Long, but expected: String | Int")
        }

        @Test
        fun `should handle renamed type parameter in override`() {
            compile("""
                open class Base<T> {
                    open fun process(value: @UnionAdv(Type(typeParameter = "T"), Type(String::class)) Any?) {}
                }
    
                class Derived<D> : Base<D>() {
                    override fun process(value: @UnionAdv(Type(typeParameter = "D"), Type(String::class)) Any?) {}
                }
    
                fun main() {
                    val d = Derived<Int>()
                    d.process(123)
                    d.process("hello")
                    d.process(null)
                }
            """)
        }
    
        @Test
        fun `should handle more restrictive bound in override`() {
            compile("""
                open class Base<T : Number> {
                    open fun process(value: @UnionAdv(Type(typeParameter = "T")) Any) {}
                }
    
                class Derived<D : Int> : Base<D>() {
                    override fun process(value: @UnionAdv(Type(typeParameter = "D")) Any) {}
                }
    
                fun main() {
                    val d = Derived<Int>()
                    d.process(123)
                }
            """)
        }
    }

    @Nested
    inner class MiscCases {
        @Test
        fun `should allow null for nullable union type`() {
            compile("val x: @Union(String::class, Int::class) Any? = null")
        }

        @Test
        fun `should forbid null for non-nullable union type`() {
            compile(
                "val x: @Union(String::class, Int::class) Any = null",
                shouldFail = true,
                errorMessage = "Null cannot be a value of a non-null type 'Any'"
            )
        }

        @Test
        fun `should check vararg with explicit type argument`() {
            compile("fun <T> process(vararg items: T) {}\nfun main() { process<@Union(String::class, Int::class) Any>(\"a\", 1, \"b\", 2) }")
        }

        @Test
        fun `should fail for vararg when common supertype is inferred`() {
            compile(
                "val list: List<@Union(String::class, Int::class) Any> = listOf(\"a\", 1, 2.0)",
                shouldFail = true,
                errorMessage = "Type mismatch. Found: List<Comparable<*> & Serializable>, but expected: List<String | Int>"
            )
        }

        @Test
        fun `should check union in lambda argument`() {
            compile("""
                val lambda: (id: @Union(String::class, Int::class) Any) -> Unit = { }
                fun main() { lambda("a"); lambda(1) }
            """)
        }
    
        @Test
        fun `should fail for incorrect type in lambda argument`() {
            compile("""
                val lambda: (id: @Union(String::class, Int::class) Any) -> Unit = { }
                fun main() { lambda(1.0) }
            """, shouldFail = true, errorMessage = "Type mismatch. Found: Double, but expected: String | Int")
        }
    }
}

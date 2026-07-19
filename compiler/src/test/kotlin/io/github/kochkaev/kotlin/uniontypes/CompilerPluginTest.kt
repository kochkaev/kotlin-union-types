package io.github.kochkaev.kotlin.uniontypes

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.github.kochkaev.kotlin.uniontypes.compiler.UnionTypeCompilerPluginRegistrar
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
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
            package io.github.kochkaev.kotlin.uniontypes
            
            import io.github.kochkaev.kotlin.uniontypes.annotations.*
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

class AssignmentTests : BaseCompilerPluginTest() {
    @Test fun `should allow assigning a correct type`() = compile("val x: @Union(String::class, Int::class) Any = \"hello\"")
    @Test fun `should forbid assigning an incorrect type`() = compile("val x: @Union(String::class, Int::class) Any = 1.0", shouldFail = true, errorMessage = "Type mismatch")
}

class FunctionTests : BaseCompilerPluginTest() {
    @Test fun `should allow correct return type`() = compile("fun f(): @Union(String::class) Any = \"a\"")
    @Test fun `should forbid incorrect return type`() = compile("fun f(): @Union(String::class) Any = 1", shouldFail = true, errorMessage = "Type mismatch")
}

class TypeAliasTests : BaseCompilerPluginTest() {
    @Language("kotlin") private val commonSource = "typealias A = @Union(String::class, Int::class, Long::class) Any"
    @Test fun `should expand union when typealias is used in annotation`() = compile("$commonSource\ntypealias B = @Union(A::class, Float::class) Any\nfun main() { val v: B = 7.8f }")
    @Test fun `should constrain union when annotation is on a union typealias`() = compile("$commonSource\ntypealias C = @Union(String::class, Int::class) A\nfun main() { val v: C = 123 }")
    @Test fun `should fail when assigning a filtered-out type`() = compile("$commonSource\ntypealias C = @Union(String::class, Int::class) A\nfun main() { val v: C = 456L }", shouldFail = true, errorMessage = "Type mismatch")
    @Test fun `should fail if constraining type is not in the base union`() = compile("$commonSource\ntypealias D = @Union(String::class, Float::class) A", shouldFail = true, errorMessage = "is not a subtype of")
}

class AdvUnionTests : BaseCompilerPluginTest() {
    @Test fun `should handle parameterized types`() = compile("val x: @UnionAdv(Type(List::class, generics = [Type(String::class)])) Any = listOf(\"a\")")
    @Test fun `should fail on incorrect parameterized type`() = compile("val x: @UnionAdv(Type(List::class, generics = [Type(String::class)])) Any = listOf(1)", shouldFail = true, errorMessage = "Type mismatch")
    @Test fun `should handle typeParameter from a function`() = compile("fun <T: Number> process(value: @UnionAdv(Type(typeParameter = \"T\"), Type(String::class)) Any) {}\nfun main() { process<Int>(123); process<Int>(\"hello\") }")
    @Test fun `should fail on incorrect typeParameter from a function`() = compile("fun <T: Number> process(value: @UnionAdv(Type(typeParameter = \"T\"), Type(String::class)) Any) {}\nfun main() { process<Int>(true) }", shouldFail = true, errorMessage = "Type mismatch")
    @Test fun `should forbid using type and typeParameter simultaneously`() = compile("val x: @UnionAdv(Type(type = String::class, typeParameter = \"T\")) Any = \"a\"", shouldFail = true, errorMessage = "Cannot use 'type' and 'typeParameter' at the same time")
}

class VarargAndNullabilityTests : BaseCompilerPluginTest() {
    @Test fun `should check vararg with explicit type argument`() = compile("fun <T> process(vararg items: T) {}\nfun main() { process<@Union(String::class, Int::class) Any>(\"a\", 1, \"b\", 2) }")
    @Test fun `should fail for vararg when common supertype is inferred`() = compile("val list: List<@Union(String::class, Int::class) Any> = listOf(\"a\", 1, 2.0)", shouldFail = true, errorMessage = "Type mismatch")
    @Test fun `should allow null for nullable union type`() = compile("val x: @Union(String::class, Int::class) Any? = null")
    @Test fun `should forbid null for non-nullable union type`() = compile("val x: @Union(String::class, Int::class) Any = null", shouldFail = true, errorMessage = "Null cannot be a value of a non-null type")
}

class ComplexGenericsTests : BaseCompilerPluginTest() {
    @Test
    fun `should check union type in type arguments with explicit type`() {
        compile("""
            val list: List<@Union(String::class, Int::class) Any> = listOf<@Union(String::class, Int::class) Any>("a", 1, "b")
        """)
    }

    @Test
    fun `should fail when inferred type for listOf is not a subtype`() {
        // listOf("a", 1, "b") infers List<Serializable & Comparable<*>>, which is not a subtype of List<String | Int>
        compile("""
            val list: List<@Union(String::class, Int::class) Any> = listOf("a", 1, "b")
        """, shouldFail = true, errorMessage = "Type mismatch")
    }

    @Test
    fun `should calculate intersection for 'where' clauses with different supertypes`() {
        compile("""
            typealias A = @Union(String::class, Int::class) Comparable<*>
            typealias B = @Union(Number::class, CharSequence::class) Any
            fun <T> f(v: T) where T: A, T: B {}
            fun main() {
                f(123)
            }
        """)
    }

    @Test
    fun `should fail for type not in 'where' clause intersection`() {
        compile("""
            typealias A = @Union(String::class, Int::class) Comparable<*>
            typealias B = @Union(Number::class, CharSequence::class) Any
            fun <T> f(v: T) where T: B, T: A {}
            fun main() {
                f(123L)
            }
        """, shouldFail = true, errorMessage = "Type mismatch")
    }
}

class PropertyAndOverrideTests : BaseCompilerPluginTest() {
    @Language("kotlin") private val base = """
        typealias U_S_I = @Union(String::class, Int::class) Any
        typealias U_S_I_D = @Union(String::class, Int::class, Double::class) Any
        open class Base {
            open fun f1(): U_S_I_D = "base"
            open fun f2(): U_S_I = "base"
            open val p1: U_S_I_D = "base"
            open var p2: U_S_I_D = "base"
        }
    """

    @Test fun `should allow overriding function with a subset of union`() = compile("$base\nclass D:Base(){ override fun f1(): U_S_I = \"d\" }")
    @Test fun `should forbid overriding function with a superset of union`() = compile("$base\nclass D:Base(){ override fun f2(): U_S_I_D = \"d\" }", shouldFail = true, errorMessage = "Type mismatch")
    @Test fun `should allow overriding val with a subtype`() = compile("$base\nclass D:Base(){ override val p1: String = \"d\" }")
    @Test fun `should forbid overriding var type on override (invariance)`() = compile("$base\nclass D:Base(){ override var p2: U_S_I = \"d\" }", shouldFail = true, errorMessage = "Type mismatch")
}

class LambdaAndFunctionTypeTests : BaseCompilerPluginTest() {
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
        """, shouldFail = true, errorMessage = "Type mismatch")
    }
}

class AdvancedAnnotationTests : BaseCompilerPluginTest() {
    @Test
    fun `should combine stacked annotations`() {
        compile("val x: @Union(String::class) @Union(Int::class) Any = 1")
    }

    @Test
    fun `should fail for incorrect type with stacked annotations`() {
        compile("val x: @Union(String::class) @Union(Int::class) Any = 1.0", shouldFail = true, errorMessage = "Type mismatch")
    }
}

class MultipleInheritanceTests : BaseCompilerPluginTest() {
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
        """, shouldFail = true, errorMessage = "Type mismatch")
    }
}

class ForbiddenCasesTests : BaseCompilerPluginTest() {
    @Test fun `should forbid union on supertype`() = compile("class MyClass : @Union(String::class) Any()", shouldFail = true, errorMessage = "@Union/@UnionAdv annotation is not allowed on supertypes")
    @Test fun `should forbid extension on union type property`() = compile("val (@Union(String::class) Any).ext: Int\n get() = 0", shouldFail = true, errorMessage = "Extension functions/properties are not allowed on union types")
    @Test fun `should forbid union on context parameter`() = compile("context(context: @Union(String::class) Any) fun f() {}", shouldFail = true, errorMessage = "Union types are not allowed on context parameters")
}

// TODO: not implemented yet
class CastAndWhenTests : BaseCompilerPluginTest() {
    @Test
    fun `should warn on unreachable when branch`() {
        compile("""
            fun main() {
                val x: @Union(String::class) Any = "a"
                when(x) {
                    is String -> {}
                    is Int -> {} // Unreachable
                }
            }
        """, warningMessage = "Unreachable branch")
    }

    @Test
    fun `should error on a cast that will always fail`() {
        compile("""
            fun main() {
                val x: @Union(String::class) Any = "a"
                val y = x as Int
            }
        """, shouldFail = true, errorMessage = "Cast will always fail")
    }

    @Test
    fun `should warn on a useless safe cast`() {
        compile("""
            fun main() {
                val x: @Union(String::class) Any = "a"
                val y = x as? Int
            }
        """, warningMessage = "Useless cast")
    }

    @Test
    fun `should warn on an unsafe cast`() {
        compile("""
            fun main() {
                val x: @Union(String::class, Int::class) Any = "a"
                val y = x as String
            }
        """, warningMessage = "Unsafe cast")
    }
}

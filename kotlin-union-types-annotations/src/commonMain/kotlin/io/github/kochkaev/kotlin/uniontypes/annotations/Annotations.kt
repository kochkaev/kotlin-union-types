@file:Suppress("unused", "RedundantVisibilityModifier")

package io.github.kochkaev.kotlin.uniontypes.annotations

import kotlin.reflect.KClass

/**
 * Defines a union type, restricting the annotated type to one of the specified `types`.
 *
 * This annotation allows you to enforce that a variable, parameter, or return value
 * can only hold instances of a limited set of types, even if the base type is a common
 * supertype like `Any`. The compiler plugin will then check assignments and usages
 * to ensure type safety.
 *
 * **Basic Usage:**
 *
 * You can apply `@Union` to any type. The base type (e.g., `Any`) must be a supertype
 * of all types listed in the annotation.
 *
 * ```kotlin
 * // 'value' can be either a String or an Int.
 * val value: @Union(String::class, Int::class) Any
 *
 * value = "hello" // OK
 * value = 123     // OK
 * value = 1.0     // Compilation error: Type mismatch
 * ```
 *
 * **With Type Aliases:**
 *
 * For better readability and reuse, you can combine `@Union` with a type alias.
 *
 * ```kotlin
 * typealias StringOrInt = @Union(String::class, Int::class) Any
 *
 * fun process(data: StringOrInt) { /* ... */ }
 *
 * process("text") // OK
 * process(42)     // OK
 * process(true)   // Compilation error: Type mismatch
 * ```
 *
 * You can also create more restrictive type aliases from existing union types:
 *
 * ```kotlin
 * typealias NumberOrString = @Union(Number::class, String::class) Any
 *
 * // A more specific union that only allows Int or String.
 * // Note that Int is a subtype of Number.
 * typealias IntOrString = @Union(Int::class, String::class) NumberOrString
 *
 * val a: IntOrString = 100 // OK
 * val b: IntOrString = "b" // OK
 * val c: IntOrString = 1.0 // Error: Double is not Int or String
 * ```
 *
 * @param types An array of `KClass` references representing the allowed types in the union.
 * @see UnionAdv for a more powerful version that supports generics and type parameters.
 * @see Intersection
 */
@Target(AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME) // Keep for reflection
@Repeatable
public annotation class Union(vararg val types: KClass<*>)

/**
 * Defines an advanced union type with support for generics and type parameters.
 *
 * `@UnionAdv` is a more powerful alternative to `@Union` when you need to define
 * union types involving generic type arguments or forward type parameters from
 * the enclosing scope (like from a generic function or class).
 *
 * **Usage with Generics:**
 *
 * Use `Type` to specify a main type and its `generics`.
 *
 * ```kotlin
 * // Represents a union of List<String> or List<Int>
 * val list: @UnionAdv(
 *     Type(List::class, generics = [Type(String::class)]),
 *     Type(List::class, generics = [Type(Int::class)])
 * ) Any
 *
 * list = listOf("a", "b") // OK
 * list = listOf(1, 2)     // OK
 * list = listOf(1.0)      // Compilation error: Type mismatch
 * ```
 *
 * **Usage with Type Parameters:**
 *
 * You can reference a type parameter from a generic function or class by its name.
 * This allows creating flexible union types that adapt to the caller's context.
 *
 * ```kotlin
 * // This function accepts either a value of type T or a String.
 * fun <T : Number> process(value: @UnionAdv(Type(typeParameter = "T"), Type(String::class)) Any) {
 *     // ...
 * }
 *
 * process<Int>(123)       // OK, T is Int
 * process<Double>(1.23)   // OK, T is Double
 * process<Int>("hello")   // OK
 * process<Int>(true)      // Compilation error: Type mismatch
 * ```
 *
 * @param types A vararg of `Type` instances, each defining a component of the union.
 * @see Union for a simpler annotation for non-generic types.
 * @see Type for how to construct the arguments for this annotation.
 * @see IntersectionAdv
 */
@Target(AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME) // Keep for reflection
@Repeatable
public annotation class UnionAdv(vararg val types: Type)

/**
 * Defines an intersection type, requiring the annotated type to conform to all specified `types`.
 *
 * This annotation is conceptually similar to Kotlin's `where` clause for generics, but it can be
 * applied to any type, parameter, or return value. It enforces that a value must be a subtype
 * of all types listed in the intersection.
 *
 * **Basic Usage:**
 *
 * ```kotlin
 * // 'value' must be both a CharSequence and a Serializable.
 * val value: @Intersection(CharSequence::class, Serializable::class) Any
 *
 * value = "hello" // OK, String is both CharSequence and Serializable
 * value = 123L // Compilation Error: Long is not CharSequence
 * ```
 *
 * **With Type Aliases:**
 *
 * Intersections are powerful when combined with type aliases to create complex, reusable constraints.
 *
 * ```kotlin
 * typealias ComparableAndSerializable = @Intersection(Comparable::class, Serializable::class) Any
 *
 * fun <T: ComparableAndSerializable> sortAndStore(items: List<T>) {
 *     // ...
 * }
 * ```
 *
 * @param types An array of `KClass` references representing all required types for the intersection.
 * @see IntersectionAdv for a version that supports generics.
 * @see Union
 */
@Target(AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME) // Keep for reflection
@Repeatable
public annotation class Intersection(vararg val types: KClass<*>)

/**
 * Defines an advanced intersection type with support for generics and type parameters.
 *
 * `@IntersectionAdv` provides the same functionality as `@Intersection` but adds the ability
 * to work with generic type arguments and forward type parameters from an enclosing scope.
 *
 * **Usage with Generics:**
 *
 * ```kotlin
 * // Requires a value to be a Comparable list of Strings.
 * val value: @IntersectionAdv(
 *     Type(List::class, generics = [Type(String::class)]),
 *     Type(RandomAccess::class)
 * ) Any
 *
 * // ArrayList<String> implements both List<String> and RandomAccess.
 * value = arrayListOf("a", "b") // OK
 * ```
 *
 * @param types A vararg of `Type` instances, each defining a component of the intersection.
 * @see Intersection for a simpler annotation for non-generic types.
 * @see Type for how to construct the arguments for this annotation.
 * @see UnionAdv
 */
@Target(AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME) // Keep for reflection
@Repeatable
public annotation class IntersectionAdv(vararg val types: Type)

/**
 * A descriptor used within `@UnionAdv` and `@IntersectionAdv` to define a type,
 * including its generics or a reference to a type parameter.
 *
 * **You must provide either `type` or `typeParameter`, but not both.**
 *
 * @param type The main `KClass` of this type component (e.g., `List::class`).
 * @param typeParameter The name of a type parameter from an enclosing scope (e.g., `"T"` in `fun <T>...`).
 * @param generics An array of nested `Type` instances for specifying generic arguments (e.g., for `List<String>`, `type` would be `List::class` and `generics` would be `[Type(String::class)]`).
 */
@Target()
@Retention(AnnotationRetention.RUNTIME) // Keep for reflection
public annotation class Type(
    val type: KClass<*> = Any::class,
    val typeParameter: String = "",
    vararg val generics: Type = []
)

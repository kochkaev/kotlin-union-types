@file:Suppress("unused", "RedundantVisibilityModifier")

package io.github.kochkaev.kotlin.uniontypes.annotations

import kotlin.reflect.KClass

/**
 * Marks a type usage as a "union type", restricting its possible
 * values to the specified [types] and their subtypes.
 *
 * The annotated type should typically be a supertype of all specified
 * types, like `Any` or `Serializable`.
 *
 * Example:
 * ```kotlin
 * val id: @UnionType(String::class, Long::class) Any
 * ```
 */
@Target(AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME) // Keep for reflection
@Repeatable
public annotation class Union(vararg val types: KClass<*>)

@Target(AnnotationTarget.TYPE, AnnotationTarget.TYPE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME) // Keep for reflection
@Repeatable
public annotation class AdvUnion(vararg val types: Type)

@Target()
@Retention(AnnotationRetention.RUNTIME) // Keep for reflection
public annotation class Type(
    val type: KClass<*> = Any::class,
    val typeParameter: String = "",
    vararg val generics: Type = []
)


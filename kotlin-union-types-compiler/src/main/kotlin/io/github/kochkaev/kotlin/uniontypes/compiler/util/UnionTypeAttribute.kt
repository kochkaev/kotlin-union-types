package io.github.kochkaev.kotlin.uniontypes.compiler.util

import org.jetbrains.kotlin.fir.types.ConeAttribute
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import kotlin.reflect.KClass

class UnionTypeAttribute(
    val types: List<ConeKotlinType>
) : ConeAttribute<UnionTypeAttribute>() {

    override val key: KClass<out UnionTypeAttribute>
        get() = UnionTypeAttribute::class

    override fun union(other: UnionTypeAttribute?): UnionTypeAttribute {
        if (other == null) return this
        return UnionTypeAttribute((this.types + other.types).distinct())
    }

    override fun intersect(other: UnionTypeAttribute?): UnionTypeAttribute? {
        if (other == null) return this
        val intersected = this.types.intersect(other.types.toSet()).toList()
        return if (intersected.isNotEmpty()) UnionTypeAttribute(intersected) else null
    }

    override fun add(other: UnionTypeAttribute?): UnionTypeAttribute = union(other)
    override fun isSubtypeOf(other: UnionTypeAttribute?): Boolean {
        if (other == null) return true
        return other.types.containsAll(this.types)
    }

    override fun toString(): String =
        "union(${types.joinToString(" | ")})"
    override fun renderForReadability(): String = toString()

    override val keepInInferredDeclarationType: Boolean
        get() = true
}
package io.github.kochkaev.kotlin.uniontypes.compiler.util

import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType.Companion.union
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.types.model.TypeSubstitutorMarker

class UnionBuilder private constructor(
    val declaration: DeclarationInfo? = null,
    val substitutor: TypeSubstitutorMarker? = null,
    val autoExpand: Boolean = true,
    val skipValidCheck: Boolean = false,
) {
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    operator fun invoke(
        type: ConeKotlinType,
        unionOverride: List<ConeKotlinType>? = null,
        intersectionOverride: List<ConeKotlinType>? = null,
        autoExpand: Boolean = this.autoExpand,
        skipValidCheck: Boolean = this.skipValidCheck,
    ) = type.union(
        declaration = declaration,
        substitutor = substitutor,
        unionOverride = unionOverride,
        intersectionOverride = intersectionOverride,
        autoExpand = autoExpand,
        skipValidCheck = skipValidCheck,
        nullIfNotValid = false,
    )!!
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    operator fun invoke(
        type: ConeKotlinType,
        unionOverride: List<ConeKotlinType>? = null,
        intersectionOverride: List<ConeKotlinType>? = null,
        autoExpand: Boolean = this.autoExpand,
        nullIfNotValid: Boolean,
        skipValidCheck: Boolean = this.skipValidCheck || nullIfNotValid,
    ) = type.union(
        declaration = declaration,
        substitutor = substitutor,
        unionOverride = unionOverride,
        intersectionOverride = intersectionOverride,
        autoExpand = autoExpand,
        skipValidCheck = skipValidCheck,
        nullIfNotValid = nullIfNotValid,
    )

    fun with(
        declaration: DeclarationInfo? = this.declaration,
        substitutor: TypeSubstitutorMarker? = this.substitutor,
        autoExpand: Boolean = this.autoExpand,
        skipValidCheck: Boolean = this.skipValidCheck,
    ) = UnionBuilder(declaration, substitutor, autoExpand, skipValidCheck)

    companion object {
        fun of(
            declaration: DeclarationInfo? = null,
            substitutor: TypeSubstitutorMarker? = null,
            autoExpand: Boolean = true,
            skipValidCheck: Boolean = false,
        ) = UnionBuilder(declaration, substitutor, autoExpand, skipValidCheck)
    }
}
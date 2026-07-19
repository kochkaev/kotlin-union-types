package io.github.kochkaev.kotlin.uniontypes.compiler.util

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.getSuperTypes
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.fir.types.abbreviatedType
import org.jetbrains.kotlin.fir.types.abbreviatedTypeOrSelf
import org.jetbrains.kotlin.fir.types.canBeNull
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isNullableNothing
import org.jetbrains.kotlin.fir.types.isSubtypeOf
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.types.model.TypeSubstitutorMarker
import org.jetbrains.kotlin.types.model.TypeSystemInferenceExtensionContext
import org.jetbrains.kotlin.types.model.safeSubstitute

class UnionConeType private constructor(
    val coneType: ConeKotlinType,
    val substitutor: TypeSubstitutorMarker? = null,
    val declaration: FirDeclaration? = null,
    val unionOverride: List<ConeKotlinType>? = null,
) {
    context(context: CheckerContext)
    private val typeContext: TypeSystemInferenceExtensionContext
        get() = context.session.typeContext

    companion object {
        fun ConeKotlinType.union(
            declaration: FirDeclaration? = null,
            substitutor: TypeSubstitutorMarker? = null,
            unionOverride: List<ConeKotlinType>? = null,
        ) = UnionConeType(this, substitutor, declaration, unionOverride)

        fun builder(
            declaration: FirDeclaration? = null,
            substitutor: TypeSubstitutorMarker? = null,
        ) = { coneType: ConeKotlinType -> coneType.union(declaration, substitutor) }
    }

    private var _isUnionType: Boolean? = null
    context(context: CheckerContext, diagnosticReporter: DiagnosticReporter?)
    val isUnionType: Boolean
        get() = _isUnionType.elseIfNull {
            _isUnionType = fullyResolvedUnion.isNotEmpty()
            _isUnionType!!
        }
    private var _isDeclaredUnionType: Boolean? = null
    context(context: CheckerContext)
    val isDeclaredUnionType: Boolean
        get() = _isDeclaredUnionType.elseIfNull {
            _isDeclaredUnionType = unionAnnotations.isNotEmpty()
            _isDeclaredUnionType!!
        }
    private var _unionAnnotations: List<FirAnnotation>? = null
    context(context: CheckerContext)
    val unionAnnotations: List<FirAnnotation>
        get() = _unionAnnotations.elseIfNull {
            _unionAnnotations = thisType.getUnionAnnotations()
            _unionAnnotations!!
        }
    private var _unionRaw: List<ConeKotlinType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val unionRaw: List<ConeKotlinType>
        get() = _unionRaw.elseIfNull {
            _unionRaw = unionAnnotations.unwrapUnionsOrEmptyOrNullIfError(declaration)
            if (_unionRaw == null) {
                _isBroken = true
                _unionRaw = listOf()
            }
            _unionRaw!!
        }
    private var _declaredUnion: List<ConeKotlinType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val declaredUnion: List<ConeKotlinType>
        get() = _declaredUnion.elseIfNull {
            _declaredUnion = unionRaw.map { substituteOrSelf(it) }
            _declaredUnion!!
        }
    private var _union: List<ConeKotlinType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val union: List<ConeKotlinType>
        get() = _union.elseIfNull {
            _union = unionOverride ?: declaredUnion
            _union!!
        }
    private var _unionOrThis: List<ConeKotlinType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val unionOrThis: List<ConeKotlinType>
        get() = _unionOrThis.elseIfNull {
            _unionOrThis = union.ifEmpty { listOf(thisType) }
            _unionOrThis!!
        }
    private var _unionWrapped: List<UnionConeType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val unionWrapped: List<UnionConeType>
        get() = _unionWrapped.elseIfNull {
            _unionWrapped = union.map { copyTo(it) }
            _unionWrapped!!
        }
    private var _unionWrappedOrThis: List<UnionConeType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val unionWrappedOrThis: List<UnionConeType>
        get() = _unionWrappedOrThis.elseIfNull {
            _unionWrappedOrThis = unionWrapped.ifEmpty { listOf(this) }
            _unionWrappedOrThis!!
        }
    private var _resolvedUnion: List<ConeKotlinType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val resolvedUnion: List<ConeKotlinType>
        get() = _resolvedUnion.elseIfNull {
            _resolvedUnion = union.fold(mutableListOf()) { aac, it ->
                val resolved = it.tryRecursiveResolveTypealiasUnion()
                if (!resolved.isNullOrEmpty()) aac += resolved
                else aac += it
                aac
            }
            _resolvedUnion!!
        }
    private var _fullyResolvedUnion: List<ConeKotlinType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val fullyResolvedUnion: List<ConeKotlinType>
        get() = _fullyResolvedUnion.elseIfNull {
            _fullyResolvedUnion = resolvedUnion
            if (_fullyResolvedUnion.isNullOrEmpty()) {
                val builder = toBuilder()
                val nextType = coneType.unwrapTypeAliasOrNull()?.coneType?.let {
                    it.abbreviatedType ?: it
                }
                if (nextType != null) {
                    val next = builder(nextType)
                    _fullyResolvedUnion = next.fullyResolvedUnion
                }
            }
            _fullyResolvedUnion!!
        }
    private var _fullyResolvedUnionOrThis: List<ConeKotlinType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val fullyResolvedUnionOrThis: List<ConeKotlinType>
        get() = _fullyResolvedUnionOrThis.elseIfNull {
            _fullyResolvedUnionOrThis = fullyResolvedUnion.ifEmpty { listOf(thisType) }
            _fullyResolvedUnionOrThis!!
        }
    private var _fullyResolvedUnionWrapped: List<UnionConeType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val fullyResolvedUnionWrapped: List<UnionConeType>
        get() = _fullyResolvedUnionWrapped.elseIfNull {
            _fullyResolvedUnionWrapped = fullyResolvedUnion.map { copyTo(it) }
            _fullyResolvedUnionWrapped!!
        }
    private var _fullyResolvedUnionWrappedOrThis: List<UnionConeType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val fullyResolvedUnionWrappedOrThis: List<UnionConeType>
        get() = _fullyResolvedUnionWrappedOrThis.elseIfNull {
            _fullyResolvedUnionWrappedOrThis = fullyResolvedUnionWrapped.ifEmpty { listOf(this) }
            _fullyResolvedUnionWrappedOrThis!!
        }

    private var _resolvedType: ConeKotlinType? = null
    context(context: CheckerContext)
    val resolvedType: ConeKotlinType
        get() = _resolvedType.elseIfNull {
            _resolvedType = substituteOrSelf(coneType.fullyExpandedType())
            _resolvedType!!
        }

    private var _thisType: ConeKotlinType? = null
    context(context: CheckerContext)
    val thisType: ConeKotlinType
        get() = _thisType.elseIfNull {
            _thisType = substituteOrSelf(coneType.abbreviatedTypeOrSelf)
            _thisType!!
        }

    private var _isNullable: Boolean? = null
    context(context: CheckerContext)
    val isNullable: Boolean
        get() = _isNullable.elseIfNull {
            _isNullable = thisType.canBeNull(context.session, true)
            _isNullable!!
        }

    private var _isBroken: Boolean? = null
    context(context: CheckerContext, diagnosticReporter: DiagnosticReporter?)
    val isBroken: Boolean
        get() = _isBroken.elseIfNull {
            unionRaw
            if (_isBroken == null) _isBroken = false
            _isBroken!!
        }

    val isUnionOverrode: Boolean = !unionOverride.isNullOrEmpty()


    context(context: CheckerContext, reporter: DiagnosticReporter?)
    fun isCompatible(
        that: UnionConeType,
        checkNullability: Boolean = false,
    ): Boolean = with(context) {
        if (isBroken) return true

        val target = thisType
        val other = that.thisType
        val isTypeParameter = target is ConeTypeParameterType
        val isCompatible = other.isSubtypeOf(target, context.session)
        var genericsMatches = true
        var isInUnion = true
        val nullabilityMatches = if (checkNullability) isNullable <= that.isNullable else true
        if (target.isNullableNothing || other.isNullableNothing) return nullabilityMatches

        if (isCompatible) {
            var supertype: ConeKotlinType?
            if (target.equalsClasses(other)) supertype = other
            else {
                val lookupTag = (other as? ConeClassLikeType)?.lookupTag
                val symbol = lookupTag?.toSymbol()
                val supertypes = symbol?.getSuperTypes(context.session) ?: listOf()
                supertype = supertypes.firstOrNull { target.equalsClasses(it) }?.let { that.substituteOrSelf(it) }
            }
            val baseArguments = target.typeArguments
            val substitutor = context.session.typeContext.createSubstitutorForSuperTypes(other)
            supertype?.typeArguments?.forEachIndexed { i, projection ->
                val expect = baseArguments[i].type
                val found = projection.type?.let { substituteOrSelf(it, substitutor) }
                val matches = expect == null || found != null && copyTo(expect).isCompatible(that.copyTo(found))
                genericsMatches = genericsMatches && matches
            }
        }
        if (isUnionType) {
            val otherUnion = that.fullyResolvedUnionWrapped
            isInUnion =
                if (otherUnion.isEmpty()) unionMatches(fullyResolvedUnionWrapped, that)
                else unionMatches(fullyResolvedUnionWrapped, otherUnion)
        }

        return (isCompatible || isTypeParameter) && genericsMatches && isInUnion && nullabilityMatches
    }

    context(context: CheckerContext)
    fun substitute(other: ConeKotlinType, customSubstitutor: TypeSubstitutorMarker? = substitutor): ConeKotlinType? =
        customSubstitutor?.safeSubstitute(typeContext, other) as? ConeKotlinType
    context(context: CheckerContext)
    fun substituteOrSelf(other: ConeKotlinType, customSubstitutor: TypeSubstitutorMarker? = substitutor): ConeKotlinType =
        substitute(other, customSubstitutor) ?: other

    fun copyTo(other: ConeKotlinType): UnionConeType =
        UnionConeType(
            coneType = other,
            substitutor = substitutor,
            declaration = declaration,
        )
    fun toBuilder() = builder(declaration, substitutor)
    fun withOverride(unionOverride: List<ConeKotlinType>?) =
        coneType.union(declaration, substitutor, unionOverride)
}

package io.github.kochkaev.kotlin.uniontypes.compiler.util

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
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
    val rawType: ConeKotlinType,
    val substitutor: TypeSubstitutorMarker? = null,
    val declaration: FirDeclaration? = null,
    val unionOverride: List<ConeKotlinType>? = null,
) {
    context(context: CheckerContext)
    private val typeContext: TypeSystemInferenceExtensionContext
        get() = context.session.typeContext

    companion object {
        context(context: CheckerContext, reporter: DiagnosticReporter?)
        fun ConeKotlinType.union(
            declaration: FirDeclaration? = null,
            substitutor: TypeSubstitutorMarker? = null,
            unionOverride: List<ConeKotlinType>? = null,
        ) = UnionConeType(this, substitutor, declaration, unionOverride).apply {
            checkValid()
        }

        context(context: CheckerContext, reporter: DiagnosticReporter?)
        fun builder(
            declaration: FirDeclaration? = null,
            substitutor: TypeSubstitutorMarker? = null,
            autoExpand: Boolean = true,
        ) = { coneType: ConeKotlinType -> coneType.union(declaration, substitutor).let {
            if (autoExpand) it.withIntersectionOrThis else it
        } }
    }

    private var _cachedUnexpanded: UnionConeType? = null
    val cachedUnexpanded: UnionConeType?
        get() = _cachedUnexpanded

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
            _unionRaw = unionAnnotations.unwrapUnionOrEmptyOrNullIfError(declaration)
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
                val resolved = it.tryRecursiveResolveTypealias(UNION_ANNOTATION_CLASS_ID, UNION_ADV_ANNOTATION_CLASS_ID)
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
            _fullyResolvedUnion = resolved?.resolvedUnion ?: listOf()
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

    private var _expandedType: ConeKotlinType? = null
    context(context: CheckerContext)
    val expandedType: ConeKotlinType
        get() = _expandedType.elseIfNull {
            _expandedType = substituteOrSelf(rawType.fullyExpandedType())
            _expandedType!!
        }

    private var _resolved: Array<UnionConeType?>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val resolved: UnionConeType?
        get() = _resolved.elseIfNull {
            val resolved =
                if (isDeclaredUnionType || isDeclaredIntersectionType || isUnionOverrode) this
                else rawAbbreviated?.resolved
            _resolved = arrayOf(resolved)
            _resolved!!
        }[0]
    private var _rawAbbreviated: Array<UnionConeType?>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val rawAbbreviated: UnionConeType?
        get() = _rawAbbreviated.elseIfNull {
            var rawAbbreviated: UnionConeType? = null
            val builder = toBuilder(autoExpand = false)
            val nextType = rawType.unwrapTypeAliasOrNull()?.coneType?.let {
                it.abbreviatedType ?: it
            }
            if (nextType != null) rawAbbreviated = builder(nextType)
            _rawAbbreviated = arrayOf(rawAbbreviated)
            _rawAbbreviated!!
        }[0]

    private var _thisType: ConeKotlinType? = null
    context(context: CheckerContext)
    val thisType: ConeKotlinType
        get() = _thisType.elseIfNull {
            _thisType = substituteOrSelf(rawType.abbreviatedTypeOrSelf)
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
            intersectionRaw
            if (_isBroken == null) _isBroken = false
            _isBroken!!
        }

    val isUnionOverrode: Boolean = !unionOverride.isNullOrEmpty()

    private var _intersectionAnnotations: List<FirAnnotation>? = null
    context(context: CheckerContext)
    val intersectionAnnotations: List<FirAnnotation>
        get() = _intersectionAnnotations.elseIfNull {
            _intersectionAnnotations = thisType.getIntersectionAnnotations()
            _intersectionAnnotations!!
        }
    private var _intersectionRaw: List<ConeKotlinType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val intersectionRaw: List<ConeKotlinType>
        get() = _intersectionRaw.elseIfNull {
            _intersectionRaw = intersectionAnnotations.unwrapIntersectionOrEmptyOrNullIfError(declaration)
            if (_intersectionRaw == null) {
                _isBroken = true
                _intersectionRaw = listOf()
            }
            _intersectionRaw!!
        }
    private var _isDeclaredIntersectionType: Boolean? = null
    context(context: CheckerContext)
    val isDeclaredIntersectionType: Boolean
        get() = _isDeclaredIntersectionType.elseIfNull {
            _isDeclaredIntersectionType = intersectionAnnotations.isNotEmpty()
            _isDeclaredIntersectionType!!
        }
    private var _declaredIntersection: List<ConeKotlinType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val declaredIntersection: List<ConeKotlinType>
        get() = _declaredIntersection.elseIfNull {
            _declaredIntersection = intersectionRaw.map { substituteOrSelf(it) }
            _declaredIntersection!!
        }
    private var _declaredIntersectionWrapped: List<UnionConeType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val declaredIntersectionWrapped: List<UnionConeType>
        get() = _declaredIntersectionWrapped.elseIfNull {
            _declaredIntersectionWrapped = declaredIntersection.map { copyTo(it) }
            _declaredIntersectionWrapped!!
        }
    private var _resolvedIntersection: List<ConeKotlinType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val resolvedIntersection: List<ConeKotlinType>
        get() = _resolvedIntersection.elseIfNull {
            _resolvedIntersection = resolved?.declaredIntersection ?: listOf()
            _resolvedIntersection!!
        }
    private var _resolvedIntersectionWrapped: List<UnionConeType>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val resolvedIntersectionWrapped: List<UnionConeType>
        get() = _resolvedIntersectionWrapped.elseIfNull {
            _resolvedIntersectionWrapped = resolvedIntersection.map { copyTo(it) }
            _resolvedIntersectionWrapped!!
        }


    private var _withIntersection: Array<UnionConeType?>? = null
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val withIntersection: UnionConeType?
        get() = _withIntersection.elseIfNull {
            _withIntersection = arrayOf(
                if (resolvedIntersection.isNotEmpty()) {
                    resolvedIntersectionWrapped.intersectUnions(toBuilder()).apply {
                        _cachedUnexpanded = this@UnionConeType
                    }
                } else null
            )
            _withIntersection!!
        }[0]
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    val withIntersectionOrThis: UnionConeType
        get() = withIntersection ?: this


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

    context(context: CheckerContext, reporter: DiagnosticReporter?)
    fun copyTo(other: ConeKotlinType): UnionConeType = toBuilder().invoke(other)
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    fun toBuilder(autoExpand: Boolean = true) = builder(declaration, substitutor, autoExpand)
    context(context: CheckerContext, reporter: DiagnosticReporter?)
    fun withOverride(unionOverride: List<ConeKotlinType>?) =
        rawType.union(declaration, substitutor, unionOverride)

    context(context: CheckerContext, reporter: DiagnosticReporter?)
    fun checkValid(): Boolean {
        val isIntersection = resolvedIntersection.isNotEmpty()
        if (isUnionType && isIntersection) {
            reporter?.reportOn(
                source = declaration?.source,
                factory = UnionTypeErrors.INTERSECTION_AND_UNION_AT_SAME_TIME
            )
            return false
        }
        if (isIntersection && resolved?.rawAbbreviated?.isUnionType == true) {
           reporter?.reportOn(
                source = declaration?.source,
                factory = UnionTypeErrors.INTERSECTION_ON_UNION_TYPE
            )
            return false
        }
        return true
    }
}

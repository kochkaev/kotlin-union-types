package io.github.kochkaev.kotlin.uniontypes.compiler.util

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import org.jetbrains.kotlin.AbstractKtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.classKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.typeParameterSymbols
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRefsOwner
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirCollectionLiteral
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirSpreadArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.toRegularClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.ConeTypeIntersector
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.ProjectionKind
import org.jetbrains.kotlin.fir.types.abbreviatedType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isSubtypeOf
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.types.toConeTypeProjection
import org.jetbrains.kotlin.fir.types.toTypeProjection
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.typeAnnotations
import org.jetbrains.kotlin.fir.types.typeContext
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import kotlin.collections.contains

val UNION_ANNOTATION_CLASS_ID = ClassId.topLevel(FqName("io.github.kochkaev.kotlin.uniontypes.annotations.Union"))
val UNION_ADV_ANNOTATION_CLASS_ID = ClassId.topLevel(FqName("io.github.kochkaev.kotlin.uniontypes.annotations.UnionAdv"))
val unionAnnotationsClassIds = listOf(UNION_ANNOTATION_CLASS_ID, UNION_ADV_ANNOTATION_CLASS_ID)
val INTERSECTION_ANNOTATION_CLASS_ID = ClassId.topLevel(FqName("io.github.kochkaev.kotlin.uniontypes.annotations.Intersection"))
val INTERSECTION_ADV_ANNOTATION_CLASS_ID = ClassId.topLevel(FqName("io.github.kochkaev.kotlin.uniontypes.annotations.IntersectionAdv"))
val intersectionAnnotationsClassIds = listOf(INTERSECTION_ANNOTATION_CLASS_ID, INTERSECTION_ADV_ANNOTATION_CLASS_ID)

internal fun ConeKotlinType.getAnnotations(list: List<ClassId>): List<FirAnnotation> =
    typeAnnotations.filter {
        list.contains(it.annotationTypeRef.coneType.classId)
    }
internal fun ConeKotlinType.getUnionAnnotations(): List<FirAnnotation> = getAnnotations(unionAnnotationsClassIds)
internal fun ConeKotlinType.getIntersectionAnnotations(): List<FirAnnotation> = getAnnotations(intersectionAnnotationsClassIds)

context(context: CheckerContext)
internal fun ConeKotlinType.unwrapTypeAliasOrNull(): FirTypeRef? {
    val abbreviated = fullyExpandedType().abbreviatedType
    if (abbreviated != null) {
        val aliasSymbol = abbreviated.toSymbol()
        if (aliasSymbol is FirTypeAliasSymbol) {
            val expandedRef = aliasSymbol.resolvedExpandedTypeRef
            return expandedRef
        }
    }
    return null
}

internal fun FirBasedSymbol<*>.getContainingSymbol(session: FirSession) = when (this) {
    is FirValueParameterSymbol -> containingDeclarationSymbol
    is FirCallableSymbol -> containingClassLookupTag()?.toSymbol(session)
    is FirClassLikeSymbol<*> -> {
        val outerClassId = classId.outerClassId
        if (outerClassId != null) {
            session.symbolProvider.getClassLikeSymbolByClassId(outerClassId)
        } else {
            null
        }
    }
    else -> null
}

internal fun unwrapTypeParameters(
    symbol: FirBasedSymbol<*>?,
    session: FirSession,
): List<FirTypeParameterSymbol> {
    val allTP = mutableListOf<FirTypeParameterSymbol>()
    var current = symbol

    while (current != null) {
        current.typeParameterSymbols?.let { allTP += it }
        current = current.getContainingSymbol(session)
    }

    return allTP.distinctBy { it.name }
}

context(context: CheckerContext, reporter: DiagnosticReporter?)
internal fun List<FirAnnotation>.unwrapOrEmptyOrNullIfError(
    declaration: DeclarationInfo? = null,
    recursive: Boolean = false,
    simpleClassId: ClassId,
    advancedClassId: ClassId,
): List<ConeKotlinType>? {
    val list = mutableListOf<ConeKotlinType>()
    this
        .map { it.unwrapOrEmptyOrNullIfError(declaration, recursive, simpleClassId, advancedClassId) }
        .forEach {
            if (it == null) return null
            list.addAll(it)
        }
    return list.distinct()
}
context(context: CheckerContext, reporter: DiagnosticReporter?)
internal fun List<FirAnnotation>.unwrapUnionOrEmptyOrNullIfError(
    declaration: DeclarationInfo? = null,
    recursive: Boolean = false,
): List<ConeKotlinType>? = unwrapOrEmptyOrNullIfError(declaration, recursive, UNION_ANNOTATION_CLASS_ID, UNION_ADV_ANNOTATION_CLASS_ID)
context(context: CheckerContext, reporter: DiagnosticReporter?)
internal fun List<FirAnnotation>.unwrapIntersectionOrEmptyOrNullIfError(
    declaration: DeclarationInfo? = null,
    recursive: Boolean = false,
): List<ConeKotlinType>? = unwrapOrEmptyOrNullIfError(declaration, recursive, INTERSECTION_ANNOTATION_CLASS_ID, INTERSECTION_ADV_ANNOTATION_CLASS_ID)

context(context: CheckerContext, reporter: DiagnosticReporter?)
internal fun FirAnnotation.unwrapOrEmptyOrNullIfError(
    declaration: DeclarationInfo? = null,
    recursive: Boolean = false,
    simpleClassId: ClassId,
    advancedClassId: ClassId,
): List<ConeKotlinType>? {
    val allowedTypesArgument = argumentMapping.mapping.values.firstIsInstanceOrNull<FirVarargArgumentsExpression>() ?: return emptyList()
    val allowedTypes = mutableListOf<ConeKotlinType>()
    val isAdv = this.annotationTypeRef.coneType.classId == advancedClassId
    val typeParameters = if (isAdv) unwrapTypeParameters(declaration?.symbol, context.session) else emptyList()
    allowedTypesArgument.arguments.forEach { argument ->
        val raw = if (isAdv) {
            // Advanced
            argument.unwrapAdvancedType(typeParameters)
        } else {
            // Simple
            val kclassType = argument.resolvedType
            (kclassType.typeArguments.firstOrNull() as? ConeKotlinTypeProjection)?.type
        }
        if (raw == null) return null
        if (recursive) {
            val resolved = raw.tryRecursiveResolveTypealias(simpleClassId, advancedClassId)
            if (!resolved.isNullOrEmpty()) allowedTypes += resolved
            else allowedTypes += raw
        } else allowedTypes += raw
    }
    return allowedTypes
}

context(context: CheckerContext, reporter: DiagnosticReporter?)
internal fun ConeKotlinType.tryRecursiveResolveTypealias(simple: ClassId, advanced: ClassId): List<ConeKotlinType>? =
    unwrapTypeAliasOrNull()?.coneType?.getAnnotations(listOf(simple, advanced))?.unwrapOrEmptyOrNullIfError(recursive = true, simpleClassId = simple, advancedClassId = advanced)

context(context: CheckerContext, reporter: DiagnosticReporter?)
internal fun FirExpression.unwrapAdvancedType(
    typeParameters: List<FirTypeParameterSymbol> = listOf(),
): ConeKotlinType? {
    val arguments = (this as? FirFunctionCall)?.argumentList?.arguments ?: return null
    if (arguments.isEmpty()) return null

    val typeExpr = arguments.filterIsInstance<FirGetClassCall>().firstOrNull()
    val typeParametersExpr = arguments.filterIsInstance<FirVarargArgumentsExpression>().firstOrNull()?.arguments
    val hasTypeParameters = !typeParametersExpr.isNullOrEmpty()
    val rawType = typeExpr?.argument?.resolvedType

    val typeParameter = arguments.filterIsInstance<FirLiteralExpression>().firstOrNull()
    val rawTypeParameter = typeParameter?.value as? String
    val hasTypeParameter = !rawTypeParameter.isNullOrEmpty()

    if ((rawType != null || hasTypeParameters) && hasTypeParameter) {
        reporter?.reportOn(
            source = source,
            factory = UnionTypeErrors.TYPE_AND_TYPE_PARAMETER_AT_SAME_TIME
        )
        return null
    }

    val baseConeType =
        if (hasTypeParameter) {
            val symbol = typeParameters.firstNotNullOfOrNull {
                it.takeIf { s -> s.name.asString() == rawTypeParameter }
            }
            symbol?.toConeType() ?: run { with(UnionTypeErrors) {
                reporter?.reportOn(
                    source = source,
                    factory = TYPE_PARAMETER_NOT_FOUND,
                    a = rawTypeParameter
                )
                return null
            } }
        }
        else typeExpr?.argument?.resolvedType ?: return null

    val typeArguments = typeParametersExpr?.unwrapVararg()?.map { nestedExpr ->
        val nestedType = (if (nestedExpr is FirSpreadArgumentExpression) nestedExpr.expression else nestedExpr).unwrapAdvancedType(typeParameters)
        nestedType?.toTypeProjection(ProjectionKind.INVARIANT) ?: ConeStarProjection
    } ?: emptyList()

    return if (typeArguments.isNotEmpty()) {
        baseConeType.withArguments(typeArguments.toTypedArray())
    } else {
        baseConeType
    }
}

fun List<FirExpression>.unwrapVararg(): List<FirExpression>? = let {
    val first = it.firstOrNull()
    if (first is FirSpreadArgumentExpression)
        (first.expression as? FirCollectionLiteral)?.argumentList?.arguments
    else it
}

context(context: CheckerContext, reporter: DiagnosticReporter)
fun checkCompareVararg(
    target: UnionConeType,
    arguments: List<Pair<UnionConeType, AbstractKtSourceElement?>>,
) {
    arguments.forEach { (argument, source) ->
        val matches = target.isCompatible(argument, false)
        if (!matches) with (UnionTypeErrors) {
            reporter.reportOn(
                source = source,
                factory = TYPE_MISMATCH,
                a = argument to context,
                b = target to context
            )
        }
    }
}

context(context: CheckerContext, reporter: DiagnosticReporter?)
fun UnionConeType.intersectUnions(): UnionConeType =
    fullyResolvedUnionWrapped.plus(this).intersectUnions(this.toBuilder())
context(context: CheckerContext, reporter: DiagnosticReporter?)
fun List<UnionConeType>.intersectUnions(
    builder: UnionBuilder,
): UnionConeType {
    val session = context.session
    val typeContext = session.typeContext
    if (isEmpty()) return builder(typeContext.anyType())
    else if (size == 1) return this.first()

    val newBase = ConeTypeIntersector.intersectTypes(typeContext, this.map { it.expandedType })
    val new = builder(newBase)
    val intersected = reduce { accumulator, nextParent ->
        new.withUnionOverride(intersectUnions(accumulator, nextParent))
    }

    return intersected
}

context(context: CheckerContext)
internal fun ConeKotlinType.canHaveSubtypeWith(other: ConeKotlinType): Boolean {
    val session = context.session

    if (isSubtypeOf(other, session) || other.isSubtypeOf(this, session)) return true

    val symbolA = toSymbol() as? FirClassLikeSymbol<*>
    val symbolB = other.toSymbol() as? FirClassLikeSymbol<*>

    if (symbolA != null && symbolB != null) {
        val isFinalA = symbolA.modality == Modality.FINAL
        val isFinalB = symbolB.modality == Modality.FINAL
        if (isFinalA || isFinalB) return false

        val kindA = symbolA.classKind
        val kindB = symbolB.classKind

        if (kindA == ClassKind.INTERFACE || kindB == ClassKind.INTERFACE) return true
        if (kindA == ClassKind.ANNOTATION_CLASS || kindB == ClassKind.ANNOTATION_CLASS) return false
        if (kindA == ClassKind.ENUM_ENTRY || kindB == ClassKind.ENUM_ENTRY) return false
//        if (kindA == ClassKind.OBJECT || kindB == ClassKind.OBJECT) return false
//        if (kindA == ClassKind.ENUM_CLASS || kindB == ClassKind.ENUM_CLASS) return false
        if (kindA == ClassKind.CLASS && kindB == ClassKind.CLASS) return false
    }

    return true
}

context(context: CheckerContext, reporter: DiagnosticReporter?)
internal fun intersectUnions(u1: UnionConeType, u2: UnionConeType): List<ConeKotlinType> {
    val resultVariants = mutableListOf<ConeKotlinType>()
    for (type1 in u1.fullyResolvedUnionOrThis) {
        for (type2 in u2.fullyResolvedUnionOrThis) {
            if (type1.canHaveSubtypeWith(type2)) {
                val intersection = type1.intersect(type2)
                resultVariants.add(intersection)
            }
        }
    }
    return resultVariants
}

context(context: CheckerContext)
internal fun ConeKotlinType.intersect(other: ConeKotlinType) =
    ConeTypeIntersector.intersectTypes(context.session.typeContext, listOf(this, other))

context(context: CheckerContext, reporter: DiagnosticReporter)
fun checkCompare(
    target: UnionConeType?,
    other: UnionConeType?,
    source: AbstractKtSourceElement?,
    error: (DiagnosticReporter, AbstractKtSourceElement?, UnionConeType, UnionConeType) -> Unit = { reporter, source, target, other ->
        reporter.reportOn(
            source = source,
            factory = UnionTypeErrors.TYPE_MISMATCH,
            a = other to context,
            b = target to context,
        )
    },
    invariance: Boolean = false,
) {
    if (target == null || other == null || !target.isValid || !other.isValid) return
    val rawTarget = target.whileDo({ it.cachedUnexpanded != null }) { it.cachedUnexpanded!! }
    val rawOther = other.whileDo({ it.cachedUnexpanded != null }) { it.cachedUnexpanded!! }
    if (!rawOther.thisType.isSubtypeOf(rawTarget.thisType, context.session)) return

    val skipSubtypeCheck = target == rawTarget && other == rawOther
    var matches = target.isCompatible(other, checkNullability = false, skipSubtypeCheck)
    val nullabilityMatches = target.isNullable <= other.isNullable

    if (matches && !nullabilityMatches) return
    if (invariance) matches = matches && other.isCompatible(target, false)

    if (!matches) error(reporter, source, target, other)
}

fun <T> T.whileDo(condition: (T) -> Boolean, block: (T) -> T): T {
    var current = this
    while (condition(current)) { current = block(current) }
    return current
}

context(context: CheckerContext, reporter: DiagnosticReporter?)
fun unionMatches(base: List<UnionConeType>, compareTo: List<UnionConeType>) =
    compareTo.all { type ->
        base.any { it.isCompatible(type, false) }
    }
context(context: CheckerContext, reporter: DiagnosticReporter?)
fun unionMatches(base: List<UnionConeType>, compareTo: UnionConeType) =
    base.any { it.isCompatible(compareTo, false) }

fun ConeKotlinType.equalsClasses(other: ConeKotlinType) =
    classId == other.classId

inline fun <T> T?.elseIfNull(crossinline supplier: () -> T): T =
    this ?: supplier()

private fun ConeKotlinType.getOuterClassType(outerClassSymbol: FirClassLikeSymbol<*>): ConeClassLikeType? {
    if (this !is ConeClassLikeType) return null
    if (this.lookupTag.classId == outerClassSymbol.classId) {
        return this
    }
    val outerTypeProjection = this.typeArguments.lastOrNull()
    if (outerTypeProjection is ConeKotlinTypeProjection) {
        return outerTypeProjection.type.getOuterClassType(outerClassSymbol)
    }
    return null
}

@OptIn(SymbolInternals::class)
context(context: CheckerContext)
fun FirFunctionCall.createUniversalSubstitutor(): ConeSubstitutor {
    val substitutionMap = mutableMapOf<FirTypeParameterSymbol, ConeKotlinType>()

    // Collect function type parameters
    val functionSymbol = this.toResolvedCallableSymbol()
    val functionTypeParameters = functionSymbol?.typeParameterSymbols ?: emptyList()
    val functionTypeArguments = this.typeArguments

    for (i in functionTypeParameters.indices) {
        val parameter = functionTypeParameters[i]
        val argument = functionTypeArguments.getOrNull(i)
        argument?.toConeTypeProjection()?.type?.let { substitutionMap[parameter] = it }
    }

    // Collect type parameters from outer classes
    var currentDispatchReceiverType = this.dispatchReceiver?.resolvedType as? ConeLookupTagBasedType
    while (currentDispatchReceiverType != null) {
        val classSymbol = currentDispatchReceiverType.lookupTag.toSymbol(context.session)
        val classFir = classSymbol?.fir as? FirTypeParameterRefsOwner
        if (classFir != null) {
            val classTypeParameters = classFir.typeParameters.map { it.symbol }
            val classTypeArguments = currentDispatchReceiverType.typeArguments

            for (i in classTypeParameters.indices) {
                val parameter = classTypeParameters[i]
                val argument = classTypeArguments.getOrNull(i)
                if (argument is ConeKotlinTypeProjection) {
                    substitutionMap[parameter] = argument.type
                }
            }
        }
        currentDispatchReceiverType = classSymbol?.getContainingClassSymbol()?.fir?.let {
            this.dispatchReceiver?.resolvedType?.getOuterClassType(it.symbol)
        }
    }

    return substitutorByMap(substitutionMap, context.session)
}


@OptIn(SymbolInternals::class)
context(context: CheckerContext)
internal fun FirCallableSymbol<*>.createSubstitutor(
    derivedClassSymbol: FirClassSymbol<*>? = null,
    derivedCallableSymbol: FirCallableSymbol<*>? = null,
): ConeSubstitutor {
    val map = mutableMapOf<FirTypeParameterSymbol, ConeKotlinType>()

    // Collect class type parameters
    if (derivedClassSymbol != null) {
        val baseClass = this.fir.getContainingClass()
        val baseClassSymbol = baseClass?.symbol as? FirClassSymbol<*>
        if (baseClassSymbol != null) {
            val derivedType = derivedClassSymbol.defaultType()
            val baseSuperType = findSubstitutedSuperType(derivedType, baseClassSymbol)
            if (baseSuperType != null) {
                val baseTypeParams = baseClassSymbol.typeParameterSymbols
                val typeArgs = baseSuperType.typeArguments
                for (i in baseTypeParams.indices) {
                    val argType = typeArgs.getOrNull(i)?.type ?: continue
                    map[baseTypeParams[i]] = argType
                }
            }
        }
    }

    // Collect function/property type parameters
    if (derivedCallableSymbol != null) {
        val baseCallableTypeParams = this.typeParameterSymbols
        val derivedCallableTypeParams = derivedCallableSymbol.typeParameterSymbols

        for (i in baseCallableTypeParams.indices) {
            val baseParam = baseCallableTypeParams[i]
            val derivedParam = derivedCallableTypeParams.getOrNull(i) ?: continue
            map[baseParam] = derivedParam.toConeType()
        }
    }

    if (map.isEmpty()) return ConeSubstitutor.Empty
    return substitutorByMap(map, context.session)
}
context(context: CheckerContext)
private fun findSubstitutedSuperType(
    derivedType: ConeKotlinType,
    baseClassSymbol: FirClassSymbol<*>,
): ConeClassLikeType? {
    if (derivedType !is ConeClassLikeType) return null
    if (derivedType.lookupTag == baseClassSymbol.toLookupTag()) return derivedType

    val derivedClassSymbol = derivedType.lookupTag.toSymbol(context.session) as? FirClassSymbol<*> ?: return null
    val typeParameters = derivedClassSymbol.typeParameterSymbols
    val typeArguments = derivedType.typeArguments

    val map = typeParameters.zip(typeArguments).mapNotNull { (param, arg) ->
        val type = arg.type ?: return@mapNotNull null
        param to type
    }.toMap()
    val substitutor = substitutorByMap(map, context.session)

    for (superTypeRef in derivedClassSymbol.resolvedSuperTypeRefs) {
        val substitutedSuperType = substitutor.substituteOrSelf(superTypeRef.coneType)
        val result = findSubstitutedSuperType(substitutedSuperType, baseClassSymbol)
        if (result != null) return result
    }

    return null
}

fun createCallSiteSubstitutor(
    initializer: FirElement,
    context: CheckerContext
): ConeSubstitutor {
    val qualifiedAccess = initializer as? FirQualifiedAccessExpression ?: return ConeSubstitutor.Empty
    val callableSymbol = qualifiedAccess.toResolvedCallableSymbol(context.session) ?: return ConeSubstitutor.Empty

    val typeParameters = callableSymbol.typeParameterSymbols
    val typeArguments = qualifiedAccess.typeArguments

    if (typeParameters.isEmpty() || typeParameters.size != typeArguments.size) {
        return ConeSubstitutor.Empty
    }

    val mapping = mutableMapOf<FirTypeParameterSymbol, ConeKotlinType>()
    for (i in typeParameters.indices) {
        val paramSymbol = typeParameters[i]
        val projection = typeArguments[i]
        val argType = (projection as? FirTypeProjectionWithVariance)?.typeRef?.coneType
        if (argType != null) {
            mapping[paramSymbol] = argType
        }
    }

    return if (mapping.isNotEmpty()) substitutorByMap(mapping, context.session) else ConeSubstitutor.Empty
}

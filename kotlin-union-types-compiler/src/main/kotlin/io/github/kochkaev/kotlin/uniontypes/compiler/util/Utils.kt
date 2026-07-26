package io.github.kochkaev.kotlin.uniontypes.compiler.util

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import org.jetbrains.kotlin.AbstractKtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
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
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.fir.expressions.FirCollectionLiteral
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirSpreadArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMappingIncludingContextArguments
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjection
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType
import org.jetbrains.kotlin.fir.types.ConeStarProjection
import org.jetbrains.kotlin.fir.types.ConeTypeIntersector
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.FirTypeRef
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
import org.jetbrains.kotlin.fir.types.variance
import org.jetbrains.kotlin.fir.types.withArguments
import org.jetbrains.kotlin.fir.types.withAttributes
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import kotlin.collections.contains
import kotlin.collections.forEach

val UNION_ANNOTATION_CLASS_ID = ClassId.topLevel(FqName("io.github.kochkaev.kotlin.uniontypes.meta.Union"))
val UNION_ADV_ANNOTATION_CLASS_ID = ClassId.topLevel(FqName("io.github.kochkaev.kotlin.uniontypes.meta.UnionAdv"))
val unionAnnotationsClassIds = listOf(UNION_ANNOTATION_CLASS_ID, UNION_ADV_ANNOTATION_CLASS_ID)
val INTERSECTION_ANNOTATION_CLASS_ID = ClassId.topLevel(FqName("io.github.kochkaev.kotlin.uniontypes.meta.Intersection"))
val INTERSECTION_ADV_ANNOTATION_CLASS_ID = ClassId.topLevel(FqName("io.github.kochkaev.kotlin.uniontypes.meta.IntersectionAdv"))
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
        .map { it.unwrapOrEmptyOrNullIfErrorRecurseSafe(declaration, recursive, simpleClassId, advancedClassId) }
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
internal fun FirAnnotation.unwrapOrEmptyOrNullIfErrorRecurseSafe(
    declaration: DeclarationInfo? = null,
    recursive: Boolean = false,
    simpleClassId: ClassId,
    advancedClassId: ClassId,
): List<ConeKotlinType>? {
    try {
        val unwrapped = unwrapOrEmptyOrNullIfError(declaration, recursive, simpleClassId, advancedClassId)
        return unwrapped
    } catch (_: StackOverflowError) {
        report(
            source = source,
            factory = UnionTypeErrors.RECURSIVE_IN_UNION_OR_INTERSECTION,
        )
        return null
    }
}

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
            val unwrapped = argument.unwrapAdvancedType(typeParameters)
            if (unwrapped != null && unwrapped.second != Variance.INVARIANT) report(
                source = source,
                factory = UnionTypeErrors.VARIANCE_NOT_ON_GENERIC_TYPE
            )
            unwrapped?.first?.filterNotNull()
        } else {
            // Simple
            val kclassType = argument.resolvedType
            listOfNotNull((kclassType.typeArguments.firstOrNull() as? ConeKotlinTypeProjection)?.type)
        }
        if (raw == null) return null
        if (recursive) raw.forEach { raw ->
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
): Pair<List<ConeKotlinType?>, Variance>? {
    val arguments = (this as? FirCall)?.resolvedArgumentMappingIncludingContextArguments ?: return null
    if (arguments.isEmpty()) return null

    val typeExpr = arguments.filter { (_, value) ->
        value.name.asString() == "type"
    } .keys.firstOrNull() as? FirGetClassCall
    val genericsExpr = arguments.filter { (_, value) ->
        value.name.asString() == "generics"
    } .keys.firstOrNull() as? FirVarargArgumentsExpression
    val typeParameterExpr = arguments.filter { (_, value) ->
        value.name.asString() == "typeParameter"
    } .keys.firstOrNull() as? FirLiteralExpression
    val unionExpr = arguments.filter { (_, value) ->
        value.name.asString() == "union"
    } .keys.firstOrNull() as? FirCollectionLiteral
    val intersectionExpr = arguments.filter { (_, value) ->
        value.name.asString() == "intersection"
    } .keys.firstOrNull() as? FirCollectionLiteral
    val varianceExpr = arguments.filter { (_, value) ->
        value.name.asString() == "variance"
    } .keys.firstOrNull() as? FirPropertyAccessExpression

    if ((typeExpr != null || genericsExpr != null) + (typeParameterExpr != null) + (unionExpr != null) + (intersectionExpr != null) > 1) {
        report(
            source = source,
            factory = UnionTypeErrors.ILLEGAL_ADV_TYPE_DECLARATION
        )
        return null
    }

    val resolved = when {
        typeParameterExpr != null -> {
            val rawTypeParameter = typeParameterExpr.value as String
            val symbol = typeParameters.firstNotNullOfOrNull {
                it.takeIf { s -> s.name.asString() == rawTypeParameter }
            }
            symbol?.toConeType()?.let { listOf(it) } ?: run { with(UnionTypeErrors) {
                report(
                    source = source,
                    factory = TYPE_PARAMETER_NOT_FOUND,
                    a = rawTypeParameter
                )
                return null
            } }
        }
        typeExpr != null -> {
            val rawType = typeExpr.argument.resolvedType
            val rawGenerics = genericsExpr?.arguments
            val typeArguments = rawGenerics?.unwrapVararg()?.map { nestedExpr ->
                val targetExpr = if (nestedExpr is FirSpreadArgumentExpression) nestedExpr.expression else nestedExpr
                val nested = targetExpr.unwrapAdvancedType(typeParameters) ?: return null
                val nestedTypes = nested.first.filterNotNull()
                val nestedVariance = nested.second
                val coneType = when {
                    nestedTypes.isEmpty() -> null
                    nestedTypes.size == 1 -> nestedTypes.single()
                    else -> createUnionCarrierType(nestedTypes)
                }
                coneType?.toTypeProjection(nestedVariance) ?: ConeStarProjection
            } ?: emptyList()
            val typed = if (typeArguments.isNotEmpty())
                    rawType.withArguments(typeArguments.toTypedArray())
                else rawType
            listOf(typed)
        }
        unionExpr != null -> {
            val nested = unionExpr.arguments.map { it.unwrapAdvancedType(typeParameters) ?: return null }
            nested.forEach { (_, variance) ->
                if (variance != Variance.INVARIANT) report(
                    source = source,
                    factory = UnionTypeErrors.VARIANCE_NOT_ON_GENERIC_TYPE
                )
            }
            val nestedUnions = nested.map { it.first.filterNotNull() }
            nestedUnions.subList(1, nestedUnions.size)
                .fold(nestedUnions[0]) { acc, union -> acc.union(union).toList() }
                .distinct()
        }
        intersectionExpr != null -> {
            val nested = intersectionExpr.arguments.map { it.unwrapAdvancedType(typeParameters) ?: return null }
            nested.forEach { (_, variance) ->
                if (variance != Variance.INVARIANT) report(
                    source = source,
                    factory = UnionTypeErrors.VARIANCE_NOT_ON_GENERIC_TYPE
                )
            }
            val nestedUnions = nested.map { it.first.filterNotNull() }
            if (nestedUnions.isEmpty()) {
                listOf(ConeStarProjection.type)
            } else if (nestedUnions.size == 1) {
                nestedUnions.single()
            } else {
                nestedUnions.subList(1, nestedUnions.size)
                    .fold(nestedUnions[0]) { acc, union -> acc.intersectUnions(union) }
            }
        }
        else -> listOf(ConeStarProjection.type)
    }

    val variance = when (varianceExpr?.calleeReference?.name?.asString()) {
        "IN" -> Variance.IN_VARIANCE
        "OUT" -> Variance.OUT_VARIANCE
        "INVARIANT", null -> Variance.INVARIANT
        else -> return null
    }

    return resolved to variance
}

context(context: CheckerContext)
fun createUnionCarrierType(types: List<ConeKotlinType>) =
    context.session.builtinTypes.nullableAnyType.coneType.withUnionAttribute(types)
fun ConeKotlinType.withUnionAttribute(types: List<ConeKotlinType>): ConeKotlinType {
    if (types.isEmpty()) return this
    if (types.size == 1) return types.single()
    val attribute = UnionTypeAttribute(types)
    return withAttributes(attributes.add(attribute))
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
            report(
                source = source,
                factory = TYPE_MISMATCH,
                a = argument,
                b = target
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
    u1.fullyResolvedUnionOrThis.forEach { type1 ->
        u2.fullyResolvedUnionOrThis.forEach { type2 ->
            if (type1.canHaveSubtypeWith(type2)) {
                val intersection = type1.intersect(type2)
                resultVariants += intersection
            }
        }
    }
    return resultVariants
}
context(context: CheckerContext)
internal fun List<ConeKotlinType>.intersectUnions(other: List<ConeKotlinType>): List<ConeKotlinType> {
    val resultVariants = mutableListOf<ConeKotlinType>()
    forEach { type1 ->
        other.forEach { type2 ->
            if (type1.canHaveSubtypeWith(type2)) {
                val intersection = type1.intersect(type2)
                resultVariants += intersection
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
    error: (AbstractKtSourceElement?, UnionConeType, UnionConeType) -> Unit = { source, target, other ->
        report(
            source = source,
            factory = UnionTypeErrors.TYPE_MISMATCH,
            a = other,
            b = target,
        )
    },
    invariance: Boolean = false,
    invert: Boolean = false,
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

    if (matches == invert) error(source, target, other)
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

context(context: CheckerContext)
fun ConeClassLikeType.getEffectiveVariance(index: Int): Variance {
    val classSymbol = this.lookupTag.toSymbol(context.session) as? FirClassSymbol<*>
        ?: return Variance.INVARIANT

    // declaration-site
    val typeParamSymbol = classSymbol.typeParameterSymbols.getOrNull(index)
        ?: return Variance.INVARIANT // Type argument not found
    val declarationSiteVariance = typeParamSymbol.variance // IN_VARIANCE, OUT_VARIANCE or INVARIANT

    // use-site
    val projection = this.typeArguments.getOrNull(index) ?: return declarationSiteVariance
    val useSiteVariance = projection.variance

    return if (useSiteVariance != Variance.INVARIANT) useSiteVariance else declarationSiteVariance
}


context(context: CheckerContext, reporter: DiagnosticReporter?)
fun report(
    source: AbstractKtSourceElement?,
    factory: KtDiagnosticFactory0,
    positioningStrategy: AbstractSourceElementPositioningStrategy? = null,
) {
    reporter?.reportOn(source, factory, positioningStrategy)
}
context(context: CheckerContext, reporter: DiagnosticReporter?)
fun <A> report(
    source: AbstractKtSourceElement?,
    factory: KtDiagnosticFactory1<A>,
    a: A,
    positioningStrategy: AbstractSourceElementPositioningStrategy? = null,
) {
    (a as? UnionConeType)?.resolveAllForDiagnostics()
    reporter?.reportOn(source, factory, a, positioningStrategy)
}
context(context: CheckerContext, reporter: DiagnosticReporter?)
fun <A, B> report(
    source: AbstractKtSourceElement?,
    factory: KtDiagnosticFactory2<A, B>,
    a: A,
    b: B,
    positioningStrategy: AbstractSourceElementPositioningStrategy? = null,
) {
    (a as? UnionConeType)?.resolveAllForDiagnostics()
    (b as? UnionConeType)?.resolveAllForDiagnostics()
    reporter?.reportOn(source, factory, a, b, positioningStrategy)
}

fun ConeKotlinType.calculateHash(): Int {
    var result = hashCode()
    result = 31 * result + attributes.hashCode()
    result = 31 * result + classId.hashCode()
    result = 31 * result + variance.hashCode()
    return result
}

operator fun Boolean.plus(that: Boolean): Int =
    (if (this) 1 else 0) + (if (that) 1 else 0)
operator fun Boolean.plus(that: Int): Int =
    (if (this) 1 else 0) + that
operator fun Int.plus(that: Boolean): Int =
    this + (if (that) 1 else 0)
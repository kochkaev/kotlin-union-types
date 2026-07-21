package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import io.github.kochkaev.kotlin.uniontypes.compiler.util.checkCompare
import io.github.kochkaev.kotlin.uniontypes.compiler.util.getUnionAnnotations
import io.github.kochkaev.kotlin.uniontypes.compiler.util.info
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.*

object UnionTypeClassDeclarationChecker : FirClassChecker(MppCheckerKind.Common) {
    @OptIn(SymbolInternals::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        val typeContext = context.session.typeContext
        val substitutor = typeContext.createSubstitutorForSuperTypes(declaration.defaultType())

        val unionBuilder = UnionConeType.builder(
            declaration = declaration.info(),
        )

        declaration.superTypeRefs.forEach { superTypeRef ->
            if (superTypeRef !is FirResolvedTypeRef) return@forEach

            val superConeType = superTypeRef.coneType
            val unionTypeAnnotations = superConeType.getUnionAnnotations()

            if (unionTypeAnnotations.isNotEmpty()) {
                reporter.reportOn(
                    source = superTypeRef.source,
                    factory = UnionTypeErrors.UNION_TYPE_ON_SUPERTYPE,
                )
            }

            if (superConeType is ConeClassLikeType) {
                val superTypeSymbol = superConeType.lookupTag.toSymbol() ?: return@forEach
                val superTypeFir = superTypeSymbol.fir as? FirClass ?: return@forEach

                superTypeFir.typeParameters.forEachIndexed { i, typeParameterRef ->
                    val typeParameterSymbol = typeParameterRef.symbol
                    val argumentFromChild = superConeType.typeArguments.getOrNull(i)
                    if (argumentFromChild == null || argumentFromChild !is ConeKotlinTypeProjection) return@forEachIndexed
//                    val typeParameterCone = with(typeContext) { substitutor?.safeSubstitute(typeParameterRef.toConeType()) } as? ConeKotlinType ?: return@forEach
                    val actualBounds = argumentFromChild.type.let { cone ->
                        if (cone is ConeTypeParameterType)
                            declaration.typeParameters
                                .map { it.symbol }
                                .firstOrNull { it.name == cone.lookupTag.name }
                                ?.resolvedBounds
                                ?.map { it.coneType to it.source }
                        else listOf(cone to superTypeRef.source)
                    } ?: return@forEachIndexed
                    if (actualBounds.isEmpty()) return@forEachIndexed

                    typeParameterSymbol.resolvedBounds.forEach { bound ->
                        val boundWrapped= unionBuilder(bound.coneType)
                        if (!boundWrapped.isUnionType) return@forEach
                        actualBounds.forEach {
                            checkCompare(
                                target = boundWrapped,
                                other = unionBuilder(it.first),
                                source = it.second,
                            )
                        }
                    }
                }
            }
        }
    }
}

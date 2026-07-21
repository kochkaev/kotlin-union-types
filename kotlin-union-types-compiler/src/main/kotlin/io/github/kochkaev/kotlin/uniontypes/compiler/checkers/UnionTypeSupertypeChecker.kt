package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import io.github.kochkaev.kotlin.uniontypes.compiler.util.info
import io.github.kochkaev.kotlin.uniontypes.compiler.util.unwrapTypeAliasOrNull
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirTypeRefChecker
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.resolve.firClassLike
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.abbreviatedType
import org.jetbrains.kotlin.fir.types.abbreviatedTypeOrSelf
import org.jetbrains.kotlin.fir.types.coneType

object UnionTypeSupertypeChecker : FirTypeRefChecker(MppCheckerKind.Common) {

    @OptIn(SymbolInternals::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(typeRef: FirTypeRef) {
        if (typeRef !is FirResolvedTypeRef) return

        val unionBuilder = UnionConeType.builder(
            declaration = context.containingDeclarations.last().fir.info(),
        )

        val annotatedType = unionBuilder(typeRef.coneType)
        val typeAlias = typeRef.coneType.unwrapTypeAliasOrNull()?.coneType?.abbreviatedTypeOrSelf?.let(unionBuilder)

        if (typeAlias != null && typeAlias.isUnionType) annotatedType.fullyResolvedUnionWrapped.forEach { allowedType ->
            if (!typeAlias.isCompatible(allowedType)) {
                reporter.reportOn(
                    source = typeRef.source,
                    factory = UnionTypeErrors.INVALID_SUPERTYPE_FOR_UNION_TYPE,
                    a = allowedType to context,
                    b = typeAlias to context
                )
            }
        } else annotatedType.fullyResolvedUnionWrapped.forEach { allowedType ->
            if (!annotatedType.isCompatible(allowedType)) {
                reporter.reportOn(
                    source = typeRef.source,
                    factory = UnionTypeErrors.INVALID_SUPERTYPE_FOR_UNION_TYPE,
                    a = allowedType to context,
                    b = annotatedType to context
                )
            }
        }
    }
}

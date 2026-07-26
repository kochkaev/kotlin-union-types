package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import io.github.kochkaev.kotlin.uniontypes.compiler.util.info
import io.github.kochkaev.kotlin.uniontypes.compiler.util.withUnionAttribute
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirTypeRefChecker
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

object UnionTypeSupertypeChecker : FirTypeRefChecker(MppCheckerKind.Common) {

    @OptIn(SymbolInternals::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(typeRef: FirTypeRef) {
        if (typeRef !is FirResolvedTypeRef) return

        val unionBuilder = UnionConeType.builder(
            declaration = context.containingDeclarations.last().fir.info(),
            skipValidCheck = false,
        )

        unionBuilder(typeRef.coneType).unionRaw.ifNotEmpty {
            typeRef.coneType.withUnionAttribute(this)
        }
    }
}

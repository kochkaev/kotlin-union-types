package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirTypeOperatorCallChecker
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.references.toResolvedVariableSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef

object UnionTypeTypeOperatorCallChecker : FirTypeOperatorCallChecker(MppCheckerKind.Common) {

    @OptIn(SymbolInternals::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirTypeOperatorCall) {
        val operation = expression.operation
        if (operation != FirOperation.AS && operation != FirOperation.SAFE_AS) return
        val declaration = expression.toResolvedCallableSymbol(context.session)?.fir ?: return

        val unionBuilder = UnionConeType.builder(
            declaration = declaration,
        )

        val operand = expression.argument
        val operandVariableSymbol = (operand as? FirQualifiedAccessExpression)
            ?.calleeReference
            ?.toResolvedVariableSymbol()
            ?: return

        val targetType = unionBuilder(operandVariableSymbol.resolvedReturnTypeRef.coneType)
        val castTargetType = (expression.conversionTypeRef as? FirResolvedTypeRef)?.coneType?.let { unionBuilder(it) } ?: return

        val matches = targetType.isCompatible(castTargetType)

        when (operation) {
            FirOperation.AS -> {
                if (!matches) {
                    reporter.reportOn(
                        source = expression.conversionTypeRef.source,
                        factory = UnionTypeErrors.CAST_WILL_ALWAYS_FAIL,
                        a = castTargetType to context
                    )
                } else if (targetType.fullyResolvedUnion.size > 1) {
                    val otherTypes = targetType.fullyResolvedUnionWrapped.filter { !it.isCompatible(castTargetType) }
                    if (otherTypes.isNotEmpty()) {
                        reporter.reportOn(
                            source = expression.source,
                            factory = UnionTypeErrors.UNSAFE_UNION_TYPE_CAST,
                            a = otherTypes to context
                        )
                    }
                }
            }
            FirOperation.SAFE_AS -> {
                if (!matches) {
                    reporter.reportOn(
                        source = expression.source,
                        factory = UnionTypeErrors.USELESS_CAST,
                        a = castTargetType to context
                    )
                }
            }
        }
    }
}

package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirWhenExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirOperation
import org.jetbrains.kotlin.fir.expressions.FirTypeOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef

object UnionTypeWhenExpressionChecker : FirWhenExpressionChecker(MppCheckerKind.Common) {


    @OptIn(SymbolInternals::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirWhenExpression) {
        val subjectVariable = expression.subjectVariable ?: return
        val declaration = expression.toResolvedCallableSymbol(context.session)?.fir ?: return

        val unionBuilder = UnionConeType.builder(
            declaration = declaration,
        )

        val subjectVariableSymbol = subjectVariable.symbol
        val targetType = unionBuilder(subjectVariableSymbol.resolvedReturnTypeRef.coneType)

        for (branch in expression.branches) {
            val condition = branch.condition
            if (condition !is FirTypeOperatorCall || condition.operation != FirOperation.IS) continue

            val checkedType = (condition.conversionTypeRef as? FirResolvedTypeRef)?.coneType?.let { unionBuilder(it) } ?: return
            val isReachable = targetType.isCompatible(checkedType)

            if (!isReachable) {
                reporter.reportOn(
                    source = condition.conversionTypeRef.source,
                    factory = UnionTypeErrors.UNREACHABLE_WHEN_BRANCH,
                    a = checkedType to context,
                )
            }
        }
    }
}

package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.DeclarationInfo
import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import io.github.kochkaev.kotlin.uniontypes.compiler.util.intersectUnions
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

        val unionBuilder = UnionConeType.builder(
            declaration = DeclarationInfo(
                source = expression.source,
                symbol = context.containingDeclarations.last().fir.symbol,
            ),
        )

        val operand = expression.argument
        val operandVariableSymbol = (operand as? FirQualifiedAccessExpression)
            ?.calleeReference
            ?.toResolvedVariableSymbol()
            ?: return

        val operandType = unionBuilder(operandVariableSymbol.resolvedReturnTypeRef.coneType)
        val conversionType = (expression.conversionTypeRef as? FirResolvedTypeRef)?.coneType?.let { unionBuilder(it) } ?: return

        val isExpr = operation == FirOperation.IS || operation == FirOperation.NOT_IS
        val matches = if (isExpr) conversionType.isCompatible(operandType) else operandType.isCompatible(conversionType)

        when (operation) {
            FirOperation.AS -> if (!matches) {
                reporter.reportOn(
                    source = expression.conversionTypeRef.source,
                    factory = UnionTypeErrors.CAST_WILL_ALWAYS_FAIL,
                )
            } else if (!operandType.fullyResolvedUnionWrappedOrThis.intersectUnions(unionBuilder).isCompatible(conversionType)) {
                reporter.reportOn(
                    source = expression.conversionTypeRef.source,
                    factory = UnionTypeErrors.UNSAFE_CAST,
                    a = operandType to context,
                    b = conversionType to context,
                )
            }
            FirOperation.SAFE_AS -> if (!matches) {
                reporter.reportOn(
                    source = expression.source,
                    factory = UnionTypeErrors.USELESS_CAST,
                )
            }
            FirOperation.IS -> if (!matches) {
                reporter.reportOn(
                    source = expression.source,
                    factory = UnionTypeErrors.CHECK_FOR_INSTANCE_IS_ALWAYS_FALSE,
                )
            }
            FirOperation.NOT_IS -> if (matches) {
                reporter.reportOn(
                    source = expression.source,
                    factory = UnionTypeErrors.CHECK_FOR_INSTANCE_IS_ALWAYS_FALSE,
                )
            }
            else -> {}
        }
    }
}

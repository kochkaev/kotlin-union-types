package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType.Companion.union
import io.github.kochkaev.kotlin.uniontypes.compiler.util.checkCompare
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirVariableAssignmentChecker
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.calleeReference
import org.jetbrains.kotlin.fir.references.toResolvedVariableSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.*

object UnionTypeAssignmentChecker : FirVariableAssignmentChecker(MppCheckerKind.Common) {

    @OptIn(SymbolInternals::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirVariableAssignment) {
        val variableSymbol = expression.calleeReference?.toResolvedVariableSymbol() ?: return
        val targetType = variableSymbol.resolvedReturnTypeRef.coneType
        val assignedValueType = expression.rValue.resolvedType
        val declaration = variableSymbol.fir

        val unionBuilder = UnionConeType.builder(
            declaration = declaration,
        )

        val targetWrapped = unionBuilder(targetType)
        val assignedWrapped = unionBuilder(assignedValueType)

        checkCompare(
            target = targetWrapped,
            other = assignedWrapped,
            source = expression.rValue.source,
        )
    }
}


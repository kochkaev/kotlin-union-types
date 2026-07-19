package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import io.github.kochkaev.kotlin.uniontypes.compiler.util.unionMatches
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid

object UnionTypeFunctionReturnChecker : FirFunctionChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        val unionBuilder = UnionConeType.builder(
            declaration = declaration,
        )

        val returnType = unionBuilder(declaration.returnTypeRef.coneType)
        val body = declaration.body ?: return
        val actualReturnTypes = mutableListOf<Pair<FirExpression, ConeKotlinType>>()

        val visitor = object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                if (element !is FirReturnExpression) {
                    element.acceptChildren(this)
                    return
                }
                if (element.target.labeledElement != declaration) return
                when (val resultExpr = element.result) {
                    is FirWhenExpression -> {
                        // For `when` (and `if`)
                        resultExpr.branches.forEach { branch ->
                            val branchResult = branch.result
                            actualReturnTypes.add(branchResult to branchResult.resolvedType)
                        }
                    }
                    else -> {
                        // For other expression bodies
                        actualReturnTypes.add(resultExpr to resultExpr.resolvedType)
                    }
                }
            }
        }
        body.acceptChildren(visitor)

        actualReturnTypes.forEach { (expression, actualType) ->
            if (!actualType.isUnit && !unionMatches(returnType.fullyResolvedUnionWrappedOrThis, unionBuilder(actualType).fullyResolvedUnionWrappedOrThis)) {
                reporter.reportOn(
                    source = expression.source,
                    factory = UnionTypeErrors.TYPE_MISMATCH,
                    a = unionBuilder(actualType) to context,
                    b = returnType to context
                )
            }
        }
    }
}

package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.extractAllowedTypes
import io.github.kochkaev.kotlin.uniontypes.compiler.util.findUnionTypeAnnotations
import io.github.kochkaev.kotlin.uniontypes.compiler.util.isTypeCompatibleWithUnion
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.resolvedType
import org.jetbrains.kotlin.fir.types.isUnit
import org.jetbrains.kotlin.fir.types.resolvedType

object UnionTypeFunctionReturnChecker : FirFunctionChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        if (declaration !is FirSimpleFunction) return

        val returnTypeRef = declaration.returnTypeRef
        val unionTypeAnnotations = returnTypeRef.findUnionTypeAnnotations()
        val allowedTypes = extractAllowedTypes(unionTypeAnnotations)
        if (allowedTypes.isEmpty()) return

        // Handle expression body functions
        val body = declaration.body
        if (body is FirExpression) {
            // If it's an expression body, the type of the expression is the return type
            val actualReturnType = body.resolvedType
            if (!actualReturnType.isUnit && !isTypeCompatibleWithUnion(actualReturnType, allowedTypes, context.session)) {
                reporter.reportOn(
                    source = body.source,
                    factory = UnionTypeErrors.TYPE_MISMATCH_IN_UNION_TYPE,
                    a = actualReturnType,
                    b = allowedTypes
                )
            }
        } else if (body is FirBlock) {
            // Handle block body functions with explicit return statements
            body.statements.filterIsInstance<FirReturnExpression>().forEach { returnExpression ->
                val actualReturnType = returnExpression.result.resolvedType
                if (!actualReturnType.isUnit && !isTypeCompatibleWithUnion(actualReturnType, allowedTypes, context.session)) {
                    reporter.reportOn(
                        source = returnExpression.result.source,
                        factory = UnionTypeErrors.TYPE_MISMATCH_IN_UNION_TYPE,
                        a = actualReturnType,
                        b = allowedTypes
                    )
                }
            }
            // Handle implicit return for Unit functions (if the union type is not Unit)
            if (declaration.returnTypeRef.resolvedType.isUnit && !allowedTypes.any { it.isUnit }) {
                // If the function implicitly returns Unit, but Unit is not allowed in the union,
                // this is also a mismatch.
                // This case is tricky as there's no explicit source for the "Unit" return.
                // For now, we might skip this or report on the function name.
            }
        }
    }
}

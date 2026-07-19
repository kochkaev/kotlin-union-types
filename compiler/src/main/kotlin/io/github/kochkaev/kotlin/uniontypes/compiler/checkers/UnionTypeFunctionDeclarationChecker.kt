package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.findUnionTypeAnnotations
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.types.coneType

object UnionTypeExtensionFunctionChecker : FirFunctionChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        // Check if it's an extension function
        val receiverTypeRef = declaration.receiverParameter?.typeRef ?: return

        val receiverConeType = receiverTypeRef.coneType
        val unionTypeAnnotations = receiverConeType.findUnionTypeAnnotations()

        if (unionTypeAnnotations.isNotEmpty()) {
            reporter.reportOn(
                source = receiverTypeRef.source,
                factory = UnionTypeErrors.EXTENSION_ON_UNION_TYPE,
            )
        }
    }
}

package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.findUnionTypeAnnotations
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef

object UnionTypeDeclarationChecker : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        if (declaration !is FirRegularClass) return

        for (superTypeRef in declaration.superTypeRefs) {
            if (superTypeRef !is FirResolvedTypeRef) continue

            val superConeType = superTypeRef.coneType
            val unionTypeAnnotations = superConeType.findUnionTypeAnnotations()

            if (unionTypeAnnotations.isNotEmpty()) {
                reporter.reportOn(
                    source = superTypeRef.source,
                    factory = UnionTypeErrors.UNION_TYPE_ON_SUPERTYPE,
                )
            }
        }
    }
}

package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.findUnionTypeAnnotations
import io.github.kochkaev.kotlin.uniontypes.compiler.util.extractAllowedTypes
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirTypeRefChecker
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isSubtypeOf

object UnionTypeSupertypeChecker : FirTypeRefChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(typeRef: FirTypeRef) {
        if (typeRef !is FirResolvedTypeRef) return

        val unionAnnotations = typeRef.findUnionTypeAnnotations()
        if (unionAnnotations.isEmpty()) return

        val allowedTypes = extractAllowedTypes(unionAnnotations)
        val annotatedType = typeRef.coneType

        for (allowedType in allowedTypes) {
            if (!allowedType.isSubtypeOf(annotatedType, context.session)) {
                reporter.reportOn(
                    source = typeRef.source,
                    factory = UnionTypeErrors.INVALID_SUPERTYPE_FOR_UNION_TYPE,
                    a = allowedType,
                    b = annotatedType
                )
            }
        }
    }
}

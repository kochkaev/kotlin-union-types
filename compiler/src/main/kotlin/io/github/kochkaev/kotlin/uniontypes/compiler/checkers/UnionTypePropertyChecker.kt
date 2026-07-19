package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType.Companion.union
import io.github.kochkaev.kotlin.uniontypes.compiler.util.checkCompare
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.types.*

object UnionTypePropertyChecker : FirPropertyChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirProperty) {
        val initializer = declaration.initializer ?: return

        val unionBuilder = UnionConeType.builder(
            declaration = declaration,
        )

        val propertyType = unionBuilder(declaration.returnTypeRef.coneType)
        val initializerType = unionBuilder(initializer.resolvedType)

        checkCompare(
            target = propertyType,
            other = initializerType,
            source = initializer.source,
        )
    }
}

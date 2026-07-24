package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.util.DeclarationInfo
import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import io.github.kochkaev.kotlin.uniontypes.compiler.util.checkCompare
import io.github.kochkaev.kotlin.uniontypes.compiler.util.createCallSiteSubstitutor
import io.github.kochkaev.kotlin.uniontypes.compiler.util.createSubstitutor
import io.github.kochkaev.kotlin.uniontypes.compiler.util.info
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.chain
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.*

object UnionTypePropertyChecker : FirPropertyChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirProperty) {
        val unionBuilder = UnionConeType.builder(
            declaration = declaration.info(),
        )
        val propertyType = unionBuilder(declaration.returnTypeRef.coneType)

        val derivedClassSymbol = declaration.getContainingClass()?.symbol as? FirClassSymbol<*>
        val initializer = declaration.initializer ?: return
        val initializerSymbol = initializer.toResolvedCallableSymbol(context.session)

        val callSubstitutor = createCallSiteSubstitutor(initializer, context)
        val classSubstitutor = initializerSymbol?.createSubstitutor(derivedClassSymbol, declaration.symbol) ?: ConeSubstitutor.Empty
        val substitutor = callSubstitutor.chain(classSubstitutor)

        val unionBuilderLocal = UnionConeType.builder(
            declaration = DeclarationInfo(
                source = initializer.source,
                symbol = initializerSymbol,
            ),
            substitutor = substitutor
        )
        val initializerType = unionBuilderLocal(initializer.resolvedType)

        checkCompare(
            target = propertyType,
            other = initializerType,
            source = initializer.source,
        )
    }
}

package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import io.github.kochkaev.kotlin.uniontypes.compiler.util.checkCompare
import io.github.kochkaev.kotlin.uniontypes.compiler.util.info
import io.github.kochkaev.kotlin.uniontypes.compiler.util.intersectUnions
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isSubtypeOf

object UnionTypeFunctionDeclarationChecker : FirFunctionChecker(MppCheckerKind.Common) {

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        val unionBuilder = UnionConeType.builder(
            declaration = declaration.info(),
        )

        // Check extension function
        declaration.receiverParameter?.typeRef?.let { receiverTypeRef ->
            val receiverType = unionBuilder(receiverTypeRef.coneType)

            if (receiverType.isUnionType) {
                reporter.reportOn(
                    source = receiverTypeRef.source,
                    factory = UnionTypeErrors.EXTENSION_ON_UNION_TYPE,
                )
            }
        }

        // Check context parameters
        declaration.contextParameters.forEach { parameter ->
            val parameterType = unionBuilder(parameter.returnTypeRef.coneType)

            if (parameterType.isUnionType) {
                reporter.reportOn(
                    source = parameter.source,
                    factory = UnionTypeErrors.UNION_TYPE_ON_CONTEXT_PARAMETER,
                )
            }
        }

        // Check override
        if (declaration.isOverride) {
            val containingClass = declaration.getContainingClass()
            val scope = containingClass?.unsubstitutedScope(
                withForcedTypeCalculator = false,
                memberRequiredPhase = null
            )

            val symbol = declaration.symbol
            val derivedReturnConeType = symbol.resolvedReturnType
            val derivedReturnType = unionBuilder(derivedReturnConeType)
            val baseReturnTypes = mutableListOf<UnionConeType>()

            var mathBaseType = true
            if (symbol is FirNamedFunctionSymbol)
                scope?.processDirectOverriddenFunctionsWithBaseScope(symbol) { overriddenSymbol, _ ->
                    val type = overriddenSymbol.resolvedReturnType
                    if (!derivedReturnConeType.isSubtypeOf(type, context.session)) {
                        mathBaseType = false
                        ProcessorAction.STOP
                    } else {
                        baseReturnTypes.add(unionBuilder(type))
                        ProcessorAction.NEXT
                    }
                }

            val length = baseReturnTypes.size
            if (mathBaseType && length > 0) {
                val targetType =
                    if (length == 1) baseReturnTypes.first()
                    else baseReturnTypes.intersectUnions(unionBuilder.with(skipValidCheck = true))
                checkCompare(
                    target = targetType,
                    other = derivedReturnType,
                    source = declaration.source,
//                    error = { reporter, source, target, other ->
//                        reporter.reportOn(
//                            source = source,
//                            factory = UnionTypeErrors.TYPE_MISMATCH,
//                            a = other to context,
//                            b = target to context,
//                        )
//                    },
                )
            }
        }
    }
}

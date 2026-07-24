package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.DeclarationInfo
import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import io.github.kochkaev.kotlin.uniontypes.compiler.util.checkCompare
import io.github.kochkaev.kotlin.uniontypes.compiler.util.createSubstitutor
import io.github.kochkaev.kotlin.uniontypes.compiler.util.info
import io.github.kochkaev.kotlin.uniontypes.compiler.util.intersectUnions
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.backend.utils.processOverriddenFunctionsFromSuperClasses
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.getContainingClass
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.scopes.unsubstitutedScope
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.isSubtypeOf

object UnionTypeFunctionDeclarationChecker : FirFunctionChecker(MppCheckerKind.Common) {

    @OptIn(SymbolInternals::class)
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
            val derivedClassSymbol = containingClass?.symbol as? FirClassSymbol<*>
            val scope = containingClass?.unsubstitutedScope(
                context.session,
                context.scopeSession,
                withForcedTypeCalculator = false,
                memberRequiredPhase = FirResolvePhase.STATUS
            )

            val symbol = declaration.symbol
            val derivedReturnConeType = symbol.resolvedReturnType
            val derivedReturnType = unionBuilder(derivedReturnConeType)
            val baseReturnTypes = mutableListOf<UnionConeType>()
            val argumentMap = hashMapOf<String, UnionConeType>()

            var mathBaseType = true
            if (symbol is FirNamedFunctionSymbol)
                scope?.processOverriddenFunctionsFromSuperClasses(symbol, containingClass) { overriddenSymbol ->
                    val substitutor = overriddenSymbol.createSubstitutor(derivedClassSymbol, symbol)
                    val unionBuilderLocal = UnionConeType.builder(
                        declaration = DeclarationInfo(
                            source = declaration.source,
                            symbol = overriddenSymbol,
                        ),
                        substitutor = substitutor
                    )

                    val type = substitutor.substituteOrSelf(overriddenSymbol.resolvedReturnType)
                    if (!derivedReturnConeType.isSubtypeOf(type, context.session)) {
                        mathBaseType = false
                        ProcessorAction.STOP
                    } else {
                        baseReturnTypes.add(unionBuilderLocal(type))
                        overriddenSymbol.valueParameterSymbols.forEach { parameterSymbol ->
                            val argName = parameterSymbol.name.asString()
                            val argType = parameterSymbol.resolvedReturnType
                            val argCurr = argumentMap[argName]
                            if (argCurr == null)
                                argumentMap[argName] = unionBuilderLocal(argType)
                            else {
                                val new = listOf(argCurr, unionBuilderLocal(argType)).intersectUnions(unionBuilderLocal)
                                argumentMap[argName] = new
                            }
                        }
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
                )
                symbol.valueParameterSymbols.forEach { parameterSymbol ->
                    val expected = argumentMap[parameterSymbol.name.asString()] ?: return@forEach
                    val actual = unionBuilder(parameterSymbol.resolvedReturnType)
                    checkCompare(
                        target = expected,
                        other = actual,
                        source = parameterSymbol.source
                    )
                }
            }
        }
    }
}

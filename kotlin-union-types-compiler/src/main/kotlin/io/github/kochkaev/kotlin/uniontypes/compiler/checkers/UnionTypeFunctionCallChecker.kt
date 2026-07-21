package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import io.github.kochkaev.kotlin.uniontypes.compiler.util.checkCompare
import io.github.kochkaev.kotlin.uniontypes.compiler.util.checkCompareVararg
import io.github.kochkaev.kotlin.uniontypes.compiler.util.createUniversalSubstitutor
import io.github.kochkaev.kotlin.uniontypes.compiler.util.info
import org.jetbrains.kotlin.AbstractKtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirSpreadArgumentExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.createConeSubstitutorFromTypeArguments
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.expressions.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.scopes.impl.toConeType
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.*

object UnionTypeFunctionCallChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    @OptIn(SymbolInternals::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val symbol = expression.toResolvedCallableSymbol()
        val argumentMapping = expression.resolvedArgumentMapping ?: return
        val declaration = symbol?.fir
        val substitutor = expression.createUniversalSubstitutor()

        val unionBuilder = UnionConeType.builder(
            declaration = declaration?.info(),
            substitutor = substitutor,
        )

        // Check type arguments
        symbol?.typeParameterSymbols?.forEach { symbol ->
            val coneType = symbol.toConeType()
            val actualType = unionBuilder(substitutor.substituteOrSelf(coneType))
            symbol.resolvedBounds.forEach { bound ->
                val boundWrapped = unionBuilder(bound.coneType)
                if (!boundWrapped.isUnionType) return@forEach
                checkCompare(
                    target = boundWrapped,
                    other = actualType,
                    source = expression.source,
                )
            }
        }

        // Check receiver
        val actualReceiver = expression.explicitReceiver?.resolvedType?.let { unionBuilder(it) }
        val expectedReceiver = symbol?.resolvedReceiverType?.let { substitutor.substituteOrSelf(it) }?.let { unionBuilder(it) }
        if (actualReceiver != null && expectedReceiver != null) {
            checkCompare(
                target = expectedReceiver,
                other = actualReceiver,
                source = expression.source,
            )
        }

        // Check arguments
        argumentMapping.forEach { (argument, parameter) ->
            val targetType = substitutor.substituteOrSelf(parameter.returnTypeRef.coneType)
            val targetWrapped = unionBuilder(targetType)
            val argumentType = argument.resolvedType

            // TODO: fix vararg checking if it possible
            var toCheck: Pair<UnionConeType, AbstractKtSourceElement?>? = null
            if (argument is FirVarargArgumentsExpression && argumentType.isArrayOrPrimitiveArray) {
                val varargBase = targetType.arrayElementType() ?: return@forEach
                val vararg = argument.arguments.mapNotNull {
                    val expected = unionBuilder(it.resolvedType)
                    if (it is FirSpreadArgumentExpression) {
                        toCheck = expected to it.source
                        null
                    } else {
                        expected to it.source
                    }
                }
                if (vararg.isEmpty()) return@forEach
                checkCompareVararg(
                    target = unionBuilder(varargBase),
                    arguments = vararg,
                )
            } else {
                toCheck = unionBuilder(argumentType) to argument.source
            }

            toCheck?.let { (arg, source) ->
                checkCompare(
                    target = targetWrapped,
                    other = arg,
                    source = source,
                )
            }
        }
    }
}

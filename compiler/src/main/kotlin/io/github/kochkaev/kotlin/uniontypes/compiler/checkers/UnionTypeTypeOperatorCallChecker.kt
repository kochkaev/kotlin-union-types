package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirTypeOperatorCallChecker
import org.jetbrains.kotlin.fir.expressions.FirTypeOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirOperation
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.references.toResolvedVariableSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

object UnionTypeTypeOperatorCallChecker : FirTypeOperatorCallChecker(MppCheckerKind.Common) {

    private val UNION_TYPE_ANNOTATION_CLASS_ID = ClassId.topLevel(FqName("io.github.kochkaev.kotlin.uniontypes.annotations.UnionType"))

    override fun check(expression: FirTypeOperatorCall, context: CheckerContext, reporter: DiagnosticReporter) {
        val operation = expression.operation
        if (operation != FirOperation.AS && operation != FirOperation.SAFE_AS) return

        val operand = expression.argument
        val operandVariableSymbol = (operand as? FirQualifiedAccessExpression)
            ?.calleeReference
            ?.toResolvedVariableSymbol()
            ?: return

        val operandTypeRef = operandVariableSymbol.resolvedReturnTypeRef
        val unionTypeAnnotation = operandTypeRef.annotations.find {
            it.annotationTypeRef.coneType.classId == UNION_TYPE_ANNOTATION_CLASS_ID
        } ?: return

        val allowedTypesArgument = unionTypeAnnotation.argumentMapping.mapping.values.firstOrNull()
                as? FirVarargArgumentsExpression ?: return
        
        val allowedTypes: List<ConeKotlinType> = allowedTypesArgument.arguments.mapNotNull { kclassReference ->
            (kclassReference.resolvedType.typeArguments.firstOrNull() as? ConeKotlinTypeProjection)?.type
        }

        val targetType = (expression.conversionTypeRef as? FirResolvedTypeRef)?.coneType ?: return

        val isTargetTypeInUnion = allowedTypes.any { allowedType ->
            targetType.isSubtypeOf(allowedType, context.session) || allowedType.isSubtypeOf(targetType, context.session)
        }

        when (operation) {
            FirOperation.AS -> {
                if (!isTargetTypeInUnion) {
                    val errorMessage = "Cast will always fail: type ${targetType.renderReadable()} is not part of the union type."
                    reporter.reportOn(expression.conversionTypeRef.source, UnionTypeErrors.CAST_WILL_ALWAYS_FAIL, errorMessage, context)
                } else if (allowedTypes.size > 1) {
                    val otherTypes = allowedTypes.filter { !it.isSubtypeOf(targetType, context.session) }.joinToString { it.renderReadable() }
                    if (otherTypes.isNotEmpty()) {
                        val errorMessage = "Unsafe cast: this value can also be of type(s) [$otherTypes]."
                        reporter.reportOn(expression.source, UnionTypeErrors.UNSAFE_UNION_TYPE_CAST, errorMessage, context)
                    }
                }
            }
            FirOperation.SAFE_AS -> {
                if (!isTargetTypeInUnion) {
                    val errorMessage = "Useless cast: type ${targetType.renderReadable()} is not part of the union type and the cast will always return null."
                    reporter.reportOn(expression.source, UnionTypeErrors.USELESS_CAST, errorMessage, context)
                }
            }
            else -> {}
        }
    }
}

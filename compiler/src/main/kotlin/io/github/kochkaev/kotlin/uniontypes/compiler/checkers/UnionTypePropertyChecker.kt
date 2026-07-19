package io.github.kochkaev.kotlin.uniontypes.compiler.checkers

import io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics.UnionTypeErrors
import io.github.kochkaev.kotlin.uniontypes.compiler.util.findUnionTypeAnnotation
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

object UnionTypePropertyChecker : FirPropertyChecker(MppCheckerKind.Common) {

    private val UNION_TYPE_ANNOTATION_CLASS_ID = ClassId.topLevel(FqName("io.github.kochkaev.kotlin.uniontypes.annotations.UnionType"))

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirProperty) {
        val initializer = declaration.initializer ?: return
        val propertyTypeRef = declaration.returnTypeRef

        // Use the utility function to find the annotation, handling typealiases
        val unionTypeAnnotation = propertyTypeRef.findUnionTypeAnnotation() ?: return

        val allowedTypesArgument = unionTypeAnnotation.argumentMapping.mapping.values.firstOrNull()
                as? FirVarargArgumentsExpression ?: return
        
        val allowedTypes: List<ConeKotlinType> = allowedTypesArgument.arguments.mapNotNull { kclassReference ->
            val kclassType = kclassReference.resolvedType
            (kclassType.typeArguments.firstOrNull() as? ConeKotlinTypeProjection)?.type
        }

        val initializerType = initializer.resolvedType

        val isAllowed = allowedTypes.any { allowedType ->
            initializerType.isSubtypeOf(allowedType, context.session)
        }

        if (!isAllowed) {
            reporter.reportOn(
                source = initializer.source,
                factory = UnionTypeErrors.TYPE_MISMATCH_IN_UNION_TYPE,
                a = initializerType,
                b = allowedTypes
            )
        }
    }
}

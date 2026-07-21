package io.github.kochkaev.kotlin.uniontypes.compiler

import io.github.kochkaev.kotlin.uniontypes.compiler.checkers.*
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.type.TypeCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

@OptIn(SessionConfiguration::class)
class UnionTypeAdditionalCheckers(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val classCheckers = setOf(UnionTypeClassDeclarationChecker)
        override val propertyCheckers = setOf(UnionTypePropertyChecker, UnionTypePropertyDeclarationChecker)
        override val functionCheckers = setOf(UnionTypeFunctionReturnChecker, UnionTypeFunctionDeclarationChecker)
    }

    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val variableAssignmentCheckers = setOf(UnionTypeAssignmentChecker)
        override val functionCallCheckers = setOf(UnionTypeFunctionCallChecker)
        override val whenExpressionCheckers = setOf(UnionTypeWhenExpressionChecker)
        override val typeOperatorCallCheckers = setOf(UnionTypeTypeOperatorCallChecker)
    }

    override val typeCheckers: TypeCheckers = object : TypeCheckers() {
        override val typeRefCheckers = setOf(UnionTypeSupertypeChecker)
    }
}

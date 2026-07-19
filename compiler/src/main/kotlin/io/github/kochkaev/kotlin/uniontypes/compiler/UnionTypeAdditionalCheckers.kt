package io.github.kochkaev.kotlin.uniontypes.compiler

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

class UnionTypeAdditionalCheckers(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        // Here we will add declaration checkers
    }

    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        // Here we will add expression checkers
    }
}

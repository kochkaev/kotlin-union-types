package io.github.kochkaev.kotlin.uniontypes.compiler

import io.github.kochkaev.kotlin.uniontypes.compiler.checkers.UnionTypeAdditionalCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class UnionTypeFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        // This is the correct place to register our FirAdditionalCheckersExtension.Factory
        // The `+` operator is a shorthand for `add` method in ExtensionRegistrarContext
        +FirAdditionalCheckersExtension.Factory { session ->
            UnionTypeAdditionalCheckers(session)
        }
    }
}

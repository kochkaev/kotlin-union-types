package io.github.kochkaev.kotlin.uniontypes.compiler

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class UnionTypeFirExtensionRegistrar : FirExtensionRegistrar() {
    override fun ExtensionRegistrarContext.configurePlugin() {
        +::UnionTypeAdditionalCheckers
    }
}

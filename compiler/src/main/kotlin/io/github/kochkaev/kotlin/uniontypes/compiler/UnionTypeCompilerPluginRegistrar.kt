package io.github.kochkaev.kotlin.uniontypes.compiler

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

@OptIn(ExperimentalCompilerApi::class)
class UnionTypeCompilerPluginRegistrar : CompilerPluginRegistrar() {

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        // This is where we register our FIR extension registrar.
        // The compiler will then use it to get our checkers.
        FirExtensionRegistrar.registerExtension(UnionTypeFirExtensionRegistrar())
    }
}

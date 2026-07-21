package io.github.kochkaev.kotlin.uniontypes.compiler.util

import org.jetbrains.kotlin.AbstractKtSourceElement
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol

data class DeclarationInfo(
    val source: AbstractKtSourceElement?,
    val symbol: FirBasedSymbol<FirDeclaration>?,
    val declaration: FirDeclaration? = null,
)

fun FirDeclaration.info() = DeclarationInfo(
    source = source,
    symbol = symbol,
    declaration = this,
)
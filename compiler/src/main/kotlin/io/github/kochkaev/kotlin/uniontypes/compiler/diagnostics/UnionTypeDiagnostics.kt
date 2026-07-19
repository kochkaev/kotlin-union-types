package io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics

import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.rendering.Renderers
import org.jetbrains.kotlin.diagnostics.rendering.Renderer
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.render

object UnionTypeErrors {
    val UNION_TYPE_ON_SUPERTYPE by Errors.SimpleError(
        "{0}: @UnionType annotation is not allowed on supertypes",
        Renderers.STRING
    )
}

class UnionTypeMessages : DefaultErrorMessages.Extension {
    override fun getMap(): DiagnosticFactoryToRendererMap {
        return DiagnosticFactoryToRendererMap("UnionType").apply {
            put(UnionTypeErrors.UNION_TYPE_ON_SUPERTYPE, "UnionType annotation is not allowed on supertypes")
        }
    }
}

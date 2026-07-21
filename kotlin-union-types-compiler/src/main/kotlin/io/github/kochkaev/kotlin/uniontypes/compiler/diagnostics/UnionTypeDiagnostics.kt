package io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics

import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionConeType
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.*
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.Renderer
import org.jetbrains.kotlin.diagnostics.rendering.Renderers
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.renderer.ConeIdRenderer
import org.jetbrains.kotlin.fir.renderer.ConeIdShortRenderer
import org.jetbrains.kotlin.fir.renderer.ConeTypeRendererForReadability
import org.jetbrains.kotlin.fir.types.ConeFlexibleType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeSimpleKotlinType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.types.model.TypeConstructorMarker

typealias UnionContextPair = Pair<UnionConeType, CheckerContext>

object UnionTypeErrors: KtDiagnosticsContainer() {
    val UNION_TYPE_ON_SUPERTYPE by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val TYPE_MISMATCH by error2<PsiElement, UnionContextPair, UnionContextPair>(SourceElementPositioningStrategies.DEFAULT)
    val INVALID_SUPERTYPE_FOR_UNION_TYPE by error2<PsiElement, UnionContextPair, UnionContextPair>(SourceElementPositioningStrategies.DEFAULT)

    val UNREACHABLE_WHEN_BRANCH by warning1<PsiElement, UnionContextPair>(SourceElementPositioningStrategies.DEFAULT)
    val CAST_WILL_ALWAYS_FAIL by error1<PsiElement, UnionContextPair>(SourceElementPositioningStrategies.DEFAULT)
    val USELESS_CAST by warning1<PsiElement, UnionContextPair>(SourceElementPositioningStrategies.DEFAULT)
    val UNSAFE_UNION_TYPE_CAST by warning1<PsiElement, Pair<Collection<UnionConeType>, CheckerContext>>(SourceElementPositioningStrategies.DEFAULT)

    val EXTENSION_ON_UNION_TYPE by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val UNION_TYPE_ON_CONTEXT_PARAMETER by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val TYPE_AND_TYPE_PARAMETER_AT_SAME_TIME by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val INTERSECTION_AND_UNION_AT_SAME_TIME by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val INTERSECTION_ON_UNION_TYPE by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)

    val TYPE_PARAMETER_NOT_FOUND by error1<PsiElement, String>(SourceElementPositioningStrategies.DEFAULT)

    override fun getRendererFactory(): BaseDiagnosticRendererFactory {
        return object : BaseDiagnosticRendererFactory() {
            override val MAP by KtDiagnosticFactoryToRendererMap("UnionType") {
                with(UnionTypeDiagnosticRenderers) { with (it) {
                    put(
                        factory = UNION_TYPE_ON_SUPERTYPE,
                        message = "Union and intersection type annotations is not allowed on supertypes",
                    )
                    put(
                        factory = TYPE_MISMATCH,
                        message = "Type mismatch. Found: {0}, but expected: {1}",
                        rendererA = TYPE_WITH_UNIONS,
                        rendererB = TYPE_WITH_UNIONS,
                    )
                    put(
                        factory = INVALID_SUPERTYPE_FOR_UNION_TYPE,
                        message = "Type {0} is not a subtype of {1} as required by the union/intersection type annotation.",
                        rendererA = TYPE_WITH_UNIONS,
                        rendererB = TYPE_WITH_UNIONS
                    )

                    put(
                        factory = UNREACHABLE_WHEN_BRANCH,
                        message = "Unreachable branch: type {0} is not part of the union type",
                        rendererA = TYPE_WITH_UNIONS
                    )
                    put(
                        factory = CAST_WILL_ALWAYS_FAIL,
                        message = "Cast will always fail: type {0} is not part of the union type.",
                        rendererA = TYPE_WITH_UNIONS
                    )
                    put(
                        factory = USELESS_CAST,
                        message = "Useless cast: type {0} is not part of the union type and the cast will always return null.",
                        rendererA = TYPE_WITH_UNIONS
                    )
                    put(
                        factory = UNSAFE_UNION_TYPE_CAST,
                        message = "Unsafe cast: this value can also be of type(s) {0}.",
                        rendererA = UNION_OF_TYPES
                    )

                    put(
                        factory = EXTENSION_ON_UNION_TYPE,
                        message = "Extension functions/properties are not allowed on union/intersection types."
                    )
                    put(
                        factory = UNION_TYPE_ON_CONTEXT_PARAMETER,
                        message = "Union/intersection types are not allowed on context parameters."
                    )
                    put(
                        factory = TYPE_AND_TYPE_PARAMETER_AT_SAME_TIME,
                        message = "Cannot use 'type' and 'typeParameter' at the same time."
                    )
                    put(
                        factory = INTERSECTION_AND_UNION_AT_SAME_TIME,
                        message = "A type cannot be annotated with both @Union/@UnionAdv and @Intersection/@IntersectionAdv at the same time."
                    )
                    put(
                        factory = INTERSECTION_ON_UNION_TYPE,
                        message = "An @Intersection/@IntersectionAdv annotation cannot be applied to a union type."
                    )


                    put(
                        factory = TYPE_PARAMETER_NOT_FOUND,
                        message = "Type parameter {0} not found",
                        rendererA = Renderers.TO_STRING
                    )
                } }
            }
        }
    }
}
object UnionTypeDiagnosticRenderers {
    val TYPE = Renderer<Pair<ConeKotlinType, CheckerContext>> { (type: ConeKotlinType, context: CheckerContext) ->
        type.renderReadable()
    }
    val TYPE_WITH_UNIONS = Renderer<Pair<UnionConeType, CheckerContext>> { (type: UnionConeType, context: CheckerContext) ->
        with(context) {
            type.renderReadable()
        }
    }
    val UNION_OF_TYPES = Renderer<Pair<Collection<UnionConeType>, CheckerContext>> { (collection: Collection<UnionConeType>, context: CheckerContext) ->
        collection.joinToString(" | ") { with (context) {
            it.renderReadable()
        } }
    }
    context(context: CheckerContext)
    fun UnionConeType.renderReadable(preRenderedConstructors: Map<TypeConstructorMarker, String>? = null): String {
        val builder = StringBuilder()
        val renderer = with(null as? DiagnosticReporter) {
            ConeTypeRendererWithUnion(context, builder, preRenderedConstructors, toBuilder()) { ConeIdShortRenderer() }
        }
        renderer.render(this)
        return builder.toString()
    }
}
open class ConeTypeRendererWithUnion (
    val context: CheckerContext,
    builder: StringBuilder,
    preRenderedConstructors: Map<TypeConstructorMarker, String>? = null,
    val unionBuilder: (ConeKotlinType) -> UnionConeType =
        with(context) { with(null as DiagnosticReporter?) { UnionConeType.builder() } },
    idRendererCreator: () -> ConeIdRenderer,
): ConeTypeRendererForReadability(builder, preRenderedConstructors, idRendererCreator) {

    open fun render(
        union: UnionConeType,
        unionSeparator: String = " | ",
        nullabilityMarker: String = withContext { if (union.thisType !is ConeFlexibleType && union.thisType.isMarkedNullable) "?" else "" },
    ) = withContext {
        union.unionOrThis
            .filterIsInstance<ConeSimpleKotlinType>()
            .fold(false) { notFirstIteration, type ->
                if (notFirstIteration) builder.append(unionSeparator)
                super.render(type, nullabilityMarker)
                true
            }
    }
    override fun renderSimpleType(type: ConeSimpleKotlinType, nullabilityMarker: String) {
        val union = unionBuilder(type)
        withContext {
            if (union.isDeclaredUnionType || union.isUnionOverrode) render(union)
            else super.renderSimpleType(type, nullabilityMarker)
        }
    }

    fun <T> withContext(block: context(CheckerContext, DiagnosticReporter?) () -> T): T =
        with(context) { with(null as DiagnosticReporter?) { block() } }
}

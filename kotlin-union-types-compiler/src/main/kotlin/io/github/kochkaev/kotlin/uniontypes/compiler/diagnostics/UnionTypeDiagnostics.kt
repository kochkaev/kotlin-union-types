package io.github.kochkaev.kotlin.uniontypes.compiler.diagnostics

import io.github.kochkaev.kotlin.uniontypes.compiler.util.UnionBuilder
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
import org.jetbrains.kotlin.fir.types.ConeIntersectionType
import org.jetbrains.kotlin.fir.types.ConeSimpleKotlinType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.types.model.TypeConstructorMarker

typealias UnionContextPair = Pair<UnionConeType, CheckerContext>

object UnionTypeErrors: KtDiagnosticsContainer() {
    val UNION_TYPE_ON_SUPERTYPE by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val TYPE_MISMATCH by error2<PsiElement, UnionContextPair, UnionContextPair>(SourceElementPositioningStrategies.DEFAULT)

    val UNREACHABLE_WHEN_BRANCH by warning0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val CAST_WILL_ALWAYS_FAIL by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val CHECK_FOR_INSTANCE_IS_ALWAYS_FALSE by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val USELESS_CAST by warning0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val UNSAFE_CAST by warning2<PsiElement, UnionContextPair, UnionContextPair>(SourceElementPositioningStrategies.DEFAULT)

    val EXTENSION_ON_UNION_TYPE by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val UNION_TYPE_ON_CONTEXT_PARAMETER by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val TYPE_AND_TYPE_PARAMETER_AT_SAME_TIME by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val INTERSECTION_AND_UNION_AT_SAME_TIME by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)
    val INTERSECTION_ON_UNION_TYPE by error0<PsiElement>(SourceElementPositioningStrategies.DEFAULT)

    val TYPE_PARAMETER_NOT_FOUND by error1<PsiElement, String>(SourceElementPositioningStrategies.DEFAULT)

    val INVALID_UNION_OF_MEMBERS by error2<PsiElement, UnionContextPair, UnionContextPair>(SourceElementPositioningStrategies.DEFAULT)
    val INVALID_INTERSECTION_OF_MEMBERS by error2<PsiElement, UnionContextPair, UnionContextPair>(SourceElementPositioningStrategies.DEFAULT)


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
                        factory = UNREACHABLE_WHEN_BRANCH,
                        message = "Unreachable 'when' branch",
                    )
                    put(
                        factory = CAST_WILL_ALWAYS_FAIL,
                        message = "This cast can never succeed.",
                    )
                    put(
                        factory = CHECK_FOR_INSTANCE_IS_ALWAYS_FALSE,
                        message = "Check for instance is always 'false'.",
                    )
                    put(
                        factory = USELESS_CAST,
                        message = "This cast can never succeed.",
                    )
                    put(
                        factory = UNSAFE_CAST,
                        message = "Unsafe cast of {0} to {1}",
                        rendererA = TYPE_WITH_UNIONS,
                        rendererB = TYPE_WITH_UNIONS,
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

                    put(
                        factory = INVALID_UNION_OF_MEMBERS,
                        message = "The union of all members ({0}) must be a subtype of or equivalent to the base type ({1}).",
                        rendererA = TYPE_WITH_UNIONS,
                        rendererB = TYPE_WITH_UNIONS,
                    )
                    put(
                        factory = INVALID_INTERSECTION_OF_MEMBERS,
                        message = "The intersection of all members ({0}) must be a subtype of or equivalent to the base type ({1}).",
                        rendererA = TYPE_WITH_UNIONS,
                        rendererB = TYPE_WITH_UNIONS,
                    )
                } }
            }
        }
    }
}
object UnionTypeDiagnosticRenderers {
    val TYPE_WITH_UNIONS = Renderer<Pair<UnionConeType, CheckerContext>> { (type: UnionConeType, context: CheckerContext) ->
        with(context) {
            type.renderReadable()
        }
    }
    context(context: CheckerContext)
    fun UnionConeType.renderReadable(preRenderedConstructors: Map<TypeConstructorMarker, String>? = null): String {
        val builder = StringBuilder()
        val renderer = ConeTypeRendererWithUnion(context, builder, preRenderedConstructors, toBuilder()) { ConeIdShortRenderer() }
        renderer.render(this)
        return builder.toString()
    }
}
open class ConeTypeRendererWithUnion (
    val context: CheckerContext,
    builder: StringBuilder,
    preRenderedConstructors: Map<TypeConstructorMarker, String>? = null,
    val unionBuilder: UnionBuilder = UnionConeType.builder(),
    idRendererCreator: () -> ConeIdRenderer,
): ConeTypeRendererForReadability(builder, preRenderedConstructors, idRendererCreator) {
    override fun renderSimpleType(type: ConeSimpleKotlinType, nullabilityMarker: String) = withContext {
        val union = unionBuilder(type)
        render(union, nullabilityMarker = nullabilityMarker)
    }
    fun render(
        type: UnionConeType,
        unionSeparator: String = " | ",
        nullabilityMarker: String = withContext { if (type.thisType !is ConeFlexibleType && type.thisType.isMarkedNullable) "?" else "" },
    ) {
        withContext {
            val raw = type.thisType
            val isNotEmptyUnionOverrode = type.isUnionOverrideNotEmpty
            val isEmptyUnionOverrode = type.isUnionOverrode && !isNotEmptyUnionOverrode
            when {
                type.cachedUnexpanded != null -> {
                    render(type.cachedUnexpanded!!, unionSeparator)
                    return@withContext
                }
                type.isDeclaredUnionType && !isEmptyUnionOverrode || isNotEmptyUnionOverrode -> {
                    type.unionWrapped
                        .fold(false) { notFirstIteration, type ->
                            if (notFirstIteration) builder.append(unionSeparator)
                            super.render(type.thisType, nullabilityMarker)
                            true
                        }
                    return@withContext
                }
                raw is ConeIntersectionType -> {
                    super.render(raw)
                    return@withContext
                }
                raw is ConeSimpleKotlinType -> super.renderSimpleType(raw, nullabilityMarker)
                else -> super.render(raw, nullabilityMarker)
            }
        }
    }

    @Suppress("RedundantWith")
    fun <T> withContext(block: context(CheckerContext, DiagnosticReporter?) () -> T): T =
        with(context) { with(null as DiagnosticReporter?) { block() } }
}
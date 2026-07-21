package io.github.kochkaev.kotlin.uniontypes.idea.plugin

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtTypeReference

class UnionTypeAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is KtTypeReference) {
            // TODO: Implement union type validation logic here
        }
    }
}
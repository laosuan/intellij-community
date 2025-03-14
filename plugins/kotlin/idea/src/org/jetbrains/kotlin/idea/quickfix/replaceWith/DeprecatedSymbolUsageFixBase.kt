// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.replaceWith

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.kotlin.base.fe10.analysis.getAnnotationValue
import org.jetbrains.kotlin.base.fe10.analysis.getArrayValue
import org.jetbrains.kotlin.base.fe10.analysis.getStringValue
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.TypeAliasConstructorDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.restrictToKotlinSources
import org.jetbrains.kotlin.idea.caches.KotlinShortNamesCache
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.codeInliner.CallableUsageReplacementStrategy
import org.jetbrains.kotlin.idea.codeInliner.ClassUsageReplacementStrategy
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.core.OptionalParametersHelper
import org.jetbrains.kotlin.idea.intentions.isInvokeOperator
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.references.resolveMainReferenceToDescriptors
import org.jetbrains.kotlin.idea.references.resolveToDescriptors
import org.jetbrains.kotlin.idea.util.application.isDispatchThread
import org.jetbrains.kotlin.idea.util.replaceOrCreateTypeArgumentList
import org.jetbrains.kotlin.ir.expressions.typeParametersCount
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isCallee
import org.jetbrains.kotlin.psi.psiUtil.referenceExpression
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.components.hasDefaultValue
import org.jetbrains.kotlin.resolve.calls.model.isReallySuccess
import org.jetbrains.kotlin.resolve.calls.util.FakeCallableDescriptorForTypeAliasObject
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.constants.StringValue
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.util.constructors
import org.jetbrains.kotlin.util.mapAll

//TODO: different replacements for property accessors

abstract class DeprecatedSymbolUsageFixBase(
    element: KtReferenceExpression,
    val replaceWith: ReplaceWithData
) : KotlinQuickFixAction<KtReferenceExpression>(element) {

    internal val isAvailable: Boolean

    init {
        assert(!isDispatchThread()) {
            "${javaClass.name} should not be created on EDT"
        }
        isAvailable = buildUsageReplacementStrategy(
            element,
            replaceWith,
            recheckAnnotation = true,
            reformat = false
        )?.let { it.createReplacer(element) != null } == true
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean = element != null && isAvailable

    // TODO: it has to be fixed as it runs ReferencesSearch under writeAction
    override fun startInWriteAction(): Boolean = true

    final override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val expression = element ?: return
        val strategy = buildUsageReplacementStrategy(
            expression, replaceWith, recheckAnnotation = false, reformat = true, editor = editor
        ) ?: return
        invoke(strategy, project, editor)
    }

    protected abstract operator fun invoke(replacementStrategy: UsageReplacementStrategy, project: Project, editor: Editor?)

    companion object {
        fun fetchReplaceWithPattern(
            descriptor: DeclarationDescriptor,
            project: Project,
            contextElement: KtReferenceExpression?,
            replaceInWholeProject: Boolean
        ): ReplaceWithData? {
            val annotation = descriptor.unwrapTypealiasIfNeeded().annotations.findAnnotation(StandardNames.FqNames.deprecated) ?: return null
            val replaceWithValue = annotation.getAnnotationValue(Deprecated::replaceWith) ?: return null
            val pattern = replaceWithValue.getStringValue(ReplaceWith::expression)?.takeIf { it.isNotEmpty() } ?: return null
            val imports = replaceWithValue.getArrayValue(ReplaceWith::imports)?.mapAll { (it as? StringValue)?.value } ?: return null

            // should not be available for descriptors with optional parameters if we cannot fetch default values for them (currently for library with no sources)
            if (descriptor is CallableDescriptor && descriptor.valueParameters.any {
                    it.hasDefaultValue() && OptionalParametersHelper.defaultParameterValue(it, project) == null
                }
            ) return null

            return if (replaceInWholeProject) {
                ReplaceWithData(pattern, imports, true)
            } else {
                ReplaceWithData(pattern.applyContextElement(contextElement, descriptor), imports, false)
            }
        }

        private fun String.applyContextElement(
            element: KtReferenceExpression?,
            descriptor: DeclarationDescriptor
        ): String {
            if (element == null) return this
            val psiFactory = KtPsiFactory(element.project)
            val expressionFromPattern = psiFactory.createExpressionIfPossible(this) ?: return this

            val classLiteral = when (expressionFromPattern) {
                is KtClassLiteralExpression -> expressionFromPattern
                is KtDotQualifiedExpression -> expressionFromPattern.receiverExpression as? KtClassLiteralExpression
                else -> null
            }
            if (classLiteral != null) {
                val receiver = classLiteral.receiverExpression ?: return this
                val typeParameterText = (descriptor as? CallableDescriptor)?.typeParameters?.firstOrNull()?.name?.asString() ?: return this
                if (receiver.text != typeParameterText) return this
                val typeReference = (element.parent as? KtCallExpression)?.typeArguments?.firstOrNull()?.typeReference ?: return this
                val type = element.analyze(BodyResolveMode.PARTIAL)[BindingContext.TYPE, typeReference]
                if (type != null && KotlinBuiltIns.isArray(type)) {
                    receiver.replace(typeReference)
                } else {
                    receiver.replace(psiFactory.createExpression(typeReference.text.takeWhile { it != '<' }))
                }
                return expressionFromPattern.text
            }

            if (expressionFromPattern !is KtCallExpression) return this
            val methodFromPattern = expressionFromPattern.referenceExpression()?.let { name ->
                KotlinShortNamesCache(element.project).getMethodsByName(
                    name.text,
                    element.resolveScope
                ).firstOrNull()
            }

            val patternTypeArgumentList = expressionFromPattern.typeArgumentList
            val patternTypeArgumentCount = methodFromPattern?.typeParameterList?.typeParameters?.size
                ?: patternTypeArgumentList?.arguments?.size
                ?: return this

            val typeArgumentList = (element.parent as? KtCallExpression)?.typeArgumentList
                ?: (element.parent as? KtUserType)?.typeArgumentList
            val descriptorTypeParameterCount = (descriptor as? CallableDescriptor)?.typeParametersCount
                ?: (descriptor as? ClassDescriptor)?.declaredTypeParameters?.size
            return if (patternTypeArgumentCount == descriptorTypeParameterCount ||
                patternTypeArgumentCount == typeArgumentList?.arguments?.size
            ) {
                if (typeArgumentList != null) expressionFromPattern.replaceOrCreateTypeArgumentList(typeArgumentList.copy() as KtTypeArgumentList)
                expressionFromPattern.text
            } else this
        }

        data class Data(
            val referenceExpression: KtReferenceExpression,
            val replaceWith: ReplaceWithData,
            val descriptor: DeclarationDescriptor
        )

        fun extractDataFromDiagnostic(deprecatedDiagnostic: Diagnostic, replaceInWholeProject: Boolean): Data? {
            val referenceExpression = when (val psiElement = deprecatedDiagnostic.psiElement) {
                is KtArrayAccessExpression -> psiElement
                is KtSimpleNameExpression -> psiElement
                is KtConstructorCalleeExpression -> psiElement.constructorReferenceExpression
                else -> null
            } ?: return null

            val descriptor = when (deprecatedDiagnostic.factory) {
                Errors.DEPRECATION -> DiagnosticFactory.cast(deprecatedDiagnostic, Errors.DEPRECATION).a
                Errors.DEPRECATION_ERROR -> DiagnosticFactory.cast(deprecatedDiagnostic, Errors.DEPRECATION_ERROR).a
                Errors.TYPEALIAS_EXPANSION_DEPRECATION ->
                    DiagnosticFactory.cast(deprecatedDiagnostic, Errors.TYPEALIAS_EXPANSION_DEPRECATION).b
                Errors.TYPEALIAS_EXPANSION_DEPRECATION_ERROR ->
                    DiagnosticFactory.cast(deprecatedDiagnostic, Errors.TYPEALIAS_EXPANSION_DEPRECATION_ERROR).b
                else -> throw IllegalStateException("Bad QuickFixRegistrar configuration")
            }

            val replacement =
                fetchReplaceWithPattern(descriptor, referenceExpression.project, referenceExpression, replaceInWholeProject) ?: return null
            return Data(referenceExpression, replacement, descriptor)
        }

        private fun buildUsageReplacementStrategy(
            element: KtReferenceExpression,
            replaceWith: ReplaceWithData,
            recheckAnnotation: Boolean,
            reformat: Boolean,
            editor: Editor? = null
        ): UsageReplacementStrategy? {
            val resolutionFacade = element.getResolutionFacade()
            val bindingContext = resolutionFacade.analyze(element, BodyResolveMode.PARTIAL)
            val resolvedCall = element.getResolvedCall(bindingContext)
            var target = resolvedCall?.resultingDescriptor?.takeIf { it.isInvokeOperator }
                ?: element.mainReference.resolveToDescriptors(bindingContext).singleOrNull()?.unwrapTypealiasIfNeeded()
                ?: return null

            var replacePatternFromSymbol =
                fetchReplaceWithPattern(target, resolutionFacade.project, element, replaceWith.replaceInWholeProject)
            if (replacePatternFromSymbol == null && target is ConstructorDescriptor) {
                target = target.containingDeclaration
                replacePatternFromSymbol =
                    fetchReplaceWithPattern(target, resolutionFacade.project, element, replaceWith.replaceInWholeProject)
            }

            // check that ReplaceWith hasn't changed
            if (recheckAnnotation && replacePatternFromSymbol != replaceWith) return null

            when (target) {
                is CallableDescriptor -> {
                    if (resolvedCall == null) return null
                    if (!resolvedCall.isReallySuccess()) return null
                    val replacement = ReplaceWithAnnotationAnalyzer.analyzeCallableReplacement(
                        replaceWith, target, resolutionFacade, reformat
                    ) ?: return null
                    return CallableUsageReplacementStrategy(replacement, inlineSetter = false)
                }

                is ClassifierDescriptorWithTypeParameters -> {
                    val replacementType = ReplaceWithAnnotationAnalyzer.analyzeClassifierReplacement(replaceWith, target, resolutionFacade)
                    return when {
                        replacementType != null -> {
                            if (editor != null) {
                                val typeAlias = element
                                    .getStrictParentOfType<KtUserType>()
                                    ?.getStrictParentOfType<KtTypeReference>()
                                    ?.getStrictParentOfType<KtTypeAlias>()
                                if (typeAlias != null) {
                                    val usedConstructorWithOwnReplaceWith = usedConstructorsWithOwnReplaceWith(
                                        element.project, target, typeAlias, element, replaceWith.replaceInWholeProject
                                    )

                                    if (usedConstructorWithOwnReplaceWith != null) {
                                        val constructorStr = DescriptorRenderer.ONLY_NAMES_WITH_SHORT_TYPES.render(
                                            usedConstructorWithOwnReplaceWith
                                        )
                                        HintManager.getInstance().showErrorHint(
                                            editor,
                                            KotlinBundle.message(
                                                "there.is.own.replacewith.on.0.that.is.used.through.this.alias.please.replace.usages.first",
                                                constructorStr
                                            )
                                        )
                                        return null
                                    }
                                }
                            }

                            //TODO: check that it's really resolved and is not an object otherwise it can be expression as well
                            ClassUsageReplacementStrategy(replacementType, null, element.project)
                        }
                        target is ClassDescriptor -> {
                            val constructor = target.unsubstitutedPrimaryConstructor ?: return null
                            val replacementExpression = ReplaceWithAnnotationAnalyzer.analyzeCallableReplacement(
                                replaceWith,
                                constructor,
                                resolutionFacade,
                                reformat
                            ) ?: return null
                            ClassUsageReplacementStrategy(null, replacementExpression, element.project)
                        }
                        else -> null
                    }
                }

                else -> return null
            }
        }

        private fun DeclarationDescriptor.unwrapTypealiasIfNeeded(): DeclarationDescriptor =
            if (this is FakeCallableDescriptorForTypeAliasObject) typeAliasDescriptor else this

        private fun usedConstructorsWithOwnReplaceWith(
            project: Project,
            classifier: ClassifierDescriptorWithTypeParameters,
            typeAlias: PsiElement,
            contextElement: KtReferenceExpression,
            replaceInWholeProject: Boolean
        ): ConstructorDescriptor? {
            val specialReplaceWithForConstructor = classifier.constructors.filter {
                fetchReplaceWithPattern(it, project, contextElement, replaceInWholeProject) != null
            }.toSet()

            if (specialReplaceWithForConstructor.isEmpty()) {
                return null
            }

            val searchAliasConstructorUsagesScope = GlobalSearchScope.allScope(project).restrictToKotlinSources()
            ReferencesSearch.search(typeAlias, searchAliasConstructorUsagesScope).asIterable().find { reference ->
                val element = reference.element

                if (element is KtSimpleNameExpression && element.isCallee()) {
                    val aliasConstructors = element.resolveMainReferenceToDescriptors().filterIsInstance<TypeAliasConstructorDescriptor>()
                    for (referenceConstructor in aliasConstructors) {
                        if (referenceConstructor.underlyingConstructorDescriptor in specialReplaceWithForConstructor) {
                            return referenceConstructor.underlyingConstructorDescriptor
                        }
                    }
                }

                false
            }

            return null
        }
    }
}

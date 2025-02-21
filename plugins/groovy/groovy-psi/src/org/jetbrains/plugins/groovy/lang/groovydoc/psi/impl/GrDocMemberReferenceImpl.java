// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.groovydoc.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTagValueToken;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;

public abstract class GrDocMemberReferenceImpl extends GroovyDocPsiElementImpl implements GrDocMemberReference {
  public GrDocMemberReferenceImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public @Nullable GrDocReferenceElement getReferenceHolder() {
    return findChildByClass(GrDocReferenceElement.class);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return getManager().areElementsEquivalent(element, resolve());
  }

  @Override
  public @Nullable PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (isReferenceTo(element)) return this;

    if (element instanceof PsiClass) {
      GrDocReferenceElement holder = getReferenceHolder();
      if (holder != null) {
        return replace(holder.getReferenceElement().bindToElement(element).getParent());
      }
      GrDocReferenceElement ref =
        GroovyPsiElementFactory.getInstance(getProject()).createDocReferenceElementFromFQN(((PsiClass)element).getQualifiedName());
      return replace(ref);
    }
    else if (element instanceof PsiMember) {
      PsiClass clazz = ((PsiMember)element).getContainingClass();
      if (clazz == null) return null;
      String qName = clazz.getQualifiedName();
      String memberRefText;
      if (element instanceof PsiField) {
        memberRefText = ((PsiField)element).getName();
      }
      else if (element instanceof PsiMethod) {
        PsiParameterList list = ((PsiMethod)element).getParameterList();
        StringBuilder builder = new StringBuilder();
        builder.append(((PsiMethod)element).getName()).append("(");
        PsiParameter[] params = list.getParameters();
        for (int i = 0; i < params.length; i++) {
          PsiParameter parameter = params[i];
          PsiType type = parameter.getType();
          if (i > 0) builder.append(", ");
          builder.append(type.getPresentableText());
        }
        builder.append(")");
        memberRefText = builder.toString();
      }
      else {
        return null;
      }
      GrDocMemberReference ref = GroovyPsiElementFactory.getInstance(getProject()).createDocMemberReferenceFromText(qName, memberRefText);
      return replace(ref);
    }
    return null;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    PsiElement nameElement = getReferenceNameElement();
    ASTNode node = nameElement.getNode();
    ASTNode newNameNode = GroovyPsiElementFactory.getInstance(getProject()).createDocMemberReferenceNameFromText(newElementName).getNode();
    assert newNameNode != null && node != null;
    node.getTreeParent().replaceChild(node, newNameNode);
    return this;
  }

  @Override
  public @NotNull GrDocTagValueToken getReferenceNameElement() {
    GrDocTagValueToken token = findChildByClass(GrDocTagValueToken.class);
    assert token != null;
    return token;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return this;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    final PsiElement refNameElement = getReferenceNameElement();
    final int offsetInParent = refNameElement.getStartOffsetInParent();
    return new TextRange(offsetInParent, offsetInParent + refNameElement.getTextLength());
  }

  @Override
  public @NotNull String getCanonicalText() {
    return getRangeInElement().substring(getElement().getText());
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public @Nullable PsiElement getQualifier() {
    return getReferenceHolder();
  }

  @Override
  public @Nullable @NonNls String getReferenceName() {
    return getReferenceNameElement().getText();
  }

  @Override
  public @Nullable PsiElement resolve() {
    ResolveResult[] results = multiResolve(false);
    if (results.length == 1) {
      return results[0].getElement();
    }
    return null;
  }

  @Override
  public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
    return multiResolveImpl();
  }

  protected abstract ResolveResult[] multiResolveImpl();
}

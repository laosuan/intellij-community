/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;

public final class ConditionalExpressionWithIdenticalBranchesInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("conditional.expression.with.identical.branches.problem.descriptor");
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new CollapseConditionalFix();
  }

  private static class CollapseConditionalFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("conditional.expression.with.identical.branches.collapse.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)startElement;
      final PsiExpression thenExpression = conditionalExpression.getThenExpression();
      if (thenExpression == null) {
        return;
      }
      final PsiExpression elseExpression = conditionalExpression.getElseExpression();
      if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenExpression, elseExpression)) {
        CommentTracker commentTracker = new CommentTracker();
        PsiReplacementUtil.replaceExpression(conditionalExpression, commentTracker.text(thenExpression), commentTracker);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConditionalExpressionWithIdenticalBranchesVisitor();
  }

  private static class ConditionalExpressionWithIdenticalBranchesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression thenExpression = expression.getThenExpression();
      if (thenExpression == null) {
        return;
      }
      final PsiExpression elseExpression = expression.getElseExpression();
      if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenExpression, elseExpression)) {
        registerError(expression);
      }
    }
  }
}
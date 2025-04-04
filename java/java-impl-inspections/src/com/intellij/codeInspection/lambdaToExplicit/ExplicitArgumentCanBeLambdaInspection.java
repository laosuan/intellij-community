// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.lambdaToExplicit;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiEmptyExpressionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class ExplicitArgumentCanBeLambdaInspection extends AbstractBaseJavaLocalInspectionTool {

    @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.LAMBDA_EXPRESSIONS);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        for(LambdaAndExplicitMethodPair info : LambdaAndExplicitMethodPair.INFOS) {
          PsiExpression arg = info.getLambdaCandidateFromExplicitCall(call);
          if(arg != null && !(arg instanceof PsiEmptyExpressionImpl) && !ExpressionUtils.isSafelyRecomputableExpression(arg)) {
            holder.registerProblem(arg, JavaBundle.message("inspection.explicit.argument.can.be.lambda.message"),
                                   new ConvertExplicitCallToLambdaFix(info, info.getLambdaMethodName(call)));
            return;
          }
        }
      }
    };
  }

  private static class ConvertExplicitCallToLambdaFix extends PsiUpdateModCommandQuickFix {
    private final LambdaAndExplicitMethodPair myInfo;
    private final String myName;

    ConvertExplicitCallToLambdaFix(LambdaAndExplicitMethodPair info, String name) {
      myInfo = info;
      myName = name;
    }

    @Override
    public @Nls @NotNull String getName() {
      return JavaBundle.message("inspection.explicit.argument.can.be.lambda.fix.name", myName);
    }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.explicit.argument.can.be.lambda.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiExpression arg = ObjectUtils.tryCast(element, PsiExpression.class);
      if (arg == null) return;
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(arg, PsiMethodCallExpression.class);
      if (call == null) return;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      int idx = ArrayUtil.indexOf(args, arg);
      if(idx < 0) return;
      ExpressionUtils.bindCallTo(call, myName);
      String lambdaText = myInfo.makeLambda(arg);
      PsiLambdaExpression lambda =
        (PsiLambdaExpression)arg.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(lambdaText, arg));
      LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference(lambda);
    }
  }
}

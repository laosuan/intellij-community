// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.arrangement.match;

import com.intellij.application.options.codeStyle.arrangement.ArrangementConstants;
import com.intellij.application.options.codeStyle.arrangement.animation.ArrangementAnimationManager;
import com.intellij.application.options.codeStyle.arrangement.animation.ArrangementAnimationPanel;
import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.application.options.codeStyle.arrangement.component.ArrangementAndMatchConditionComponent;
import com.intellij.application.options.codeStyle.arrangement.component.ArrangementAtomMatchConditionComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchConditionVisitor;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsManager;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementUiComponent;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class ArrangementMatchNodeComponentFactory {

  private static final Logger LOG = Logger.getInstance(ArrangementMatchNodeComponentFactory.class);

  private final @NotNull ArrangementStandardSettingsManager mySettingsManager;
  private final @NotNull ArrangementColorsProvider          myColorsProvider;
  private final @NotNull ArrangementMatchingRulesControl    myList;

  public ArrangementMatchNodeComponentFactory(@NotNull ArrangementStandardSettingsManager manager,
                                              @NotNull ArrangementColorsProvider provider,
                                              @NotNull ArrangementMatchingRulesControl list)
  {
    mySettingsManager = manager;
    myColorsProvider = provider;
    myList = list;
  }

  /**
   * Allows to build UI component for the given model.
   *
   * @param rendererTarget      target model element for which UI component should be built
   * @param rule                rule which contains given 'renderer target' condition and serves as
   *                            a data entry for the target list model
   * @param allowModification   flag which indicates whether given model can be changed at future
   * @return renderer for the given model
   */
  public @NotNull ArrangementUiComponent getComponent(final @NotNull ArrangementMatchCondition rendererTarget,
                                             final @NotNull StdArrangementMatchRule rule,
                                             final boolean allowModification)
  {
    final Ref<ArrangementUiComponent> ref = new Ref<>();
    rendererTarget.invite(new ArrangementMatchConditionVisitor() {
      @Override
      public void visit(@NotNull ArrangementAtomMatchCondition condition) {
        RemoveAtomConditionCallback callback = allowModification ? new RemoveAtomConditionCallback(rule) : null;
        ArrangementUiComponent component = new ArrangementAtomMatchConditionComponent(
          mySettingsManager, myColorsProvider, condition, callback
        );
        ref.set(component);
      }

      @Override
      public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
        ref.set(new ArrangementAndMatchConditionComponent(rule, condition, ArrangementMatchNodeComponentFactory.this, mySettingsManager, allowModification));
      }
    });
    return ref.get();
  }

  private final class RemoveAtomConditionCallback implements Consumer<ArrangementAtomMatchConditionComponent>,
                                                             ArrangementAnimationManager.Callback
  {

    private final @NotNull StdArrangementMatchRule myRule;

    private @NotNull Object myModelValue;
    private int myRow;

    RemoveAtomConditionCallback(@NotNull StdArrangementMatchRule rule) {
      myRule = rule;
      myModelValue = myRule;
    }

    @Override
    public void consume(@NotNull ArrangementAtomMatchConditionComponent component) {
      ArrangementAtomMatchCondition condition = component.getMatchCondition();
      ArrangementMatchingRulesModel model = myList.getModel();
      int i = getModelIndex();
      if (i < 0) {
        return;
      }
      myRow = i;

      ArrangementMatchCondition existingCondition = myRule.getMatcher().getCondition();
      if (existingCondition.equals(condition)) {
        // We can't just remove an element at this time because that breaks last row rendering. 
        model.set(i, myModelValue = new DummyElement());
      }
      else {
        assert existingCondition instanceof ArrangementCompositeMatchCondition;
        Set<ArrangementMatchCondition> operands = ((ArrangementCompositeMatchCondition)existingCondition).getOperands();
        operands.remove(condition);
        if (operands.isEmpty()) {
          // We can't just remove an element at this time because that breaks last row rendering.
          model.set(i, myModelValue = new DummyElement());
        }
        else if (operands.size() == 1) {
          myModelValue = new StdArrangementMatchRule(new StdArrangementEntryMatcher(operands.iterator().next()), myRule.getOrderType());
          model.set(i, myModelValue);
        }
        else if (ArrangementConstants.LOG_RULE_MODIFICATION) {
          LOG.info(String.format("Removed '%s' condition. Current rule state: %s", condition, myRule));
          myModelValue = myRule;
        }
      }

      ArrangementAnimationPanel panel = component.getAnimationPanel();
      new ArrangementAnimationManager(panel, this).startAnimation();
    }

    @Override
    public void onAnimationIteration(boolean finished) {
      refreshRow();
      if (myRow < 0) {
        return;
      }
      myList.repaintRows(myRow, myRow, finished);
      if (!finished) {
        return;
      }
      if (myModelValue instanceof DummyElement) {
        myList.removeRow(myRow);
      }
    }

    private void refreshRow() {
      ArrangementMatchingRulesModel model = myList.getModel();
      if (myRow < 0 || myRow >= model.getSize()) {
        myRow = getModelIndex();
      }
      else {
        Object o = model.getElementAt(myRow);
        if (o != myModelValue) {
          myRow = getModelIndex();
        }
      }
    }

    private int getModelIndex() {
      // We can't just use model.indexOf(myRule) because there is a possible case that the model contain equal
      // rules (rule1.equals(rule2) == true). That's why we have a helper method for search by reference identity. 
      ArrangementMatchingRulesModel model = myList.getModel();
      for (int i = 0, max = model.getSize(); i < max; i++) {
        if (model.getElementAt(i) == myModelValue) {
          return i;
        }
      }
      return -1;
    }
  }

  private static final class DummyElement {
    @Override
    public String toString() {
      return "dummy-" + System.identityHashCode(this);
    }
  }
}

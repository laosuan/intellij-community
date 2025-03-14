/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.refactoring.classes.membersManager;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * Plugin that moves class properties.
 * It represents property (whatever old or new) as one of its methods.
 *
 * @author Ilya.Kazakevich
 */
class PropertiesManager extends MembersManager<PyElement> {

  PropertiesManager() {
    super(PyElement.class);
  }

  @Override
  protected Collection<? extends PyElement> getElementsToStoreReferences(@NotNull Collection<PyElement> elements) {
    List<PyElement> result = new ArrayList<>();
    for (PyElement element : elements) {
      PyPossibleClassMember classMember = as(element, PyPossibleClassMember.class);
      if (classMember == null) {
        continue;
      }
      PyClass pyClass = classMember.getContainingClass();
      if (pyClass == null) {
        continue;
      }
      Property property = getProperty(pyClass, element);
      result.addAll(getAllFunctions(property));
      PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(property.getDefinitionSite(), PyAssignmentStatement.class);
      if (assignment != null) {
        result.add(assignment);
      }
    }
    return result;
  }

  @Override
  protected @NotNull List<? extends PyElement> getMembersCouldBeMoved(final @NotNull PyClass pyClass) {
    final List<PyElement> elements = new ArrayList<>(pyClass.getProperties().size());
    for (final Property property : pyClass.getProperties().values()) {
      elements.add(getElement(property));
    }
    return elements;
  }

  private static @NotNull PyElement getElement(final @NotNull Property property) {
    final PyCallable getter = property.getGetter().valueOrNull();
    final PyCallable setter = property.getSetter().valueOrNull();
    final PyCallable deleter = property.getDeleter().valueOrNull();

    if (getter != null) {
      return getter;
    }
    else if (setter != null) {
      return setter;
    }
    else if (deleter != null) {
      return deleter;
    }
    else {
      final PyTargetExpression site = property.getDefinitionSite();
      assert site != null : "Property has no methods nor declaration. That is not property";
      return site;
    }
  }

  private static @NotNull Property getProperty(final @NotNull PyClass pyClass, final @NotNull PyElement element) {
    final Collection<Property> properties = pyClass.getProperties().values();
    if (element instanceof PyTargetExpression) {
      return getPropertyByTargetExpression(properties, (PyTargetExpression)element);
    }
    if (element instanceof PyFunction) {
      return getPropertyByFunction(properties, (PyFunction)element);
    }
    throw new IllegalArgumentException("Not function nor target");
  }

  private static @NotNull Property getPropertyByFunction(final @NotNull Collection<Property> properties,
                                                         final @NotNull PyFunction functionToSearch) {
    for (final Property property : properties) {
      for (final PyFunction function : getAllFunctions(property)) {
        if (function.equals(functionToSearch)) {
          return property;
        }
      }
    }
    throw new IllegalArgumentException("No property found");
  }

  private static @NotNull Property getPropertyByTargetExpression(final @NotNull Iterable<Property> properties,
                                                                 final @NotNull PyTargetExpression element) {
    for (final Property property : properties) {
      if (element.equals(property.getDefinitionSite())) {
        return property;
      }
    }
    throw new IllegalArgumentException("No property found");
  }

  private static @NotNull Collection<PyFunction> getAllFunctions(final @NotNull Property property) {
    final Collection<PyFunction> result = new ArrayList<>(3);
    final PyCallable getter = property.getGetter().valueOrNull();
    final PyCallable setter = property.getSetter().valueOrNull();
    final PyCallable deleter = property.getDeleter().valueOrNull();

    if (getter instanceof PyFunction) {
      result.add((PyFunction)getter);
    }
    if (setter instanceof PyFunction) {
      result.add((PyFunction)setter);
    }
    if (deleter instanceof PyFunction) {
      result.add((PyFunction)deleter);
    }
    return result;
  }

  @Override
  protected Collection<PyElement> moveMembers(final @NotNull PyClass from,
                                              final @NotNull Collection<PyMemberInfo<PyElement>> members,
                                              final PyClass @NotNull ... to) {
    final Collection<PyElement> result = new ArrayList<>();

    final Collection<PyElement> elements = fetchElements(members);
    for (final PyElement element : elements) {
      final Property property = getProperty(from, element);
      final Collection<PyFunction> functions = getAllFunctions(property);
      result.addAll(MethodsManager.moveMethods(from, functions, false, to));
      final PyTargetExpression definitionSite = property.getDefinitionSite();
      if (definitionSite != null) {
        final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(definitionSite, PyAssignmentStatement.class);
        result.addAll(ClassFieldsManager.moveAssignmentsImpl(from, Collections.singleton(assignmentStatement), to));
      }
    }
    return result;
  }

  @Override
  public @NotNull PyMemberInfo<PyElement> apply(final @NotNull PyElement input) {
    return new PyMemberInfo<>(input, false, getName(input), false, this, false);
  }

  private static String getName(final @NotNull PyElement input) {
    final PyClass clazz = PsiTreeUtil.getParentOfType(input, PyClass.class);
    assert clazz != null : "Element not declared in class";
    final Property property = getProperty(clazz, input);
    return property.getName();
  }

  @Override
  public boolean hasConflict(final @NotNull PyElement member, final @NotNull PyClass aClass) {
    return false;
  }

  @Override
  protected @NotNull MultiMap<PyClass, PyElement> getDependencies(final @NotNull PyElement member) {
    final PyRecursiveElementVisitorWithResult visitor = new PyReferenceVisitor();
    member.accept(visitor);

    return visitor.myResult;
  }

  @Override
  protected @NotNull Collection<PyElement> getDependencies(final @NotNull MultiMap<PyClass, PyElement> usedElements) {
    return Collections.emptyList();
  }

  private static class PyReferenceVisitor extends PyRecursiveElementVisitorWithResult {


    @Override
    public void visitPyExpression(final @NotNull PyExpression node) {
      final PsiReference reference = node.getReference();
      if (reference == null) {
        return;
      }

      final PsiElement declaration = reference.resolve();
      if (!(declaration instanceof PyFunction function)) {
        return;
      }

      final Property property = function.getProperty();
      if (property == null) {
        return;
      }

      final PyClass aClass = function.getContainingClass();
      if (aClass == null) {
        return;
      }
      final Collection<PyFunction> functions = getAllFunctions(property);
      for (final PyFunction pyFunction : functions) {
        final PyClass functionClass = pyFunction.getContainingClass();
        if (functionClass != null) {
          myResult.putValue(functionClass, pyFunction);
        }
      }

      final PyTargetExpression definitionSite = property.getDefinitionSite();
      if (definitionSite != null) {
        final PyClass pyClass = PsiTreeUtil.getParentOfType(definitionSite, PyClass.class);
        if (pyClass != null) {
          myResult.putValue(pyClass, definitionSite);
        }
      }
    }
  }
}

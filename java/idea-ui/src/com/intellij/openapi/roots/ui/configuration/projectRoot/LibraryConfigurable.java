// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryPresentationManager;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryRootsComponent;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.LibraryProjectStructureElement;
import com.intellij.openapi.roots.ui.configuration.projectRoot.daemon.ProjectStructureElement;
import com.intellij.openapi.util.Disposer;
import com.intellij.projectModel.ProjectModelBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class LibraryConfigurable extends ProjectStructureElementConfigurable<Library> {
  private LibraryRootsComponent myLibraryEditorComponent;
  private final Library myLibrary;
  private final StructureLibraryTableModifiableModelProvider myModel;
  private final StructureConfigurableContext myContext;
  private final Project myProject;
  private final LibraryProjectStructureElement myProjectStructureElement;
  private boolean myPropertiesLoaded;

  protected LibraryConfigurable(final StructureLibraryTableModifiableModelProvider modelProvider,
                                final Library library,
                                final StructureConfigurableContext context,
                                final Runnable updateTree) {
    super(true, updateTree);
    myModel = modelProvider;
    myContext = context;
    myProject = context.getProject();
    myLibrary = library;
    myProjectStructureElement = new LibraryProjectStructureElement(context, myLibrary);
  }

  @Override
  public JComponent createOptionsPanel() {
    myLibraryEditorComponent = new LibraryRootsComponent(myProject, this::getLibraryEditor);
    myLibraryEditorComponent.addListener(() -> {
      myContext.getDaemonAnalyzer().queueUpdate(myProjectStructureElement);
      updateName();
    });
    return myLibraryEditorComponent.getComponent();
  }

  @Override
  public boolean isModified() {
    return myLibraryEditorComponent != null && myLibraryEditorComponent.hasChanges();
  }

  @Override
  public @NotNull ProjectStructureElement getProjectStructureElement() {
    return myProjectStructureElement;
  }

  @Override
  public void apply() {
    applyProperties();
  }

  @Override
  public void reset() {
    resetProperties();
  }

  @Override
  public void disposeUIResources() {
    if (myLibraryEditorComponent != null) {
      Disposer.dispose(myLibraryEditorComponent);
      myLibraryEditorComponent = null;
    }
  }

  @Override
  public void setDisplayName(final String name) {
    if (!isUpdatingNameFieldFromDisplayName()) {
      getLibraryEditor().setName(name);
      if (myLibraryEditorComponent != null) {
        myLibraryEditorComponent.onLibraryRenamed();
      }
      myContext.getDaemonAnalyzer().queueUpdateForAllElementsWithErrors();
    }
  }

  protected LibraryEditor getLibraryEditor() {
    return myModel.getModifiableModel().getLibraryEditor(myLibrary);
  }

  @Override
  public Library getEditableObject() {
    return myLibrary;
  }

  @Override
  public String getBannerSlogan() {
    final LibraryTable libraryTable = myLibrary.getTable();
    String libraryType = libraryTable == null
                         ? ProjectModelBundle.message("module.library.display.name", 1)
                         : libraryTable.getPresentation().getDisplayName(false);
    return JavaUiBundle.message("project.roots.library.banner.text", getDisplayName(), libraryType);
  }

  @Override
  public String getDisplayName() {
    if (myModel.getModifiableModel().hasLibraryEditor(myLibrary)) {
      return getLibraryEditor().getName();
    }

    return myLibrary.getName();
  }

  public void onSelected() {
    resetProperties();
  }

  public void onUnselected() {
    applyProperties();
  }

  private void resetProperties() {
    if (myLibraryEditorComponent != null) {
      myLibraryEditorComponent.updatePropertiesLabel();
      myLibraryEditorComponent.resetProperties();
      myPropertiesLoaded = true;
    }
  }

  private void applyProperties() {
    if (myLibraryEditorComponent != null && myPropertiesLoaded) {
      myLibraryEditorComponent.applyProperties();
      myPropertiesLoaded = false;
    }
  }

  @Override
  public Icon getIcon(boolean open) {
    return LibraryPresentationManager.getInstance().getNamedLibraryIcon(myLibrary, myContext);
  }

  public void updateComponent() {
    if (myLibraryEditorComponent != null) {
      myLibraryEditorComponent.updateRootsTree();
    }
  }
}

// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class AnnotationOrderRootType extends PersistentOrderRootType {
  public static final String ANNOTATIONS_ID = "ANNOTATIONS";

  /**
   * @return External annotations path
   */
  public static OrderRootType getInstance() {
    return getOrderRootType(AnnotationOrderRootType.class);
  }

  @ApiStatus.Internal
  public AnnotationOrderRootType() {
    super(ANNOTATIONS_ID, "annotationsPath", "annotation-paths", null);
  }

  @Override
  public boolean skipWriteIfEmpty() {
    return true;
  }

  public static VirtualFile @NotNull [] getFiles(@NotNull OrderEntry entry) {
    List<VirtualFile> result = new ArrayList<>();
    RootPolicy<List<VirtualFile>> policy = new RootPolicy<>() {
      @Override
      public List<VirtualFile> visitLibraryOrderEntry(@NotNull final LibraryOrderEntry orderEntry, final List<VirtualFile> value) {
        Collections.addAll(value, orderEntry.getRootFiles(getInstance()));
        return value;
      }

      @Override
      public List<VirtualFile> visitJdkOrderEntry(@NotNull final JdkOrderEntry orderEntry, final List<VirtualFile> value) {
        Collections.addAll(value, orderEntry.getRootFiles(getInstance()));
        return value;
      }

      @Override
      public List<VirtualFile> visitModuleSourceOrderEntry(@NotNull ModuleSourceOrderEntry orderEntry, List<VirtualFile> value) {
        JavaModuleExternalPaths moduleExtension = orderEntry.getRootModel().getModuleExtension(JavaModuleExternalPaths.class);
        if (moduleExtension != null) {
          Collections.addAll(value, moduleExtension.getExternalAnnotationsRoots());
        }
        return value;
      }
    };
    entry.accept(policy, result);
    return VfsUtilCore.toVirtualFileArray(result);
  }

  public static @NotNull List<String> getUrls(@NotNull OrderEntry entry) {
    List<String> result = new ArrayList<>();
    RootPolicy<List<String>> policy = new RootPolicy<>() {
      @Override
      public List<String> visitLibraryOrderEntry(@NotNull final LibraryOrderEntry orderEntry, final List<String> value) {
        Collections.addAll(value, orderEntry.getRootUrls(getInstance()));
        return value;
      }

      @Override
      public List<String> visitJdkOrderEntry(@NotNull final JdkOrderEntry orderEntry, final List<String> value) {
        Collections.addAll(value, orderEntry.getRootUrls(getInstance()));
        return value;
      }

      @Override
      public List<String> visitModuleSourceOrderEntry(@NotNull ModuleSourceOrderEntry orderEntry, List<String> value) {
        Collections.addAll(value, orderEntry.getRootModel().getModuleExtension(JavaModuleExternalPaths.class).getExternalAnnotationsUrls());
        return value;
      }
    };
    entry.accept(policy, result);
    return result;
  }
}

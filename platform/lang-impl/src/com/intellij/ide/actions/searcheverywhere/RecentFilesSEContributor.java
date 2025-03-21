// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.google.common.collect.Lists;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class RecentFilesSEContributor extends FileSearchEverywhereContributor {

  public RecentFilesSEContributor(@NotNull AnActionEvent event) {
    super(event);
  }

  @Override
  public @NotNull String getSearchProviderId() {
    return RecentFilesSEContributor.class.getSimpleName();
  }

  @Override
  public @NotNull String getGroupName() {
    return IdeBundle.message("search.everywhere.group.name.recent.files");
  }

  @Override
  public int getSortWeight() {
    return 70;
  }

  @Override
  public int getElementPriority(@NotNull Object element, @NotNull String searchPattern) {
    return super.getElementPriority(element, searchPattern) + 5;
  }

  @Override
  public void fetchWeightedElements(@NotNull String pattern,
                                    @NotNull ProgressIndicator progressIndicator,
                                    @NotNull Processor<? super FoundItemDescriptor<Object>> consumer) {
    String searchString = filterControlSymbols(pattern);
    boolean preferStartMatches = !searchString.startsWith("*");
    MinusculeMatcher matcher = createMatcher(searchString, preferStartMatches);
    List<VirtualFile> opened = Arrays.asList(FileEditorManager.getInstance(myProject).getSelectedFiles());
    List<VirtualFile> history = Lists.reverse(EditorHistoryManager.getInstance(myProject).getFileList());

    List<FoundItemDescriptor<Object>> res = new ArrayList<>();
    ProgressIndicatorUtils.yieldToPendingWriteActions();
    ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(
      () -> {
        PsiManager psiManager = PsiManager.getInstance(myProject);
        StreamEx<VirtualFile> stream = StreamEx.of(history);
        if (!StringUtil.isEmptyOrSpaces(searchString)) {
          stream = stream.filter(file -> matcher.matches(file.getName()));
        }

        Comparator<FoundItemDescriptor<?>> comparator = Comparator.comparing(it -> it.getWeight());
        stream.filter(vf -> !opened.contains(vf) && vf.isValid())
          .distinct()
          .map(vf -> {
            PsiFile f = psiManager.findFile(vf);
            String name = vf.getName();
            return f == null ? null : new FoundItemDescriptor<Object>(f, matcher.matchingDegree(name));
          })
          .nonNull()
          .sorted(comparator.reversed())
          .into(res);

        ContainerUtil.process(res, consumer);
      }, progressIndicator);
  }

  private static MinusculeMatcher createMatcher(String searchString, boolean preferStartMatches) {
    NameUtil.MatcherBuilder builder = NameUtil.buildMatcher("*" + searchString);
    if (preferStartMatches) {
      builder = builder.preferringStartMatches();
    }
    return builder.build();
  }

  @Override
  public boolean isShownInSeparateTab() {
    return false;
  }

  public static final class Factory implements SearchEverywhereContributorFactory<Object> {
    @Override
    public @NotNull SearchEverywhereContributor<Object> createContributor(@NotNull AnActionEvent initEvent) {
      return PSIPresentationBgRendererWrapper.wrapIfNecessary(new RecentFilesSEContributor(initEvent));
    }
  }
}

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.localVcs.UpToDateLineNumberProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.diff.DiffProvider;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents annotations ("vcs blame") for some file in a specific revision
 *
 * @see AnnotationProvider
 */
public abstract class FileAnnotation {
  private static final Logger LOG = Logger.getInstance(FileAnnotation.class);

  private final @NotNull Project myProject;

  private boolean myIsClosed;
  private Runnable myCloser;
  private Consumer<? super FileAnnotation> myReloader;

  protected FileAnnotation(@NotNull Project project) {
    myProject = project;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @Nullable VcsKey getVcsKey() {
    return null;
  }

  /**
   * @return annotated file
   * <p>
   * If annotations are called on a local file, it can be this file.
   * If annotations are called on a specific revision, it can be corresponding {@link com.intellij.openapi.vcs.vfs.VcsVirtualFile}.
   * Note: file content might differ from content in annotated revision {@link #getAnnotatedContent}.
   */
  public @Nullable VirtualFile getFile() {
    return null;
  }

  /**
   * @return file content in the annotated revision
   * <p>
   * It might differ from {@code getFile()} content. Ex: annotations for a local file, that has non-committed changes.
   * In this case {@link UpToDateLineNumberProvider} will be used to transfer lines between local and annotated revisions.
   */
  public abstract @Nullable @NonNls String getAnnotatedContent();


  /**
   * @return annotated revision
   * <p>
   * This information might be used to close annotations on local file if current revision was changed,
   * and invocation of AnnotationProvider on this file will produce different results - see {@link #isBaseRevisionChanged}.
   */
  public abstract @Nullable VcsRevisionNumber getCurrentRevision();

  /**
   * @param number current revision number {@link DiffProvider#getCurrentRevision(VirtualFile)}
   * @return whether annotations should be updated
   */
  public boolean isBaseRevisionChanged(@NotNull VcsRevisionNumber number) {
    final VcsRevisionNumber currentRevision = getCurrentRevision();
    return currentRevision != null && !currentRevision.equals(number);
  }


  /**
   * This method is invoked when the file annotation is no longer used.
   * NB: method might be invoked multiple times
   *
   * @deprecated Historically ignored by users of {@link AnnotationProvider}.
   * Implementations should not rely on this method to remove listeners, as it might cause memory leaks.
   */
  @Deprecated
  public void dispose() {
  }

  /**
   * Get annotation aspects.
   * The typical aspects are revision number, date, author.
   * The aspects are displayed each in own column in the returned order.
   */
  public abstract LineAnnotationAspect @NotNull [] getAspects();


  /**
   * @return number of lines in annotated content
   */
  public abstract int getLineCount();

  /**
   * The tooltip that is shown over annotation.
   * Typically, this is a detailed info about related revision. ex: long revision number, commit message
   */
  public abstract @Nullable @NlsContexts.Tooltip String getToolTip(int lineNumber);

  public @Nullable @NlsContexts.Tooltip String getHtmlToolTip(int lineNumber) {
    String toolTip = getToolTip(lineNumber);
    return XmlStringUtil.escapeString(toolTip);
  }

  /**
   * @return last revision that modified this line.
   */
  public abstract @Nullable VcsRevisionNumber getLineRevisionNumber(int lineNumber);

  /**
   * @return time of the last modification of this line.
   * Typically, this is a timestamp associated with {@link #getLineRevisionNumber}
   */
  public abstract @Nullable Date getLineDate(int lineNumber);


  /**
   * @return revisions that are mentioned in the annotations, from newest to oldest
   * Can be used to sort revisions, if they can't be sorted by {@code Date} or show file modification number for a revision.
   */
  public abstract @Nullable List<VcsFileRevision> getRevisions();


  /**
   * Allows to switch between different representation modes.
   * <p>
   * Ex: in SVN it's possible to show revision that modified line - "svn blame -g",
   * or the commit that merged that change into current branch - "svn blame".
   * <p>
   * when "show merge sources" is turned on, {@link #getLineRevisionNumber} returns merge source revision,
   * while {@link #originalRevision} returns merge revision.
   */
  public @Nullable AnnotationSourceSwitcher getAnnotationSourceSwitcher() {
    return null;
  }

  /**
   * @return last revision that modified this line in current branch.
   * @see #getAnnotationSourceSwitcher()
   * @see #getLineRevisionNumber(int)
   */
  public @Nullable VcsRevisionNumber originalRevision(int lineNumber) {
    return getLineRevisionNumber(lineNumber);
  }


  public synchronized boolean isClosed() {
    return myIsClosed;
  }

  /**
   * Notify that annotations should be closed
   */
  public final synchronized void close() {
    myIsClosed = true;
    if (myCloser != null) {
      myCloser.run();

      myCloser = null;
      myReloader = null;
    }
  }

  /**
   * Notify that annotation information has changed, and should be updated.
   * If `this` is visible, hide it and show new one instead.
   * If `this` is not visible, do nothing.
   *
   * @param newFileAnnotation annotations to be shown or `null` to load annotations again
   */
  @RequiresEdt
  public final synchronized void reload(@Nullable FileAnnotation newFileAnnotation) {
    if (myReloader != null) myReloader.consume(newFileAnnotation);
  }

  /**
   * @see #close()
   */
  public final synchronized void setCloser(@NotNull Runnable closer) {
    if (myIsClosed) return;
    myCloser = closer;
  }

  /**
   * @see #reload(FileAnnotation)
   */
  public final synchronized void setReloader(@Nullable Consumer<? super FileAnnotation> reloader) {
    if (myIsClosed) return;
    myReloader = reloader;
  }


  public @Nullable CurrentFileRevisionProvider getCurrentFileRevisionProvider() {
    return createDefaultCurrentFileRevisionProvider(this);
  }

  public @Nullable PreviousFileRevisionProvider getPreviousFileRevisionProvider() {
    return createDefaultPreviousFileRevisionProvider(this);
  }

  public @Nullable AuthorsMappingProvider getAuthorsMappingProvider() {
    return createDefaultAuthorsMappingProvider(this);
  }

  public @Nullable RevisionsOrderProvider getRevisionsOrderProvider() {
    return createDefaultRevisionsOrderProvider(this);
  }

  public @Nullable RevisionChangesProvider getRevisionsChangesProvider() {
    return createDefaultRevisionsChangesProvider(this);
  }

  public @Nullable LineModificationDetailsProvider getLineModificationDetailsProvider() {
    return null;
  }


  public interface CurrentFileRevisionProvider {
    @Nullable
    VcsFileRevision getRevision(int lineNumber);
  }

  public interface PreviousFileRevisionProvider {
    @Nullable
    VcsFileRevision getPreviousRevision(int lineNumber);

    @Nullable
    VcsFileRevision getLastRevision();
  }

  public interface AuthorsMappingProvider {
    @NotNull
    Map<VcsRevisionNumber, String> getAuthors();
  }

  public interface RevisionsOrderProvider {
    @NotNull
    List<List<VcsRevisionNumber>> getOrderedRevisions();
  }

  public interface RevisionChangesProvider {
    @NotNull
    Pair<? extends CommittedChangeList, FilePath> getChangesIn(int lineNumber) throws VcsException;
  }

  public interface LineModificationDetailsProvider {
    @Nullable
    AnnotatedLineModificationDetails getDetails(int lineNumber) throws VcsException;
  }

  public static @NotNull @NlsSafe String formatDate(@NotNull Date date) {
    return DateFormatUtil.formatPrettyDate(date);
  }

  private static @Nullable CurrentFileRevisionProvider createDefaultCurrentFileRevisionProvider(@NotNull FileAnnotation annotation) {
    List<VcsFileRevision> revisions = annotation.getRevisions();
    if (revisions == null) return null;


    Map<VcsRevisionNumber, VcsFileRevision> map = new HashMap<>();
    for (VcsFileRevision revision : revisions) {
      map.put(revision.getRevisionNumber(), revision);
    }

    List<VcsFileRevision> lineToRevision = new ArrayList<>(annotation.getLineCount());
    for (int i = 0; i < annotation.getLineCount(); i++) {
      lineToRevision.add(map.get(annotation.getLineRevisionNumber(i)));
    }

    return (lineNumber) -> {
      LOG.assertTrue(lineNumber >= 0 && lineNumber < lineToRevision.size());
      return lineToRevision.get(lineNumber);
    };
  }

  private static @Nullable PreviousFileRevisionProvider createDefaultPreviousFileRevisionProvider(@NotNull FileAnnotation annotation) {
    List<VcsFileRevision> revisions = annotation.getRevisions();
    if (revisions == null) return null;

    Map<VcsRevisionNumber, VcsFileRevision> map = new HashMap<>();
    for (int i = 0; i < revisions.size(); i++) {
      VcsFileRevision revision = revisions.get(i);
      VcsFileRevision previousRevision = i + 1 < revisions.size() ? revisions.get(i + 1) : null;
      map.put(revision.getRevisionNumber(), previousRevision);
    }

    List<VcsFileRevision> lineToRevision = new ArrayList<>(annotation.getLineCount());
    for (int i = 0; i < annotation.getLineCount(); i++) {
      lineToRevision.add(map.get(annotation.getLineRevisionNumber(i)));
    }

    VcsFileRevision lastRevision = ContainerUtil.getFirstItem(revisions);

    return new PreviousFileRevisionProvider() {
      @Override
      public @Nullable VcsFileRevision getPreviousRevision(int lineNumber) {
        LOG.assertTrue(lineNumber >= 0 && lineNumber < lineToRevision.size());
        return lineToRevision.get(lineNumber);
      }

      @Override
      public @Nullable VcsFileRevision getLastRevision() {
        return lastRevision;
      }
    };
  }

  private static @Nullable AuthorsMappingProvider createDefaultAuthorsMappingProvider(@NotNull FileAnnotation annotation) {
    List<VcsFileRevision> revisions = annotation.getRevisions();
    if (revisions == null) return null;

    Map<VcsRevisionNumber, String> authorsMapping = new HashMap<>();
    for (VcsFileRevision revision : revisions) {
      String author = revision.getAuthor();
      if (author != null) authorsMapping.put(revision.getRevisionNumber(), author);
    }

    return () -> authorsMapping;
  }

  private static @Nullable RevisionsOrderProvider createDefaultRevisionsOrderProvider(@NotNull FileAnnotation annotation) {
    List<VcsFileRevision> revisions = annotation.getRevisions();
    if (revisions == null) return null;

    List<List<VcsRevisionNumber>> orderedRevisions =
      ContainerUtil.map(revisions, (revision) -> Collections.singletonList(revision.getRevisionNumber()));

    return () -> orderedRevisions;
  }

  private static @Nullable RevisionChangesProvider createDefaultRevisionsChangesProvider(@NotNull FileAnnotation annotation) {
    VirtualFile file = annotation.getFile();
    if (file == null) return null;

    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(annotation.getProject()).getVcsFor(file);
    if (vcs == null) return null;

    CommittedChangesProvider<?, ?> changesProvider = vcs.getCommittedChangesProvider();
    if (changesProvider == null) return null;

    return (lineNumber) -> {
      VcsRevisionNumber revisionNumber = annotation.getLineRevisionNumber(lineNumber);
      if (revisionNumber == null) {
        throw new IllegalArgumentException(VcsBundle.message("error.annotated.line.out.of.bounds", lineNumber, annotation.getLineCount()));
      }

      Pair<? extends CommittedChangeList, FilePath> pair = changesProvider.getOneList(file, revisionNumber);
      if (pair == null || pair.getFirst() == null) {
        throw new VcsException(VcsBundle.message("error.cant.load.affected.files", file.getPath(), revisionNumber.asString()));
      }
      if (pair.getSecond() == null) return Pair.create(pair.getFirst(), VcsUtil.getFilePath(file));

      return pair;
    };
  }
}

// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.diff

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.text.HtmlChunk.html
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Icon

@ApiStatus.ScheduledForRemoval
@Deprecated("Deprecated with the move to ViewModel-based approach")
@ApiStatus.Internal
abstract class LineGutterIconRenderer : GutterIconRenderer(), DumbAware, Disposable {

  abstract val line: Int
  abstract var visibleIcon: Icon

  var iconVisible = false

  override fun getIcon(): Icon = if (iconVisible) visibleIcon else EmptyIcon.ICON_16

  protected open fun getShortcut(): ShortcutSet = CustomShortcutSet.EMPTY

  override fun isNavigateAction() = true

  override fun getAlignment() = Alignment.RIGHT

  abstract fun disposeInlay()

  override fun dispose() {
    disposeInlay()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AddCommentGutterIconRenderer) return false

    if (line != other.line) return false

    return true
  }

  override fun hashCode(): Int {
    return line
  }
}

@Deprecated("Deprecated with the move to ViewModel-based approach")
abstract class AddCommentGutterIconRenderer : LineGutterIconRenderer() {
  override var visibleIcon: Icon = AllIcons.General.InlineAdd

  override fun getTooltipText(): @Nls String =
    html()
      .addText(CollaborationToolsBundle.message("diff.add.comment.icon.tooltip"))
      .addRaw(HelpTooltip.getShortcutAsHtml(getFirstKeyboardShortcutText(getShortcut())))
      .toString()
}

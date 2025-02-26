// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.shared.importing

import KotlinGradleScriptingBundle
import com.intellij.build.FilePosition
import com.intellij.build.SyncViewManager
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemEventDispatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import java.io.File

@Nls
private val gradle_build_script_errors_group = KotlinGradleScriptingBundle.message("kotlin.build.script.errors")

class KotlinGradleDslErrorReporter(
    project: Project,
    private val task: ExternalSystemTaskId
) {
    private val syncViewManager = project.service<SyncViewManager>()
    private val buildEventDispatcher = ExternalSystemEventDispatcher(task, syncViewManager)

    fun reportError(
        scriptFile: File,
        model: KotlinDslScriptModel
    ) {
        model.messages.forEach {
            val severity = when (it.severity) {
                KotlinDslScriptModel.Severity.WARNING -> MessageEvent.Kind.WARNING
                KotlinDslScriptModel.Severity.ERROR -> MessageEvent.Kind.ERROR
            }
            val position = it.position
            if (position == null) {
                buildEventDispatcher.onEvent(
                    task,
                    MessageEventImpl(
                        task,
                        severity,
                        gradle_build_script_errors_group,
                        it.text,
                        it.details
                    )
                )
            } else {
                buildEventDispatcher.onEvent(
                    task,
                    FileMessageEventImpl(
                        task,
                        severity,
                        gradle_build_script_errors_group,
                        it.text, it.details,
                        // 0-based line numbers
                        FilePosition(scriptFile, position.line - 1, position.column)
                    ),
                )
            }
        }
    }

    companion object {
        @IntellijInternalApi
        val build_script_errors_group: String
            @TestOnly
            get() = gradle_build_script_errors_group
    }
}

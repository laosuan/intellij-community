// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.Strings
import com.intellij.util.EnvironmentUtil
import com.intellij.util.ui.EDT
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.switchToSdk
import org.assertj.swing.internal.annotation.InternalApi
import org.jetbrains.annotations.ApiStatus

private val LOGGER = logger<PySdkFromEnvironmentVariableConfigurator>()

@ApiStatus.Internal
class PySdkFromEnvironmentVariableConfigurator(private val project: Project) : JpsProjectLoadedListener {
  companion object {
    private const val PYCHARM_PYTHON_PATH = "PycharmPythonPath"

    @InternalApi
    fun getPycharmPythonPathProperty() : String? {
      // see https://www.jetbrains.com/help/pycharm/configure-an-interpreter-using-command-line.html
      return System.getProperty(PYCHARM_PYTHON_PATH) ?: EnvironmentUtil.getValue("PYCHARM_PYTHON_PATH")
    }

    fun findOrCreateSdkByPath(path: String): Sdk? {
      EDT.assertIsEdt()
      return findByPath(path) ?: createSdkByPath(path)
    }

    fun setModuleSdk(module: Module, projectSdk: Sdk?, sdk: Sdk, pythonPath: String) {
      EDT.assertIsEdt()
      val moduleSdk = PythonSdkUtil.findPythonSdk(module)
      if (pythonPath != projectSdk?.homePath || pythonPath != moduleSdk?.homePath) {
        switchToSdk(module, sdk, moduleSdk)
      }
    }
  }

  override fun loaded() {
    val pycharmPythonPathEnvVariable = getPycharmPythonPathProperty()
    if (Strings.isEmptyOrSpaces(pycharmPythonPathEnvVariable)) {
      LOGGER.debug("$PYCHARM_PYTHON_PATH is null or empty")
      return
    }
    LOGGER.info("Found $PYCHARM_PYTHON_PATH='${getPycharmPythonPathProperty()}' system property")

    checkAndSetSdk(project, pycharmPythonPathEnvVariable!!)
  }

  private fun checkAndSetSdk(project: Project, pycharmPythonPathEnvVariable: String) {
    runInEdt {
      val sdk = findOrCreateSdkByPath(pycharmPythonPathEnvVariable) ?: return@runInEdt

      val projectSdk = ProjectRootManager.getInstance(project).projectSdk

      ModuleManager.getInstance(project).modules.forEach {
        setModuleSdk(it, projectSdk, sdk, pycharmPythonPathEnvVariable)
      }
    }
  }
}

private fun findByPath(pycharmPythonPathEnvVariable: String): Sdk? {
  val sdkType = PythonSdkType.getInstance()
  val sdks = ProjectJdkTable.getInstance().getSdksOfType(sdkType)
  val sdk = SdkConfigurationUtil.findByPath(sdkType, sdks.toTypedArray(), pycharmPythonPathEnvVariable)

  if (sdk != null) {
    LOGGER.info("Found a previous sdk")
  }

  return sdk
}

private fun createSdkByPath(pycharmPythonPathEnvVariable: String): Sdk? {
  val sdk = SdkConfigurationUtil.createAndAddSDK(pycharmPythonPathEnvVariable, PythonSdkType.getInstance())
  if (sdk != null) {
    LOGGER.info("No suitable sdk found, created a new one")
  }
  return sdk
}
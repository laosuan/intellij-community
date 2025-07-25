package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.ui

fun Finder.welcomeScreen(action: WelcomeScreenUI.() -> Unit = {}): WelcomeScreenUI {
  val welcomeScreenClass = if (isRemDevMode) "TabbedWelcomeScreen" else "FlatWelcomeFrame"
  return x("//div[@class='${welcomeScreenClass}']", WelcomeScreenUI::class.java).apply(action)
}

fun Driver.welcomeScreen(action: WelcomeScreenUI.() -> Unit = {}): WelcomeScreenUI {
  return this.ui.welcomeScreen(action)
}

open class WelcomeScreenUI(data: ComponentData) : UiComponent(data) {
  open val createNewProjectButton: UiComponent = x("//div[(@accessiblename='New Project' and @class='JButton') or (@visible_text='New Project' and @class!='JBLabel')]")
  open val openProjectButton: UiComponent = x("//div[(@accessiblename='Open' and @class='JButton')  or (@visible_text='Open' and @class!='JBLabel')]")
  val fromVcsButton: UiComponent = x("//div[@accessiblename='Clone Repository' and @class='JButton']")

  val leftItems: JTreeUiComponent = tree("//div[@class='Tree']")

  fun openSettingsDialog() {
    driver.invokeAction("ShowSettings", now = false)
  }

  fun clickProjects() {
    leftItems.clickPath("Projects")
  }

  fun clickRemoteDev() {
    leftItems.clickPath("Remote Development")
  }

  fun clickRemoteDevSsh() {
    leftItems.clickPath("Remote Development", "SSH")
  }

  fun clickRemoteDevSpace() {
    leftItems.clickPath("Remote Development", "JetBrains Space")
  }

  fun clickCustomize() {
    leftItems.clickPath("Customize")
  }

  fun clickPlugins() {
    leftItems.clickPath("Plugins")
  }

  fun clickLearn() {
    leftItems.clickPath("Learn")
  }

  fun clickRecentProject(projectName: String) {
    x { byClass("CardLayoutPanel") }.waitOneText(projectName).click()
  }
}
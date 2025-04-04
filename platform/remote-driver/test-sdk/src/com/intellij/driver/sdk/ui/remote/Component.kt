package com.intellij.driver.sdk.ui.remote

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.remoteDev.BeControlClass
import com.intellij.driver.sdk.remoteDev.BeControlComponentBuilder
import java.awt.Point
import java.awt.Rectangle

@Remote("java.awt.Component")
@BeControlClass(BeControlComponentBuilder::class)
interface Component {
  val x: Int
  val y: Int
  val width: Int
  val height: Int
  fun getBounds(): Rectangle
  fun isShowing(): Boolean
  fun isEnabled(): Boolean
  fun requestFocus()
  fun isFocusOwner(): Boolean
  fun getLocationOnScreen(): Point
  fun getClass(): Class
  fun getForeground(): ColorRef
  fun getBackground(): ColorRef
  fun getAccessibleContext(): AccessibleContextRef?
  fun getParent(): Component
  fun isDisplayable(): Boolean
}

@Remote("java.awt.Window")
interface Window: Component {
  fun isFocused(): Boolean
  fun dispose()
  fun toFront()
  fun setBounds(x: Int, y: Int, width: Int, height: Int)
  fun getWindows(): List<Window>
}

@Remote("java.awt.Color")
interface ColorRef {
  fun getRGB(): Int

  fun getRed(): Int
  fun getGreen(): Int
  fun getBlue(): Int
}

fun ColorRef.toHexString(): String {
  return String.format("#%02X%02X%02X", getRed(), getGreen(), getBlue())
}

@Remote("java.lang.Class")
interface Class

@Remote("javax.accessibility.AccessibleContext")
interface AccessibleContextRef {
  fun getAccessibleName(): String?
}
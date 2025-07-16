// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.hunspell

import ai.grazie.nlp.langs.LanguageISO
import ai.grazie.spell.lists.hunspell.HunspellWordList
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.util.Consumer
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStreamReader

internal data class HunspellBundle(val dic: File, val aff: File, val trigrams: File)

class HunspellDictionary : Dictionary {
  companion object {
    private fun loadHunspellBundle(path: String): HunspellBundle? {
      if (FileUtilRt.getExtension(path) != "dic") return null

      val pathWithoutExtension = FileUtilRt.getNameWithoutExtension(path)
      val dic = File("$pathWithoutExtension.dic")
      val aff = File("$pathWithoutExtension.aff")
      val trigrams = File("$pathWithoutExtension.trigrams.txt")

      return if (dic.exists() && aff.exists()) HunspellBundle(dic, aff, trigrams) else null
    }

    fun isHunspell(path: String): Boolean {
      return loadHunspellBundle(path) !== null
    }
  }

  @JvmOverloads
  constructor(path: String, name: String? = null, language: LanguageISO? = null) {
    val bundle = loadHunspellBundle(path)
    if (bundle == null) throw FileNotFoundException("File '$path' not found")

    this.name = name ?: path
    this.language = language
    this.dict = HunspellWordList.create(
      bundle.aff.readText(),
      bundle.dic.readText(),
      if (bundle.trigrams.exists()) bundle.trigrams.readLines() else null
    ) { ProgressManager.checkCanceled() }

    InputStreamReader(bundle.dic.inputStream()).use { reader ->
      reader.forEachLine { line ->
        line.takeWhile { it != ' ' && it != '/' }.lowercase().chars().forEach { this.alphabet.add(it) }
      }
    }
  }

  constructor(dic: String, aff: String, trigrams: List<String>?, name: String, language: LanguageISO) {
    check(dic.isNotEmpty()) { "Dictionary must not be empty string" }
    check(aff.isNotEmpty()) { "Affix must not be empty string" }

    this.name = name
    this.language = language
    this.dict = HunspellWordList.create(
      aff,
      dic,
      trigrams
    ) { ProgressManager.checkCanceled() }

    dic.lines().forEach { line ->
      line.takeWhile { it != ' ' && it != '/' }.lowercase().chars().forEach { this.alphabet.add(it) }
    }
  }

  private val name: String
  val dict: HunspellWordList
  private val alphabet: HashSet<Int> = HashSet()
  private val language: LanguageISO?

  fun language(): String? = language?.name ?: dict.language()

  override fun getName() = name

  override fun contains(word: String): Boolean? {
    if (dict.contains(word, false)) return true
    // mark a word as alien if it contains non-alphabetical characters
    if (word.lowercase().chars().anyMatch { it !in this.alphabet }) return null
    return false
  }

  override fun consumeSuggestions(word: String, consumer: Consumer<String>) {
    for (suggestion in dict.suggest(word)) {
      consumer.consume(suggestion)
    }
  }

  override fun getWords(): MutableSet<String> = throw UnsupportedOperationException()
}

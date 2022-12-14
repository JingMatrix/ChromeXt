package org.matrix.chromext.script

import kotlin.text.Regex

class UserScript(script: String) {
  private val blocksReg =
      Regex(
          """\A(?<metablock>// ==UserScript==\r?\n([\S\s]*?)\r?\n// ==/UserScript==)(?<code>[\S\s]*)""")
  private val metaReg = Regex("""^//\s+@(?<key>[\w-]+)\s+(?<value>.+)""")
  private var metablock: String = ""
  var code: String = ""
  var isValid: Boolean = false
  var metas: MutableMap<String, MutableList<String>> =
      mutableMapOf("run-at" to mutableListOf("document-idle"))

  init {
    isValid = parse(script)
  }

  fun parse(script: String): Boolean {
    val blockMatchGroup = blocksReg.matchEntire(script)?.groups as? MatchNamedGroupCollection
    if (blockMatchGroup == null) {
      return false
    }

    code = blockMatchGroup.get("code")?.value as String
    metablock = blockMatchGroup.get("metablock")?.value as String
    metablock.split("\n").forEach {
      val metaMatchGroup = metaReg.matchEntire(it)?.groups as? MatchNamedGroupCollection
      if (metaMatchGroup != null) {
        val key = metaMatchGroup.get("key")?.value
        if (key != null) {
          val value = metaMatchGroup.get("key")?.value as String
          if (metas[key] == null) {
            metas[key] = mutableListOf(value)
          }
          metas[key]!!.add(value)
        }
      }
    }

    return metas.containsKey("include") || metas.containsKey("match")
  }
}

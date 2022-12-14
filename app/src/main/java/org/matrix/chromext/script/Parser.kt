package org.matrix.chromext.script

import kotlin.text.Regex

enum class RunAt(val state: String) {
  START("document-start"),
  END("document-end"),
  IDLE("document-idle")
}

private val blocksReg =
    Regex(
        """\A(?<metablock>// ==UserScript==\r?\n([\S\s]*?)\r?\n// ==/UserScript==)(?<code>[\S\s]*)""")
private val metaReg = Regex("""^//\s+@(?<key>[\w-]+)\s+(?<value>.+)""")

fun parseScript(input: String): Script? {
  val blockMatchGroup = blocksReg.matchEntire(input)?.groups as? MatchNamedGroupCollection
  if (blockMatchGroup == null) {
    return null
  }

  val script =
      object {
        var name = "smaple"
        var namespace = "ChromeXt"
        var runAt = RunAt.IDLE
        var match = mutableListOf<String>()
        val code = blockMatchGroup.get("code")?.value as String
      }
  val metablock = blockMatchGroup.get("metablock")?.value as String
  metablock.split("\n").forEach {
    val metaMatchGroup = metaReg.matchEntire(it)?.groups as? MatchNamedGroupCollection
    if (metaMatchGroup != null) {
      val key = metaMatchGroup.get("key")?.value as String
      val value = metaMatchGroup.get("value")?.value as String
      when (key) {
        "name" -> script.name = value
        "namespace" -> script.namespace = value
        "match" -> script.match.add(value)
        "run-at" ->
            when (value) {
              "document-start" -> script.runAt = RunAt.START
              "document-end" -> script.runAt = RunAt.END
              "document-idle" -> script.runAt = RunAt.IDLE
            }
      }
    }
  }

  if (script.match.size == 0) {
    return null
  } else {
    return Script(
        script.namespace + ":" + script.name,
        script.match.toTypedArray(),
        script.code,
        script.runAt)
  }
}

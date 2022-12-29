package org.matrix.chromext.script

import kotlin.text.Regex

private val blocksReg =
    Regex(
        """[\S\s]*?(?<metablock>// ==UserScript==\r?\n([\S\s]*?)\r?\n// ==/UserScript==)(?<code>[\S\s]*)""")
private val metaReg = Regex("""^//\s+@(?<key>[\w-]+)\s+(?<value>.+)""")

fun parseScript(input: String): Script? {
  val blockMatchGroup = blocksReg.matchEntire(input)?.groups as? MatchNamedGroupCollection
  if (blockMatchGroup == null) {
    return null
  }

  val script =
      object {
        var name = "sample"
        var namespace = "ChromeXt"
        var runAt = RunAt.IDLE
        var match = mutableListOf<String>()
        var grant = mutableListOf<String>()
        var exclude = mutableListOf<String>()
        var require = mutableListOf<String>()
        val meta = blockMatchGroup.get("metablock")?.value as String
        val code = blockMatchGroup.get("code")?.value as String
      }
  val metablock = blockMatchGroup.get("metablock")?.value as String
  metablock.split("\n").forEach {
    val metaMatchGroup = metaReg.matchEntire(it)?.groups as? MatchNamedGroupCollection
    if (metaMatchGroup != null) {
      val key = metaMatchGroup.get("key")?.value as String
      val value = metaMatchGroup.get("value")?.value as String
      when (key) {
        "name" -> script.name = value.replace(":", "")
        "namespace" -> script.namespace = value
        "match" -> script.match.add(value)
        "include" -> script.match.add(value)
        "grant" ->
            if (value != "none") {
              script.grant.add(value)
            }
        "exclude" -> script.exclude.add(value)
        "require" -> script.require.add(value)
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
        (script.namespace + ":" + script.name).replace("\\", ""),
        script.match.toTypedArray(),
        script.grant.toTypedArray(),
        script.exclude.toTypedArray(),
        script.require.toTypedArray(),
        script.meta,
        script.code,
        script.runAt,
        script.code.contains("\\`"))
  }
}

package org.matrix.chromext.script

import java.net.HttpURLConnection
import java.net.URL
import kotlin.text.Regex
import org.json.JSONObject
import org.matrix.chromext.Chrome

private val blocksReg =
    Regex(
        """(?<metablock>[\S\s]*?// ==UserScript==\r?\n[\S\s]*?\r?\n// ==/UserScript==\s+)(?<code>[\S\s]*)""")
private val metaReg = Regex("""^//\s+@(?<key>[\w-]+)\s+(?<value>.+)""")

fun parseScript(input: String, storage: String? = null): Script? {
  val blockMatchGroup = blocksReg.matchEntire(input)?.groups
  if (blockMatchGroup == null) {
    return null
  }

  val script =
      object {
        var name = "sample"
        var namespace = "ChromeXt"
        var match = mutableListOf<String>()
        var grant = mutableListOf<String>()
        var exclude = mutableListOf<String>()
        var require = mutableListOf<String>()
        val meta = (blockMatchGroup[1]?.value as String)
        val code = blockMatchGroup[2]?.value as String
        var storage: JSONObject? = null
      }
  script.meta.split("\n").forEach {
    val metaMatchGroup = metaReg.matchEntire(it)?.groups
    if (metaMatchGroup != null) {
      val key = metaMatchGroup[1]?.value as String
      val value = metaMatchGroup[2]?.value as String
      when (key) {
        "name" -> script.name = value.replace(":", "")
        "namespace" -> script.namespace = value
        "match" -> script.match.add(value)
        "include" -> script.match.add(value)
        "grant" -> script.grant.add(value)
        "exclude" -> script.exclude.add(value)
        "require" -> script.require.add(value)
      }
    }
  }

  if (!script.grant.contains("GM_xmlhttpRequest") &&
      (script.grant.contains("GM_download") ||
          script.grant.contains("GM.xmlHttpRequest") ||
          script.grant.contains("GM_getResourceText"))) {
    script.grant.add("GM_xmlhttpRequest")
  }

  if (script.grant.contains("GM.getValue") ||
      script.grant.contains("GM_getValue") ||
      script.grant.contains("GM_cookie")) {
    runCatching { script.storage = JSONObject(storage!!) }
        .onFailure { script.storage = JSONObject() }
  }

  if (script.match.size == 0) {
    return null
  } else {
    val lib = mutableListOf<String>()
    Chrome.IO.submit { script.require.forEach { runCatching { lib.add(downloadLib(it)) } } }
    val parsed =
        Script(
            script.namespace + ":" + script.name,
            script.match.toTypedArray(),
            script.grant.toTypedArray(),
            script.exclude.toTypedArray(),
            script.meta,
            script.code,
            script.storage,
            lib)
    return parsed
  }
}

private fun downloadLib(libUrl: String): String {
  val url = URL(libUrl)
  val connection = url.openConnection() as HttpURLConnection
  return connection.inputStream.bufferedReader().use { it.readText() }
}

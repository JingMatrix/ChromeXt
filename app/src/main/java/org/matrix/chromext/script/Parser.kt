package org.matrix.chromext.script

import kotlin.text.Regex
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.Log

// const size_t kMaxURLChars = 2 * 1024 * 1024; in chromium/src/ur/url_constants.cc
// const uint32 kMaxURLChars = 2097152; in chromium/src/url/mojom/url.mojom
const val kMaxURLChars = 2097152

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
        val meta = (blockMatchGroup.get("metablock")?.value as String).replace("`", "")
        val code = blockMatchGroup.get("code")?.value as String
        var resource = mutableListOf<String>()
      }
  script.meta.split("\n").forEach {
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
        "resource" -> script.resource.add(value.trim().replace("\\s+".toRegex(), " "))
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
    val parsed =
        Script(
            (script.namespace + ":" + script.name).replace("\\", ""),
            script.match.toTypedArray(),
            script.grant.toTypedArray(),
            script.exclude.toTypedArray(),
            script.require.toTypedArray(),
            script.resource.toTypedArray(),
            script.meta,
            script.code,
            script.runAt,
            shouldWrap = script.code.length > kMaxURLChars - 2000)
    val id = parsed.id
    if (parsed.grant.contains("GM_getResourceText")) {
      parsed.resource.forEach {
        val content = it.split(" ")
        val name = content.first()
        val url = content.last()
        Log.d("Downloading resource for ${name}: ${url}")
        if (url.startsWith("http")) {
          Download.start(url, resourcePath(id, name), true)
        }
      }
    }
    return parsed
  }
}

const val RESERVED_CHARS = "|\\?*<\":>+[]/' "

fun resourcePath(id: String, name: String): String =
    "Resource/" +
        id.filterNot { RESERVED_CHARS.contains(it) } +
        "/" +
        name.filterNot { RESERVED_CHARS.contains(it) }

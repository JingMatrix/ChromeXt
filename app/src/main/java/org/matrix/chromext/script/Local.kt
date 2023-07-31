package org.matrix.chromext.script

import java.io.File
import java.io.FileReader
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ResourceMerge

object GM {
  private val localScript: Map<String, String>

  init {
    val ctx = Chrome.getContext()
    ResourceMerge.enrich(ctx)
    localScript =
        ctx.assets
            .open("GM.js")
            .bufferedReader()
            .use { it.readText() }
            .split("// Kotlin separator\n\n")
            .associateBy({ it.lines()[0].split("(")[0].split(" ").last() }, { it })
  }

  fun bootstrap(script: Script): List<String> {
    var code = script.code
    var grants = ""

    if (!script.meta.startsWith("// ==UserScript==")) {
      code = script.meta + code
    }

    script.grant.forEach {
      when (it) {
        "GM_info" -> return@forEach
        "unsafeWindow" -> grants += "const unsafeWindow = window;"
        "GM_log" -> grants += "const GM_log = console.log.bind(console);"
        else ->
            if (localScript.containsKey(it)) {
              grants += localScript.get(it)
            } else if (!it.contains(".")) {
              grants +=
                  "function ${it}(...args) { console.error('${it} is not implemented in ChromeXt yet, called with', args) }\n"
            } else if (it.startsWith("GM.")) {
              val name = it.substring(3)
              if (script.grant.contains("GM_${name}")) {
                grants +=
                    "${it} = async (...arguments) => new Promise((resolve, reject) => {resolve(GM_${name}(...arguments))});"
              }
            }
      }
    }

    if (script.resource.size > 0) {
      val Resources = JSONArray()
      runCatching {
            script.resource.forEach {
              val content = it.split(" ")
              if (content.size != 2) throw Exception("Invalid resource ${it}")
              val name = content.first()
              val url = content.last()
              val resource = JSONObject()
              resource.put("name", name)
              resource.put("url", url.split("#").first())
              val file =
                  File(Chrome.getContext().getExternalFilesDir(null), resourcePath(script.id, name))
              if (file.exists()) {
                val text = FileReader(file).use { it.readText() }
                resource.put("content", text)
              }
              Resources.put(resource)
            }
          }
          .onFailure { Log.i("Fail to process resources for ${script.id}: " + it.message) }
      grants += "GM_info.script.resources = ${Resources};"
    }

    grants += localScript.get("GM_bootstrap")!!

    val GM_info =
        JSONObject(
            mapOf("scriptMetaStr" to script.meta, "script" to JSONObject().put("id", script.id)))
    val codes =
        mutableListOf(
            "(() => { const GM = {}; const GM_info = ${GM_info}; GM_info.script.code = () => {${code}};\n${grants}GM_bootstrap();})();")
    if (script.storage != null) {
      val storage_info =
          JSONObject(mapOf("id" to script.id, "data" to JSONObject().put("init", script.storage!!)))
      codes.add("ChromeXt.post('scriptStorage', ${storage_info});")
    }

    return codes
  }
}

object Local {

  val promptInstallUserScript: String
  val customizeDevTool: String
  val eruda: String
  val initChromeXt: String
  val openEruda: String
  val cspRule: String

  init {
    val ctx = Chrome.getContext()
    ResourceMerge.enrich(ctx)
    promptInstallUserScript = ctx.assets.open("editor.js").bufferedReader().use { it.readText() }
    customizeDevTool = ctx.assets.open("devtools.js").bufferedReader().use { it.readText() }
    val css =
        JSONArray(ctx.assets.open("eruda.css").bufferedReader().use { it.readText() }.split("\n\n"))
    eruda =
        "const _eruda_styles = ${css};\n" +
            ctx.assets.open("eruda.js").bufferedReader().use { it.readText() }
    val localScript =
        ctx.assets
            .open("scripts.js")
            .bufferedReader()
            .use { it.readText() }
            .split("// Kotlin separator\n\n")
    initChromeXt = localScript[0]
    openEruda = localScript[1]
    cspRule = localScript[2]
  }
}

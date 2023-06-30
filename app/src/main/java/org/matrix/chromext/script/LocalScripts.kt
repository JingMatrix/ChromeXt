package org.matrix.chromext.script

import java.io.File
import java.io.FileReader
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log

object GM {

  val localScript =
      Chrome.getContext()
          .assets
          .open("local_script.js")
          .bufferedReader()
          .use { it.readText() }
          .split("// Kotlin separator\n\n")
          .associateBy({ it.lines()[0].split("(")[0].split(" ")[1] }, { it })

  fun bootstrap(script: Script): String? {
    var code = script.code
    var grants = localScript.get("GM_bootstrap")!!

    if (!script.meta.startsWith("// ==UserScript==")) {
      code = script.meta + code
    }

    if (script.storage == "") {
      script.storage = "{}"
    }

    script.grant.forEach granting@{
      when (it) {
        "GM_info" -> return@granting
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
                if (name.contains("get") || name.contains("Value")) {
                  grants +=
                      "${it} = async (...arguments) => new Promise((resolve, reject) => {resolve(GM_${name}(...arguments))});"
                } else {
                  grants += "${it} = GM_${name};"
                }
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

    code =
        "(() => { const GM = {}; const GM_info = { scriptMetaStr: `${script.meta}`, storage: ${script.storage}, script: { id: `${script.id}`, code: () => {${code}} } };\n${grants}GM_bootstrap();})();"

    return code
  }
}

const val openEruda =
    """
try {
  if (eruda._isInit) {
    eruda.hide();
    eruda.destroy();
  } else {
    eruda.init();
    eruda._localConfig();
    eruda.show();
  }
} catch (e) {
  globalThis.ChromeXt(JSON.stringify({ action: 'loadEruda', payload: '' }));
}"""

const val cspRule =
    """
if (ChromeXt.cspRules) {
  const meta = document.createElement('meta');
  meta.setAttribute('http-equiv', 'Content-Security-Policy');
  meta.setAttribute('content', ChromeXt.cspRules);
  try {
    document.head.append(meta);
  } catch {
    setTimeout(() => {
      document.head.append(meta);
    }, 0);
  }
}"""

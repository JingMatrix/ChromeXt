package org.matrix.chromext.extension

import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.Resource
import org.matrix.chromext.script.Local

/** Builds the JavaScript injected by the WebExtensions shim. */
object ChromeApi {

  private var source: String? = null

  private fun shim(): String =
      source
          ?: run {
            Resource.enrich(Chrome.getContext())
            val text =
                Chrome.getContext().assets.open("chrome-api.js").bufferedReader().use {
                  it.readText()
                }
            source = text
            text
          }

  fun config(extension: Extension, transport: String): JSONObject {
    val i18n = JSONObject()
    i18n.put("locale", extension.defaultLocale ?: "en")
    i18n.put("messages", extension.messages ?: JSONObject())
    return JSONObject()
        .put("id", extension.id)
        .put("origin", ExtServer.origin(extension))
        .put("transport", transport)
        .put("i18n", i18n)
        .put("manifest", extension.manifest)
  }

  /** The background script served at /__chromext__.js, running inside the host page. */
  fun script(extension: Extension): String = buildString {
    append("const __CHROMEXT_CFG__ = ").append(config(extension, "host")).append(";\n")
    append(shim()).append("\n")
    append("globalThis.chrome = __chromextCreateApi(__CHROMEXT_CFG__);\n")
    append("globalThis.browser = globalThis.chrome;\n")
    // Service worker code frequently relies on importScripts, which pages do not have
    append("if (typeof self.importScripts === 'undefined') {\n")
    append("  self.importScripts = function () {\n")
    append("    for (var i = 0; i < arguments.length; i++) {\n")
    append("      try { document.write('<script src=\"' + new URL(arguments[i],")
    append(" self.location.href) + '\"><\\/script>'); } catch (e) { console.error(e); }\n")
    append("    }\n")
    append("  };\n")
    append("}\n")
    // Messages relayed by the host page
    append("window.addEventListener('message', function (event) {\n")
    append("  var data = event.data && event.data.__chromext;\n")
    append("  if (!data || data.ext !== __CHROMEXT_CFG__.id) return;\n")
    append("  try {\n")
    append("    var hooks = globalThis.__chromextHooks;\n")
    append("    if (data.type === 'deliver') {\n")
    append("      if (hooks && hooks.deliver && hooks.deliver[data.ext]) {\n")
    append("        hooks.deliver[data.ext](data.message, data.sender, data.uuid);\n")
    append("      } else {\n")
    append("        console.warn('No message listener for ' + data.ext);\n")
    append("      }\n")
    append("    } else if (data.type === 'resolve') {\n")
    append("      globalThis.__chromextResolve(data.uuid, data.ok, data.value);\n")
    append("    } else if (data.type === 'portOpen') {\n")
    append("      if (hooks && hooks.portOpen && hooks.portOpen[data.ext]) {\n")
    append("        hooks.portOpen[data.ext](data.portId, data.name, data.sender);\n")
    append("      }\n")
    append("    } else if (data.type === 'portMessage') {\n")
    append("      globalThis.__chromextPortMessage &&")
    append(" globalThis.__chromextPortMessage(data.portId, data.message);\n")
    append("    } else if (data.type === 'portDisconnect') {\n")
    append("      globalThis.__chromextPortDisconnect &&")
    append(" globalThis.__chromextPortDisconnect(data.portId);\n")
    append("    }\n")
    append("  } catch (e) { console.error(e); }\n")
    append("});\n")
    // Announce readiness only once the declared background scripts have run
    append("(function () {\n")
    append("  var sent = false;\n")
    append("  function announce() {\n")
    append("    if (sent) return;\n")
    append("    sent = true;\n")
    append("    parent.postMessage({ __chromext: { type: 'ready',")
    append(" ext: __CHROMEXT_CFG__.id } }, '*');\n")
    append("  }\n")
    append("  if (document.readyState === 'complete') announce();\n")
    append("  else window.addEventListener('load', announce, { once: true });\n")
    append("  setTimeout(announce, 3000);\n")
    append("})();\n")
  }

  /** One content script bundle: shim + declared files, sharing a private scope. */
  fun bootstrap(extension: Extension, contentScript: ContentScript): String? {
    val js = contentScript.js.mapNotNull { extension.readText(it) }
    val css = contentScript.css.mapNotNull { extension.readText(it) }
    if (js.isEmpty() && css.isEmpty()) return null
    return buildString {
      append(";(function(){\n")
      append("const __CHROMEXT_CFG__ = ").append(config(extension, "content")).append(";\n")
      append("function __chromextBridge(payload){\n")
      append("  var t = Symbol[").append(JSONObject.quote(Local.name)).append("];\n")
      append("  var api = t;\n")
      append("  try { var u = t.unlock(").append(Local.key).append("); if (u) api = u; }")
      append(" catch (e) { api = t; }\n")
      append("  api.dispatch('ext', payload);\n")
      append("}\n")
      append(shim()).append("\n")
      css.forEach { style ->
        append("(function(){try{var s=document.createElement('style');s.textContent=")
        append(JSONObject.quote(style))
        append(";(document.head||document.documentElement).appendChild(s);}catch(e){}})();\n")
      }
      append("var __api = __chromextCreateApi(__CHROMEXT_CFG__);\n")
      append("(function(chrome, browser){\n")
      js.forEach { append(it).append("\n;\n") }
      append("})(__api, __api);\n")
      append("})();\n")
    }
  }
}

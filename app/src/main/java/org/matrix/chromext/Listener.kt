package org.matrix.chromext

import android.content.Context
import java.io.File
import java.io.FileReader
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.devtools.DEV_FRONT_END
import org.matrix.chromext.devtools.DevToolClient
import org.matrix.chromext.devtools.getInspectPages
import org.matrix.chromext.proxy.ERUD_URL
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.ScriptDbHelper
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.matching
import org.matrix.chromext.script.parseScript
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.XMLHttpRequest

object Listener {

  val xmlhttpRequests = mutableMapOf<Double, XMLHttpRequest>()
  val devToolClients = mutableMapOf<Pair<String, String>, Pair<DevToolClient, DevToolClient>>()
  val allowedActions =
      mapOf(
          "front-end" to listOf("inspectPages", "getIds", "getMeta", "updateMeta", "deleteScript"),
          "devtools" to listOf("websocket"))

  private fun parseArray(str: String): Array<String> {
    val result = mutableListOf<String>()
    val array = JSONArray(str)
    for (i in 0 until array.length()) {
      result.add(array.getString(i))
    }
    return result.toTypedArray()
  }

  private fun syncSharedPreference(
      payload: String,
      item: String,
      cache: MutableMap<String, String>
  ): String? {
    val result = JSONObject(payload)
    val origin = result.getString("origin")
    val sharedPref = Chrome.getContext().getSharedPreferences(item, Context.MODE_PRIVATE)
    with(sharedPref.edit()) {
      if (result.has("data")) {
        val data = result.getString("data")
        putString(origin, data)
        cache.put(origin, data)
      } else if (cache.containsKey(origin)) {
        remove(origin)
        cache.remove(origin)
      }
      apply()
    }
    return null
  }

  private fun checkPermisson(action: String, tab: Any?): Boolean {
    val url = Chrome.getUrl(tab)!!
    if (allowedActions.get("front-end")!!.contains(action) && !url.endsWith("/ChromeXt/"))
        return false
    if (allowedActions.get("devtools")!!.contains(action) && !url.startsWith(DEV_FRONT_END))
        return false
    return true
  }

  fun startAction(text: String, currentTab: Any? = null) {
    runCatching {
          val data = JSONObject(text)
          val action = data.getString("action")
          val payload = data.getString("payload")
          if (checkPermisson(action, currentTab)) {
            val callback = on(action, payload, currentTab)
            if (callback != null) Chrome.evaluateJavascript(listOf(callback))
          }
        }
        .onFailure { Log.d("Ignore console.debug: " + text) }
  }

  fun on(action: String, payload: String = "", currentTab: Any? = null): String? {
    var callback: String? = null
    when (action) {
      "installScript" -> {
        val script = parseScript(payload, "", true)
        if (script == null) {
          callback = "alert('Invalid UserScript');"
        } else {
          Log.i("Install script ${script.id}")
          ScriptDbManager.apply {
            insert(script)
            scripts.removeAll(scripts.filter { it.id == script.id })
            scripts.add(script)
          }
        }
      }
      "unsafe-eval" -> {
        Chrome.evaluateJavascript(listOf(payload))
      }
      "scriptStorage" -> {
        val detail = JSONObject(payload)
        val id = detail.getString("id")
        val script = ScriptDbManager.scripts.find { it.id == id }
        if (script?.storage == null) return callback
        if (detail.optBoolean("broadcast")) {
          thread {
            detail.remove("broadcast")
            Chrome.broadcast("scriptStorage", "{detail: ${detail}}") { matching(script, it) }
          }
        }
        val data = detail.getJSONObject("data")
        val key = data.getString("key")
        if (data.has("value")) {
          script.storage!!.put(key, data.get("value"))
        } else if (data.has("id")) {
          if (script.storage!!.has(key)) {
            data.put("value", script.storage!!.get(key))
          }
          detail.put("data", data)
          callback =
              "window.dispatchEvent(new CustomEvent('scriptSyncValue', {detail: ${detail}}));"
        } else {
          script.storage!!.remove(key)
        }
      }
      "abortRequest" -> {
        val uuid = payload.toDouble()
        xmlhttpRequests.get(uuid)?.abort()
      }
      "xmlhttpRequest" -> {
        val detail = JSONObject(payload)
        val uuid = detail.getDouble("uuid")
        val request =
            XMLHttpRequest(
                detail.getString("id"), detail.getJSONObject("request"), uuid, currentTab)
        xmlhttpRequests.put(uuid, request)
        thread { request.send() }
      }
      "userAgentSpoof" -> {
        val loadUrlParams = UserScriptProxy.newLoadUrlParams(payload)
        if (UserScriptProxy.userAgentHook(payload, loadUrlParams)) {
          UserScriptProxy.loadUrl.invoke(Chrome.getTab(), loadUrlParams)
          callback = "console.log('User-Agent spoofed');"
        }
      }
      "loadEruda" -> {
        val ctx = Chrome.getContext()
        val eruda = File(ctx.getExternalFilesDir(null), "Download/Eruda.js")
        if (eruda.exists()) {
          callback = FileReader(eruda).use { it.readText() } + "\n"
          callback += ctx.assets.open("local_eruda.js").bufferedReader().use { it.readText() }
          callback += "eruda.init(); eruda._localConfig(); eruda.show();"
          Chrome.evaluateJavascript(listOf(callback))
          callback = null
        } else {
          Log.toast(ctx, "Updating Eruda...")
          Download.start(ERUD_URL, "Download/Eruda.js", true) { on("loadEruda", "") }
        }
      }
      "cosmeticFilter" -> {
        callback = syncSharedPreference(payload, "CosmeticFilter", ScriptDbManager.cosmeticFilters)
      }
      "userAgent" -> {
        callback = syncSharedPreference(payload, "UserAgent", ScriptDbManager.userAgents)
      }
      "cspRule" -> {
        callback = syncSharedPreference(payload, "CSPRule", ScriptDbManager.cspRules)
      }
      "inspectPages" -> {
        thread {
          val code =
              "window.dispatchEvent(new CustomEvent('inspect_pages', { detail: ${getInspectPages()} }));"
          Chrome.evaluateJavascript(listOf(code), currentTab)
        }
      }
      "getIds" -> {
        val result = JSONArray()
        ScriptDbManager.scripts.forEach { result.put(it.id) }
        callback = "window.dispatchEvent(new CustomEvent('script_id', {detail: ${result}}));"
      }
      "getMeta" -> {
        val ids = parseArray(payload)
        val result = JSONArray()
        ScriptDbManager.scripts.filter { ids.contains(it.id) }.forEach { result.put(it.meta) }
        callback = "window.dispatchEvent(new CustomEvent('script_meta', {detail: ${result}}));"
      }
      "updateMeta" -> {
        val data = JSONObject(payload)
        val script = ScriptDbManager.scripts.filter { it.id == data.getString("id") }.first()
        val newScript =
            parseScript(data.getString("meta") + script.code, script.storage?.toString())!!
        ScriptDbManager.insert(newScript)
        ScriptDbManager.scripts.remove(script)
        ScriptDbManager.scripts.add(newScript)
      }
      "deleteScript" -> {
        val ids = parseArray(payload)
        val dbHelper = ScriptDbHelper(Chrome.getContext())
        val db = dbHelper.writableDatabase
        db.delete("script", "id = ?", ids)
        ScriptDbManager.scripts.removeAll(ScriptDbManager.scripts.filter { ids.contains(it.id) })
        dbHelper.close()
      }
      "websocket" -> {
        val detail = JSONObject(payload)
        if (!detail.has("tabId")) {
          on("inspectPages")
          return callback
        }

        val tabId = detail.optString("tabId")
        val targetTabId = detail.getString("targetTabId")
        val key = Pair(targetTabId, tabId)

        if (detail.has("message")) {
          val message = JSONObject(detail.getString("message"))
          devToolClients
              .get(key)
              ?.first
              ?.command(
                  message.getInt("id"),
                  message.getString("method"),
                  message.getJSONObject("params"))
        } else {
          thread {
            fun response(res: JSONObject): Boolean? {
              val response = JSONObject(mapOf("detail" to res))
              val mTab = devToolClients.get(key)?.second
              if (mTab?.isClosed() == false) {
                mTab.evaluateJavascript(
                    "window.dispatchEvent(new CustomEvent('websocket', ${response}))")
                return true
              }
              return false
            }

            fun closeSockets() {
              devToolClients.get(key)?.first?.close()
              devToolClients.get(key)?.second?.close()
              devToolClients.remove(key)
            }

            if (devToolClients.containsKey(key)) {
              closeSockets()
            }

            devToolClients.put(key, Pair(DevToolClient(targetTabId), DevToolClient(tabId)))

            response(JSONObject(mapOf("open" to true)))
            devToolClients.get(key)?.first?.listen {
              if (response(JSONObject(mapOf("message" to it))) == false) {
                closeSockets()
              }
            }
          }
        }
      }
    }
    return callback
  }
}
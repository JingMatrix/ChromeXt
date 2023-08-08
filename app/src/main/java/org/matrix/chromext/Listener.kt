package org.matrix.chromext

import android.app.Activity
import android.content.Context
import java.io.File
import java.io.FileReader
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.devtools.DEV_FRONT_END
import org.matrix.chromext.devtools.DevToolClient
import org.matrix.chromext.devtools.getInspectPages
import org.matrix.chromext.extension.LocalFiles
import org.matrix.chromext.proxy.ERUD_URL
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbHelper
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.matching
import org.matrix.chromext.script.parseScript
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.XMLHttpRequest
import org.matrix.chromext.utils.invokeMethod

object Listener {

  val xmlhttpRequests = mutableMapOf<Double, XMLHttpRequest>()
  val devToolClients = mutableMapOf<Pair<String, String>, Pair<DevToolClient, DevToolClient>>()
  val allowedActions =
      mapOf(
          "front-end" to listOf("inspect_pages", "userscript", "extension"),
          "devtools" to listOf("websocket"))

  private fun syncSharedPreference(
      payload: String,
      item: String,
      cache: MutableMap<String, String>,
  ): String? {
    val result = JSONObject(payload)
    val origin = result.getString("origin")
    val sharedPref = Chrome.getContext().getSharedPreferences(item, Context.MODE_PRIVATE)
    with(sharedPref.edit()) {
      if (result.has("data") && result.optString("data").length > 0) {
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
          if (data.optBoolean("blocked")) throw Error("Blocked Access")
          val payload = data.optString("payload")
          if (checkPermisson(action, currentTab)) {
            val callback = on(action, payload, currentTab)
            if (callback != null) Chrome.evaluateJavascript(listOf(callback), currentTab)
          }
        }
        .onFailure { Log.d("Ignore console.debug: " + text) }
  }

  fun on(action: String, payload: String = "", currentTab: Any? = null): String? {
    var callback: String? = null
    when (action) {
      "focus" -> {
        val activity =
            (currentTab ?: Chrome.getTab())?.invokeMethod { name == "getContext" } as Activity
        activity.window.decorView.requestFocus()
      }
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
      "unsafeEval" -> {
        callback = payload
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
          callback = "ChromeXt.unlock(${Local.key}).post('scriptSyncValue', ${detail});"
        } else {
          script.storage!!.remove(key)
        }
      }
      "xmlhttpRequest" -> {
        val detail = JSONObject(payload)
        val uuid = detail.getDouble("uuid")
        if (detail.optBoolean("abort")) {
          xmlhttpRequests.get(uuid)?.abort()
        } else {
          val request =
              XMLHttpRequest(
                  detail.getString("id"), detail.getJSONObject("request"), uuid, currentTab)
          xmlhttpRequests.put(uuid, request)
          thread { request.send() }
        }
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
          val code = FileReader(eruda).use { it.readText() } + "\n" + Local.eruda
          Chrome.evaluateJavascript(listOf("(()=>{${code}})();"))
        } else {
          Log.toast(ctx, "Updating Eruda...")
          Download.start(ERUD_URL, "Download/Eruda.js", true) { on("loadEruda") }
        }
      }
      "syncData" -> {
        val type = JSONObject(payload).getString("name")
        callback =
            when (type) {
              "filters" ->
                  syncSharedPreference(payload, "CosmeticFilter", ScriptDbManager.cosmeticFilters)
              "userAgent" -> syncSharedPreference(payload, "UserAgent", ScriptDbManager.userAgents)
              "cspRules" -> syncSharedPreference(payload, "CSPRule", ScriptDbManager.cspRules)
              else -> null
            }
      }
      "inspectPages" -> {
        thread {
          val code = "ChromeXt.post('inspect_pages', ${getInspectPages()});"
          Chrome.evaluateJavascript(listOf(code), currentTab)
        }
      }
      "userscript" -> {
        if (payload == "") {
          val detail = JSONObject(mapOf("type" to "init"))
          detail.put("ids", JSONArray(ScriptDbManager.scripts.map { it.id }))
          callback = "ChromeXt.post('userscript', ${detail});"
        } else {
          val data = JSONObject(payload)
          if (data.has("meta")) {
            val script = ScriptDbManager.scripts.filter { it.id == data.getString("id") }.first()
            val newScript =
                parseScript(data.getString("meta") + script.code, script.storage?.toString())
            if (newScript != null) {
              ScriptDbManager.insert(newScript)
              ScriptDbManager.scripts.remove(script)
              ScriptDbManager.scripts.add(newScript)
            } else {
              callback = "alert('Fail to update script metadata');"
            }
          } else if (data.has("ids")) {
            val jsonArray = data.getJSONArray("ids")
            val ids = Array(jsonArray.length()) { jsonArray.getString(it) }
            val scripts = ScriptDbManager.scripts.filter { ids.contains(it.id) }
            if (data.optBoolean("delete")) {
              val dbHelper = ScriptDbHelper(Chrome.getContext())
              val db = dbHelper.writableDatabase
              db.delete("script", "id = ?", ids)
              ScriptDbManager.scripts.removeAll(scripts)
              dbHelper.close()
            } else {
              val result = JSONArray(scripts.map { it.meta })
              callback = "ChromeXt.post('script_meta', ${result});"
            }
          }
        }
      }
      "extension" -> {
        if (payload == "") {
          if (BuildConfig.DEBUG) {
            callback = "ChromeXt.post('extension', ${LocalFiles.start()});"
          } else {
            Log.toast(Chrome.getContext(), "Work in progress, might be ready in the future :)")
          }
        }
      }
      "websocket" -> {
        val detail = JSONObject(payload)
        if (!detail.has("tabId")) {
          on("inspectPages")
          return callback
        }

        val targetTabId = detail.getString("targetTabId")
        val tabId = detail.optString("tabId")
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
              val mTab = devToolClients.get(key)?.second
              if (mTab?.isClosed() == false) {
                mTab.evaluateJavascript("ChromeXt.post('websocket', ${res})")
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

package org.matrix.chromext

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.webkit.WebView
import java.io.File
import java.io.FileReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.devtools.DevSessions
import org.matrix.chromext.devtools.DevToolClient
import org.matrix.chromext.devtools.getInspectPages
import org.matrix.chromext.devtools.hitDevTools
import org.matrix.chromext.extension.LocalFiles
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbHelper
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.parseScript
import org.matrix.chromext.utils.ERUD_URL
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.XMLHttpRequest
import org.matrix.chromext.utils.invalidUserScriptUrls
import org.matrix.chromext.utils.isChromeXtFrontEnd
import org.matrix.chromext.utils.isDevToolsFrontEnd
import org.matrix.chromext.utils.isUserScript
import org.matrix.chromext.utils.matching
import org.matrix.chromext.utils.parseOrigin

object Listener {

  val xmlhttpRequests = mutableMapOf<Double, XMLHttpRequest>()
  val allowedActions =
      mapOf(
          "userscript" to listOf("block", "installScript"),
          "front-end" to listOf("inspect_pages", "userscript", "extension"),
          "devtools" to listOf("websocket"),
      )
  val tabNotification = mutableMapOf<Int, Any>()

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

  private fun checkPermisson(action: String, key: Double, tab: Any?): Boolean {
    if (key != Local.key) return false
    val url = Chrome.getUrl(tab)!!
    if (url.endsWith(".txt")) return false
    if (isUserScript(url) && !allowedActions.get("userscript")!!.contains(action)) return false
    if (allowedActions.get("front-end")!!.contains(action) && !isChromeXtFrontEnd(url)) return false
    if (allowedActions.get("devtools")!!.contains(action) && !isDevToolsFrontEnd(url)) return false
    return true
  }

  private fun checkErudaVerison(ctx: Context, callback: (String?) -> Unit) {
    val url = URL(ERUD_URL + "@latest/eruda.js")
    val connection = url.openConnection() as HttpURLConnection
    runCatching {
          connection.inputStream.bufferedReader().use {
            var firstLine = it.readLine()
            val new_version = Local.getErudaVersion(ctx, firstLine)
            if (new_version == null) {
              callback(null)
            } else if (new_version != Local.eruda_version) {
              Local.eruda_version = new_version
              callback(firstLine + "\n" + it.readText())
            } else {
              callback("latest")
            }
            it.close()
          }
        }
        .onFailure { callback(null) }
  }

  fun startAction(text: String, currentTab: Any? = null) {
    runCatching {
          val data = JSONObject(text)
          val action = data.getString("action")
          val key = data.getDouble("key")
          val payload = data.optString("payload")
          if (checkPermisson(action, key, currentTab)) {
            val callback = on(action, payload, currentTab)
            if (callback != null) Chrome.evaluateJavascript(listOf(callback), currentTab)
          }
        }
        .onFailure { Log.i("${it::class.java.name}: startAction fails with " + text) }
  }

  fun on(action: String, payload: String = "", currentTab: Any? = null): String? {
    var callback: String? = null
    when (action) {
      "copy" -> {
        val data = JSONObject(payload)
        val type = data.getString("type")
        val text = data.getString("text")
        val label = data.optString("label")
        val clipData =
            when (type) {
              "html" -> ClipData.newHtmlText(label, text, data.optString("htmlText"))
              else -> ClipData.newPlainText(label, text)
            }
        val context = Chrome.getContext()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(clipData)
      }
      "block" -> {
        val url = Chrome.getUrl(currentTab)
        if (isUserScript(url)) invalidUserScriptUrls.add(url!!)
        callback = "if (Symbol.ChromeXt) Symbol.ChromeXt.lock(${Local.key},'${Local.name}');"
      }
      "focus" -> {
        Chrome.updateTab(currentTab)
      }
      "installScript" -> {
        val script = parseScript(payload)
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
      "notification" -> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
          callback = "console.error('notification API requires Android Oreo')"
          return callback
        }
        val detail = JSONObject(payload)
        val id = detail.getString("id")
        val uuid = detail.getInt("uuid")
        val title = detail.getString("title")
        val text = detail.getString("text")
        val timeout = detail.getLong("timeout")
        val ctx = Chrome.getContext()
        var channel = "xposed_notification"
        if (detail.optBoolean("silent")) channel += "_slient"
        val builder =
            Notification.Builder(ctx, channel)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(
                    Icon.createWithResource("org.matrix.chromext", R.drawable.ic_extension))
                .setTimeoutAfter(timeout)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setOnlyAlertOnce(true)
        if (detail.optBoolean("onclick")) {
          builder.setContentIntent(ScriptNotification.newIntent(id, uuid))
          tabNotification.put(uuid, currentTab!!)
        }
        val notificationManager =
            ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (detail.has("image")) {
          Chrome.IO.submit {
            runCatching {
                  val url = URL(detail.getString("image"))
                  val connection = url.openConnection() as HttpURLConnection
                  val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                  builder.setLargeIcon(Icon.createWithBitmap(bitmap))
                }
                .onFailure { Log.d("Fail to set notification image: ${it.message}") }
            notificationManager.notify(id, uuid, builder.build())
          }
        } else {
          notificationManager.notify(id, uuid, builder.build())
        }
        if (detail.optBoolean("highlight")) (ctx as Activity).getWindow().decorView.requestFocus()
      }
      "scriptStorage" -> {
        val detail = JSONObject(payload)
        val id = detail.getString("id")
        val script = ScriptDbManager.scripts.find { it.id == id }
        if (script?.storage == null) return callback
        if (detail.optBoolean("broadcast")) {
          detail.remove("broadcast")
          Chrome.broadcast("scriptStorage", detail) {
            it != null && matching(script, it) && parseOrigin(it) != null
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
          callback = "Symbol.${Local.name}.unlock(${Local.key}).post('scriptSyncValue', ${detail});"
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
          Chrome.IO.submit { request.send() }
        }
      }
      "cookie" -> {
        if (WebViewHook.isInit) WebView.setWebContentsDebuggingEnabled(true)
        val detail = JSONObject(payload)
        val method = detail.getString("method")
        val params = detail.optJSONObject("params")
        val data = JSONArray()
        fun checkResult(result: JSONObject): Boolean {
          data.put(result)
          if (result.getInt("id") == 2) {
            detail.put("response", data)
            detail.remove("params")
            val code = "Symbol.${Local.name}.unlock(${Local.key}).post('cookie', ${detail});"
            Chrome.evaluateJavascript(listOf(code), currentTab)
            return false
          }
          return true
        }
        val url = Chrome.getUrl(currentTab)
        Chrome.IO.submit {
          val tabId = Chrome.getTabId(currentTab, url)
          val client = DevSessions.new(tabId)
          Chrome.IO.submit { client.listen { if (!checkResult(it)) client.close() } }
          client.command(null, "Network.enable", JSONObject())
          client.command(null, method, params)
        }
      }
      "userAgentSpoof" -> {
        if (UserScriptHook.isInit) {
          val loadUrlParams = UserScriptProxy.newLoadUrlParams(payload)
          if (UserScriptProxy.userAgentHook(payload, loadUrlParams)) {
            UserScriptProxy.loadUrl.invoke(Chrome.getTab(), loadUrlParams)
            callback = "console.log('User-Agent spoofed');"
          }
        }
      }
      "loadEruda" -> {
        val ctx = Chrome.getContext()
        val eruda = File(ctx.filesDir, "Eruda.js")
        if (eruda.exists()) {
          val codes =
              mutableListOf(
                  FileReader(eruda).use { it.readText() } +
                      "\n//# sourceURL=${ERUD_URL}@${Local.eruda_version}/eruda.js")
          codes.add("{${Local.eruda}}\n//# sourceURL=local://ChromeXt/eruda")
          Chrome.evaluateJavascript(codes)
        } else {
          on("updateEruda", JSONObject().put("load", true).toString())
        }
      }
      "updateEruda" -> {
        val ctx = Chrome.getContext()
        Handler(ctx.mainLooper).post { Log.toast(ctx, "Updating Eruda...") }
        Chrome.IO.submit {
          checkErudaVerison(ctx) {
            val msg =
                if (it == "latest") {
                  "Eruda is already the latest"
                } else if (it != null) {
                  "Updated to eruda v" + Local.eruda_version
                } else {
                  "Failed to download Eruda.js from ${ERUD_URL}"
                }
            Handler(ctx.mainLooper).post { Log.toast(ctx, msg) }
            if (it != null) {
              if (it != "latest") {
                val file = File(ctx.filesDir, "Eruda.js")
                file.outputStream().write(it.toByteArray())
              }
              if (payload != "" && JSONObject(payload).optBoolean("load")) on("loadEruda")
            }
          }
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
        if (WebViewHook.isInit) WebView.setWebContentsDebuggingEnabled(true)
        Chrome.IO.submit {
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
        val targetTabId = detail.getString("targetTabId")
        var target = DevSessions.get(targetTabId)
        if (detail.has("message")) {
          val message = JSONObject(detail.getString("message"))
          target?.command(
              message.getInt("id"), message.getString("method"), message.optJSONObject("params"))
        } else {
          fun response(res: JSONObject) {
            if (Chrome.checkTab(currentTab)) {
              Chrome.evaluateJavascript(listOf("ChromeXt.post('websocket', ${res})"), currentTab)
            } else {
              target?.close()
            }
          }
          Chrome.IO.submit {
            target?.close()
            hitDevTools().close()
            target = DevToolClient(targetTabId)
            if (!target!!.isClosed()) {
              DevSessions.add(target!!)
              response(JSONObject(mapOf("open" to true)))
              target!!.listen { response(JSONObject(mapOf("message" to it))) }
            }
            response(JSONObject(mapOf("error" to "Remote session closed")))
          }
        }
      }
    }
    return callback
  }
}

private class ScriptNotification(detail: JSONObject) : BroadcastReceiver() {
  private val detail = detail

  companion object {
    const val ACTION_USERSCRIPT = "ChromeXt"
    const val UUID = "GM_notification"

    fun newIntent(id: String, uuid: Int): PendingIntent {
      val ctx = Chrome.getContext()
      val detail = JSONObject(mapOf("id" to id, "uuid" to uuid))
      ctx.registerReceiver(ScriptNotification(detail), IntentFilter(ACTION_USERSCRIPT))
      val intent =
          Intent().apply {
            setAction(ACTION_USERSCRIPT)
            putExtra(UUID, uuid)
          }
      return PendingIntent.getBroadcast(ctx, uuid, intent, PendingIntent.FLAG_IMMUTABLE)
    }
  }

  override fun onReceive(ctx: Context, intent: Intent) {
    if (intent.getAction() == ACTION_USERSCRIPT) {
      val uuid = intent.getIntExtra(UUID, 0)
      if (uuid == detail.getInt("uuid")) {
        val tab = Listener.tabNotification.get(detail.getInt("uuid"))!!
        val code = "Symbol.${Local.name}.unlock(${Local.key}).post('notification', ${detail});"
        Chrome.evaluateJavascript(listOf(code), tab)
        ctx.unregisterReceiver(this)
        Listener.tabNotification.remove(uuid)
      }
    }
  }
}

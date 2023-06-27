package org.matrix.chromext.script

import android.content.ContentValues
import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.CursorWindow
import android.os.Build
import java.io.File
import java.io.FileReader
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.proxy.ERUD_URL
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.utils.Download
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.XMLHttpRequest

object ScriptDbManager {

  val scripts = query()
  val xmlhttpRequests = mutableMapOf<Double, XMLHttpRequest>()

  @Suppress("UNCHECKED_CAST")
  val cosmeticFilters =
      Chrome.getContext().getSharedPreferences("CosmeticFilter", Context.MODE_PRIVATE).getAll()
          as MutableMap<String, String>
  @Suppress("UNCHECKED_CAST")
  val userAgents =
      Chrome.getContext().getSharedPreferences("UserAgent", Context.MODE_PRIVATE).getAll()
          as MutableMap<String, String>

  private fun insert(vararg script: Script) {
    val dbHelper = ScriptDbHelper(Chrome.getContext())
    val db = dbHelper.writableDatabase
    script.forEach {
      val lines = db.delete("script", "id = ?", arrayOf(it.id))
      if (lines > 0) {
        Log.d("Update ${lines.toString()} rows with id ${it.id}")
      }
      val values =
          ContentValues().apply {
            put("id", it.id)
            put("code", it.code)
            put("meta", it.meta)
            put("storage", it.storage)
          }
      if (db.insert("script", null, values).toString() == "-1") {
        Log.e("Inserting script failed for: " + it.id)
      }
    }
    dbHelper.close()
  }

  fun updateScriptStorage() {
    val dbHelper = ScriptDbHelper(Chrome.getContext())
    val db = dbHelper.writableDatabase
    scripts.forEach {
      if (it.storage != "") {
        val values = ContentValues().apply { put("storage", it.storage) }
        if (db.update("script", values, "id = ?", arrayOf(it.id)).toString() == "-1") {
          Log.e("Updating scriptStorage failed for: " + it.id)
        } else {
          Log.d("ScriptStorage updated for " + it.id)
        }
      }
    }
    dbHelper.close()
  }

  private fun query(
      selection: String? = null,
      selectionArgs: Array<String>? = null,
  ): MutableList<Script> {
    val dbHelper = ScriptDbHelper(Chrome.getContext())
    val db = dbHelper.readableDatabase
    val cursor = db.query("script", null, selection, selectionArgs, null, null, null)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      (cursor as AbstractWindowedCursor).setWindow(CursorWindow("max", 1024 * 1024 * 10))
    }
    val quried_scripts = mutableListOf<Script>()
    with(cursor) {
      while (moveToNext()) {
        val id = getString(getColumnIndexOrThrow("id"))
        val meta = getString(getColumnIndexOrThrow("meta"))
        val code = getString(getColumnIndexOrThrow("code"))
        val storage = getString(getColumnIndexOrThrow("storage"))
        val script = parseScript(meta + code, storage)
        if (script != null && script.id == id) {
          quried_scripts.add(script)
        }
      }
    }
    cursor.close()
    dbHelper.close()
    return quried_scripts
  }

  private fun parseArray(str: String): Array<String> {
    val result = mutableListOf<String>()
    runCatching {
          val array = JSONArray(str)
          for (i in 0 until array.length()) {
            result.add(array.getString(i))
          }
        }
        .onFailure { Log.ex(it) }
    return result.toTypedArray()
  }

  private fun syncSharedPreference(
      payload: String,
      item: String,
      cache: MutableMap<String, String>
  ): String? {
    runCatching {
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
        }
        .onFailure {
          Log.e("Error parsing ${payload}, ")
          Log.ex(it)
        }
    return null
  }

  fun on(action: String, payload: String): String? {
    var callback: String? = null
    when (action) {
      "installScript" -> {
        val script = parseScript(payload)
        if (script == null) {
          callback = "alert('Invalid UserScript');"
        } else {
          Log.i("Install script ${script.id}")
          insert(script)
          scripts.removeAll(scripts.filter { it.id == script.id })
          scripts.add(script)
        }
      }
      "installDefault" -> {
        val code = Chrome.getContext().assets.open(payload).bufferedReader().use { it.readText() }
        if (on("installScript", code) != null) {
          callback = "alert('Fail to install ${payload}');"
        }
      }
      "scriptStorage" -> {
        Chrome.broadcast("scriptStorage", "{detail:${payload}}")
        runCatching {
              val detail = JSONObject(payload)
              val id = detail.getString("id")
              val data = detail.getJSONObject("data")
              val key = data.getString("key")
              scripts
                  .filter { it.id == id }
                  .first()
                  .apply {
                    val json = if (storage == "") JSONObject() else JSONObject(storage)
                    if (data.has("value")) {
                      json.put(key, data.get("value"))
                    } else {
                      json.remove(key)
                    }
                    storage = json.toString()
                  }
            }
            .onFailure {
              Log.d("Failure with scriptStorage: " + payload)
              Log.ex(it)
            }
      }
      "abortRequest" -> {
        val uuid = payload.toDouble()
        xmlhttpRequests.get(uuid)?.abort()
      }
      "xmlhttpRequest" -> {
        runCatching {
          val detail = JSONObject(payload)
          val uuid = detail.getDouble("uuid")
          val request =
              XMLHttpRequest(detail.getString("id"), detail.getJSONObject("request"), uuid)
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
          callback = FileReader(eruda).use { it.readText() } + "\n"
          callback += ctx.assets.open("local_eruda.js").bufferedReader().use { it.readText() }
          callback += "eruda.init(); eruda._localConfig(); eruda.show();"
        } else {
          Log.toast(ctx, "Updating Eruda...")
          Download.start(ERUD_URL, "Download/Eruda.js", true) {
            if (UserScriptHook.isInit) {
              UserScriptProxy.evaluateJavascript(on("loadEruda", "")!!)
            } else if (WebViewHook.isInit) {
              WebViewHook.evaluateJavascript(on("loadEruda", "")!!)
            }
          }
        }
      }
      "getIds" -> {
        val result = scripts.map { it.id }
        callback =
            "window.dispatchEvent(new CustomEvent('script_id',{detail:[`${result.joinToString(separator = "`,`")}`]}));"
      }
      "getMeta" -> {
        val ids = parseArray(payload)
        val result = scripts.filter { ids.contains(it.id) }.map { it.meta }
        callback =
            "window.dispatchEvent(new CustomEvent('script_meta',{detail:[`${result.joinToString(separator = "`,`")}`]}));"
      }
      "updateMeta" -> {
        runCatching {
          val data = JSONObject(payload)
          val script = scripts.filter { it.id == data.getString("id") }.first()
          val newScript = parseScript(data.getString("meta") + script.code, script.storage)!!
          insert(newScript)
          scripts.remove(script)
          scripts.add(newScript)
        }
      }
      "deleteScript" -> {
        val ids = parseArray(payload)
        val dbHelper = ScriptDbHelper(Chrome.getContext())
        val db = dbHelper.writableDatabase
        db.delete("script", "id = ?", ids)
        scripts.removeAll(scripts.filter { ids.contains(it.id) })
        dbHelper.close()
      }
      "cosmeticFilter" -> {
        callback = syncSharedPreference(payload, "CosmeticFilter", cosmeticFilters)
      }
      "userAgent" -> {
        callback = syncSharedPreference(payload, "UserAgent", userAgents)
      }
    }
    return callback
  }
}

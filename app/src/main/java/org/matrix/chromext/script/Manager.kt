package org.matrix.chromext.script

import android.content.ContentValues
import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.CursorWindow
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.invokeMethod
import org.matrix.chromext.utils.isChromeXtFrontEnd
import org.matrix.chromext.utils.isDevToolsFrontEnd
import org.matrix.chromext.utils.isUserScript
import org.matrix.chromext.utils.matching
import org.matrix.chromext.utils.parseOrigin
import org.matrix.chromext.utils.resolveContentUrl
import org.matrix.chromext.utils.shouldBypassSandbox

object ScriptDbManager {

  val scripts = query()
  val cosmeticFilters: MutableMap<String, String>
  val userAgents: MutableMap<String, String>
  val cspRules: MutableMap<String, String>
  var keepStorage: Boolean

  init {
    val ctx = Chrome.getContext()
    @Suppress("UNCHECKED_CAST")
    cosmeticFilters =
        ctx.getSharedPreferences("CosmeticFilter", Context.MODE_PRIVATE).getAll()
            as MutableMap<String, String>
    @Suppress("UNCHECKED_CAST")
    userAgents =
        ctx.getSharedPreferences("UserAgent", Context.MODE_PRIVATE).getAll()
            as MutableMap<String, String>
    @Suppress("UNCHECKED_CAST")
    cspRules =
        ctx.getSharedPreferences("CSPRule", Context.MODE_PRIVATE).getAll()
            as MutableMap<String, String>

    keepStorage =
        ctx.getSharedPreferences("ChromeXt", Context.MODE_PRIVATE).getBoolean("keep_storage", true)
  }

  fun insert(vararg script: Script) {
    val dbHelper = ScriptDbHelper(Chrome.getContext())
    val db = dbHelper.writableDatabase
    script.forEach {
      val lines = db.delete("script", "id = ?", arrayOf(it.id))
      if (lines > 0) {
        val id = it.id
        Log.d("Update ${lines} rows with id ${id}")
        if (keepStorage) it.storage = scripts.find { it.id == id }?.storage
      }
      val values =
          ContentValues().apply {
            put("id", it.id)
            put("code", it.code)
            put("meta", it.meta)
            if (it.storage != null) {
              put("storage", it.storage.toString())
            }
          }
      runCatching { db.insertOrThrow("script", null, values) }
          .onFailure {
            Log.e("Fail to store script ${values.getAsString("id")} into SQL database.")
            Log.ex(it)
          }
    }
    dbHelper.close()
  }

  private fun fixEncoding(url: String, path: String, codes: MutableList<String>) {
    if (path.endsWith(".js") || path.endsWith(".txt")) {
      // Fix encoding for local text files
      val inputStream = Chrome.getContext().contentResolver.openInputStream(Uri.parse(url))
      val text = inputStream?.bufferedReader()?.readText()
      if (text != null) {
        val data = JSONObject(mapOf("utf-8" to text))
        codes.add("window.content=${data};")
        codes.add(Local.encoding)
      }
      inputStream?.close()
    } else if (url.endsWith(".js") || url.endsWith(".txt")) {
      // Fix encoding for remote text files
      codes.add(Local.encoding)
    }

    if (codes.size > 1 && (url.endsWith(".txt") || path.endsWith(".txt")))
        codes.add("fixEncoding();")
  }

  fun invokeScript(url: String, webView: Any? = null) {
    val codes = mutableListOf<String>(Local.initChromeXt)
    val path = resolveContentUrl(url)
    val webSettings = webView?.invokeMethod { name == "getSettings" }

    var trustedPage = true
    // Whether ChromeXt is accessible in the global context
    var runScripts = false
    // Whether UserScripts are invoked
    var bypassSandbox = false

    fixEncoding(url, path, codes)

    if (isUserScript(url, path)) {
      trustedPage = false
      codes.add(Local.promptInstallUserScript)
      bypassSandbox = shouldBypassSandbox(url)
    } else if (isDevToolsFrontEnd(url)) {
      codes.add(Local.customizeDevTool)
      webSettings?.invokeMethod(null) { name == "setUserAgentString" }
    } else if (!isChromeXtFrontEnd(url)) {
      val origin = parseOrigin(url)
      if (origin != null) {
        if (cspRules.contains(origin)) {
          runCatching {
            val rule = JSONArray(cspRules.get(origin))
            codes.add("Symbol.ChromeXt.cspRules.push(...${rule});${Local.cspRule}")
          }
        }
        if (cosmeticFilters.contains(origin)) {
          runCatching {
            val filter = JSONArray(cosmeticFilters.get(origin))
            codes.add("Symbol.ChromeXt.filters.push(...${filter});${Local.cosmeticFilter}")
          }
        }
        if (userAgents.contains(origin)) {
          val agent = userAgents.get(origin)
          codes.add("Object.defineProperties(window.navigator,{userAgent:{value:'${agent}'}});")
          webSettings?.invokeMethod(agent) { name == "setUserAgentString" }
        }
        trustedPage = false
        runScripts = true
      }
    }

    if (trustedPage) {
      codes.add("globalThis.ChromeXt = Symbol.ChromeXt;")
    } else if (runScripts) {
      codes.add("Symbol.ChromeXt.lock(${Local.key}, '${Local.name}');")
    }
    codes.add("//# sourceURL=local://ChromeXt/init")
    val code = codes.joinToString("\n")
    webSettings?.invokeMethod(true) { name == "setJavaScriptEnabled" }
    Chrome.evaluateJavascript(listOf(code), webView, bypassSandbox, bypassSandbox)
    if (runScripts) {
      codes.clear()
      scripts.filter { matching(it, url) }.forEach { GM.bootstrap(it, codes) }
      Chrome.evaluateJavascript(codes)
    }
  }

  fun updateScriptStorage() {
    val dbHelper = ScriptDbHelper(Chrome.getContext())
    val db = dbHelper.writableDatabase
    scripts.forEach {
      if (it.storage != null) {
        val values = ContentValues().apply { put("storage", it.storage.toString()) }
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
      (cursor as AbstractWindowedCursor).setWindow(CursorWindow("max", 1024 * 1024 * 10.toLong()))
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
}
